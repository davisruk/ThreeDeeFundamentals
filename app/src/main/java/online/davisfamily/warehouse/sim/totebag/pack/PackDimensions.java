package online.davisfamily.warehouse.sim.totebag.pack;

import online.davisfamily.warehouse.sim.totebag.plan.*;
import online.davisfamily.warehouse.sim.totebag.pack.*;
import online.davisfamily.warehouse.sim.totebag.machine.*;
import online.davisfamily.warehouse.sim.totebag.conveyor.*;
import online.davisfamily.warehouse.sim.totebag.transfer.*;
import online.davisfamily.warehouse.sim.totebag.device.*;
import online.davisfamily.warehouse.sim.totebag.assignment.*;
import online.davisfamily.warehouse.sim.totebag.control.*;

public record PackDimensions(float length, float width, float height) {
    public PackDimensions {
        if (length <= 0f) {
            throw new IllegalArgumentException("length must be > 0");
        }
        if (width <= 0f) {
            throw new IllegalArgumentException("width must be > 0");
        }
        if (height <= 0f) {
            throw new IllegalArgumentException("height must be > 0");
        }
    }
}
