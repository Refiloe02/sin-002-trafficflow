# CongestionServiceApp

## Overview

Tracks the city-wide Congestion Level (0-8).

Part of the [TrafficFlow](../README.md) project. Independent Maven module, no
parent pom.

MQ: this service publishes to the ActiveMQ topic `congestion-topic` — see [`../common/`](../common). Broker URL and topic name come from the common `co.wethinkcode.trafficflow.mq.MqConfig` class alongside it in this module.

## Project structure

```
congestion-service/
├── pom.xml
└── src/main/java/co/wethinkcode/trafficflow/
    ├── CongestionServiceApp.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/congestion-service.jar
```

Listens on port `7022`. `GET /congestion` returns `{"level": n}`; `POST /congestion` with `{"level": 4}` updates it and,
when the level really changed, publishes `{"level":4,"timestamp":"..."}` to `congestion-topic`.

## Test

Run `mvn test`. To check a running instance:

```
curl http://localhost:7022/health   # -> OK
```

