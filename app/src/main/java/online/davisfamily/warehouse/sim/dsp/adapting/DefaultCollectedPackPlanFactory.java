package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.warehouse.sim.dsp.bagging.DspPackPlanFactory;
import online.davisfamily.warehouse.sim.dsp.bagging.PackSourceProvenance;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

public class DefaultCollectedPackPlanFactory implements CollectedPackPlanFactory {
    public static final PackDimensions DEFAULT_DIMENSIONS = new PackDimensions(0.20f, 0.10f, 0.08f);

    private final PackDimensions packDimensions;
    private final DspPackPlanFactory packPlanFactory;

    public DefaultCollectedPackPlanFactory(DspPackPlanFactory packPlanFactory) {
        this(DEFAULT_DIMENSIONS, packPlanFactory);
    }

    public DefaultCollectedPackPlanFactory(
            PackDimensions packDimensions,
            DspPackPlanFactory packPlanFactory) {
        if (packDimensions == null) {
            throw new IllegalArgumentException("packDimensions must not be null");
        }
        if (packPlanFactory == null) {
            throw new IllegalArgumentException("packPlanFactory must not be null");
        }
        this.packDimensions = packDimensions;
        this.packPlanFactory = packPlanFactory;
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
                String lineReference = collectedLine.line().lineReference();
                packPlans.add(packPlanFactory.createPackPlan(
                        "pack-" + lineReference + "-" + packNumber,
                        lineReference,
                        packDimensions,
                        new PackSourceProvenance(
                                collectedLine.sourceOrderSheetKey(),
                                lineReference,
                                collectedLine.line().productId(),
                                collectedLine.sourceServiceCentreId(),
                                collectedLine.line().pharmacyId(),
                                collectedLine.line().patientId(),
                                collectedLine.line().prescriptionId())));
            }
        }
        return List.copyOf(packPlans);
    }
}
