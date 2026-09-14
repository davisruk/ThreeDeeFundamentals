package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.av02.Av02AllocatedTote;
import online.davisfamily.warehouse.sim.dsp.av02.Av02InventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.PackSourceProvenance;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackTrace;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentStage;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
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
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
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
    void shouldReuseFiveThousandBagPlanIndexAndRetainWorkloadValues() {
        LargePlanFixture fixture = fiveThousandBagPlan();

        P2pWorkloadPlanIndex initial = factory.planIndexFor(
                fixture.planning(), fixture.manifestCatalog());
        assertSame(initial, factory.planIndexFor(
                fixture.planning(), fixture.manifestCatalog()));

        P2pWorkloadSnapshot first = factory.create(
                new P2pServiceCentreWorkSnapshot(
                        new LinkedHashMap<>(),
                        new LinkedHashMap<>()),
                fixture.manifestCatalog(),
                fixture.planning(),
                emptyOutbound(),
                COSTS);
        P2pWorkloadSnapshot second = factory.create(
                new P2pServiceCentreWorkSnapshot(
                        new LinkedHashMap<>(),
                        new LinkedHashMap<>()),
                fixture.manifestCatalog(),
                fixture.planning(),
                emptyOutbound(),
                COSTS);

        assertSame(first, second);
        for (int index = 0; index < first.serviceCentres().size(); index++) {
            assertSame(first.serviceCentres().get(index), second.serviceCentres().get(index));
        }
        assertEquals(3, first.serviceCentres().size());
        assertEquals(5_000, first.serviceCentres().stream()
                .mapToInt(P2pServiceCentreWorkloadSnapshot::remainingUnallocatedBagCount)
                .sum());
        assertEquals(5_000, first.serviceCentres().stream()
                .mapToInt(P2pServiceCentreWorkloadSnapshot::remainingUnallocatedPackCount)
                .sum());

        BagPlanningResult replacementPlanning = new BagPlanningResult(
                fixture.planning().plannedBags(),
                fixture.planning().p2pToteLoadPlans(),
                fixture.planning().packTraces());
        P2pWorkloadPlanIndex afterPlanningReplacement = factory.planIndexFor(
                replacementPlanning, fixture.manifestCatalog());
        assertNotSame(initial, afterPlanningReplacement);
        assertSame(afterPlanningReplacement, factory.planIndexFor(
                replacementPlanning, fixture.manifestCatalog()));

        InboundToteManifestCatalog replacementCatalog = new InboundToteManifestCatalog(
                fixture.manifestCatalog().manifests());
        P2pWorkloadPlanIndex afterCatalogReplacement = factory.planIndexFor(
                replacementPlanning, replacementCatalog);
        assertNotSame(afterPlanningReplacement, afterCatalogReplacement);
        assertSame(afterCatalogReplacement, factory.planIndexFor(
                replacementPlanning, replacementCatalog));
    }

    @Test
    void shouldReplaceOnlyCentresWhoseWorkloadValuesChange() {
        LargePlanFixture fixture = fiveThousandBagPlan();
        P2pWorkloadSnapshot base = factory.create(
                new P2pServiceCentreWorkSnapshot(
                        new LinkedHashMap<>(),
                        new LinkedHashMap<>()),
                fixture.manifestCatalog(),
                fixture.planning(),
                emptyOutbound(),
                COSTS);
        InboundToteManifest input104 = fixture.manifestCatalog()
                .findByPhysicalToteId(new PhysicalToteId("input-104"))
                .orElseThrow();

        P2pWorkloadSnapshot toteChanged = factory.create(
                new P2pServiceCentreWorkSnapshot(
                        Map.of("104", List.of(input104.physicalToteId())),
                        Map.of()),
                fixture.manifestCatalog(),
                fixture.planning(),
                emptyOutbound(),
                COSTS);

        assertNotSame(base, toteChanged);
        assertNotSame(base.require("104"), toteChanged.require("104"));
        assertSame(base.require("108"), toteChanged.require("108"));
        assertSame(base.require("109"), toteChanged.require("109"));
        assertEquals(0, base.require("104").remainingInboundToteCount());
        assertEquals(1, toteChanged.require("104").remainingInboundToteCount());

        P2pWorkloadSnapshot noTote = factory.create(
                new P2pServiceCentreWorkSnapshot(
                        new LinkedHashMap<>(),
                        new LinkedHashMap<>()),
                fixture.manifestCatalog(),
                fixture.planning(),
                emptyOutbound(),
                COSTS);
        PlannedBag firstBag = fixture.planning().plannedBags().getFirst();
        P2pWorkloadSnapshot bagChanged = factory.create(
                new P2pServiceCentreWorkSnapshot(
                        new LinkedHashMap<>(),
                        new LinkedHashMap<>()),
                fixture.manifestCatalog(),
                fixture.planning(),
                allocatedSnapshot(firstBag),
                COSTS);

        assertNotSame(noTote, bagChanged);
        assertNotSame(noTote.require("104"), bagChanged.require("104"));
        assertSame(noTote.require("108"), bagChanged.require("108"));
        assertSame(noTote.require("109"), bagChanged.require("109"));
        assertEquals(
                noTote.require("104").remainingUnallocatedBagCount() - 1,
                bagChanged.require("104").remainingUnallocatedBagCount());

        P2pWorkloadSnapshot emptyChanged = factory.create(
                new P2pServiceCentreWorkSnapshot(
                        new LinkedHashMap<>(),
                        Map.of("108", List.of(new OrderSheetKey("new-empty-108", 1)))),
                fixture.manifestCatalog(),
                fixture.planning(),
                allocatedSnapshot(firstBag),
                COSTS);

        assertNotSame(bagChanged, emptyChanged);
        assertSame(bagChanged.require("104"), emptyChanged.require("104"));
        assertNotSame(bagChanged.require("108"), emptyChanged.require("108"));
        assertSame(bagChanged.require("109"), emptyChanged.require("109"));
        assertEquals(
                List.of(new OrderSheetKey("new-empty-108", 1)),
                emptyChanged.require("108").unallocatedEmptyOrderSheetKeys());

        P2pWorkloadCostConfig changedCosts = new P2pWorkloadCostConfig(
                COSTS.toteHandlingCost(),
                COSTS.packProcessingCost(),
                COSTS.baggingCost().plus(Duration.ofSeconds(1)));
        P2pWorkloadSnapshot costChanged = factory.create(
                new P2pServiceCentreWorkSnapshot(
                        new LinkedHashMap<>(),
                        Map.of("108", List.of(new OrderSheetKey("new-empty-108", 1)))),
                fixture.manifestCatalog(),
                fixture.planning(),
                allocatedSnapshot(firstBag),
                changedCosts);

        assertNotSame(emptyChanged, costChanged);
        assertNotSame(emptyChanged.require("104"), costChanged.require("104"));
        assertNotSame(emptyChanged.require("108"), costChanged.require("108"));
        assertNotSame(emptyChanged.require("109"), costChanged.require("109"));
        assertNotEquals(
                emptyChanged.require("108").estimatedSingleLineWork(),
                costChanged.require("108").estimatedSingleLineWork());
    }

    @Test
    void shouldReplaceEqualCountValuesWhenToteIdentityOrOrderingChanges() {
        LargePlanFixture fixture = fiveThousandBagPlan();
        InboundToteManifest input104 = fixture.manifestCatalog()
                .findByPhysicalToteId(new PhysicalToteId("input-104"))
                .orElseThrow();
        InboundToteManifest input108 = fixture.manifestCatalog()
                .findByPhysicalToteId(new PhysicalToteId("input-108"))
                .orElseThrow();
        InboundToteManifest input109 = fixture.manifestCatalog()
                .findByPhysicalToteId(new PhysicalToteId("input-109"))
                .orElseThrow();
        InboundToteManifest alternate104 = manifest(
                "alternate-104", "order-alternate-104", "104", 3);
        InboundToteManifestCatalog replacementCatalog = new InboundToteManifestCatalog(
                List.of(input104, alternate104, input108, input109));

        P2pWorkloadSnapshot original = factory.create(
                new P2pServiceCentreWorkSnapshot(
                        Map.of("104", List.of(input104.physicalToteId())),
                        Map.of()),
                fixture.manifestCatalog(),
                fixture.planning(),
                emptyOutbound(),
                COSTS);
        P2pWorkloadSnapshot alternate = factory.create(
                new P2pServiceCentreWorkSnapshot(
                        Map.of("104", List.of(alternate104.physicalToteId())),
                        Map.of()),
                replacementCatalog,
                fixture.planning(),
                emptyOutbound(),
                COSTS);

        assertEquals(
                original.require("104").remainingInboundToteCount(),
                alternate.require("104").remainingInboundToteCount());
        assertNotSame(original.require("104"), alternate.require("104"));
        assertSame(original.require("108"), alternate.require("108"));
        assertSame(original.require("109"), alternate.require("109"));

        P2pWorkloadSnapshot ordered = factory.create(
                new P2pServiceCentreWorkSnapshot(
                        Map.of("104", List.of(
                                input104.physicalToteId(),
                                alternate104.physicalToteId())),
                        Map.of()),
                replacementCatalog,
                fixture.planning(),
                emptyOutbound(),
                COSTS);
        P2pWorkloadSnapshot reordered = factory.create(
                new P2pServiceCentreWorkSnapshot(
                        Map.of("104", List.of(
                                alternate104.physicalToteId(),
                                input104.physicalToteId())),
                        Map.of()),
                replacementCatalog,
                fixture.planning(),
                emptyOutbound(),
                COSTS);

        assertEquals(2, ordered.require("104").remainingInboundToteCount());
        assertEquals(2, reordered.require("104").remainingInboundToteCount());
        assertNotSame(ordered.require("104"), reordered.require("104"));
        assertSame(ordered.require("108"), reordered.require("108"));
        assertSame(ordered.require("109"), reordered.require("109"));
        assertEquals(
                List.of(input104.physicalToteId(), alternate104.physicalToteId()),
                ordered.require("104").remainingToteIds());
        assertEquals(
                List.of(alternate104.physicalToteId(), input104.physicalToteId()),
                reordered.require("104").remainingToteIds());
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
    void shouldCountAllocatedAv02ToteAsPhysicalP2pWork() {
        PhysicalToteId physicalToteId = new PhysicalToteId("av02-empty-1");
        OrderSheetKey orderSheetKey = new OrderSheetKey("empty-104", 1);
        Av02AllocatedTote allocated = new Av02AllocatedTote(
                new OperationalPhysicalToteIdentity(
                        OperationalPhysicalToteSource.AV02,
                        physicalToteId,
                        orderSheetKey,
                        OrderType.EMPTY,
                        "104",
                        PhysicalToteRole.PRE_P2P,
                        0),
                PhysicalToteRecord.preP2p(physicalToteId),
                "pharmacy-104");
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        ledger.register(allocated.physicalTote());
        ledger.assign(
                orderSheetKey,
                physicalToteId,
                PhysicalToteAssignmentStage.PRE_P2P,
                Duration.ZERO);

        P2pWorkloadSnapshot snapshot = factory.create(
                new P2pServiceCentreWorkSnapshot(
                        Map.of("104", List.of(physicalToteId)),
                        Map.of()),
                new InboundToteManifestCatalog(List.of()),
                new BagPlanningResult(List.of(), List.of(), List.of()),
                emptyOutbound(),
                COSTS,
                new Av02InventorySnapshot(1, List.of(), List.of(allocated)),
                ledger.snapshot());

        P2pServiceCentreWorkloadSnapshot serviceCentre = snapshot.require("104");
        assertEquals(List.of(physicalToteId), serviceCentre.remainingToteIds());
        assertEquals(Duration.ofSeconds(10), serviceCentre.estimatedSingleLineWork());
        assertTrue(serviceCentre.hasEstimatedWork());
    }

    @Test
    void shouldRejectRemainingPhysicalToteWithNoUniqueSource() {
        PhysicalToteId physicalToteId = new PhysicalToteId("unknown-1");
        assertThrows(IllegalStateException.class, () -> factory.create(
                new P2pServiceCentreWorkSnapshot(
                        Map.of("104", List.of(physicalToteId)),
                        Map.of()),
                new InboundToteManifestCatalog(List.of()),
                new BagPlanningResult(List.of(), List.of(), List.of()),
                emptyOutbound(),
                COSTS,
                new Av02InventorySnapshot(1, List.of(), List.of()),
                new PhysicalToteLifecycleLedger().snapshot()));
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
    void shouldFullyRevalidateReplacementPlanAndManifestInputs() {
        InboundToteManifest input = manifest("input-104", "order-104", "104", 0);
        PlannedBag bag = plannedBag(
                "rx-104", "104", "pharmacy-104", input, "pack-104");
        BagPlanningResult validPlanning = planning(List.of(bag), List.of(input));
        InboundToteManifestCatalog validCatalog = new InboundToteManifestCatalog(List.of(input));

        P2pWorkloadPlanIndex initial = factory.planIndexFor(validPlanning, validCatalog);

        BagPlanningResult invalidPlanning = new BagPlanningResult(
                List.of(bag), List.of(), List.of());
        assertThrows(IllegalStateException.class, () -> factory.planIndexFor(
                invalidPlanning, validCatalog));

        assertThrows(IllegalStateException.class, () -> factory.planIndexFor(
                validPlanning, new InboundToteManifestCatalog(List.of())));

        assertSame(initial, factory.planIndexFor(validPlanning, validCatalog));
    }

    @Test
    void shouldKeepPreviousSnapshotWhenLaterCentreValidationFails() {
        InboundToteManifest input104 = manifest("input-104", "order-104", "104", 0);
        PlannedBag bag104 = plannedBag(
                "rx-104", "104", "pharmacy-104", input104, "pack-104");
        BagPlanningResult planning = planning(List.of(bag104), List.of(input104));
        InboundToteManifestCatalog catalog = new InboundToteManifestCatalog(List.of(input104));
        P2pServiceCentreWorkSnapshot validWork = new P2pServiceCentreWorkSnapshot(
                Map.of("104", List.of(input104.physicalToteId())),
                Map.of());
        P2pWorkloadSnapshot successful = factory.create(
                validWork,
                catalog,
                planning,
                emptyOutbound(),
                COSTS);

        LinkedHashMap<String, List<PhysicalToteId>> invalidTotes = new LinkedHashMap<>();
        invalidTotes.put("104", List.of(input104.physicalToteId()));
        invalidTotes.put("108", List.of(new PhysicalToteId("missing-108")));
        assertThrows(
                IllegalStateException.class,
                () -> factory.create(
                        new P2pServiceCentreWorkSnapshot(invalidTotes, Map.of()),
                        catalog,
                        planning,
                        emptyOutbound(),
                        COSTS));

        assertSame(successful, factory.create(
                validWork,
                catalog,
                planning,
                emptyOutbound(),
                COSTS));
    }

    @Test
    void shouldKeepWorkloadCachesIndependentAcrossFactoryInstances() {
        InboundToteManifest input = manifest("input-104", "order-104", "104", 0);
        PlannedBag bag = plannedBag(
                "rx-104", "104", "pharmacy-104", input, "pack-104");
        BagPlanningResult planning = planning(List.of(bag), List.of(input));
        InboundToteManifestCatalog catalog = new InboundToteManifestCatalog(List.of(input));

        P2pWorkloadSnapshot first = new P2pWorkloadSnapshotFactory().create(
                P2pServiceCentreWorkSnapshot.empty(),
                catalog,
                planning,
                emptyOutbound(),
                COSTS);
        P2pWorkloadSnapshot second = new P2pWorkloadSnapshotFactory().create(
                P2pServiceCentreWorkSnapshot.empty(),
                catalog,
                planning,
                emptyOutbound(),
                COSTS);

        assertNotSame(first, second);
        assertNotSame(first.require("104"), second.require("104"));
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

    private static LargePlanFixture fiveThousandBagPlan() {
        InboundToteManifest input104 = manifest("input-104", "order-104", "104", 0);
        InboundToteManifest input108 = manifest("input-108", "order-108", "108", 1);
        InboundToteManifest input109 = manifest("input-109", "order-109", "109", 2);
        Map<String, InboundToteManifest> inputs = Map.of(
                "104", input104,
                "108", input108,
                "109", input109);

        List<PlannedBag> bags = new ArrayList<>(5_000);
        List<PlannedPackTrace> traces = new ArrayList<>(5_000);
        for (int index = 0; index < 5_000; index++) {
            String serviceCentreId = switch (index % 3) {
                case 0 -> "104";
                case 1 -> "108";
                default -> "109";
            };
            InboundToteManifest input = inputs.get(serviceCentreId);
            PlannedBag bag = plannedBag(
                    "rx-large-" + index,
                    serviceCentreId,
                    "pharmacy-" + serviceCentreId,
                    input,
                    "pack-large-" + index);
            bags.add(bag);
            traces.add(trace("pack-large-" + index, bag, input));
        }
        return new LargePlanFixture(
                new BagPlanningResult(bags, List.of(), traces),
                new InboundToteManifestCatalog(List.of(input104, input108, input109)));
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

    private record LargePlanFixture(
            BagPlanningResult planning,
            InboundToteManifestCatalog manifestCatalog) {
    }
}
