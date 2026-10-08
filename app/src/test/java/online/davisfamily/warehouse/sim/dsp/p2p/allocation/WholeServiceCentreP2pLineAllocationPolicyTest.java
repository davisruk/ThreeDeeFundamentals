package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import static org.junit.jupiter.api.Assertions.*;
import static online.davisfamily.warehouse.sim.dsp.p2p.allocation.WholeServiceCentreP2pAllocationPlannerTest.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.ReleasePhysicalToteFromOsrCommand;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationAssignment;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationAssignmentSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationRequirement;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pBaggingActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pInputActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineAllocationBlockReason;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineAllocationDecision;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineAllocationRequest;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseCatalogSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseRegistry;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPackPathActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.policy.WholeServiceCentreReleaseLedger;

class WholeServiceCentreP2pLineAllocationPolicyTest {
    private final WholeServiceCentreP2pLineAllocationPolicy policy = new WholeServiceCentreP2pLineAllocationPolicy();

    @Test
    void shouldSeedFiveIndependentTotesThenBalanceActualCommittedCountsWithoutMutation() {
        List<NotionalToteOrder> orders = IntStream.range(0, 7).mapToObj(i -> order("order-" + i)).toList();
        List<InboundToteManifest> manifests = orders.stream().map(order -> new InboundToteManifest(
                new PhysicalToteId("physical-" + order.orderId()), order.orderSheetKey(), OrderType.FULL_PACK,
                "A", order.items(), order.sequenceNumber())).toList();
        var ledger = new WholeServiceCentreReleaseLedger(new LoadedDspData(List.of(), orders,
                List.of(), Set.of(), Set.of(), manifests, DspDatasetLoadReport.empty()), timetable(17), LINES);
        var registry = new P2pLineLeaseRegistry(LINES);
        var activities = new LinkedHashMap<P2pLineId, P2pLineActivitySnapshot>();
        LINES.forEach(line -> activities.put(line.lineId(), P2pLineActivitySnapshot.idle()));
        var planner = new WholeServiceCentreP2pAllocationPlanner();
        for (int index = 0; index < manifests.size(); index++) {
            var leases = registry.snapshot(activities);
            var before = ledger.snapshot();
            var allocation = plan(planner, leases, before);
            var manifest = manifests.get(index);
            var request = request(manifest.physicalToteId().value(), leases, allocation, true, admissions(leases, true));
            var decision = policy.allocate(request);
            assertEquals(LINES.get(index < 5 ? index : index - 5).lineId(),
                    decision.assignment().orElseThrow().lineId());
            assertEquals(decision, policy.allocate(request));
            assertSame(before, ledger.snapshot());
            assertEquals(leases, registry.snapshot(activities));
            var assignment = decision.assignment().orElseThrow();
            // Isolated accepted-release accounting; production guard/composition is Step 6.
            registry.acquireLease(assignment.lineId(), "A", activities.get(assignment.lineId()));
            registry.commitAssignment(assignment);
            ledger.recordApplied(new ReleasePhysicalToteFromOsrCommand(manifest.physicalToteId(),
                    manifest.orderSheetKey(), "A", assignment.destination().targetId(), Optional.of(assignment)));
            if (index == 0) {
                activities.put(LINES.get(0).lineId(), openActivity(0, "A", "pharmacy"));
            }
        }
        assertEquals(2, ledger.snapshot().committedToteCount("A", LINES.get(0).lineId()));
        assertEquals(2, ledger.snapshot().committedToteCount("A", LINES.get(1).lineId()));
        assertTrue(ledger.snapshot().allReleased("A"));
    }

    @Test
    void shouldUseLeastCountThenAffinityThenConfiguredOrderAndRespectPinnedBag() {
        var leases = new P2pLineLeaseCatalogSnapshot(IntStream.range(0, 5).mapToObj(i ->
                new P2pLineLeaseSnapshot(LINES.get(i), Optional.of("A"),
                        i == 1 ? openActivity(i, "A", "pharmacy") : P2pLineActivitySnapshot.idle(), List.of())).toList());
        var allocation = plan(new WholeServiceCentreP2pAllocationPlanner(), leases,
                releases(5, 1, Map.of(LINES.get(0).lineId(), 2, LINES.get(1).lineId(), 1,
                        LINES.get(2).lineId(), 1, LINES.get(3).lineId(), 1, LINES.get(4).lineId(), 1)));
        assertEquals(LINES.get(1).lineId(), assigned(request("candidate", leases, allocation, true, admissions(leases, true))));
        var fewer = plan(new WholeServiceCentreP2pAllocationPlanner(), leases,
                releases(5, 1, Map.of(LINES.get(1).lineId(), 1)));
        assertEquals(LINES.get(0).lineId(), assigned(request("candidate", leases, fewer, true, admissions(leases, true))));
        var pinned = new P2pLineAllocationRequest(new PhysicalToteId("candidate"), "A", List.of("pharmacy"), true,
                leases, admissions(leases, true), Optional.of(fewer),
                Set.of(new P2pBagCorrelationRequirement("bag", 2)),
                new P2pBagCorrelationAssignmentSnapshot(List.of(new P2pBagCorrelationAssignment("bag", LINES.get(1).lineId()))));
        assertEquals(LINES.get(1).lineId(), assigned(pinned));
        assertEquals(1, fewer.wholeServiceCentrePolicy().orElseThrow().releases().committedToteCount("A", LINES.get(1).lineId()));
    }

    @Test
    void shouldRejectForeignNonquiescentAndInadmissibleLinesButNotGateEarlierRoutes() {
        var lines = new ArrayList<>(idleCatalog().lines());
        lines.set(0, new P2pLineLeaseSnapshot(LINES.get(0), Optional.of("B"), P2pLineActivitySnapshot.idle(), List.of()));
        var busy = new P2pLineActivitySnapshot(new P2pInputActivitySnapshot(1, 0, false, 0),
                P2pPackPathActivitySnapshot.idle(), P2pBaggingActivitySnapshot.idle(), Optional.empty());
        lines.set(1, new P2pLineLeaseSnapshot(LINES.get(1), Optional.empty(), busy, List.of()));
        var leases = new P2pLineLeaseCatalogSnapshot(lines);
        var allocation = plan(new WholeServiceCentreP2pAllocationPlanner(), leases, releases(1, 1, Map.of()));
        var admissions = admissions(leases, true);
        admissions.put(LINES.get(2).destination(), false);
        assertEquals(LINES.get(3).lineId(), assigned(request("candidate", leases, allocation, true, admissions)));
        var blocked = policy.allocate(request("candidate", leases, allocation, true, admissions(leases, false)));
        assertEquals(P2pLineAllocationBlockReason.NO_COMPATIBLE_P2P_LINE, blocked.blockReason().orElseThrow());
        assertEquals(LINES.get(2).lineId(), assigned(request("candidate", leases, allocation, false, admissions(leases, false))));
        assertEquals(lines, leases.lines());
        var later = new P2pLineAllocationRequest(new PhysicalToteId("later"), "B", List.of("pharmacy"), true,
                leases, admissions, Optional.of(allocation));
        assertEquals(P2pLineAllocationBlockReason.NO_ELASTIC_LINE_BUDGET, policy.allocate(later).blockReason().orElseThrow());
    }

    @Test
    void shouldIgnoreDeadlineClockAndWeightsForIdenticalPhysicalFactsAndRequireWholeProfile() {
        var leases = idleCatalog();
        var releases = releases(1, 1, Map.of());
        var planner = new WholeServiceCentreP2pAllocationPlanner();
        var first = planner.create(clock(6), supply(true), workload(Duration.ofSeconds(1)), timetable(17), leases, releases, Duration.ofHours(1));
        var urgent = planner.create(clock(21), supply(true), workload(Duration.ofDays(30)), timetable(7), leases, releases, Duration.ofHours(2));
        assertEquals(policy.allocate(request("same", leases, first, true, admissions(leases, true))),
                policy.allocate(request("same", leases, urgent, true, admissions(leases, true))));
        assertThrows(IllegalArgumentException.class, () -> policy.allocate(null));
        var legacy = new P2pElasticAllocationSnapshot(P2pElasticAllocationSnapshot.DEADLINE_AWARE_ELASTIC_STICKY_LEASES,
                first.calibrationStatus(), first.evaluatedAt(), first.configuredLineIds(), 1, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> policy.allocate(request("same", leases, legacy, true, admissions(leases, true))));
    }

    private P2pLineId assigned(P2pLineAllocationRequest request) {
        return policy.allocate(request).assignment().orElseThrow().lineId();
    }

    static P2pLineAllocationRequest request(String physical, P2pLineLeaseCatalogSnapshot leases,
            P2pElasticAllocationSnapshot allocation, boolean direct, Map<OperationalRouteDestination, Boolean> admissions) {
        return new P2pLineAllocationRequest(new PhysicalToteId(physical), "A", List.of("pharmacy"), direct,
                leases, admissions, Optional.of(allocation));
    }

    static Map<OperationalRouteDestination, Boolean> admissions(P2pLineLeaseCatalogSnapshot leases, boolean open) {
        var result = new LinkedHashMap<OperationalRouteDestination, Boolean>();
        leases.lines().forEach(line -> result.put(line.definition().destination(), open));
        return result;
    }

    static P2pLineActivitySnapshot openActivity(int index, String owner, String pharmacy) {
        return new P2pLineActivitySnapshot(P2pInputActivitySnapshot.idle(), P2pPackPathActivitySnapshot.idle(),
                P2pBaggingActivitySnapshot.idle(), Optional.of(new OutboundToteSnapshot(
                        new PhysicalToteId("output-" + index), LINES.get(index).lineId(), Optional.of(owner),
                        Optional.of(pharmacy), 10, List.of(), Optional.empty())));
    }

    private static NotionalToteOrder order(String id) {
        var item = new DspOrderItem(id + "-line", "product", 1, "pharmacy", "patient", "rx-" + id,
                DspOrderLineType.FULL_PACK, id, 1, 1);
        return new NotionalToteOrder(id, "notional-" + id, "A", 1, OrderType.FULL_PACK, List.of(item), 999, 0);
    }
}
