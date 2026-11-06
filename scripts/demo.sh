#!/usr/bin/env bash
# End-to-end demo for TrafficFlow. Start the broker and all five services first (see README "Run").
# Tip: start intersection-service and intersection-watchdog with HEARTBEAT_INTERVAL_SECONDS=2 for a quicker demo.
# Usage: ./scripts/demo.sh
set -u

c() { echo "\$ curl $*"; curl -s -m 5 -w '   [HTTP %{http_code}]\n' "$@"; echo; }
say() { echo; echo "### $*"; }

say "1. Health of all five services"
for p in 7020 7021 7022 7023 7024; do printf 'port %s -> ' "$p"; curl -s -m 3 "localhost:$p/health"; echo; done

say "2. Stage 1 - ingestion cleaned the messy CSV (18 rows -> 17 intersections)"
c localhost:7020/report
c localhost:7020/intersections/int-1005

say "3. Stage 2 - intersection-service validates names over REST"
c "localhost:7021/intersections?district=Downtown"
c localhost:7021/districts/Atlantis                 # 404

say "4. Route estimate with no congestion"
c "localhost:7023/route?from=INT-1001&to=INT-1005"

say "5. Stage 3 - raise congestion; congestion-service publishes to congestion-topic"
c -X POST -H 'Content-Type: application/json' -d '{"level":4}' localhost:7022/congestion
sleep 1
c localhost:7023/congestion                        # routing-service learned it from the topic
c "localhost:7023/route?from=INT-1001&to=INT-1005"

say "6. Validation and error semantics"
c -X POST -H 'Content-Type: application/json' -d '{"level":9}' localhost:7022/congestion          # 400
c "localhost:7023/route?from=INT-1001&to=INT-9999"                                                # 404
c "localhost:7023/route?from=INT-1001&to=INT-1003"                                                # 409 inactive

say "7. Stage 4 - the watchdog is receiving heartbeats"
c localhost:7021/heartbeat
c localhost:7024/alert

echo "Decoupling proof: stop congestion-service, then run: curl 'localhost:7023/route?from=INT-1001&to=INT-1002'  (still answers)"
echo "Watchdog proof:   stop intersection-service, wait 3 heartbeat intervals, then run: curl localhost:7024/alert  (state DOWN)"
