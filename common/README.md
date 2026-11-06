# common — Asynchronous Decoupling (MQ)

## Overview

Part of the [TrafficFlow](../README.md) project. Holds the ActiveMQ broker shared
by the services below — not a service itself, so it has no port of its own. Two
independent integration points share this one broker:

### Topic: `congestion-topic`

Routing Service becomes aware of congestion changes via an ActiveMQ Topic instead of querying Congestion Service directly.

- Producer: `congestion-service` (`../congestion-service`)
- Consumer(s): `routing-service` (`../routing-service`)

Broker URL and topic name are shared via a common `co.wethinkcode.trafficflow.mq.MqConfig` class
(`BROKER_URL`, `TOPIC`). It's identical in every participating service's own source
tree — each service here is an independent Maven project with no shared parent pom,
so the common package is duplicated rather than imported from one place.

### Queue: `intersection-heartbeat-queue`

Intersection Watchdog notices when Intersection Service goes down by watching for
missed heartbeats / dead-lettered messages instead of polling its `/health` endpoint.

- Producer: `intersection-service` (`../intersection-service`)
- Consumer(s): `intersection-watchdog` (`../intersection-watchdog`)

Broker URL and queue name are shared the same way, via each service's own copy of
`co.wethinkcode.trafficflow.mq.MqConfig` (`BROKER_URL`, `HEARTBEAT_QUEUE`).

## Project structure

```
common/
├── docker-compose.yml
└── README.md
```

This folder holds the broker config and notes only — the actual publish/subscribe
code belongs in the producer/consumer services listed above (their poms already
depend on `activemq-client`, and each already has its own
`src/main/java/co/wethinkcode/trafficflow/mq/MqConfig.java` with the constants for
whichever topic/queue that service participates in).

## Build

Nothing to build here directly — this folder just brings up the broker used by the
services listed above.

## Run

```
docker compose up -d
```

- Broker URL for clients: `tcp://localhost:61616`
- Web console: http://localhost:8161 (default admin/admin)

Then start the producer/consumer services as usual (`mvn package && java -jar ...`
from their own directories at the project root).

## Test

```
docker compose ps          # confirm the broker container is healthy
```

Once the TODOs below are implemented, verify end-to-end by publishing a message from
`congestion-service` and confirming the consumer(s) receive it — e.g. via logs, or by
watching the topic in the web console.

## Status

- `congestion-service` publishes persistent JSON messages to `congestion-topic` whenever the level really changes.
- `routing-service` holds a **durable** subscription (client id `routing-service`), so updates published while it
  is down are delivered when it reconnects.
- `intersection-service` sends a non-persistent heartbeat (with a time-to-live) to `intersection-heartbeat-queue`;
  `intersection-watchdog` consumes it and also listens on `ActiveMQ.DLQ` for heartbeats that expired.
- Watch it live in the web console: http://localhost:8161 -> Topics / Queues.
