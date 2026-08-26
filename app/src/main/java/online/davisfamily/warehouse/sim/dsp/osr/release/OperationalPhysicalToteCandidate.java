package online.davisfamily.warehouse.sim.dsp.osr.release;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;

/**
 * Source-neutral physical identity exposed to operational release evaluation.
 * Source-specific release availability remains optional metadata on the candidate.
 */
public interface OperationalPhysicalToteCandidate {
    PhysicalToteId physicalToteId();

    OrderSheetKey orderSheetKey();

    OrderType orderType();

    String serviceCentreId();

    long sourceSequenceNumber();

    OperationalPhysicalToteSource source();

    default Optional<PhysicalToteId> blockingPhysicalToteId() {
        return Optional.empty();
    }
}
