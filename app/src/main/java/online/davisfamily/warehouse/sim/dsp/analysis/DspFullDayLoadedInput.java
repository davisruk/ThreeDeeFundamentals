package online.davisfamily.warehouse.sim.dsp.analysis;

import java.util.List;

import online.davisfamily.warehouse.sim.dsp.analysis.input.DspInputRejectionCatalog;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.schedule.DspServiceCentreTimetable;

/** Immutable, pre-runtime full-day input and its deterministic bag plan. */
public record DspFullDayLoadedInput(
        LoadedDspData loadedData,
        List<NotionalToteOrder> reportableOrders,
        DspInputRejectionCatalog rejectionCatalog,
        BagPlanningResult bagPlanningResult,
        DspDatasetLoadReport loadReport,
        DspServiceCentreTimetable timetable) {

    public DspFullDayLoadedInput(
            LoadedDspData loadedData,
            BagPlanningResult bagPlanningResult,
            DspDatasetLoadReport loadReport,
            DspServiceCentreTimetable timetable) {
        this(
                loadedData,
                loadedData == null ? List.of() : loadedData.orders(),
                DspInputRejectionCatalog.empty(),
                bagPlanningResult,
                loadReport,
                timetable);
    }

    public DspFullDayLoadedInput {
        if (loadedData == null) {
            throw new IllegalArgumentException("loadedData must not be null");
        }
        if (reportableOrders == null
                || reportableOrders.stream().anyMatch(order -> order == null)) {
            throw new IllegalArgumentException(
                    "reportableOrders must not be null or contain null");
        }
        if (rejectionCatalog == null) {
            throw new IllegalArgumentException("rejectionCatalog must not be null");
        }
        if (bagPlanningResult == null) {
            throw new IllegalArgumentException("bagPlanningResult must not be null");
        }
        if (loadReport == null) {
            throw new IllegalArgumentException("loadReport must not be null");
        }
        if (!loadedData.report().equals(loadReport)) {
            throw new IllegalArgumentException("loadReport must match loadedData.report()");
        }
        if (timetable == null) {
            throw new IllegalArgumentException("timetable must not be null");
        }
        reportableOrders = List.copyOf(reportableOrders);
    }

    public LoadedDspData data() {
        return loadedData;
    }

    public LoadedDspData executableData() {
        return loadedData;
    }

    public BagPlanningResult bagPlan() {
        return bagPlanningResult;
    }

    public DspDatasetLoadReport report() {
        return loadReport;
    }
}
