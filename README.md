# Uber Backend — Production-Grade Java Microservices

## Architecture

```
                        ┌──────────────────────────────────────────────────────┐
                        │                    API Gateway :8080                  │
                        │   JWT Auth • Rate Limiting • Circuit Breaker • CORS   │
                        └────────────────────────┬─────────────────────────────┘
                                                  │
              ┌───────────┬───────────┬───────────┼───────────┬───────────┬───────────┐
              ▼           ▼           ▼           ▼           ▼           ▼           ▼
        Driver:8081  Rider:8082  Trip:8083  Payment:8084  Notif:8085  Location:8086  ETA:8088
              │           │           │           │           │           │
              └───────────┴───────────┴───────────┴───────────┴───────────┘
                                                  │
                    ┌─────────────────────────────┼───────────────────────────┐
                    ▼                             ▼                           ▼
              Kafka Cluster                   Redis Cluster             PostgreSQL
         (12+ partitions/topic)          (GEO + Hash + ZSet)      (4 separate DBs)
```

## Services

| Service | Port | DB | Description |
|---|---|---|---|
| api-gateway | 8080 | — | JWT auth, rate limiting, routing, circuit breaking |
| driver-service | 8081 | PostgreSQL | Driver CRUD, status, trip acceptance |
| rider-service | 8082 | PostgreSQL | Rider CRUD, payment methods, ratings |
| trip-service | 8083 | PostgreSQL | Trip lifecycle state machine |
| payment-service | 8084 | PostgreSQL | Stripe integration, idempotency, retries |
| notification-service | 8085 | — | FCM push, Twilio SMS, WebSocket |
| location-service | 8086 | — | Redis GEO, GeoHash indexing, WebSocket streaming |
| matching-service | 8087 | — | Hungarian algo, surge pricing, driver scoring |
| eta-service | 8088 | — | Dijkstra, A*, traffic-aware ETA with cache |

## Key Algorithms

### GeoSpatial Indexing
- **Redis GEO commands** (`GEOADD`, `GEORADIUS`) — O(N+log M) nearest neighbor search
- **GeoHash** dual-index for O(1) cell lookup + neighbor expansion
- 6-character precision (~1.2km cells), 8-character neighbor expansion

### Driver Matching
- **Multi-factor scoring**: distance (40%) + rating (25%) + acceptance rate (15%) + trip count (10%) + wait time (10%)
- **Sequential offering**: top-scored driver offered first, 30s timeout, then next
- **Hungarian Algorithm**: batch optimal 1:1 assignment for surge periods (O(n³))
- **Distributed locking**: Redis `SETNX` prevents race conditions across instances

### Surge Pricing
- Demand/supply ratio per GeoHash cell (5-char ≈ 5km cells)
- `log` curve: multiplier = 1 + log(ratio/threshold) × 2
- Snapped to nearest 0.1x, capped at 8.0x
- 5-min rolling window, 2-min multiplier cache

### ETA
- **Haversine** + road factor heuristic for fast estimates
- **Dijkstra** for exact shortest-path on road graph
- **A\*** for path reconstruction with geographic heuristic
- Traffic multiplier: 1.5× peak hours, 0.8× late night
- Grid-snapped cache key (0.001° ≈ 100m) with 60s TTL

### Trip State Machine
```
REQUESTED → DRIVER_ASSIGNED → DRIVER_ARRIVING → DRIVER_ARRIVED → IN_PROGRESS → PAYMENT_PENDING → COMPLETED
     ↓              ↓                ↓                 ↓               ↓
  CANCELLED      CANCELLED       CANCELLED         CANCELLED        (never)
```

## Kafka Topics

| Topic | Partitions | Retention | Publisher | Consumers |
|---|---|---|---|---|
| `trip.requests` | 12 | 24h | trip-service | matching-service |
| `driver.matched` | 12 | 24h | matching-service | trip-service, notification-service |
| `trip.status` | 12 | 24h | trip-service | notification-service, rider-service |
| `location.updates` | 24 | 1h | driver-service | location-service |
| `payment.events` | 6 | 7d | trip-service, payment-service | payment-service, notification-service |
| `notifications` | 6 | 24h | any service | notification-service |
| `driver.availability` | 12 | 1h | driver-service | matching-service |
| `surge.pricing` | 6 | 24h | matching-service | — |

## Redis Key Patterns

| Pattern | Type | TTL | Usage |
|---|---|---|---|
| `drivers:geo` | GEO | — | All active driver coordinates |
| `driver:location:{id}` | Hash | 30s | lat/lon/ts/geohash per driver |
| `geohash:drivers:{hash}` | Set | — | Driver IDs per GeoHash cell |
| `driver:active:{id}` | String | 4h | Lock: driver on trip |
| `driver:metrics:{id}` | Hash | 24h | rating/trips/acceptance |
| `match:pending:{tripId}` | Hash | 5m | In-flight match state |
| `surge:demand:{hash}` | String | 5m | Ride request count |
| `surge:supply:{hash}` | String | 5m | Available driver count |
| `surge:multiplier:{hash}` | String | 2m | Computed surge |
| `eta:cache:{key}` | String | 60s | Grid-snapped ETA result |
| `rate:{userId}:{minute}` | String | 2m | Rate limit counter |
| `payment:lock:{tripId}` | String | 5m | Idempotency lock |
| `rider:payment:{id}` | String | — | Default payment method |

## Running

```bash
# Full stack with Docker Compose
docker-compose up -d

# Individual service
cd driver-service && mvn spring-boot:run

# Kubernetes
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/infra.yaml
kubectl apply -f k8s/deployments.yaml
```

## API Endpoints

### Rider flow
```
POST /api/riders                          # Register rider
PUT  /api/riders/{id}/payment-method      # Set payment method
POST /api/trips                           # Request trip
GET  /api/eta/estimate?originLat=...      # Get fare + ETA estimate
WS   /ws/notifications                    # Subscribe to real-time updates
WS   /ws/location → /topic/trip/{id}/location  # Track driver
```

### Driver flow
```
POST   /api/drivers                        # Register driver
PATCH  /api/drivers/{id}/online            # Go online
PUT    /api/drivers/{id}/location          # Push location update
POST   /api/drivers/{id}/trips/{id}/accept # Accept trip offer
POST   /api/drivers/{id}/trips/{id}/decline
PATCH  /api/trips/{id}/status             # Update trip status
```

### Trip lifecycle
```
POST  /api/trips                           # Create (REQUESTED)
PATCH /api/trips/{id}/status {"status":"DRIVER_ARRIVED"}
PATCH /api/trips/{id}/status {"status":"IN_PROGRESS"}
PATCH /api/trips/{id}/status {"status":"PAYMENT_PENDING"}
PATCH /api/trips/{id}/status {"status":"COMPLETED"}
POST  /api/trips/{id}/cancel
POST  /api/trips/{id}/rate
```
