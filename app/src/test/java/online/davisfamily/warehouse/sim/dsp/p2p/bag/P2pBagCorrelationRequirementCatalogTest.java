package online.davisfamily.warehouse.sim.dsp.p2p.bag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

class P2pBagCorrelationRequirementCatalogTest {

    @Test
    void shouldCacheDistinctCorrelationIdsAcrossPhysicalAndLogicalIndexes() {
        P2pBagCorrelationRequirement requirementA =
                new P2pBagCorrelationRequirement("bag-a", 2);
        P2pBagCorrelationRequirement requirementB =
                new P2pBagCorrelationRequirement("bag-b", 1);
        P2pBagCorrelationRequirementCatalog catalog =
                new P2pBagCorrelationRequirementCatalog(
                        Map.of(
                                new PhysicalToteId("tote-a"), List.of(requirementA, requirementB),
                                new PhysicalToteId("tote-b"), List.of(requirementB)),
                        Map.of(
                                new OrderSheetKey("sheet-a", 1), List.of(requirementA),
                                new OrderSheetKey("sheet-b", 1), List.of(requirementB)));

        Set<String> correlationIds = catalog.correlationIds();

        assertEquals(Set.of("bag-a", "bag-b"), correlationIds);
        assertSame(correlationIds, catalog.correlationIds());
        assertThrows(UnsupportedOperationException.class, () -> correlationIds.clear());
    }

    @Test
    void shouldReturnOneStableImmutableEmptyCorrelationSet() {
        P2pBagCorrelationRequirementCatalog catalog =
                P2pBagCorrelationRequirementCatalog.empty();

        Set<String> correlationIds = catalog.correlationIds();

        assertEquals(Set.of(), correlationIds);
        assertSame(correlationIds, catalog.correlationIds());
        assertThrows(UnsupportedOperationException.class, () -> correlationIds.add("bag-a"));
    }
}
