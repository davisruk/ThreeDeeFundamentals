package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.util.List;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;

public record DspP2pStickyLeaseRuntimeSnapshot(List<DspP2pStickyLineSnapshot> lines) {
    public DspP2pStickyLeaseRuntimeSnapshot {
        if (lines == null || lines.stream().anyMatch(line -> line == null)) {
            throw new IllegalArgumentException("lines must not be null or contain null");
        }
        lines = List.copyOf(lines);
    }

    public Optional<DspP2pStickyLineSnapshot> findLine(P2pLineId lineId) {
        if (lineId == null) {
            throw new IllegalArgumentException("lineId must not be null");
        }
        return lines.stream()
                .filter(line -> line.lease().definition().lineId().equals(lineId))
                .findFirst();
    }
}
