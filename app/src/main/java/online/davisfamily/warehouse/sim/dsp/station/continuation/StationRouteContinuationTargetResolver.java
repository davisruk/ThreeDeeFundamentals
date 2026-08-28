package online.davisfamily.warehouse.sim.dsp.station.continuation;

import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDisposition;

/** Resolves the exact live target for a pure continuation decision. */
public interface StationRouteContinuationTargetResolver {
    StationRouteContinuationDecision resolve(
            StationProcessingDisposition disposition,
            NotionalToteOrder order,
            StationType nextStation);
}
