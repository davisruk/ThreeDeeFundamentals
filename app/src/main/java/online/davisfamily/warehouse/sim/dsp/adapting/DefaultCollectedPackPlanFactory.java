package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

public class DefaultCollectedPackPlanFactory implements CollectedPackPlanFactory {
    public static final PackDimensions DEFAULT_DIMENSIONS = new PackDimensions(0.20f, 0.10f, 0.08f);

    private final PackDimensions packDimensions;

    public DefaultCollectedPackPlanFactory() {
        this(DEFAULT_DIMENSIONS);
    }

    public DefaultCollectedPackPlanFactory(PackDimensions packDimensions) {
        if (packDimensions == null) {
            throw new IllegalArgumentException("packDimensions must not be null");
        }
        this.packDimensions = packDimensions;
    }

    @Override
    public List<PackPlan> createPackPlans(List<AdaptedLineRecord> collectedLines) {
        if (collectedLines == null) {
            throw new IllegalArgumentException("collectedLines must not be null");
        }

        List<PackPlan> packPlans = new ArrayList<>();
        for (AdaptedLineRecord collectedLine : collectedLines) {
            if (collectedLine == null) {
                throw new IllegalArgumentException("collectedLines must not contain null");
            }
            for (int packNumber = 1; packNumber <= collectedLine.line().quantity(); packNumber++) {
                String lineId = collectedLine.line().itemId();
                packPlans.add(new PackPlan(
                        "pack-" + lineId + "-" + packNumber,
                        lineId,
                        packDimensions));
            }
        }
        return List.copyOf(packPlans);
    }
}
