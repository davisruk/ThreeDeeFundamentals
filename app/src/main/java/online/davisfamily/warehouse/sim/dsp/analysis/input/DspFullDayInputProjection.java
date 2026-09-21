package online.davisfamily.warehouse.sim.dsp.analysis.input;

import java.util.List;

import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;

/** Immutable reportable and executable full-day input projections. */
public record DspFullDayInputProjection(
        LoadedDspData executableData,
        List<NotionalToteOrder> reportableOrders,
        DspInputRejectionCatalog rejectionCatalog) {

    public DspFullDayInputProjection {
        if (executableData == null) {
            throw new IllegalArgumentException("executableData must not be null");
        }
        if (reportableOrders == null
                || reportableOrders.stream().anyMatch(order -> order == null)) {
            throw new IllegalArgumentException(
                    "reportableOrders must not be null or contain null");
        }
        if (rejectionCatalog == null) {
            throw new IllegalArgumentException("rejectionCatalog must not be null");
        }
        reportableOrders = List.copyOf(reportableOrders);
    }
}
