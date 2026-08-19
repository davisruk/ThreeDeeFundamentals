package online.davisfamily.warehouse.sim.dsp.outbound;

import java.util.LinkedHashMap;
import java.util.Map;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public final class DeterministicOutboundToteIdSource implements OutboundToteIdSource {
    private final Map<P2pLineId, Long> lastOrdinalByLine = new LinkedHashMap<>();

    @Override
    public PhysicalToteId nextId(P2pLineId lineId) {
        if (lineId == null) {
            throw new IllegalArgumentException("lineId must not be null");
        }
        long ordinal = lastOrdinalByLine.compute(
                lineId,
                (ignored, previousOrdinal) -> previousOrdinal == null
                        ? 1L
                        : Math.incrementExact(previousOrdinal));
        return new PhysicalToteId("outbound-" + lineId.value() + "-" + ordinal);
    }
}
