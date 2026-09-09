package online.davisfamily.warehouse.sim.dsp.analysis.metrics;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.time.DspOperatingPhase;

/** Bounded immutable occupancy state captured at a full-day metric boundary. */
public record DspFullDayOccupancySample(
        Duration elapsedSimulationTime,
        LocalDateTime businessDateTime,
        DspOperatingPhase phase,
        int osrOccupancy,
        int osrCapacity,
        int osrLowWaterMark,
        int upstreamWaitingCount,
        long admittedInboundToteCount,
        long departedInboundToteCount,
        long closedOutboundToteCount,
        long allocatedBagCount,
        long osrNetFlow,
        Map<P2pLineId, Optional<String>> activeLineOwners) {

    public DspFullDayOccupancySample {
        if (elapsedSimulationTime == null || elapsedSimulationTime.isNegative()) {
            throw new IllegalArgumentException(
                    "elapsedSimulationTime must be nonnegative");
        }
        if (businessDateTime == null || phase == null) {
            throw new IllegalArgumentException(
                    "businessDateTime and phase must not be null");
        }
        if (osrCapacity < 1 || osrOccupancy < 0 || osrOccupancy > osrCapacity) {
            throw new IllegalArgumentException(
                    "osrOccupancy must be between zero and osrCapacity");
        }
        if (osrLowWaterMark < 0 || osrLowWaterMark >= osrCapacity) {
            throw new IllegalArgumentException(
                    "osrLowWaterMark must be nonnegative and below osrCapacity");
        }
        if (upstreamWaitingCount < 0 || admittedInboundToteCount < 0
                || departedInboundToteCount < 0 || closedOutboundToteCount < 0
                || allocatedBagCount < 0) {
            throw new IllegalArgumentException("occupancy counts must be nonnegative");
        }
        if (activeLineOwners == null) {
            throw new IllegalArgumentException("activeLineOwners must not be null");
        }
        Map<P2pLineId, Optional<String>> ownerCopy = new LinkedHashMap<>();
        activeLineOwners.forEach((lineId, owner) -> {
            if (lineId == null || owner == null) {
                throw new IllegalArgumentException(
                        "activeLineOwners must not contain null keys or values");
            }
            ownerCopy.put(lineId, owner.map(value -> requireValue(value, "serviceCentreId")));
        });
        activeLineOwners = Collections.unmodifiableMap(ownerCopy);
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
