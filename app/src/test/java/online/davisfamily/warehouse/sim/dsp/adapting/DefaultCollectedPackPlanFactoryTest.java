package online.davisfamily.warehouse.sim.dsp.adapting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.BagSequencePosition;
import online.davisfamily.warehouse.sim.dsp.bagging.DspPackPlanFactory;
import online.davisfamily.warehouse.sim.dsp.bagging.PackProvenanceRegistry;
import online.davisfamily.warehouse.sim.dsp.bagging.PackSourceProvenance;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackSlot;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackSlotKey;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;

class DefaultCollectedPackPlanFactoryTest {
    private static final OrderSheetKey SOURCE = new OrderSheetKey("source", 1);
    private static final OrderSheetKey TARGET = new OrderSheetKey("target", 1);
    private static final BagKey BAG = new BagKey("rx", 1);

    @Test
    void plannedSlotModeReusesEachProductsDimensionsForPrepareAndCreate() {
        var first = collectedLine("A", "product-a");
        var second = collectedLine("B", "product-b");
        var firstDimensions = new PackDimensions(0.07f, 0.034f, 0.027f);
        var secondDimensions = new PackDimensions(0.174f, 0.075f, 0.03f);
        var firstSlot = slot(first, firstDimensions);
        var secondSlot = slot(second, secondDimensions);
        var plan = new BagPlanningResult(
                List.of(new PlannedBag(BAG, "104", "store", "patient", "rx",
                        List.of("pack-A-1", "pack-B-1"), List.of(TARGET))),
                List.of(), List.of(), List.of(firstSlot, secondSlot),
                List.of(new BagSequencePosition(1, 1)));
        var registry = new PackProvenanceRegistry();
        var factory = DefaultCollectedPackPlanFactory.forPlannedSlots(
                new DspPackPlanFactory(registry), plan);

        var prepared = factory.preparePackPlans(List.of(first, second));

        assertEquals(List.of("pack-A-1", "pack-B-1"),
                prepared.packPlans().stream().map(pack -> pack.packId()).toList());
        assertSame(firstDimensions, prepared.packPlans().get(0).dimensions());
        assertSame(secondDimensions, prepared.packPlans().get(1).dimensions());
        for (var pack : prepared.packPlans()) {
            assertEquals(BAG.correlationId(), pack.correlationId());
            assertTrue(registry.find(pack.packId()).isEmpty());
        }
        assertEquals(firstSlot.sourceProvenance(), prepared.provenanceByPackId().get("pack-A-1"));
        assertEquals(secondSlot.sourceProvenance(), prepared.provenanceByPackId().get("pack-B-1"));

        var created = factory.createPackPlans(List.of(first, second));

        assertEquals(prepared.packPlans(), created);
        assertSame(firstDimensions, created.get(0).dimensions());
        assertSame(secondDimensions, created.get(1).dimensions());
        assertEquals(firstSlot.sourceProvenance(), registry.find("pack-A-1").orElseThrow());
        assertEquals(secondSlot.sourceProvenance(), registry.find("pack-B-1").orElseThrow());
    }

    @Test
    void legacyConstructorsPreserveDefaultAndExplicitDimensionsForPrepareAndCreate() {
        var registry = new PackProvenanceRegistry();
        var packFactory = new DspPackPlanFactory(registry);
        var customDimensions = new PackDimensions(0.12f, 0.06f, 0.04f);
        List<DefaultCollectedPackPlanFactory> factories = List.of(
                new DefaultCollectedPackPlanFactory(packFactory),
                new DefaultCollectedPackPlanFactory(packFactory, (line, ordinal) -> "custom-bag"),
                new DefaultCollectedPackPlanFactory(customDimensions, packFactory),
                new DefaultCollectedPackPlanFactory(customDimensions, packFactory,
                        (line, ordinal) -> "custom-bag"));
        for (int index = 0; index < factories.size(); index++) {
            var line = collectedLine("legacy-" + index, "product-a");
            var expectedDimensions = index < 2
                    ? DefaultCollectedPackPlanFactory.DEFAULT_DIMENSIONS : customDimensions;
            String expectedCorrelation = index % 2 == 0 ? line.line().lineReference() : "custom-bag";
            var prepared = factories.get(index).preparePackPlans(List.of(line));
            var pack = prepared.packPlans().getFirst();
            assertSame(expectedDimensions, pack.dimensions());
            assertEquals(expectedCorrelation, pack.correlationId());
            assertTrue(registry.find(pack.packId()).isEmpty());
            var created = factories.get(index).createPackPlans(List.of(line)).getFirst();
            assertEquals(pack, created);
            assertSame(expectedDimensions, created.dimensions());
            assertEquals(prepared.provenanceByPackId().get(pack.packId()),
                    registry.find(pack.packId()).orElseThrow());
        }
    }

    private static AdaptedLineRecord collectedLine(String reference, String product) {
        return AdaptedLineRecord.fromPreparedLineWithoutLocation(new DspOrderItem(
                reference, product, 1, "store", "patient", "rx",
                DspOrderLineType.ADAPTED, "target", 1, 0), SOURCE, "104");
    }

    private static PlannedPackSlot slot(AdaptedLineRecord line, PackDimensions dimensions) {
        String reference = line.line().lineReference();
        return new PlannedPackSlot(new PlannedPackSlotKey(SOURCE, reference, 1),
                "pack-" + reference + "-1", dimensions,
                new PackSourceProvenance(SOURCE, reference, line.line().productId(),
                        "104", "store", "patient", "rx"), TARGET, Optional.empty(), BAG);
    }
}
