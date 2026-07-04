package com.uber.eta.algorithm;

import com.uber.common.dto.LocationDto;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ETAEngine {

    private static final double AVG_SPEED_KMH = 30.0;
    private static final double TRAFFIC_BUFFER_FACTOR = 1.3;
    private static final double EARTH_RADIUS_KM = 6371.0;

    public ETAResult calculateETA(LocationDto origin, LocationDto destination) {
        double straightLineKm = haversineKm(origin, destination);
        double roadFactor = estimateRoadFactor(origin, destination);
        double roadDistanceKm = straightLineKm * roadFactor;
        double travelTimeHours = roadDistanceKm / AVG_SPEED_KMH;
        double trafficMultiplier = getTrafficMultiplier();
        double etaMinutes = travelTimeHours * 60 * trafficMultiplier;

        return new ETAResult(
            (int) Math.ceil(etaMinutes),
            roadDistanceKm,
            (int) Math.ceil(etaMinutes * 60)
        );
    }

    public int[] dijkstraETA(RoadGraph graph, int sourceNode, int destNode) {
        int n = graph.nodeCount();
        double[] dist = new double[n];
        boolean[] visited = new boolean[n];
        Arrays.fill(dist, Double.MAX_VALUE);
        dist[sourceNode] = 0;

        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingDouble(e -> dist[e[0]]));
        pq.offer(new int[]{sourceNode, 0});

        while (!pq.isEmpty()) {
            int[] curr = pq.poll();
            int u = curr[0];
            if (visited[u]) continue;
            visited[u] = true;

            if (u == destNode) break;

            for (RoadGraph.Edge edge : graph.neighbors(u)) {
                double newDist = dist[u] + edge.weight();
                if (newDist < dist[edge.to()]) {
                    dist[edge.to()] = newDist;
                    pq.offer(new int[]{edge.to(), (int) newDist});
                }
            }
        }

        return new int[]{(int) dist[destNode]};
    }

    public List<Integer> aStarPath(RoadGraph graph, int source, int dest, LocationDto[] nodeLocations) {
        int n = graph.nodeCount();
        double[] gScore = new double[n];
        double[] fScore = new double[n];
        int[] cameFrom = new int[n];
        Arrays.fill(gScore, Double.MAX_VALUE);
        Arrays.fill(fScore, Double.MAX_VALUE);
        Arrays.fill(cameFrom, -1);
        gScore[source] = 0;
        fScore[source] = heuristic(nodeLocations[source], nodeLocations[dest]);

        PriorityQueue<Integer> open = new PriorityQueue<>(Comparator.comparingDouble(i -> fScore[i]));
        open.add(source);
        Set<Integer> closed = new HashSet<>();

        while (!open.isEmpty()) {
            int current = open.poll();
            if (current == dest) return reconstructPath(cameFrom, dest);
            closed.add(current);

            for (RoadGraph.Edge edge : graph.neighbors(current)) {
                if (closed.contains(edge.to())) continue;
                double tentativeG = gScore[current] + edge.weight();
                if (tentativeG < gScore[edge.to()]) {
                    cameFrom[edge.to()] = current;
                    gScore[edge.to()] = tentativeG;
                    fScore[edge.to()] = tentativeG + heuristic(nodeLocations[edge.to()], nodeLocations[dest]);
                    open.remove(edge.to());
                    open.add(edge.to());
                }
            }
        }
        return Collections.emptyList();
    }

    private double heuristic(LocationDto a, LocationDto b) {
        return haversineKm(a, b) / AVG_SPEED_KMH * 60;
    }

    private List<Integer> reconstructPath(int[] cameFrom, int current) {
        List<Integer> path = new ArrayList<>();
        while (current != -1) {
            path.add(0, current);
            current = cameFrom[current];
        }
        return path;
    }

    private double haversineKm(LocationDto a, LocationDto b) {
        double lat1 = Math.toRadians(a.getLatitude());
        double lat2 = Math.toRadians(b.getLatitude());
        double dLat = Math.toRadians(b.getLatitude() - a.getLatitude());
        double dLon = Math.toRadians(b.getLongitude() - a.getLongitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
    }

    private double estimateRoadFactor(LocationDto a, LocationDto b) {
        double latDiff = Math.abs(a.getLatitude() - b.getLatitude());
        double lonDiff = Math.abs(a.getLongitude() - b.getLongitude());
        if (latDiff < 0.01 && lonDiff < 0.01) return 1.3;
        if (latDiff < 0.05 && lonDiff < 0.05) return 1.25;
        return 1.2;
    }

    private double getTrafficMultiplier() {
        int hour = java.time.LocalTime.now().getHour();
        if ((hour >= 7 && hour <= 9) || (hour >= 17 && hour <= 19)) return 1.5;
        if (hour >= 23 || hour <= 5) return 0.8;
        return TRAFFIC_BUFFER_FACTOR;
    }

    public record ETAResult(int etaMinutes, double distanceKm, int etaSeconds) {}

    public interface RoadGraph {
        List<Edge> neighbors(int node);
        int nodeCount();
        record Edge(int to, double weight) {}
    }
}
