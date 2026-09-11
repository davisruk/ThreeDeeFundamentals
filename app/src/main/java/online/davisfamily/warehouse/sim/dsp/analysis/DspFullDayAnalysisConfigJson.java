package online.davisfamily.warehouse.sim.dsp.analysis;

import java.math.BigDecimal;
import java.util.List;

/** Raw, nullable JSON binding values for the full-day command configuration file. */
record DspFullDayAnalysisConfigJson(
        String productMaster,
        List<String> orders,
        String ordersDirectory,
        String output,
        String inspectionOutput,
        String operatingDate,
        Integer osrLowWaterMark,
        BigDecimal inboundIntervalSeconds,
        Integer av02Capacity,
        Integer outboundBagCapacity,
        Integer maximumPacksPerBag,
        Integer fixedStepMillis,
        Integer stepsPerBatch,
        Integer metricSampleSeconds,
        Boolean overwrite) {

    DspFullDayAnalysisConfigJson {
        if (orders != null) {
            orders = List.copyOf(orders);
        }
    }
}
