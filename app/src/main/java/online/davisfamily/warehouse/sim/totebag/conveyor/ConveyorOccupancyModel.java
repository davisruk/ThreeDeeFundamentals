package online.davisfamily.warehouse.sim.totebag.conveyor;

import online.davisfamily.warehouse.sim.totebag.plan.*;
import online.davisfamily.warehouse.sim.totebag.pack.*;
import online.davisfamily.warehouse.sim.totebag.machine.*;
import online.davisfamily.warehouse.sim.totebag.conveyor.*;
import online.davisfamily.warehouse.sim.totebag.transfer.*;
import online.davisfamily.warehouse.sim.totebag.device.*;
import online.davisfamily.warehouse.sim.totebag.assignment.*;
import online.davisfamily.warehouse.sim.totebag.control.*;

import java.util.List;

public class ConveyorOccupancyModel {
    private final float usableLength;
    private final float minimumGap;
    private final float safetyMargin;

    public ConveyorOccupancyModel(float usableLength, float minimumGap, float safetyMargin) {
        if (usableLength <= 0f) {
            throw new IllegalArgumentException("usableLength must be > 0");
        }
        if (minimumGap < 0f) {
            throw new IllegalArgumentException("minimumGap must be >= 0");
        }
        if (safetyMargin < 0f) {
            throw new IllegalArgumentException("safetyMargin must be >= 0");
        }
        this.usableLength = usableLength;
        this.minimumGap = minimumGap;
        this.safetyMargin = safetyMargin;
    }

    public float getUsableLength() {
        return usableLength;
    }

    public float getMinimumGap() {
        return minimumGap;
    }

    public float getSafetyMargin() {
        return safetyMargin;
    }

    public float requiredLengthFor(List<PackPlan> packPlans) {
        if (packPlans == null || packPlans.isEmpty()) {
            return 0f;
        }

        float required = 0f;
        for (PackPlan packPlan : packPlans) {
            required += packPlan.dimensions().length();
        }

        required += minimumGap * Math.max(0, packPlans.size() - 1);
        return required;
    }

    public float requiredLengthForPacks(List<Pack> packs) {
        if (packs == null || packs.isEmpty()) {
            return 0f;
        }

        float required = 0f;
        for (Pack pack : packs) {
            required += pack.getDimensions().length();
        }

        required += minimumGap * Math.max(0, packs.size() - 1);
        return required;
    }

    public float remainingLength(float occupiedLength) {
        return Math.max(0f, usableLength - occupiedLength);
    }

    public PcrReleaseDecision evaluateRelease(List<PackPlan> packPlans, float occupiedLength) {
        float requiredLength = requiredLengthFor(packPlans) + safetyMargin;
        float availableLength = remainingLength(occupiedLength);
        boolean allowed = requiredLength <= availableLength;
        String reason = allowed
                ? "Fits available downstream conveyor length"
                : "Insufficient downstream conveyor length";
        return new PcrReleaseDecision(allowed, requiredLength, availableLength, reason);
    }
}
