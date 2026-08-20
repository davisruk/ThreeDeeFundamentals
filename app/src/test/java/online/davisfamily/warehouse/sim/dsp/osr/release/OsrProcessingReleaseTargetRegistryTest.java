package online.davisfamily.warehouse.sim.dsp.osr.release;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;

class OsrProcessingReleaseTargetRegistryTest {

    @Test
    void shouldResolveTargetsByNormalizedUniqueId() {
        StubTarget first = new StubTarget(" target-1 ");
        StubTarget second = new StubTarget("target-2");
        OsrProcessingReleaseTargetRegistry registry =
                new OsrProcessingReleaseTargetRegistry(List.of(first, second));

        assertSame(first, registry.find("target-1").orElseThrow());
        assertSame(second, registry.find(" target-2 ").orElseThrow());
        assertTrue(registry.find("unknown").isEmpty());
    }

    @Test
    void shouldRejectDuplicateOrInvalidTargets() {
        assertThrows(IllegalArgumentException.class, () ->
                new OsrProcessingReleaseTargetRegistry(null));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrProcessingReleaseTargetRegistry(java.util.Arrays.asList(
                        new StubTarget("target-1"),
                        null)));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrProcessingReleaseTargetRegistry(List.of(
                        new StubTarget("target-1"),
                        new StubTarget(" target-1 "))));
        assertThrows(IllegalArgumentException.class, () ->
                new OsrProcessingReleaseTargetRegistry(List.of(new StubTarget(" "))));

        OsrProcessingReleaseTargetRegistry registry =
                new OsrProcessingReleaseTargetRegistry(List.of());
        assertThrows(IllegalArgumentException.class, () -> registry.find(null));
        assertThrows(IllegalArgumentException.class, () -> registry.find(" "));
    }

    private record StubTarget(String targetId) implements OsrProcessingReleaseTarget {
        @Override
        public SchedulerCommandApplicationResult accept(
                OsrProcessingReleaseRequest request) {
            return SchedulerCommandApplicationResult.appliedResult();
        }
    }
}
