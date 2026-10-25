package co.wethinkcode.trafficflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CongestionStoreTest {

    private final CongestionStore store = new CongestionStore();

    @Test
    void startsFreeFlowing() {
        assertEquals(0, store.get());
    }

    @Test
    void setReturnsThePreviousLevel() {
        assertEquals(0, store.set(3));
        assertEquals(3, store.set(7));
        assertEquals(7, store.get());
    }

    @Test
    void boundariesAreAccepted() {
        store.set(0);
        store.set(8);
        assertEquals(8, store.get());
    }

    @Test
    void outOfRangeLevelsAreRejectedAndStateIsKept() {
        store.set(2);
        assertThrows(IllegalArgumentException.class, () -> store.set(-1));
        assertThrows(IllegalArgumentException.class, () -> store.set(9));
        assertEquals(2, store.get());
    }
}
