package online.davisfamily.warehouse.sim.dsp.av02;

import java.util.List;

import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;

public record Av02AllocatedTote(
        OperationalPhysicalToteIdentity identity,
        PhysicalToteRecord physicalTote,
        String pharmacyId) {

    public Av02AllocatedTote {
        if (identity == null) {
            throw new IllegalArgumentException("identity must not be null");
        }
        if (physicalTote == null) {
            throw new IllegalArgumentException("physicalTote must not be null");
        }
        if (pharmacyId == null || pharmacyId.isBlank()) {
            throw new IllegalArgumentException("pharmacyId must not be blank");
        }
        if (identity.source() != OperationalPhysicalToteSource.AV02) {
            throw new IllegalArgumentException("identity source must be AV02");
        }
        if (identity.orderType() != OrderType.EMPTY) {
            throw new IllegalArgumentException("AV02 allocated tote must represent EMPTY work");
        }
        if (identity.physicalToteRole() != PhysicalToteRole.PRE_P2P
                || physicalTote.role() != PhysicalToteRole.PRE_P2P) {
            throw new IllegalArgumentException("AV02 allocated tote must use PRE_P2P role");
        }
        if (!identity.physicalToteId().equals(physicalTote.id())) {
            throw new IllegalArgumentException("identity and physical tote IDs must match");
        }
        pharmacyId = pharmacyId.trim();
    }

    public PhysicalToteId physicalToteId() {
        return identity.physicalToteId();
    }

    public OrderSheetKey orderSheetKey() {
        return identity.orderSheetKey();
    }

    public String serviceCentreId() {
        return identity.serviceCentreId();
    }

    public long sourceSequenceNumber() {
        return identity.sourceSequenceNumber();
    }

    public List<String> pharmacyIds() {
        return List.of(pharmacyId);
    }
}
