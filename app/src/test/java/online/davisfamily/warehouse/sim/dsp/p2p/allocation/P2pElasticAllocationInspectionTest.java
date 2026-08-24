package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreDeadlineSnapshot;

class P2pElasticAllocationInspectionTest {

    @Test
    void shouldDescribeProfileDeadlineWorkDemandOwnershipAndInfeasibility() {
        LocalDateTime evaluatedAt = LocalDateTime.of(2026, 8, 24, 6, 0);
        P2pLineId feeding = new P2pLineId("line-1");
        P2pLineId draining = new P2pLineId("line-2");
        P2pElasticAllocationIssue issue = new P2pElasticAllocationIssue(
                "104",
                P2pElasticAllocationIssueType.INSUFFICIENT_SHARED_LINE_CAPACITY,
                "one required line remains unmet");
        P2pServiceCentreLineDemandSnapshot demand =
                new P2pServiceCentreLineDemandSnapshot(
                        "104",
                        999,
                        Duration.ofMinutes(4),
                        new ServiceCentreDeadlineSnapshot(
                                "104",
                                "Letchworth",
                                999,
                                evaluatedAt,
                                evaluatedAt.plusHours(11),
                                evaluatedAt.plusHours(10),
                                evaluatedAt.plusHours(10),
                                evaluatedAt.plusHours(10),
                                Duration.ofHours(10),
                                false,
                                false),
                        new P2pServiceCentreWorkloadSnapshot(
                                "104",
                                List.of(new PhysicalToteId("tote-1")),
                                3,
                                List.of(new BagKey("rx-1", 1), new BagKey("rx-2", 1)),
                                List.of(new OrderSheetKey("empty-1", 1)),
                                Duration.ofHours(4)),
                        Duration.ofHours(5),
                        3,
                        2,
                        1,
                        List.of(feeding),
                        List.of(draining),
                        0,
                        1,
                        true,
                        List.of(P2pElasticAllocationIssueType
                                .INSUFFICIENT_SHARED_LINE_CAPACITY));
        P2pElasticAllocationSnapshot snapshot = new P2pElasticAllocationSnapshot(
                P2pElasticAllocationSnapshot.DEADLINE_AWARE_ELASTIC_STICKY_LEASES,
                P2pElasticAllocationCalibrationStatus.UNCALIBRATED,
                evaluatedAt,
                List.of(feeding, draining),
                2,
                List.of(demand),
                List.of(issue));

        List<String> inspection = new P2pElasticAllocationInspection().describe(snapshot);

        assertEquals("Elastic profile: DEADLINE_AWARE_ELASTIC_STICKY_LEASES",
                inspection.getFirst());
        assertTrue(inspection.contains("Elastic calibration: UNCALIBRATED"));
        assertTrue(inspection.stream().anyMatch(line -> line.startsWith("Elastic time: ")));
        assertTrue(inspection.stream().anyMatch(line ->
                line.startsWith("SC 104 deadline:")
                        && line.contains("slack=PT10H")));
        assertTrue(inspection.stream().anyMatch(line ->
                line.startsWith("SC 104 work:")
                        && line.contains("totes=1")
                        && line.contains("packs=3")
                        && line.contains("bags=2")
                        && line.contains("empty=1")));
        assertTrue(inspection.stream().anyMatch(line ->
                line.equals("SC 104 lines: raw=3, required=2, desired=1, owned=2, "
                        + "additional=0, unmet=1")));
        assertTrue(inspection.contains("SC 104 feeding: [line-1]"));
        assertTrue(inspection.contains("SC 104 draining: [line-2]"));
        assertTrue(inspection.contains("Elastic infeasible: true"));
        assertTrue(inspection.stream().anyMatch(line ->
                line.contains("104/INSUFFICIENT_SHARED_LINE_CAPACITY")));
        assertThrows(UnsupportedOperationException.class, () -> inspection.clear());
        assertThrows(IllegalArgumentException.class,
                () -> new P2pElasticAllocationInspection().describe(null));
    }
}
