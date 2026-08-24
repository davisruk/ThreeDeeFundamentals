package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.PackSourceProvenance;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackTrace;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.outbound.AllocatedOutboundBag;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteClosureReason;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.OutputSheetAllocation;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pServiceCentreWorkSnapshot;

class P2pWorkloadSnapshotTest {

    private static final P2pWorkloadCostConfig COSTS = new P2pWorkloadCostConfig(
            Duration.ofSeconds(10),
            Duration.ofSeconds(2),
            Duration.ofSeconds(3));

    private final P2pWorkloadSnapshotFactory factory = new P2pWorkloadSnapshotFactory();

    @Test
    void shouldEstimateRemainingTotePackAndBagStagesByServiceCentre() {
        InboundToteManifest input104 = manifest("input-104", "order-104", "104", 0);
        InboundToteManifest input108 = manifest("input-108", "order-108", "108", 1);
        PlannedBag remaining104 = plannedBag(
                "rx-104-a", "104", "pharmacy-104", input104, "pack-104-a", "pack-104-b");
        PlannedBag allocated104 = plannedBag(
                "rx-104-b", "104", "pharmacy-104", input104, "pack-104-c");
        PlannedBag remaining108 = plannedBag(
                "rx-108-a", "108", "pharmacy-108", input108, "pack-108-a");
        BagPlanningResult planning = planning(
                List.of(remaining104, allocated104, remaining108),
                List.of(input104, input104, input108));
        OrderSheetKey empty104 = new OrderSheetKey("empty-104", 1);

        LinkedHashMap<String, List<PhysicalToteId>> remainingTotes = new LinkedHashMap<>();
        remainingTotes.put("104", List.of(input104.physicalToteId()));
        P2pServiceCentreWorkSnapshot work = new P2pServiceCentreWorkSnapshot(
                remainingTotes,
                Map.of("104", List.of(empty104)));

        P2pWorkloadSnapshot snapshot = factory.create(
                work,
                new InboundToteManifestCatalog(List.of(input104, input108)),
                planning,
                allocatedSnapshot(allocated104),
                COSTS);

        assertEquals(List.of("104", "108"), snapshot.serviceCentres().stream()
                .map(P2pServiceCentreWorkloadSnapshot::serviceCentreId)
                .toList());
        P2pServiceCentreWorkloadSnapshot service104 = snapshot.require("104");
        assertEquals(List.of(input104.physicalToteId()), service104.remainingToteIds());
        assertEquals(1, service104.remainingInboundToteCount());
        assertEquals(2, service104.remainingUnallocatedPackCount());
        assertEquals(List.of(remaining104.bagKey()), service104.remainingBagKeys());
        assertEquals(1, service104.remainingUnallocatedBagCount());
        assertEquals(List.of(empty104), service104.unallocatedEmptyOrderSheetKeys());
        assertEquals(Duration.ofSeconds(17), service104.estimatedSingleLineWork());
        assertTrue(service104.hasEstimatedWork());

        P2pServiceCentreWorkloadSnapshot service108 = snapshot.require("108");
        assertEquals(0, service108.remainingInboundToteCount());
        assertEquals(1, service108.remainingUnallocatedPackCount());
        assertEquals(1, service108.remainingUnallocatedBagCount());
        assertEquals(Duration.ofSeconds(5), service108.estimatedSingleLineWork());
    }

    @Test
    void shouldRetainCompletedCentreAndUnsupportedEmptyDiagnosticsWithoutFabricatingWork() {
        InboundToteManifest input = manifest("input-104", "order-104", "104", 0);
        PlannedBag bag = plannedBag(
                "rx-104", "104", "pharmacy-104", input, "pack-104");
        OrderSheetKey empty = new OrderSheetKey("empty-104", 1);

        P2pWorkloadSnapshot snapshot = factory.create(
                new P2pServiceCentreWorkSnapshot(
                        Map.of(),
                        Map.of("104", List.of(empty))),
                new InboundToteManifestCatalog(List.of(input)),
                planning(List.of(bag), List.of(input)),
                allocatedSnapshot(bag),
                COSTS);

        P2pServiceCentreWorkloadSnapshot serviceCentre = snapshot.require("104");
        assertFalse(serviceCentre.hasEstimatedWork());
        assertEquals(Duration.ZERO, serviceCentre.estimatedSingleLineWork());
        assertEquals(List.of(empty), serviceCentre.unallocatedEmptyOrderSheetKeys());
        assertEquals(1, serviceCentre.unallocatedEmptyOrderCount());
    }

    @Test
    void shouldRejectManifestPlanningAndAllocationIdentityMismatches() {
        InboundToteManifest input104 = manifest("input-104", "order-104", "104", 0);
        PlannedBag planned = plannedBag(
                "rx-104", "104", "pharmacy-104", input104, "pack-104");
        P2pServiceCentreWorkSnapshot mismatchedWork = new P2pServiceCentreWorkSnapshot(
                Map.of("108", List.of(input104.physicalToteId())),
                Map.of());

        assertThrows(IllegalStateException.class, () -> factory.create(
                mismatchedWork,
                new InboundToteManifestCatalog(List.of(input104)),
                planning(List.of(planned), List.of(input104)),
                emptyOutbound(),
                COSTS));

        assertThrows(IllegalStateException.class, () -> factory.create(
                P2pServiceCentreWorkSnapshot.empty(),
                new InboundToteManifestCatalog(List.of(input104)),
                new BagPlanningResult(List.of(planned), List.of(), List.of()),
                emptyOutbound(),
                COSTS));

        PlannedBag unknown = plannedBag(
                "rx-unknown", "104", "pharmacy-104", input104, "pack-unknown");
        assertThrows(IllegalStateException.class, () -> factory.create(
                P2pServiceCentreWorkSnapshot.empty(),
                new InboundToteManifestCatalog(List.of(input104)),
                planning(List.of(planned), List.of(input104)),
                allocatedSnapshot(unknown),
                COSTS));

        PlannedBag conflicting = new PlannedBag(
                planned.bagKey(),
                planned.serviceCentreId(),
                "different-pharmacy",
                planned.patientId(),
                planned.prescriptionId(),
                planned.physicalPackIds(),
                planned.owningOrderSheetKeys());
        assertThrows(IllegalStateException.class, () -> factory.create(
                P2pServiceCentreWorkSnapshot.empty(),
                new InboundToteManifestCatalog(List.of(input104)),
                planning(List.of(planned), List.of(input104)),
                allocatedSnapshot(conflicting),
                COSTS));
    }

    @Test
    void shouldRejectOverflowAndInvalidOrMutableDomainValues() {
        InboundToteManifest first = manifest("input-1", "order-1", "104", 0);
        InboundToteManifest second = manifest("input-2", "order-2", "104", 1);
        P2pServiceCentreWorkSnapshot work = new P2pServiceCentreWorkSnapshot(
                Map.of("104", List.of(first.physicalToteId(), second.physicalToteId())),
                Map.of());
        P2pWorkloadCostConfig overflowingCosts = new P2pWorkloadCostConfig(
                Duration.ofNanos(Long.MAX_VALUE),
                Duration.ZERO,
                Duration.ZERO);

        assertThrows(IllegalArgumentException.class, () -> factory.create(
                work,
                new InboundToteManifestCatalog(List.of(first, second)),
                new BagPlanningResult(List.of(), List.of(), List.of()),
                emptyOutbound(),
                overflowingCosts));
        assertThrows(
                IllegalArgumentException.class,
                () -> new P2pWorkloadCostConfig(
                        Duration.ZERO, Duration.ZERO, Duration.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> new P2pWorkloadCostConfig(
                        Duration.ofSeconds(-1), Duration.ZERO, Duration.ZERO));

        List<PhysicalToteId> toteIds = new ArrayList<>(List.of(first.physicalToteId()));
        P2pServiceCentreWorkloadSnapshot serviceCentre =
                new P2pServiceCentreWorkloadSnapshot(
                        "104", toteIds, 0, List.of(), List.of(), Duration.ofSeconds(1));
        toteIds.clear();
        assertEquals(List.of(first.physicalToteId()), serviceCentre.remainingToteIds());
        assertThrows(
                UnsupportedOperationException.class,
                () -> serviceCentre.remainingToteIds().clear());
        assertThrows(
                IllegalArgumentException.class,
                () -> new P2pWorkloadSnapshot(List.of(serviceCentre, serviceCentre)));
        assertThrows(IllegalArgumentException.class, () -> P2pWorkloadSnapshot.empty().find(" "));
    }

    private static BagPlanningResult planning(
            List<PlannedBag> bags,
            List<InboundToteManifest> inputTotesByBag) {
        List<PlannedPackTrace> traces = new ArrayList<>();
        for (int index = 0; index < bags.size(); index++) {
            PlannedBag bag = bags.get(index);
            InboundToteManifest inputTote = inputTotesByBag.get(index);
            for (String packId : bag.physicalPackIds()) {
                traces.add(trace(packId, bag, inputTote));
            }
        }
        return new BagPlanningResult(bags, List.of(), traces);
    }

    private static PlannedPackTrace trace(
            String packId,
            PlannedBag bag,
            InboundToteManifest inputTote) {
        PackSourceProvenance provenance = new PackSourceProvenance(
                inputTote.orderSheetKey(),
                "line-" + packId,
                "product-" + packId,
                bag.serviceCentreId(),
                bag.pharmacyId(),
                bag.patientId(),
                bag.prescriptionId());
        return new PlannedPackTrace(
                packId,
                provenance,
                inputTote.physicalToteId(),
                bag.owningOrderSheetKeys().getFirst(),
                bag.bagKey());
    }

    private static PlannedBag plannedBag(
            String prescriptionId,
            String serviceCentreId,
            String pharmacyId,
            InboundToteManifest inputTote,
            String... packIds) {
        return new PlannedBag(
                new BagKey(prescriptionId, 1),
                serviceCentreId,
                pharmacyId,
                "patient-" + prescriptionId,
                prescriptionId,
                List.of(packIds),
                List.of(inputTote.orderSheetKey()));
    }

    private static InboundToteManifest manifest(
            String physicalToteId,
            String orderId,
            String serviceCentreId,
            long sequence) {
        return new InboundToteManifest(
                new PhysicalToteId(physicalToteId),
                new OrderSheetKey(orderId, 1),
                OrderType.FULL_PACK,
                serviceCentreId,
                List.of(new DspOrderItem("line-" + orderId, "product-" + orderId, 1)),
                sequence);
    }

    private static OutboundAllocationSnapshot allocatedSnapshot(PlannedBag plannedBag) {
        PhysicalToteId outboundToteId = new PhysicalToteId(
                "outbound-" + plannedBag.prescriptionId());
        AllocatedOutboundBag allocatedBag = new AllocatedOutboundBag(
                plannedBag,
                outboundToteId,
                plannedBag.owningOrderSheetKeys().stream()
                        .map(sheet -> new OutputSheetAllocation(sheet, sheet))
                        .toList());
        OutboundToteSnapshot tote = new OutboundToteSnapshot(
                outboundToteId,
                new P2pLineId("p2p-1"),
                Optional.of(plannedBag.serviceCentreId()),
                Optional.of(plannedBag.pharmacyId()),
                10,
                List.of(allocatedBag),
                Optional.of(OutboundToteClosureReason.APPLICABLE_WORK_COMPLETE));
        return new OutboundAllocationSnapshot(
                Map.of(),
                List.of(tote),
                List.of(allocatedBag));
    }

    private static OutboundAllocationSnapshot emptyOutbound() {
        return new OutboundAllocationSnapshot(Map.of(), List.of(), List.of());
    }
}
