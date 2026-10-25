package co.wethinkcode.trafficflow;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

/**
 * Applies a level update and, only when the level really changed, announces it on the topic.
 * Keeping this out of the HTTP handler makes the "publish on change" rule easy to test.
 */
public final class CongestionService {

    public record Update(int level, int previousLevel, boolean changed, boolean published) {
    }

    private final CongestionStore store;
    private final CongestionPublisher publisher;
    private final Clock clock;

    public CongestionService(CongestionStore store, CongestionPublisher publisher, Clock clock) {
        this.store = store;
        this.publisher = publisher;
        this.clock = clock;
    }

    /** @throws IllegalArgumentException if the level is outside 0-8 (nothing is stored or published) */
    public Update update(int level) {
        int previous = store.set(level);
        boolean changed = previous != level;
        boolean published = false;
        if (changed) {
            CongestionChange message = new CongestionChange(level, clock.instant().truncatedTo(ChronoUnit.SECONDS).toString());
            try {
                publisher.publish(message);
                published = true;
            } catch (Exception e) {
                // The REST state change succeeded; the broker being down must not fail the request.
                System.err.println("Could not publish " + message + ": " + e.getMessage());
            }
        }
        return new Update(level, previous, changed, published);
    }
}
