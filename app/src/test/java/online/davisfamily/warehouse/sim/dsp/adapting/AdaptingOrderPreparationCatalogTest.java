package online.davisfamily.warehouse.sim.dsp.adapting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

class AdaptingOrderPreparationCatalogTest {

    @Test
    void indexesDistinctKeysInSourceOrderAcrossTargetSheetsAndKeepsEmptyOrders() {
        DspOrderItem second = line("B", "store-1", "target", DspOrderLineType.ADAPTED);
        DspOrderItem first = line("A", "store-1", "target", DspOrderLineType.ADAPTED);
        LoadedDspData data = data(List.of(
                order("source", 1, OrderType.ADAPTED, List.of(second, first)),
                order("target", 1, OrderType.ASSOCIATED,
                        List.of(line("A", "store-1", "source", DspOrderLineType.ADAPTED))),
                order("target", 2, OrderType.ASSOCIATED,
                        List.of(line("B", "store-1", "source", DspOrderLineType.ADAPTED))),
                order("direct", 1, OrderType.ASSOCIATED,
                        List.of(line("C", "store-2", "direct", DspOrderLineType.FULL_PACK)))));
        Map<PreparedLineKey, OrderSheetKey> targets = new LinkedHashMap<>();
        targets.put(new PreparedLineKey("target", "B"), new OrderSheetKey("target", 2));
        targets.put(new PreparedLineKey("target", "A"), new OrderSheetKey("target", 1));

        AdaptingOrderPreparationCatalog catalog = new AdaptingOrderPreparationCatalog(
                data, new AdaptingTargetSheetCatalog(targets));

        assertEquals(List.of(new PreparedLineKey("target", "B"), new PreparedLineKey("target", "A")),
                catalog.requiredKeysFor("target"));
        assertEquals(List.of(), catalog.requiredKeysFor("direct"));
        assertEquals("store-1", catalog.requireStoreId("target"));
        assertEquals(new OrderSheetKey("target", 2),
                catalog.requireTargetSheet(new PreparedLineKey("target", "B")));
        assertThrows(UnsupportedOperationException.class, () -> catalog.requiredKeysFor("target").clear());
    }

    @Test
    void rejectsStoreMismatchAndMissingTargetBeforePublication() {
        DspOrderItem source = line("A", "store-1", "target", DspOrderLineType.ADAPTED);
        LoadedDspData mismatch = data(List.of(
                order("source", 1, OrderType.ADAPTED, List.of(source)),
                order("target", 1, OrderType.ASSOCIATED,
                        List.of(line("A", "store-2", "source", DspOrderLineType.ADAPTED)))));
        PreparedLineKey key = new PreparedLineKey("target", "A");
        assertThrows(IllegalStateException.class, () -> new AdaptingOrderPreparationCatalog(
                mismatch, new AdaptingTargetSheetCatalog(Map.of(key, new OrderSheetKey("target", 1)))));
        assertThrows(IllegalStateException.class, () -> new AdaptingOrderPreparationCatalog(
                mismatch, new AdaptingTargetSheetCatalog(Map.of())));
    }

    @Test
    void rejectsOnePreparedLineAliasedToDifferentTargetSheets() {
        LoadedDspData conflicting = data(List.of(
                order("target", 1, OrderType.ASSOCIATED,
                        List.of(line("A", "store-1", "source", DspOrderLineType.ADAPTED))),
                order("target", 2, OrderType.ASSOCIATED,
                        List.of(line("A", "store-1", "source", DspOrderLineType.ADAPTED)))));

        assertThrows(IllegalStateException.class, () -> new AdaptingOrderPreparationCatalog(
                conflicting, new AdaptingTargetSheetCatalog(Map.of())));
    }

    private static LoadedDspData data(List<NotionalToteOrder> orders) {
        return new LoadedDspData(List.of(), orders, List.of(), Set.of());
    }

    private static NotionalToteOrder order(
            String id, int sheet, OrderType type, List<DspOrderItem> lines) {
        return new NotionalToteOrder(id, id, "104", sheet, type, lines, 999, sheet);
    }

    private static DspOrderItem line(
            String reference, String store, String target, DspOrderLineType type) {
        return new DspOrderItem(reference, "product", 1, store, "patient", "prescription-" + reference,
                type, target, 1, 0);
    }
}
