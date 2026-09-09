package online.davisfamily.warehouse.sim.dsp.analysis.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
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
import online.davisfamily.warehouse.sim.dsp.time.DspOperatingPhase;

class DspFullDayMetricsScenarioTest {
    private static final LocalDate OPERATING_DATE = LocalDate.of(2026, 9, 2);

    @Test
    void shouldCaptureHardCutoffAndStopMetricsAfterTerminalState(@TempDir Path directory)
            throws IOException {
        DspUncalibratedFullDayProfile profile = DspUncalibratedFullDayProfile.productionBaseline(
                OPERATING_DATE, 10, Duration.ofSeconds(1), 2, 4, 4);
        DspFullDayLoadedInput input = loadInput(directory, profile);

        try (DspFullDayAnalysisRuntime runtime = new DspFullDayAnalysisRuntimeFactory()
                .create(input, profile)) {
            runtime.update(24 * 60 * 60d);
            DspFullDayMetricsSnapshot atCutoff = runtime.metricsSnapshot();
            runtime.update(1d);
            DspFullDayMetricsSnapshot afterIgnoredUpdate = runtime.metricsSnapshot();

            assertEquals(DspFullDayRuntimeState.HARD_CUTOFF_REACHED, atCutoff.state());
            assertEquals(atCutoff, afterIgnoredUpdate);
            assertEquals(DspOperatingPhase.HARD_CUTOFF_REACHED, atCutoff.clock().phase());
            assertTrue(atCutoff.occupancySamples().stream()
                    .anyMatch(sample -> sample.phase()
                            == DspOperatingPhase.HARD_CUTOFF_REACHED));
            assertTrue(atCutoff.p2pLines().stream().allMatch(line -> line.closedOutboundToteCount() >= 0));
        }
    }

    private static DspFullDayLoadedInput loadInput(
            Path directory,
            DspUncalibratedFullDayProfile profile) throws IOException {
        Path productMaster = Files.writeString(directory.resolve("products.csv"), """
                dispensingProductPackColumbusCode,name,thirdPartyLocation,length,width,height
                product-a,Product A,,200,100,80
                """);
        Path firstOrder = Files.writeString(directory.resolve("order-104.json"),
                message("order-104", "tote-104", "104", "999"));
        Path secondOrder = Files.writeString(directory.resolve("order-108.json"),
                message("order-108", "tote-108", "108", "998"));
        return new DspFullDayInputLoader().load(
                new DspFullDayInputPaths(productMaster, List.of(firstOrder, secondOrder)), profile);
    }

    private static String message(
            String orderId,
            String physicalToteId,
            String serviceCentreId,
            String priority) {
        return """
                {
                  "header": {"orderId":"%s","sheetNumber":"001"},
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
                        "referenceSheetNumber":"001",
                        "numberOfPacksPicked":"1",
                        "referenceOrderId":"%s"
                      }
                    ]
                  }
                }
                """.formatted(orderId, physicalToteId, priority, serviceCentreId, orderId, orderId);
    }
}
