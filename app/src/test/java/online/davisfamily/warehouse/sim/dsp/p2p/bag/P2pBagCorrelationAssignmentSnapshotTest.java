package online.davisfamily.warehouse.sim.dsp.p2p.bag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;

class P2pBagCorrelationAssignmentSnapshotTest {

    @Test
    void shouldIndexOrderedAssignmentsAndReturnExactEntries() {
        List<P2pBagCorrelationAssignment> source = new ArrayList<>();
        for (int index = 0; index < 5_000; index++) {
            source.add(assignment(index));
        }

        P2pBagCorrelationAssignmentSnapshot snapshot =
                new P2pBagCorrelationAssignmentSnapshot(source);

        assertEquals(5_000, snapshot.assignments().size());
        assertEquals(List.of("correlation-0", "correlation-1", "correlation-2"),
                snapshot.assignmentsByCorrelation().keySet().stream().limit(3).toList());
        assertEquals("correlation-4999",
                snapshot.assignmentsByCorrelation().keySet().stream().toList().get(4_999));
        assertEquals(assignment(0), snapshot.find(" correlation-0 ").orElseThrow());
        assertEquals(assignment(2_500), snapshot.find("correlation-2500").orElseThrow());
        assertEquals(assignment(4_999), snapshot.find("correlation-4999").orElseThrow());
        assertEquals(new P2pLineId("line-0"),
                snapshot.lineFor("correlation-0").orElseThrow());
        assertEquals(new P2pLineId("line-2500"),
                snapshot.lineFor("correlation-2500").orElseThrow());
        assertEquals(new P2pLineId("line-4999"),
                snapshot.lineFor("correlation-4999").orElseThrow());
        assertTrue(snapshot.find("missing").isEmpty());
        assertTrue(snapshot.lineFor("missing").isEmpty());
        assertSame(snapshot.assignmentsByCorrelation(), snapshot.assignmentsByCorrelation());
        assertSame(snapshot.assignmentsByCorrelation(), snapshot.correlationAssignments());
    }

    @Test
    void shouldDefensivelyCopyAndExposeImmutableStableCollections() {
        ArrayList<P2pBagCorrelationAssignment> source =
                new ArrayList<>(List.of(assignment(1), assignment(2)));
        P2pBagCorrelationAssignmentSnapshot snapshot =
                new P2pBagCorrelationAssignmentSnapshot(source);
        Map<String, P2pBagCorrelationAssignment> indexed = snapshot.assignmentsByCorrelation();
        source.clear();

        assertEquals(List.of(assignment(1), assignment(2)), snapshot.assignments());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.assignments().clear());
        assertThrows(UnsupportedOperationException.class, () -> indexed.clear());
        assertSame(indexed, snapshot.assignmentsByCorrelation());
        assertSame(indexed, snapshot.correlationAssignments());
    }

    @Test
    void shouldPreserveValidationMapCompatibilityValueSemanticsAndEmptyIdentity() {
        P2pBagCorrelationAssignment actual = assignment(7);
        Map<String, P2pBagCorrelationAssignment> source = new LinkedHashMap<>();
        source.put("alias", actual);

        P2pBagCorrelationAssignmentSnapshot fromMap =
                new P2pBagCorrelationAssignmentSnapshot(source);
        P2pBagCorrelationAssignmentSnapshot equivalent =
                new P2pBagCorrelationAssignmentSnapshot(List.of(actual));

        assertEquals(actual, fromMap.find("correlation-7").orElseThrow());
        assertEquals(List.of("correlation-7"), fromMap.assignmentsByCorrelation().keySet().stream().toList());
        assertEquals(equivalent, fromMap);
        assertEquals(equivalent.hashCode(), fromMap.hashCode());
        assertEquals(
                "P2pBagCorrelationAssignmentSnapshot[assignments=" + List.of(actual) + "]",
                fromMap.toString());
        assertSame(P2pBagCorrelationAssignmentSnapshot.empty(),
                P2pBagCorrelationAssignmentSnapshot.empty());

        assertThrows(IllegalArgumentException.class,
                () -> new P2pBagCorrelationAssignmentSnapshot((List<P2pBagCorrelationAssignment>) null));
        List<P2pBagCorrelationAssignment> nullElement = new ArrayList<>();
        nullElement.add(null);
        assertThrows(IllegalArgumentException.class,
                () -> new P2pBagCorrelationAssignmentSnapshot(nullElement));
        assertThrows(IllegalArgumentException.class,
                () -> new P2pBagCorrelationAssignmentSnapshot(
                        List.of(assignment(1), assignment(1))));
        Map<String, P2pBagCorrelationAssignment> nullValue = new LinkedHashMap<>();
        nullValue.put("correlation-1", null);
        assertThrows(IllegalArgumentException.class,
                () -> new P2pBagCorrelationAssignmentSnapshot(nullValue));
        Map<String, P2pBagCorrelationAssignment> nullKey = new LinkedHashMap<>();
        nullKey.put(null, actual);
        assertThrows(IllegalArgumentException.class,
                () -> new P2pBagCorrelationAssignmentSnapshot(nullKey));
        assertThrows(IllegalArgumentException.class, () -> fromMap.find(" "));
    }

    private static P2pBagCorrelationAssignment assignment(int index) {
        return new P2pBagCorrelationAssignment(
                "correlation-" + index,
                new P2pLineId("line-" + index));
    }
}
