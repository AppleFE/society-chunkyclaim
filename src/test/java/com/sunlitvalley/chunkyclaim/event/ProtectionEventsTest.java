package com.sunlitvalley.chunkyclaim.event;

import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.eventbus.api.Event;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtectionEventsTest {
    @Test
    void mobGriefingProtectionUsesDenyResult() {
        EntityMobGriefingEvent event = new EntityMobGriefingEvent(null);

        assertDoesNotThrow(() -> ProtectionEvents.denyMobGriefing(event));
        assertEquals(Event.Result.DENY, event.getResult());
    }
}
