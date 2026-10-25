package co.wethinkcode.trafficflow;

/** Something that can send one heartbeat. The real one talks to ActiveMQ. */
public interface HeartbeatPublisher {

    void publish(Heartbeat heartbeat) throws Exception;
}
