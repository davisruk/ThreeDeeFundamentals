package online.davisfamily.warehouse.sim.dsp.analysis.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayLoadedInput;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.io.UnresolvedProductLine;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.schedule.DspOperationalSchedulingBaselineFactory;

class DspDeferredNsCandidateCatalogTest {
    @Test
    void shouldRetainImmutableOriginalDataAndIndexInputOccurrencesOnce() {
        DspOrderItem item = new DspOrderItem(
                "shared-line", "unknown", 17, "pharmacy", "patient", "prescription",
                DspOrderLineType.ADAPTED, "target", 7, 3);
        List<NotionalToteOrder> orders = List.of(new NotionalToteOrder(
                "source", "source-tote", "109", 2, OrderType.ADAPTED, List.of(item), 990, 0));
        DspFullDayLoadedInput input = input(orders, List.of(
                new UnresolvedProductLine("source", "shared-line", "unknown", "109"),
                new UnresolvedProductLine("target", "shared-line", "unknown", "109"),
                new UnresolvedProductLine("ns-only", "line", "unknown", "104")));
        BagPlanningResult plan = input.bagPlan();
        DspDeferredNsCandidateCatalog catalog = new DspDeferredNsCandidateCatalog(input);

        assertEquals(3, catalog.inputLineCount());
        assertEquals(2, catalog.inputLineCountFor("109"));
        assertEquals(1, catalog.inputLineCountFor("104"));
        assertEquals(0, catalog.inputLineCountFor("108"));
        assertEquals(Map.of("104", 1, "109", 2), catalog.inputLineCountByServiceCentreId());
        assertEquals(List.of("104", "109"),
                List.copyOf(catalog.inputLineCountByServiceCentreId().keySet()));
        assertSame(catalog.inputLineCountByServiceCentreId(), catalog.inputLineCountByServiceCentreId());
        assertSame(input.loadReport().unresolvedProductLines(), catalog.unresolvedProductLines());
        assertSame(input.reportableOrders(), catalog.reportableOrders());
        assertSame(item, catalog.reportableOrders().getFirst().items().getFirst());
        assertSame(plan, input.bagPlan());
        assertTrue(plan.plannedPackSlots().isEmpty());
        assertTrue(input.data().orders().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> catalog.inputLineCountByServiceCentreId().clear());
        assertThrows(UnsupportedOperationException.class, () -> catalog.unresolvedProductLines().clear());
        assertThrows(UnsupportedOperationException.class, () -> catalog.reportableOrders().clear());
    }

    @Test
    void shouldSupportEmptyCandidatesAndRejectNullInput() {
        DspDeferredNsCandidateCatalog catalog = new DspDeferredNsCandidateCatalog(input(List.of(), List.of()));
        assertEquals(0, catalog.inputLineCount());
        assertEquals(0, catalog.inputLineCountFor("104"));
        assertTrue(catalog.inputLineCountByServiceCentreId().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new DspDeferredNsCandidateCatalog(null));
    }

    private static DspFullDayLoadedInput input(
            List<NotionalToteOrder> reportableOrders, List<UnresolvedProductLine> unresolved) {
        DspDatasetLoadReport report = new DspDatasetLoadReport(0, 0, 0, unresolved, List.of());
        LoadedDspData data = new LoadedDspData(
                List.of(), List.of(), List.of(), Set.of(), Set.of(), List.of(), report);
        return new DspFullDayLoadedInput(
                data, reportableOrders, DspInputRejectionCatalog.empty(),
                new BagPlanningResult(List.of(), List.of(), List.of(), List.of(), List.of()),
                report, DspOperationalSchedulingBaselineFactory.createProductionTimetable());
    }
}
