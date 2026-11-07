package com.foodrush.app.call;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class CallSessionTest {

    private static final String METERED = "stun:stun.relay.metered.ca:80, turn:global.relay.metered.ca:80,"
            + "turn:global.relay.metered.ca:80?transport=tcp,turn:global.relay.metered.ca:443,"
            + "turns:global.relay.metered.ca:443?transport=tcp";

    @Test
    public void splitsRelaysFromStunServers() {
        assertEquals(Arrays.asList("stun:stun.relay.metered.ca:80"), CallSession.urlsOfKind(METERED, false));
        assertEquals(Arrays.asList("turn:global.relay.metered.ca:80", "turn:global.relay.metered.ca:80?transport=tcp",
                        "turn:global.relay.metered.ca:443", "turns:global.relay.metered.ca:443?transport=tcp"),
                CallSession.urlsOfKind(METERED, true));
    }

    @Test
    public void emptyOrJunkListsGiveNothing() {
        assertTrue(CallSession.urlsOfKind("", true).isEmpty());
        assertTrue(CallSession.urlsOfKind("http://example.com", true).isEmpty());
    }
}
