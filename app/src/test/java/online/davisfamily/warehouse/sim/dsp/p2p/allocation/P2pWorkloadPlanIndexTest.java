package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

class P2pWorkloadPlanIndexTest {

    @Test
    void shouldPublishEncounterOrderedImmutableViewsForSharedServiceCentres() {
        InboundToteManifest input104 = manifest("input-104", "order-104", "104", 0);
        InboundToteManifest input108 = manifest("input-108", "order-108", "108", 1);
        PlannedBag first104 = bag("rx-104-a", "104", input104, "pack-104-a");
        PlannedBag first108 = bag("rx-108-a", "108", input108, "pack-108-a");
        PlannedBag second104 = bag("rx-104-b", "104", input104, "pack-104-b");
        BagPlanningResult planning = planning(
                List.of(first104, first108, second104),
                List.of(input104, input108, input104));

        P2pWorkloadPlanIndex index = P2pWorkloadPlanIndex.from(
                planning,
                new InboundToteManifestCatalog(List.of(input104, input108)));

        assertEquals(
                List.of(first104.bagKey(), first108.bagKey(), second104.bagKey()),
                new ArrayList<>(index.plannedBagsByKey().keySet()));
        assertEquals(List.of("104", "108"), new ArrayList<>(
                index.plannedBagsByServiceCentre().keySet()));
        assertEquals(List.of(first104, second104),
                index.plannedBagsByServiceCentre().get("104"));
        assertEquals(List.of(first104.bagKey(), first108.bagKey(), second104.bagKey()),
                index.plannedBagsByKey().values().stream()
                        .map(PlannedBag::bagKey)
                        .toList());
        assertEquals(List.of("104", "108"), index.orderedServiceCentreIds());

        assertSame(index.plannedBagsByKey(), index.plannedBagsByKey());
        assertSame(index.plannedBagsByServiceCentre(), index.plannedBagsByServiceCentre());
        assertSame(index.plannedBagsByServiceCentre().get("104"),
                index.plannedBagsByServiceCentre().get("104"));
        assertSame(index.orderedServiceCentreIds(), index.orderedServiceCentreIds());

        assertThrows(UnsupportedOperationException.class,
                () -> index.plannedBagsByKey().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> index.plannedBagsByServiceCentre().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> index.plannedBagsByServiceCentre().get("104").clear());
        assertThrows(UnsupportedOperationException.class,
                () -> index.orderedServiceCentreIds().clear());
    }

    @Test
    void shouldRejectEveryBagPackTraceAndManifestMismatch() {
        InboundToteManifest input104 = manifest("input-104", "order-104", "104", 0);
        InboundToteManifest input108 = manifest("input-108", "order-108", "108", 1);
        PlannedBag bag104 = bag("rx-104", "104", input104, "pack-104");
        PlannedBag otherBag = bag("rx-other", "104", input104, "pack-other");
        InboundToteManifestCatalog catalog = new InboundToteManifestCatalog(
                List.of(input104, input108));

        assertThrows(IllegalStateException.class, () -> P2pWorkloadPlanIndex.from(
                new BagPlanningResult(List.of(bag104), List.of(), List.of()),
                catalog));

        assertThrows(IllegalStateException.class, () -> P2pWorkloadPlanIndex.from(
                new BagPlanningResult(
                        List.of(bag104, otherBag),
                        List.of(),
                        List.of(trace("pack-104", bag104, input104))),
                catalog));

        assertThrows(IllegalStateException.class, () -> P2pWorkloadPlanIndex.from(
                new BagPlanningResult(
                        List.of(bag104),
                        List.of(),
                        List.of(trace("pack-104", otherBag, input104))),
                catalog));

        assertThrows(IllegalStateException.class, () -> P2pWorkloadPlanIndex.from(
                new BagPlanningResult(
                        List.of(bag104),
                        List.of(),
                        List.of(traceWithServiceCentre(
                                "pack-104", bag104, input104, "108"))),
                catalog));

        InboundToteManifest missingInput = manifest(
                "input-missing", "order-missing", "104", 2);
        assertThrows(IllegalStateException.class, () -> P2pWorkloadPlanIndex.from(
                new BagPlanningResult(
                        List.of(bag104),
                        List.of(),
                        List.of(trace("pack-104", bag104, missingInput))),
                catalog));

        PlannedBag bagWith108Input = bag("rx-108-input", "104", input108, "pack-108-input");
        assertThrows(IllegalStateException.class, () -> P2pWorkloadPlanIndex.from(
                new BagPlanningResult(
                        List.of(bagWith108Input),
                        List.of(),
                        List.of(trace("pack-108-input", bagWith108Input, input108))),
                catalog));

        assertThrows(IllegalStateException.class, () -> P2pWorkloadPlanIndex.from(
                new BagPlanningResult(
                        List.of(bag104),
                        List.of(),
                        List.of(
                                trace("pack-104", bag104, input104),
                                trace("pack-extra", bag104, input104))),
                catalog));

        PlannedBag duplicatePackBag = bag("rx-duplicate", "104", input104, "pack-104");
        assertThrows(IllegalStateException.class, () -> P2pWorkloadPlanIndex.from(
                new BagPlanningResult(
                        List.of(bag104, duplicatePackBag),
                        List.of(),
                        List.of(trace("pack-104", bag104, input104))),
                catalog));
    }

    private static BagPlanningResult planning(
            List<PlannedBag> bags,
            List<InboundToteManifest> inputsByBag) {
        List<PlannedPackTrace> traces = new ArrayList<>();
        for (int index = 0; index < bags.size(); index++) {
            PlannedBag bag = bags.get(index);
            InboundToteManifest input = inputsByBag.get(index);
            for (String packId : bag.physicalPackIds()) {
                traces.add(trace(packId, bag, input));
            }
        }
        return new BagPlanningResult(bags, List.of(), traces);
    }

    private static PlannedBag bag(
            String prescriptionId,
            String serviceCentreId,
            InboundToteManifest input,
            String... packIds) {
        return new PlannedBag(
                new BagKey(prescriptionId, 1),
                serviceCentreId,
                "pharmacy-" + serviceCentreId,
                "patient-" + prescriptionId,
                prescriptionId,
                List.of(packIds),
                List.of(input.orderSheetKey()));
    }

    private static PlannedPackTrace trace(
            String packId,
            PlannedBag bag,
            InboundToteManifest input) {
        return traceWithServiceCentre(packId, bag, input, bag.serviceCentreId());
    }

    private static PlannedPackTrace traceWithServiceCentre(
            String packId,
            PlannedBag bag,
            InboundToteManifest input,
            String serviceCentreId) {
        PackSourceProvenance provenance = new PackSourceProvenance(
                input.orderSheetKey(),
                "line-" + packId,
                "product-" + packId,
                serviceCentreId,
                bag.pharmacyId(),
                bag.patientId(),
                bag.prescriptionId());
        return new PlannedPackTrace(
                packId,
                provenance,
                input.physicalToteId(),
                input.orderSheetKey(),
                bag.bagKey());
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
}
