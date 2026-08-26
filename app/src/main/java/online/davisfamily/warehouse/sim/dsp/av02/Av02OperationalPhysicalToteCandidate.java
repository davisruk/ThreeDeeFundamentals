package online.davisfamily.warehouse.sim.dsp.av02;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteCandidate;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;

public record Av02OperationalPhysicalToteCandidate(
        OperationalPhysicalToteIdentity identity) implements OperationalPhysicalToteCandidate {

    public Av02OperationalPhysicalToteCandidate {
        if (identity == null) {
            throw new IllegalArgumentException("identity must not be null");
        }
        if (identity.source() != OperationalPhysicalToteSource.AV02
                || identity.orderType() != OrderType.EMPTY) {
            throw new IllegalArgumentException(
                    "AV02 operational candidate must represent an AV02 EMPTY identity");
        }
    }

    @Override
    public PhysicalToteId physicalToteId() {
        return identity.physicalToteId();
    }

    @Override
    public OrderSheetKey orderSheetKey() {
        return identity.orderSheetKey();
    }

    @Override
    public OrderType orderType() {
        return identity.orderType();
    }

    @Override
    public String serviceCentreId() {
        return identity.serviceCentreId();
    }

    @Override
    public long sourceSequenceNumber() {
        return identity.sourceSequenceNumber();
    }

    @Override
    public OperationalPhysicalToteSource source() {
        return identity.source();
    }
}
