# IntersectionWatchdogApp

## Overview

Cries for help if the Intersection Service crashes, since routes can no longer be validated.

Part of the [TrafficFlow](../README.md) project — its alerting service.
Independent Maven module, no parent pom.

MQ: this service subscribes to the ActiveMQ queue `intersection-heartbeat-queue` — see [`../common/`](../common). Broker URL and queue name come from the common `co.wethinkcode.trafficflow.mq.MqConfig` class alongside it in this module. Mechanism: watch for missed heartbeats and/or dead-lettered messages from `intersection-service` and raise an alert.

## Project structure

```
intersection-watchdog/
├── pom.xml
└── src/main/java/co/wethinkcode/trafficflow/
    ├── IntersectionWatchdogApp.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/intersection-watchdog.jar
```

Listens on port `7024`. Consumes `intersection-heartbeat-queue` and `ActiveMQ.DLQ`. `GET /alert` returns the state
(`UNKNOWN`, `UP`, `DOWN`), the last heartbeat and the alert history. It declares the service DOWN after
`MAX_MISSED_BEATS` (default 3) x `HEARTBEAT_INTERVAL_SECONDS` of silence, alerts once, and reports RECOVERED when beats resume.

## Test

Run `mvn test`. To check a running instance:

```
curl http://localhost:7024/health   # -> OK
```

