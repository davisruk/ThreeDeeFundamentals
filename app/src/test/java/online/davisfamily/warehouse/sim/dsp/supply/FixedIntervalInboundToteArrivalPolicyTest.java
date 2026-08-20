package online.davisfamily.warehouse.sim.dsp.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class FixedIntervalInboundToteArrivalPolicyTest {

    @Test
    void shouldExposePeakAndRepresentativeBusyIntervals() {
        FixedIntervalInboundToteArrivalPolicy peak = FixedIntervalInboundToteArrivalPolicy.peak();
        FixedIntervalInboundToteArrivalPolicy busy =
                FixedIntervalInboundToteArrivalPolicy.representativeBusyHour();

        assertEquals("FIXED_PEAK_1200_PER_HOUR", peak.policyId());
        assertEquals(Duration.ofSeconds(3), peak.interval());
        assertEquals("FIXED_BUSY_400_PER_HOUR", busy.policyId());
        assertEquals(Duration.ofSeconds(9), busy.interval());
    }

    @Test
    void shouldReturnConfiguredIntervalDeterministically() {
        FixedIntervalInboundToteArrivalPolicy policy =
                new FixedIntervalInboundToteArrivalPolicy("test", Duration.ofSeconds(7));
        InboundToteManifest manifest = manifest();

        assertEquals(Duration.ofSeconds(7), policy.intervalBeforeNextTote(manifest, 0));
        assertEquals(Duration.ofSeconds(7), policy.intervalBeforeNextTote(manifest, 42));
    }

    @Test
    void shouldRejectInvalidArrivalPolicyInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> new FixedIntervalInboundToteArrivalPolicy(null, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new FixedIntervalInboundToteArrivalPolicy(" ", Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new FixedIntervalInboundToteArrivalPolicy("test", null));
        assertThrows(IllegalArgumentException.class,
                () -> new FixedIntervalInboundToteArrivalPolicy("test", Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new FixedIntervalInboundToteArrivalPolicy("test", Duration.ofSeconds(-1)));

        FixedIntervalInboundToteArrivalPolicy policy =
                new FixedIntervalInboundToteArrivalPolicy("test", Duration.ofSeconds(1));
        assertThrows(IllegalArgumentException.class,
                () -> policy.intervalBeforeNextTote(null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> policy.intervalBeforeNextTote(manifest(), -1));
    }

    private static InboundToteManifest manifest() {
        return new InboundToteManifest(
                new PhysicalToteId("tote-1"),
                new OrderSheetKey("order-1", 1),
                OrderType.FULL_PACK,
                "104",
                java.util.List.of(new DspOrderItem("line-1", "product-1", 1)),
                0);
    }
}
