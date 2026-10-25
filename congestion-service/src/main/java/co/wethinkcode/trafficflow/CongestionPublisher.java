package co.wethinkcode.trafficflow;

/** Something that can announce a congestion change to other services. The real one talks to ActiveMQ. */
public interface CongestionPublisher {

    void publish(CongestionChange change) throws Exception;
}
