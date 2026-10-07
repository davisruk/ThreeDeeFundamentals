package online.davisfamily.warehouse.sim.dsp.analysis.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchId;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchSnapshot;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchState;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingVisitType;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactory;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeSnapshot;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspHeadlessP2pLineRuntimeSnapshot;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pBaggingActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pInputActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPackPathActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.station.continuation.StationRouteContinuationControllerSnapshot;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDispositionType;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportArrivalControllerSnapshot;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportIngressControllerSnapshot;
import online.davisfamily.warehouse.sim.machine.queue.MachineWaitQueueSnapshot;
import online.davisfamily.warehouse.sim.totebag.assignment.PrlState;

class DspFullDayBlockedProgressFormatterTest {
    @Test
    void shouldFormatHeadOnlyObservationsAndAllAggregateMachineFields(@TempDir Path directory)
            throws Exception {
        var profile = DspFullDayReportTestSupport.profile();
        var input = DspFullDayReportTestSupport.input(directory, profile);
        try (var runtime = new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            runtime.update(1d);
            var base = runtime.snapshot();
            var snapshot = blockedSnapshot(base);
            List<AdaptingBenchAdmissionSnapshot> benches = benches();
            Map<P2pLineId, Optional<String>> activeIds = activeIds(snapshot);
            var formatter = new DspFullDayBlockedProgressFormatter();
            List<String> lines = formatter.describe(snapshot, benches, activeIds);

            assertEquals(9, lines.size());
            assertEquals("BlockedProgress: continuationBlocked=continuation full"
                    + " arrivalBlocked=arrival full ingressBlocked=transport full"
                    + " continuationHead=continuation-head continuationType=CONTINUE"
                    + " selectedNextStation=ADAPTING selectedNextDestination=ADAPTING/bench-1"
                    + " ingressHead=ingress-head ingressDestination=ADAPTING/bench-1"
                    + " queue=64/64 inFlight=64/64", lines.get(0));
            assertEquals("BlockedProgress.TransportArrival: pending=2 head=arrival-head"
                    + " destination=ADAPTING/bench-1 terminalSensor=head-sensor"
                    + " blockedTote=arrival-head blocked=arrival full", lines.get(1));
            assertEquals("BlockedProgress.Adapting[bench-1]: state=IDLE activeTote=none"
                    + " visit=none remainingSeconds=0.0 occupiedPositions=0/1 queue=0/1 head=none"
                    + " admissionOpen=true blocked=none", lines.get(2));
            assertEquals("BlockedProgress.Adapting[bench-2]: state=PROCESSING_STORE"
                    + " activeTote=bench-active visit=STORE remainingSeconds=2.5 occupiedPositions=1/1 queue=1/1"
                    + " head=bench-head admissionOpen=false blocked=local full", lines.get(3));
            assertEquals("BlockedProgress.P2P[dsp-p2p-line-1]: owner=104"
                    + " activeTipperTote=tipper-active activeTipper=true stationArrival=3"
                    + " tipperInput=4 activeDischarges=5"
                    + " prls=IDLE:1,ASSIGNED:0,ACCUMULATING:1,READY_TO_RELEASE:0,RELEASING:0"
                    + " receivedPrlPacks=7 packPath=sorterInput:1,sorterOutput:2,"
                    + "pendingSorterOutfeed:3,pdc:4,activePdcTransfers:5,nonIdlePrls:1,prlPacks:7,"
                    + "activePrlToPcrTransfers:8,pcrPacks:9,pcrTravellingGroups:10,"
                    + "pcrReleasedGroups:11,outstandingExpectedBagGroups:12"
                    + " bagging=currentGroup:true,reservedGroup:false,activeReservation:true,"
                    + "pendingDischarges:2,activeDischarge:true,receiverReservation:false,"
                    + "receiverReceiving:true,receiverCompleted:3", lines.get(4));
            for (int index = 1; index < snapshot.p2pLines().size(); index++) {
                assertTrue(lines.get(4 + index).startsWith("BlockedProgress.P2P["
                        + snapshot.p2pLines().get(index).lineDefinition().lineId().value() + "]:"));
                assertTrue(lines.get(4 + index).contains("activeTipperTote=none activeTipper=false"));
                assertTrue(lines.get(4 + index).contains("prls=IDLE:31,ASSIGNED:0,ACCUMULATING:0"));
            }
            assertEquals(lines, formatter.describe(snapshot, benches, activeIds));
            assertThrows(UnsupportedOperationException.class, lines::clear);
            String text = String.join("\n", lines);
            assertFalse(text.contains("arrival-second"));
            assertFalse(text.contains("second-sensor"));
            assertFalse(text.contains("private-prl"));
            assertFalse(text.contains("deadlock"));
            assertEquals(base, runtime.snapshot());
        }
    }

    @Test
    void shouldRejectNullsAndNonExactLineCoverageAndDescribeAbsentHeads(@TempDir Path directory)
            throws Exception {
        var profile = DspFullDayReportTestSupport.profile();
        var input = DspFullDayReportTestSupport.input(directory, profile);
        try (var runtime = new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            var snapshot = runtime.snapshot();
            var formatter = new DspFullDayBlockedProgressFormatter();
            var activeIds = activeIds(snapshot);
            assertThrows(IllegalArgumentException.class,
                    () -> formatter.describe(null, List.of(), activeIds));
            assertThrows(IllegalArgumentException.class,
                    () -> formatter.describe(snapshot, null, activeIds));
            assertThrows(IllegalArgumentException.class,
                    () -> formatter.describe(snapshot, List.of(), null));
            assertThrows(IllegalArgumentException.class,
                    () -> formatter.describe(snapshot, java.util.Arrays.asList((AdaptingBenchAdmissionSnapshot) null), activeIds));
            assertThrows(IllegalArgumentException.class,
                    () -> formatter.describe(snapshot, List.of(), Map.of()));
            Map<P2pLineId, Optional<String>> extra = new LinkedHashMap<>(activeIds);
            extra.put(new P2pLineId("unknown"), Optional.empty());
            assertThrows(IllegalArgumentException.class,
                    () -> formatter.describe(snapshot, List.of(), extra));
            Map<P2pLineId, Optional<String>> nullValue = new LinkedHashMap<>(activeIds);
            nullValue.put(snapshot.p2pLines().getFirst().lineDefinition().lineId(), null);
            assertThrows(IllegalArgumentException.class,
                    () -> formatter.describe(snapshot, List.of(), nullValue));
            List<String> lines = formatter.describe(snapshot, List.of(), activeIds);
            assertTrue(lines.getFirst().contains("continuationHead=none continuationType=none"));
            assertTrue(lines.getFirst().contains("selectedNextStation=none selectedNextDestination=none"));
            assertTrue(lines.getFirst().contains("ingressHead=none ingressDestination=none"));
            assertEquals("BlockedProgress.TransportArrival: pending=0 head=none destination=none"
                    + " terminalSensor=none blockedTote=none blocked=none", lines.get(1));
        }
    }

    private static List<AdaptingBenchAdmissionSnapshot> benches() {
        return List.of(new AdaptingBenchAdmissionSnapshot(new AdaptingBenchId("bench-2"),
                new AdaptingBenchSnapshot("bench-2", AdaptingBenchState.PROCESSING_STORE,
                        "bench-active", AdaptingVisitType.STORE, 2.5d, ""),
                new MachineWaitQueueSnapshot("bench-2-queue", 1, List.of("bench-head")),
                false, "local full"),
                new AdaptingBenchAdmissionSnapshot(new AdaptingBenchId("bench-1"),
                        new AdaptingBenchSnapshot("bench-1", AdaptingBenchState.IDLE, null, null, 0d, ""),
                        new MachineWaitQueueSnapshot("bench-1-queue", 1, List.of()), true, ""));
    }

    @Test
    void shouldLabelMultiPositionScalarsAsRepresentativeWithoutDetailedPositions(@TempDir Path directory)
            throws Exception {
        var profile = DspFullDayReportTestSupport.profile();
        var input = DspFullDayReportTestSupport.input(directory, profile);
        try (var runtime = new DspFullDayAnalysisRuntimeFactory().create(input, profile)) {
            var snapshot = runtime.snapshot();
            var bench = new AdaptingBenchAdmissionSnapshot(new AdaptingBenchId("bench-1"),
                    new AdaptingBenchSnapshot("bench-1", AdaptingBenchState.PROCESSING_COLLECT,
                            "lowest-occupied", AdaptingVisitType.COLLECT, 7d, ""),
                    new MachineWaitQueueSnapshot("bench-1-queue", 3, List.of()), true, "", 3, 2);
            var lines = new DspFullDayBlockedProgressFormatter().describe(
                    snapshot, List.of(bench), activeIds(snapshot));
            assertEquals("BlockedProgress.Adapting[bench-1]: representativeState=PROCESSING_COLLECT"
                    + " representativeTote=lowest-occupied representativeVisit=COLLECT"
                    + " representativeRemainingSeconds=7.0 occupiedPositions=2/3 queue=0/3"
                    + " head=none admissionOpen=true blocked=none", lines.get(2));
            assertEquals(snapshot, runtime.snapshot());
        }
    }

    private static Map<P2pLineId, Optional<String>> activeIds(DspFullDayAnalysisRuntimeSnapshot snapshot) {
        Map<P2pLineId, Optional<String>> ids = new LinkedHashMap<>();
        for (int index = snapshot.p2pLines().size() - 1; index >= 0; index--) {
            ids.put(snapshot.p2pLines().get(index).lineDefinition().lineId(),
                    index == 0 ? Optional.of("tipper-active") : Optional.empty());
        }
        return ids;
    }

    private static DspFullDayAnalysisRuntimeSnapshot blockedSnapshot(DspFullDayAnalysisRuntimeSnapshot base) {
        var destination = new OperationalRouteDestination(StationType.ADAPTING, "bench-1");
        var continuationHead = new PhysicalToteId("continuation-head");
        var continuation = new StationRouteContinuationControllerSnapshot(Optional.of(continuationHead),
                Optional.of(StationProcessingDispositionType.CONTINUE), Optional.of(StationType.ADAPTING),
                Optional.of(destination), Optional.of(continuationHead), "continuation full", 0, 0,
                Optional.empty(), Optional.empty(), Optional.empty());
        var arrivalHead = new PhysicalToteId("arrival-head");
        var arrival = new WarehouseTransportArrivalControllerSnapshot(List.of(
                new WarehouseTransportArrivalControllerSnapshot.PendingArrival(arrivalHead, destination, "head-sensor"),
                new WarehouseTransportArrivalControllerSnapshot.PendingArrival(
                        new PhysicalToteId("arrival-second"), destination, "second-sensor")),
                Optional.empty(), Optional.empty(), Optional.of(arrivalHead), "arrival full", 0);
        var ingressHead = new PhysicalToteId("ingress-head");
        var ingress = new WarehouseTransportIngressControllerSnapshot(64, 64, 64, 64,
                Optional.of(ingressHead), Optional.of(destination), Optional.empty(), Optional.empty(),
                Optional.of(ingressHead), "transport full", 0);
        List<DspHeadlessP2pLineRuntimeSnapshot> p2pLines = new ArrayList<>(base.p2pLines());
        var first = p2pLines.getFirst();
        var activity = new P2pLineActivitySnapshot(new P2pInputActivitySnapshot(3, 4, true, 5),
                new P2pPackPathActivitySnapshot(1, 2, 3, 4, 5, 1, 7, 8, 9, 10, 11, 12),
                new P2pBaggingActivitySnapshot(true, false, true, 2, true, false, true, 3), Optional.empty());
        p2pLines.set(0, new DspHeadlessP2pLineRuntimeSnapshot(first.lineDefinition(), activity,
                first.stationProcessing(), Map.of("private-prl-a", PrlState.IDLE,
                        "private-prl-b", PrlState.ACCUMULATING),
                Map.of("private-prl-a", 0, "private-prl-b", 7), List.of(), first.outboundAllocation(), false));
        return new DspFullDayAnalysisRuntimeSnapshot(base.state(), base.clock(), base.scheduler(), base.supply(),
                base.osr(), base.av02(), base.lifecycle(), base.av02Allocation(), base.operationalRelease(),
                base.elastic(), p2pLines, base.stationProcessing(), base.stationClaims(), base.routes(),
                base.outboundTransport(), base.transportInFlight(), ingress, arrival, base.stationArrivals(),
                continuation, base.completions(), base.cutoff(), base.metrics(), base.closed());
    }
}
