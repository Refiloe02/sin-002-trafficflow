# RoutingServiceApp

## Overview

Provides estimated travel times based on congestion and intersection.

Part of the [TrafficFlow](../README.md) project. Independent Maven module, no
parent pom.

MQ: this service subscribes to the ActiveMQ topic `congestion-topic` — see [`../common/`](../common). Broker URL and topic name come from the common `co.wethinkcode.trafficflow.mq.MqConfig` class alongside it in this module.

## Project structure

```
routing-service/
├── pom.xml
└── src/main/java/co/wethinkcode/trafficflow/
    ├── RoutingServiceApp.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/routing-service.jar
```

Listens on port `7023`. `GET /route?from=INT-1001&to=INT-1005` estimates travel time (12 min base, +6 across districts,
signal delays, x(1 + 0.25 x congestion level)). Intersections are validated over REST (`INTERSECTION_URL`); the congestion
level arrives through a durable subscription to `congestion-topic`. `GET /congestion` shows what has been received.

## Test

Run `mvn test`. To check a running instance:

```
curl http://localhost:7023/health   # -> OK
```

