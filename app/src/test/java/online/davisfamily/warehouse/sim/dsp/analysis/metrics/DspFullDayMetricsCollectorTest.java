package online.davisfamily.warehouse.sim.dsp.analysis.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayInputLoader;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayInputPaths;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayLoadedInput;
import online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayRuntimeState;
import online.davisfamily.warehouse.sim.dsp.analysis.DspUncalibratedFullDayProfile;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntime;
import online.davisfamily.warehouse.sim.dsp.analysis.runtime.DspFullDayAnalysisRuntimeFactory;

class DspFullDayMetricsCollectorTest {
    private static final LocalDate OPERATING_DATE = LocalDate.of(2026, 9, 2);

    @Test
    void shouldExposeZeroStateMetadataAndInitialOccupancySample(@TempDir Path directory)
            throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadInput(directory, profile);

        try (DspFullDayAnalysisRuntime runtime = new DspFullDayAnalysisRuntimeFactory()
                .create(input, profile)) {
            DspFullDayMetricsSnapshot metrics = runtime.metricsSnapshot();

            assertEquals(profile.profileId(), metrics.profileId());
            assertEquals(profile.calibrationStatus(), metrics.calibrationStatus());
            assertEquals(profile.completionMilestone(), metrics.completionMilestone());
            assertEquals(DspFullDayRuntimeState.RUNNING, metrics.state());
            assertEquals(Duration.ZERO, metrics.observedSimulationDuration());
            assertEquals(1, metrics.occupancySamples().size());
            assertEquals(Duration.ZERO,
                    metrics.occupancySamples().getFirst().elapsedSimulationTime());
            assertEquals(2, metrics.occupancySamples().getFirst().osrOccupancy());
            assertEquals(5, metrics.p2pLines().size());
            assertEquals(0L, metrics.admittedInboundToteCount());
            assertEquals(0L, metrics.allocatedBagCount());
            assertEquals(0d, metrics.actualInboundTotesPerSecond());
        }
    }

    @Test
    void shouldIntegrateFixedStepTimeAndSampleAtConfiguredInterval(@TempDir Path directory)
            throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadInput(directory, profile, 100);

        try (DspFullDayAnalysisRuntime runtime = new DspFullDayAnalysisRuntimeFactory()
                .create(input, profile)) {
            DspFullDayMetricsSnapshot before = runtime.metricsSnapshot();
            runtime.metricsCollector().recordExecutionSpeed(2d, 1.5d);
            for (int step = 0; step < 61; step++) {
                runtime.update(1d);
            }

            DspFullDayMetricsSnapshot after = runtime.metricsSnapshot();
            assertEquals(Duration.ZERO, before.observedSimulationDuration());
            assertEquals(Duration.ofSeconds(61), after.observedSimulationDuration());
            assertEquals(2d, after.requestedExecutionSpeed());
            assertEquals(1.5d, after.achievedExecutionSpeed());
            assertTrue(after.occupancySamples().stream()
                    .anyMatch(sample -> sample.elapsedSimulationTime().equals(Duration.ofSeconds(60))));
            assertTrue(after.occupancySamples().size() >= 2);
            assertTrue(after.p2pLines().stream()
                    .allMatch(line -> line.utilization() >= 0d && line.utilization() <= 1d));
            assertEquals(Duration.ZERO, before.occupancySamples().getFirst().elapsedSimulationTime());
            assertThrows(UnsupportedOperationException.class,
                    () -> after.occupancySamples().add(after.occupancySamples().getFirst()));
        }
    }

    @Test
    void shouldCaptureEarlyCompletionAndRetainImmutablePriorMetrics(@TempDir Path directory)
            throws IOException {
        DspUncalibratedFullDayProfile profile = profile();
        DspFullDayLoadedInput input = loadInput(directory, profile);

        try (DspFullDayAnalysisRuntime runtime = new DspFullDayAnalysisRuntimeFactory()
                .create(input, profile)) {
            DspFullDayMetricsSnapshot initial = runtime.metricsSnapshot();
            for (int step = 0; step < 300
                    && runtime.state() == DspFullDayRuntimeState.RUNNING; step++) {
                runtime.update(1d);
            }

            DspFullDayMetricsSnapshot terminal = runtime.metricsSnapshot();
            assertEquals(DspFullDayRuntimeState.ALL_SUPPORTED_WORK_COMPLETE, terminal.state());
            assertTrue(terminal.serviceCentres().stream()
                    .allMatch(centre -> centre.complete()));
            assertTrue(terminal.occupancySamples().size() > initial.occupancySamples().size());
            assertEquals(Duration.ZERO, initial.observedSimulationDuration());
            assertEquals(DspFullDayRuntimeState.RUNNING, initial.state());
        }
    }

    private static DspUncalibratedFullDayProfile profile() {
        return DspUncalibratedFullDayProfile.productionBaseline(
                OPERATING_DATE, 10, Duration.ofSeconds(1), 2, 4, 4);
    }

    private static DspFullDayLoadedInput loadInput(
            Path directory,
            DspUncalibratedFullDayProfile profile) throws IOException {
        return loadInput(directory, profile, 2);
    }

    private static DspFullDayLoadedInput loadInput(
            Path directory,
            DspUncalibratedFullDayProfile profile,
            int orderCount) throws IOException {
        Path productMaster = Files.writeString(directory.resolve("products.csv"), """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                product-a,Product A,,200,100,80
                """);
        List<Path> orderFiles = new ArrayList<>();
        for (int index = 0; index < orderCount; index++) {
            String orderId = "order-" + (104 + index);
            String toteId = "tote-" + (104 + index);
            String serviceCentreId = index % 2 == 0 ? "104" : "108";
            String priority = serviceCentreId.equals("104") ? "999" : "998";
            String sheetNumber = String.format("%03d", index + 1);
            orderFiles.add(Files.writeString(directory.resolve(orderId + ".json"),
                    message(orderId, toteId, serviceCentreId, priority, sheetNumber)));
        }
        return new DspFullDayInputLoader().load(
                new DspFullDayInputPaths(productMaster, orderFiles), profile);
    }

    private static String message(
            String orderId,
            String physicalToteId,
            String serviceCentreId,
            String priority,
            String sheetNumber) {
        return """
        {
          "header": {"orderId":"%s","sheetNumber":"%s"},
                  "toteIdentifier": {"payload":"05"},
                  "transportContainer": {"payload":"%s"},
                  "orderPriority": {"payload":"%s"},
                  "serviceCentre": {"payload":"%s"},
                  "orderDetail": {
                    "numberOfOrderLines": 1,
                    "orderLines": [
                      {
                        "orderLineNumber":"line-1",
                        "orderLineType":"05",
                        "pharmacyId":"pharmacy-1",
                        "patientId":"patient-1",
                        "prescriptionId":"%s-prescription",
                        "productId":"product-a",
                        "numberOfPacks":"1",
                        "referenceSheetNumber":"%s",
                        "numberOfPacksPicked":"1",
                        "referenceOrderId":"%s"
                      }
                    ]
                  }
                }
                """.formatted(
                        orderId,
                        sheetNumber,
                        physicalToteId,
                        priority,
                        serviceCentreId,
                        orderId,
                        sheetNumber,
                        orderId);
    }
}
