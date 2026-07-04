CREATE DATABASE uber_driver_service;
CREATE DATABASE uber_rider_service;
CREATE DATABASE uber_trip_service;
CREATE DATABASE uber_payment_service;

\c uber_driver_service;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

\c uber_rider_service;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\c uber_trip_service;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

\c uber_payment_service;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
