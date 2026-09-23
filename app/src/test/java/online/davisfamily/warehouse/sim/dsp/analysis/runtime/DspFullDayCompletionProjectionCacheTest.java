package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.av02.Av02AllocatedTote;
import online.davisfamily.warehouse.sim.dsp.av02.Av02InventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.outbound.AllocatedOutboundBag;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteClosureReason;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.OutputSheetAllocation;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.supply.DspSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.PhysicalToteSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.PhysicalToteSupplyState;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreAuthorizationState;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreSupplySnapshot;

class DspFullDayCompletionProjectionCacheTest {

    @Test
    void shouldPublishExactImmutableProjectionsAndReuseEachIdentity() {
        DspFullDayCompletionProjectionCache cache = newCache();
        DspSupplySnapshot supply = supplySnapshot(true);
        PhysicalToteLifecycleSnapshot lifecycle = lifecycleSnapshot(false);
        OsrInventorySnapshot osr = osrSnapshot(false);
        Av02InventorySnapshot av02 = av02Snapshot(false);
        OutboundAllocationSnapshot outbound = outboundSnapshot(false);

        DspFullDayCompletionProjectionCache.SupplyProjection supplyProjection =
                cache.supplyProjection(supply);
        DspFullDayCompletionProjectionCache.LifecycleProjection lifecycleProjection =
                cache.lifecycleProjection(lifecycle);
        DspFullDayCompletionProjectionCache.OsrProjection osrProjection =
                cache.osrProjection(osr);
        DspFullDayCompletionProjectionCache.Av02Projection av02Projection =
                cache.av02Projection(av02);
        DspFullDayCompletionProjectionCache.OutboundProjection outboundProjection =
                cache.outboundProjection(outbound);

        assertSame(supplyProjection, cache.supplyProjection(supply));
        assertSame(lifecycleProjection, cache.lifecycleProjection(lifecycle));
        assertSame(osrProjection, cache.osrProjection(osr));
        assertSame(av02Projection, cache.av02Projection(av02));
        assertSame(outboundProjection, cache.outboundProjection(outbound));

        assertEquals(List.of("104", "108"), supplyProjection.serviceCentreIds());
        assertEquals(List.of("104", "108"),
                new ArrayList<>(supplyProjection.capacityBlockedCounts().keySet()));
        assertEquals(Map.of("104", 1, "108", 0),
                supplyProjection.capacityBlockedCounts());

        assertEquals(Map.of("104", 1, "108", 1),
                lifecycleProjection.nonTerminalInboundCounts());
        assertEquals(List.of(
                new PhysicalToteId("tote-108"),
                new PhysicalToteId("tote-104"),
                new PhysicalToteId("pre-p2p-1")),
                lifecycleProjection.nonTerminalPhysicalToteIds());

        assertEquals(List.of("108", "104"),
                new ArrayList<>(osrProjection.waitingCounts().keySet()));
        assertEquals(Map.of("108", 1, "104", 1), osrProjection.waitingCounts());

        assertEquals(List.of("104", "108"),
                new ArrayList<>(av02Projection.waitingCounts().keySet()));
        assertEquals(Map.of("104", 1, "108", 1), av02Projection.waitingCounts());

        assertEquals(List.of(
                new BagKey("rx-104-1", 1),
                new BagKey("rx-108-1", 1)),
                new ArrayList<>(outboundProjection.allocatedBagKeys()));
        assertEquals(Map.of("104", 1, "108", 0),
                outboundProjection.remainingPlannedBagCounts());
        assertEquals(Map.of("104", 2, "108", 0),
                outboundProjection.remainingPlannedPackCounts());
        assertEquals(Map.of("104", 1, "108", 1),
                outboundProjection.openOutboundCounts());

        assertThrows(UnsupportedOperationException.class,
                () -> supplyProjection.serviceCentreIds().add("extra"));
        assertThrows(UnsupportedOperationException.class,
                () -> supplyProjection.capacityBlockedCounts().put("extra", 1));
        assertThrows(UnsupportedOperationException.class,
                () -> lifecycleProjection.nonTerminalInboundCounts().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> lifecycleProjection.nonTerminalPhysicalToteIds().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> osrProjection.waitingCounts().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> av02Projection.waitingCounts().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> outboundProjection.allocatedBagKeys().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> outboundProjection.remainingPlannedBagCounts().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> outboundProjection.remainingPlannedPackCounts().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> outboundProjection.openOutboundCounts().clear());
    }

    @Test
    void shouldReplaceOnlyTheChangedDimensionAndTreatEqualDistinctInputsAsChanged() {
        DspFullDayCompletionProjectionCache cache = newCache();
        DspSupplySnapshot supply = supplySnapshot(false);
        PhysicalToteLifecycleSnapshot lifecycle = lifecycleSnapshot(false);
        OsrInventorySnapshot osr = osrSnapshot(false);
        Av02InventorySnapshot av02 = av02Snapshot(false);
        OutboundAllocationSnapshot outbound = outboundSnapshot(false);

        DspFullDayCompletionProjectionCache.SupplyProjection supplyProjection =
                cache.supplyProjection(supply);
        DspFullDayCompletionProjectionCache.LifecycleProjection lifecycleProjection =
                cache.lifecycleProjection(lifecycle);
        DspFullDayCompletionProjectionCache.OsrProjection osrProjection =
                cache.osrProjection(osr);
        DspFullDayCompletionProjectionCache.Av02Projection av02Projection =
                cache.av02Projection(av02);
        DspFullDayCompletionProjectionCache.OutboundProjection outboundProjection =
                cache.outboundProjection(outbound);

        DspSupplySnapshot changedSupplyInput = supplySnapshot(true);
        DspFullDayCompletionProjectionCache.SupplyProjection changedSupply =
                cache.supplyProjection(changedSupplyInput);
        assertNotSame(supplyProjection, changedSupply);
        assertSame(lifecycleProjection, cache.lifecycleProjection(lifecycle));
        assertSame(osrProjection, cache.osrProjection(osr));
        assertSame(av02Projection, cache.av02Projection(av02));
        assertSame(outboundProjection, cache.outboundProjection(outbound));

        assertSame(changedSupply, cache.supplyProjection(changedSupplyInput));

        PhysicalToteLifecycleSnapshot changedLifecycleInput = lifecycleSnapshot(true);
        DspFullDayCompletionProjectionCache.LifecycleProjection changedLifecycle =
                cache.lifecycleProjection(changedLifecycleInput);
        assertNotSame(lifecycleProjection, changedLifecycle);
        assertSame(changedSupply, cache.supplyProjection(changedSupplyInput));
        assertSame(osrProjection, cache.osrProjection(osr));
        assertSame(av02Projection, cache.av02Projection(av02));
        assertSame(outboundProjection, cache.outboundProjection(outbound));

        assertSame(changedLifecycle, cache.lifecycleProjection(changedLifecycleInput));

        OsrInventorySnapshot changedOsrInput = osrSnapshot(true);
        DspFullDayCompletionProjectionCache.OsrProjection changedOsr =
                cache.osrProjection(changedOsrInput);
        assertNotSame(osrProjection, changedOsr);
        assertSame(changedSupply, cache.supplyProjection(changedSupplyInput));
        assertSame(changedLifecycle, cache.lifecycleProjection(changedLifecycleInput));
        assertSame(av02Projection, cache.av02Projection(av02));
        assertSame(outboundProjection, cache.outboundProjection(outbound));

        assertSame(changedOsr, cache.osrProjection(changedOsrInput));

        Av02InventorySnapshot changedAv02Input = av02Snapshot(true);
        DspFullDayCompletionProjectionCache.Av02Projection changedAv02 =
                cache.av02Projection(changedAv02Input);
        assertNotSame(av02Projection, changedAv02);
        assertSame(changedSupply, cache.supplyProjection(changedSupplyInput));
        assertSame(changedLifecycle, cache.lifecycleProjection(changedLifecycleInput));
        assertSame(changedOsr, cache.osrProjection(changedOsrInput));
        assertSame(outboundProjection, cache.outboundProjection(outbound));

        assertSame(changedAv02, cache.av02Projection(changedAv02Input));

        OutboundAllocationSnapshot changedOutboundInput = outboundSnapshot(true);
        DspFullDayCompletionProjectionCache.OutboundProjection changedOutbound =
                cache.outboundProjection(changedOutboundInput);
        assertNotSame(outboundProjection, changedOutbound);
        assertSame(changedSupply, cache.supplyProjection(changedSupplyInput));
        assertSame(changedLifecycle, cache.lifecycleProjection(changedLifecycleInput));
        assertSame(changedOsr, cache.osrProjection(changedOsrInput));
        assertSame(changedAv02, cache.av02Projection(changedAv02Input));

        assertNotSame(changedSupply, cache.supplyProjection(supplySnapshot(true)));
        assertNotSame(changedLifecycle, cache.lifecycleProjection(lifecycleSnapshot(true)));
        assertNotSame(changedOsr, cache.osrProjection(osrSnapshot(true)));
        assertNotSame(changedAv02, cache.av02Projection(av02Snapshot(true)));
        assertNotSame(changedOutbound, cache.outboundProjection(outboundSnapshot(true)));
    }

    @Test
    void shouldValidateInputsAndRetainThePublishedProjectionAfterFailure() {
        DspFullDayCompletionProjectionCache cache = newCache();
        DspSupplySnapshot supply = supplySnapshot(false);
        PhysicalToteLifecycleSnapshot lifecycle = lifecycleSnapshot(false);
        OsrInventorySnapshot osr = osrSnapshot(false);
        Av02InventorySnapshot av02 = av02Snapshot(false);
        OutboundAllocationSnapshot outbound = outboundSnapshot(false);

        DspFullDayCompletionProjectionCache.SupplyProjection supplyProjection =
                cache.supplyProjection(supply);
        DspFullDayCompletionProjectionCache.LifecycleProjection lifecycleProjection =
                cache.lifecycleProjection(lifecycle);
        DspFullDayCompletionProjectionCache.OsrProjection osrProjection =
                cache.osrProjection(osr);
        DspFullDayCompletionProjectionCache.Av02Projection av02Projection =
                cache.av02Projection(av02);
        DspFullDayCompletionProjectionCache.OutboundProjection outboundProjection =
                cache.outboundProjection(outbound);

        assertThrows(IllegalArgumentException.class, () -> cache.supplyProjection(null));
        assertThrows(IllegalArgumentException.class, () -> cache.lifecycleProjection(null));
        assertThrows(IllegalArgumentException.class, () -> cache.osrProjection(null));
        assertThrows(IllegalArgumentException.class, () -> cache.av02Projection(null));
        assertThrows(IllegalArgumentException.class, () -> cache.outboundProjection(null));

        assertSame(supplyProjection, cache.supplyProjection(supply));
        assertSame(lifecycleProjection, cache.lifecycleProjection(lifecycle));
        assertSame(osrProjection, cache.osrProjection(osr));
        assertSame(av02Projection, cache.av02Projection(av02));
        assertSame(outboundProjection, cache.outboundProjection(outbound));
    }

    private static DspFullDayCompletionProjectionCache newCache() {
        return new DspFullDayCompletionProjectionCache(
                manifestsByServiceCentre(), plannedBagsByServiceCentre());
    }

    private static Map<String, List<InboundToteManifest>> manifestsByServiceCentre() {
        Map<String, List<InboundToteManifest>> manifests = new LinkedHashMap<>();
        manifests.put("104", List.of(manifest("tote-104", "104", "ORDER-104")));
        manifests.put("108", List.of(manifest("tote-108", "108", "ORDER-108")));
        return Collections.unmodifiableMap(manifests);
    }

    private static Map<String, List<PlannedBag>> plannedBagsByServiceCentre() {
        Map<String, List<PlannedBag>> plannedBags = new LinkedHashMap<>();
        plannedBags.put("104", List.of(
                plannedBag("rx-104-1", "104", "ORDER-104", List.of("pack-104-1")),
                plannedBag("rx-104-2", "104", "ORDER-104-2", List.of(
                        "pack-104-2", "pack-104-3"))));
        plannedBags.put("108", List.of(
                plannedBag("rx-108-1", "108", "ORDER-108", List.of("pack-108-1"))));
        return Collections.unmodifiableMap(plannedBags);
    }

    private static DspSupplySnapshot supplySnapshot(boolean blocked) {
        ServiceCentreSupplySnapshot first = supplyCentre(
                "104",
                1,
                List.of(
                        physicalSupply("supply-104-1", "104", PhysicalToteSupplyState.PRELOADED_IN_OSR),
                        physicalSupply("supply-104-2", "104",
                                blocked
                                        ? PhysicalToteSupplyState.BLOCKED_BY_OSR_CAPACITY
                                        : PhysicalToteSupplyState.PRELOADED_IN_OSR)));
        ServiceCentreSupplySnapshot second = supplyCentre(
                "108",
                2,
                List.of(physicalSupply(
                        "supply-108-1", "108", PhysicalToteSupplyState.PRELOADED_IN_OSR)));
        return new DspSupplySnapshot(
                "test-supply",
                1,
                10,
                3,
                Optional.empty(),
                Optional.empty(),
                Set.of(),
                List.of(first, second),
                0);
    }

    private static ServiceCentreSupplySnapshot supplyCentre(
            String serviceCentreId,
            int priority,
            List<PhysicalToteSupplySnapshot> physicalTotes) {
        return new ServiceCentreSupplySnapshot(
                serviceCentreId,
                priority,
                ServiceCentreAuthorizationState.PRELOADED,
                Optional.of(Duration.ZERO),
                physicalTotes.size(),
                physicalTotes.size(),
                0,
                0,
                Set.of(),
                physicalTotes);
    }

    private static PhysicalToteSupplySnapshot physicalSupply(
            String physicalToteId,
            String serviceCentreId,
            PhysicalToteSupplyState state) {
        return new PhysicalToteSupplySnapshot(
                new PhysicalToteId(physicalToteId),
                new OrderSheetKey("supply-" + physicalToteId, 1),
                OrderType.FULL_PACK,
                serviceCentreId,
                0,
                state);
    }

    private static PhysicalToteLifecycleSnapshot lifecycleSnapshot(boolean terminal) {
        Map<PhysicalToteId, PhysicalToteRecord> totes = new LinkedHashMap<>();
        PhysicalToteId first = new PhysicalToteId("tote-108");
        PhysicalToteId second = new PhysicalToteId("tote-104");
        PhysicalToteId preP2p = new PhysicalToteId("pre-p2p-1");
        PhysicalToteId consumed = new PhysicalToteId("consumed-1");
        totes.put(first, PhysicalToteRecord.inboundPack(first));
        totes.put(second, terminal
                ? new PhysicalToteRecord(
                        second,
                        PhysicalToteRole.INBOUND_PACK,
                        PhysicalToteLifecycleState.CONSUMED_AT_P2P)
                : PhysicalToteRecord.inboundPack(second));
        totes.put(preP2p, PhysicalToteRecord.preP2p(preP2p));
        totes.put(consumed, new PhysicalToteRecord(
                consumed,
                PhysicalToteRole.INBOUND_PACK,
                PhysicalToteLifecycleState.CONSUMED_AT_P2P));
        return new PhysicalToteLifecycleSnapshot(totes, List.of());
    }

    private static OsrInventorySnapshot osrSnapshot(boolean changed) {
        InboundToteManifest first = manifest("tote-108", "108", "ORDER-108");
        InboundToteManifest second = manifest("tote-104", "104", "ORDER-104");
        return new OsrInventorySnapshot(
                3,
                changed ? List.of(second) : List.of(first, second),
                List.of());
    }

    private static Av02InventorySnapshot av02Snapshot(boolean changed) {
        Av02AllocatedTote first = av02Tote("av02-104", "104", 1);
        Av02AllocatedTote second = av02Tote("av02-108", "108", 2);
        return new Av02InventorySnapshot(
                3,
                changed ? List.of(second) : List.of(first, second),
                List.of());
    }

    private static OutboundAllocationSnapshot outboundSnapshot(boolean changed) {
        PlannedBag firstPlannedBag = plannedBag(
                "rx-104-1", "104", "ORDER-104", List.of("pack-104-1"));
        PlannedBag secondPlannedBag = plannedBag(
                "rx-108-1", "108", "ORDER-108", List.of("pack-108-1"));
        AllocatedOutboundBag first = allocatedBag(firstPlannedBag, "outbound-104");
        AllocatedOutboundBag second = allocatedBag(secondPlannedBag, "outbound-108");
        OutboundToteSnapshot openFirst = new OutboundToteSnapshot(
                new PhysicalToteId("outbound-104"),
                new P2pLineId("line-104"),
                Optional.of("104"),
                Optional.of("pharmacy-104"),
                3,
                List.of(first),
                Optional.empty());
        OutboundToteSnapshot openSecond = new OutboundToteSnapshot(
                new PhysicalToteId("outbound-open-108"),
                new P2pLineId("line-108"),
                Optional.of("108"),
                Optional.of("pharmacy-108"),
                3,
                List.of(),
                Optional.empty());
        OutboundToteSnapshot closed = new OutboundToteSnapshot(
                new PhysicalToteId("outbound-108"),
                new P2pLineId("line-closed"),
                Optional.of("108"),
                Optional.of("pharmacy-108"),
                3,
                changed ? List.of() : List.of(second),
                changed
                        ? Optional.of(OutboundToteClosureReason.HARD_CUTOFF)
                        : Optional.of(OutboundToteClosureReason.APPLICABLE_WORK_COMPLETE));
        Map<P2pLineId, OutboundToteSnapshot> open = new LinkedHashMap<>();
        open.put(openFirst.p2pLineId(), openFirst);
        open.put(openSecond.p2pLineId(), openSecond);
        return new OutboundAllocationSnapshot(
                open,
                List.of(closed),
                changed ? List.of(first) : List.of(first, second));
    }

    private static InboundToteManifest manifest(
            String physicalToteId,
            String serviceCentreId,
            String orderId) {
        return new InboundToteManifest(
                new PhysicalToteId(physicalToteId),
                new OrderSheetKey(orderId, 1),
                OrderType.FULL_PACK,
                serviceCentreId,
                List.of(new DspOrderItem("line-" + physicalToteId, "product-a", 1)),
                0);
    }

    private static PlannedBag plannedBag(
            String prescriptionId,
            String serviceCentreId,
            String orderId,
            List<String> physicalPackIds) {
        return new PlannedBag(
                new BagKey(prescriptionId, 1),
                serviceCentreId,
                "pharmacy-" + serviceCentreId,
                "patient-" + prescriptionId,
                prescriptionId,
                physicalPackIds,
                List.of(new OrderSheetKey(orderId, 1)));
    }

    private static AllocatedOutboundBag allocatedBag(
            PlannedBag plannedBag,
            String outboundPhysicalToteId) {
        OrderSheetKey sourceSheet = plannedBag.owningOrderSheetKeys().getFirst();
        return new AllocatedOutboundBag(
                plannedBag,
                new PhysicalToteId(outboundPhysicalToteId),
                List.of(new OutputSheetAllocation(sourceSheet, sourceSheet)));
    }

    private static Av02AllocatedTote av02Tote(
            String physicalToteId,
            String serviceCentreId,
            long sourceSequenceNumber) {
        PhysicalToteId id = new PhysicalToteId(physicalToteId);
        return new Av02AllocatedTote(
                new OperationalPhysicalToteIdentity(
                        OperationalPhysicalToteSource.AV02,
                        id,
                        new OrderSheetKey("av02-" + physicalToteId, 1),
                        OrderType.EMPTY,
                        serviceCentreId,
                        PhysicalToteRole.PRE_P2P,
                        sourceSequenceNumber),
                PhysicalToteRecord.preP2p(id),
                "pharmacy-" + serviceCentreId);
    }
}
