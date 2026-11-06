# IntersectionServiceApp

## Overview

Validates intersection/district names (source of truth).

Part of the [TrafficFlow](../README.md) project. Independent Maven module, no
parent pom.

MQ: this service publishes to the ActiveMQ queue `intersection-heartbeat-queue` — see [`../common/`](../common). Broker URL and queue name come from the common `co.wethinkcode.trafficflow.mq.MqConfig` class alongside it in this module.

## Project structure

```
intersection-service/
├── pom.xml
└── src/main/java/co/wethinkcode/trafficflow/
    ├── IntersectionServiceApp.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/intersection-service.jar
```

Listens on port `7021`. Loads cleaned data from ingestion-service (`INGESTION_URL`). Endpoints:
`GET /intersections[?district=X]`, `GET /intersections/{id}`, `GET /districts`, `GET /districts/{name}`,
`GET /heartbeat`, `POST /refresh`. Sends a heartbeat to `intersection-heartbeat-queue` every
`HEARTBEAT_INTERVAL_SECONDS` (default 5).

## Test

Run `mvn test`. To check a running instance:

```
curl http://localhost:7021/health   # -> OK
```

