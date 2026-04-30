package online.davisfamily.warehouse.sim.totebag.machine;

import online.davisfamily.warehouse.sim.totebag.plan.*;
import online.davisfamily.warehouse.sim.totebag.pack.*;
import online.davisfamily.warehouse.sim.totebag.machine.*;
import online.davisfamily.warehouse.sim.totebag.conveyor.*;
import online.davisfamily.warehouse.sim.totebag.transfer.*;
import online.davisfamily.warehouse.sim.totebag.device.*;
import online.davisfamily.warehouse.sim.totebag.assignment.*;
import online.davisfamily.warehouse.sim.totebag.control.*;

public record CompletedBag(
        String correlationId,
        int packCount,
        BagSpec bagSpec) {
    public CompletedBag {
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        if (packCount <= 0) {
            throw new IllegalArgumentException("packCount must be > 0");
        }
        if (bagSpec == null) {
            throw new IllegalArgumentException("bagSpec must not be null");
        }
    }
}
