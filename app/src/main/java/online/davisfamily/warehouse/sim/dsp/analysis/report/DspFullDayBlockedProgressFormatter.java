package online.davisfamily.warehouse.sim.dsp.analysis.report;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeSnapshot;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspHeadlessP2pLineRuntimeSnapshot;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.totebag.assignment.PrlState;

/** Pure, bounded observations of a blocked progress milestone, not a diagnosis. */
public final class DspFullDayBlockedProgressFormatter {
    public List<String> describe(
            DspFullDayAnalysisRuntimeSnapshot snapshot,
            List<AdaptingBenchAdmissionSnapshot> benches,
            Map<P2pLineId, Optional<String>> activeTipperIds) {
        if (snapshot == null || benches == null || activeTipperIds == null
                || benches.stream().anyMatch(bench -> bench == null)) {
            throw new IllegalArgumentException("blocked progress inputs must not be null");
        }
        Set<P2pLineId> lineIds = new HashSet<>();
        for (var line : snapshot.p2pLines()) {
            lineIds.add(line.lineDefinition().lineId());
        }
        if (!lineIds.equals(activeTipperIds.keySet())
                || activeTipperIds.values().stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("active tipper IDs must cover exactly the snapshot lines");
        }
        Map<P2pLineId, Optional<String>> owners = new HashMap<>();
        for (var line : snapshot.metrics().p2pLines()) {
            owners.put(line.lineId(), line.serviceCentreId());
        }
        if (!owners.keySet().containsAll(lineIds)) {
            throw new IllegalArgumentException("metrics must contain every snapshot line");
        }

        List<String> lines = new ArrayList<>(2 + benches.size() + snapshot.p2pLines().size());
        var continuation = snapshot.continuation();
        var ingress = snapshot.transportIngress();
        var arrival = snapshot.transportArrival();
        lines.add("BlockedProgress: continuationBlocked=" + value(continuation.blockedReason())
                + " arrivalBlocked=" + value(arrival.blockedReason())
                + " ingressBlocked=" + value(ingress.blockedReason())
                + " continuationHead=" + physicalId(continuation.headPhysicalToteId())
                + " continuationType=" + optionalValue(continuation.headDispositionType())
                + " selectedNextStation=" + optionalValue(continuation.selectedNextStation())
                + " selectedNextDestination=" + destination(continuation.selectedNextDestination())
                + " ingressHead=" + physicalId(ingress.headPhysicalToteId())
                + " ingressDestination=" + destination(ingress.headDestination())
                + " queue=" + ingress.transportOccupancy() + "/" + ingress.transportCapacity()
                + " inFlight=" + ingress.inFlightOccupancy() + "/" + ingress.inFlightCapacity());
        var head = arrival.pendingArrivals().isEmpty() ? null : arrival.pendingArrivals().getFirst();
        lines.add("BlockedProgress.TransportArrival: pending=" + arrival.pendingArrivals().size()
                + " head=" + (head == null ? "none" : head.physicalToteId().value())
                + " destination=" + (head == null ? "none" : destination(head.destination()))
                + " terminalSensor=" + (head == null ? "none" : head.terminalSensorId())
                + " blockedTote=" + physicalId(arrival.blockedPhysicalToteId())
                + " blocked=" + value(arrival.blockedReason()));
        benches.stream().sorted(Comparator.comparing(AdaptingBenchAdmissionSnapshot::benchId))
                .forEach(bench -> lines.add(benchLine(bench)));
        for (var line : snapshot.p2pLines()) {
            P2pLineId id = line.lineDefinition().lineId();
            lines.add(p2pLine(line, owners.get(id), activeTipperIds.get(id)));
        }
        return List.copyOf(lines);
    }

    private static String benchLine(AdaptingBenchAdmissionSnapshot bench) {
        var state = bench.benchSnapshot();
        var queue = bench.queueSnapshot();
        return "BlockedProgress.Adapting[" + bench.benchId().value() + "]: state=" + state.state()
                + " activeTote=" + value(state.activeToteId())
                + " visit=" + (state.activeVisitType() == null ? "none" : state.activeVisitType())
                + " remainingSeconds=" + state.remainingProcessingSeconds()
                + " queue=" + queue.toteIds().size() + "/" + queue.capacity()
                + " head=" + (queue.toteIds().isEmpty() ? "none" : queue.toteIds().getFirst())
                + " admissionOpen=" + bench.admissionOpen()
                + " blocked=" + value(bench.blockedReason());
    }

    private static String p2pLine(DspHeadlessP2pLineRuntimeSnapshot line,
            Optional<String> owner, Optional<String> activeTipperId) {
        var input = line.activity().input();
        var path = line.activity().packPath();
        var bagging = line.activity().bagging();
        EnumMap<PrlState, Integer> states = new EnumMap<>(PrlState.class);
        for (PrlState state : line.prlStatesById().values()) {
            states.merge(state, 1, Integer::sum);
        }
        int receivedPacks = 0;
        for (int count : line.prlReceivedPackCountsById().values()) {
            receivedPacks += count;
        }
        StringBuilder result = new StringBuilder("BlockedProgress.P2P[")
                .append(line.lineDefinition().lineId().value()).append("]: owner=")
                .append(owner.orElse("none"))
                .append(" activeTipperTote=").append(activeTipperId.orElse("none"))
                .append(" activeTipper=").append(input.activeTipperTote())
                .append(" stationArrival=").append(input.stationArrivalCount())
                .append(" tipperInput=").append(input.tipperInputCount())
                .append(" activeDischarges=").append(input.activeTipperDischargeCount())
                .append(" prls=");
        for (PrlState state : PrlState.values()) {
            if (state.ordinal() > 0) {
                result.append(',');
            }
            result.append(state).append(':').append(states.getOrDefault(state, 0));
        }
        return result.append(" receivedPrlPacks=").append(receivedPacks)
                .append(" packPath=sorterInput:").append(path.sorterInputCount())
                .append(",sorterOutput:").append(path.sorterOutputCount())
                .append(",pendingSorterOutfeed:").append(path.pendingSorterOutfeedCount())
                .append(",pdc:").append(path.pdcPackCount())
                .append(",activePdcTransfers:").append(path.activePdcTransferCount())
                .append(",nonIdlePrls:").append(path.nonIdlePrlCount())
                .append(",prlPacks:").append(path.prlPackCount())
                .append(",activePrlToPcrTransfers:").append(path.activePrlToPcrTransferCount())
                .append(",pcrPacks:").append(path.pcrPackCount())
                .append(",pcrTravellingGroups:").append(path.pcrTravellingGroupCount())
                .append(",pcrReleasedGroups:").append(path.pcrReleasedGroupCount())
                .append(",outstandingExpectedBagGroups:").append(path.outstandingExpectedBagGroupCount())
                .append(" bagging=currentGroup:").append(bagging.currentBagGroup())
                .append(",reservedGroup:").append(bagging.reservedBagGroup())
                .append(",activeReservation:").append(bagging.activeBagReservation())
                .append(",pendingDischarges:").append(bagging.pendingBagDischargeCount())
                .append(",activeDischarge:").append(bagging.activeBagDischarge())
                .append(",receiverReservation:").append(bagging.receiverReservation())
                .append(",receiverReceiving:").append(bagging.receiverReceivingBag())
                .append(",receiverCompleted:").append(bagging.receiverCompletedBagCount()).toString();
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private static String optionalValue(Optional<?> value) {
        return value.map(Object::toString).orElse("none");
    }

    private static String physicalId(Optional<PhysicalToteId> value) {
        return value.map(PhysicalToteId::value).orElse("none");
    }

    private static String destination(Optional<OperationalRouteDestination> value) {
        return value.map(DspFullDayBlockedProgressFormatter::destination).orElse("none");
    }

    private static String destination(OperationalRouteDestination value) {
        return value.stationType() + "/" + value.targetId();
    }
}
