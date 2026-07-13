# Incident Service

## Low-Level Design (LLD) & In-Depth Overview

The **Incident Service** is the central nervous system for ticketing and alert persistence.

### Key Responsibilities
1. **Event Consumption**: Consumes all alerts from the `enriched-alerts` Kafka topic (from both `log-analyzer` and `repo-scanner`).
2. **Deduplication & Persistence**: Checks MongoDB to see if an active incident already exists for the given fingerprint. If not, it creates a new record in `devops_incidents`.
3. **Data Serving**: Provides REST endpoints for the React frontend to fetch, filter, and view active incidents.
4. **Notification Dispatch**: Whenever a new incident is created, it publishes an event to the `incident-notifications` Kafka topic.

### How to Interact
- **Port**: `8084` (Internal Docker port)
- **Database**: `devops_incidents` (MongoDB)
- **Fetch Incidents**: `GET /api/v1/incidents` (Supports filtering by repository or severity)
- **Kafka Topics**: Consumes `enriched-alerts`, Produces to `incident-notifications`.
