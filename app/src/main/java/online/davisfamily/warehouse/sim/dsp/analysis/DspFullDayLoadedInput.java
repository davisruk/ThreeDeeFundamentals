package online.davisfamily.warehouse.sim.dsp.analysis;

import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.schedule.DspServiceCentreTimetable;

/** Immutable, pre-runtime full-day input and its deterministic bag plan. */
public record DspFullDayLoadedInput(
        LoadedDspData loadedData,
        BagPlanningResult bagPlanningResult,
        DspDatasetLoadReport loadReport,
        DspServiceCentreTimetable timetable) {

    public DspFullDayLoadedInput {
        if (loadedData == null) {
            throw new IllegalArgumentException("loadedData must not be null");
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
    }

    public LoadedDspData data() {
        return loadedData;
    }

    public BagPlanningResult bagPlan() {
        return bagPlanningResult;
    }

    public DspDatasetLoadReport report() {
        return loadReport;
    }
}
