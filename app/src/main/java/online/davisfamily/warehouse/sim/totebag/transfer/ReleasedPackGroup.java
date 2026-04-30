package online.davisfamily.warehouse.sim.totebag.transfer;

import online.davisfamily.warehouse.sim.totebag.plan.*;
import online.davisfamily.warehouse.sim.totebag.pack.*;
import online.davisfamily.warehouse.sim.totebag.machine.*;
import online.davisfamily.warehouse.sim.totebag.conveyor.*;
import online.davisfamily.warehouse.sim.totebag.transfer.*;
import online.davisfamily.warehouse.sim.totebag.device.*;
import online.davisfamily.warehouse.sim.totebag.assignment.*;
import online.davisfamily.warehouse.sim.totebag.control.*;

import java.util.List;

public record ReleasedPackGroup(
        String correlationId,
        String sourcePrlId,
        List<Pack> packs,
        float requiredLength) {
    public ReleasedPackGroup {
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        if (sourcePrlId == null || sourcePrlId.isBlank()) {
            throw new IllegalArgumentException("sourcePrlId must not be blank");
        }
        if (packs == null || packs.isEmpty()) {
            throw new IllegalArgumentException("packs must not be empty");
        }
        if (requiredLength <= 0f) {
            throw new IllegalArgumentException("requiredLength must be > 0");
        }
        packs = List.copyOf(packs);
    }

    public List<PackPlan> toPackPlans() {
        return packs.stream()
                .map(pack -> new PackPlan(pack.getId(), pack.getCorrelationId(), pack.getDimensions()))
                .toList();
    }
}
