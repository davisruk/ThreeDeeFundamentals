package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.DspPackPlanFactory;
import online.davisfamily.warehouse.sim.dsp.bagging.PackSourceProvenance;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackSlot;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

public class DefaultCollectedPackPlanFactory implements CollectedPackPlanFactory {
    public static final PackDimensions DEFAULT_DIMENSIONS = new PackDimensions(0.20f, 0.10f, 0.08f);

    private final PackDimensions packDimensions;
    private final DspPackPlanFactory packPlanFactory;
    private final CollectedPackCorrelationResolver correlationResolver;
    private final PlannedSlotCollectedPackCorrelationResolver plannedSlotResolver;

    /** Full-day collection reuses each immutable slot's correlation and dimensions. */
    public static DefaultCollectedPackPlanFactory forPlannedSlots(
            DspPackPlanFactory packPlanFactory,
            BagPlanningResult bagPlan) {
        var resolver = new PlannedSlotCollectedPackCorrelationResolver(bagPlan);
        return new DefaultCollectedPackPlanFactory(
                DEFAULT_DIMENSIONS, packPlanFactory, resolver, resolver);
    }

    public DefaultCollectedPackPlanFactory(DspPackPlanFactory packPlanFactory) {
        this(
                DEFAULT_DIMENSIONS,
                packPlanFactory,
                (collectedLine, ignoredPackOrdinal) -> collectedLine.line().lineReference());
    }

    public DefaultCollectedPackPlanFactory(
            PackDimensions packDimensions,
            DspPackPlanFactory packPlanFactory) {
        this(
                packDimensions,
                packPlanFactory,
                (collectedLine, ignoredPackOrdinal) -> collectedLine.line().lineReference());
    }

    public DefaultCollectedPackPlanFactory(
            DspPackPlanFactory packPlanFactory,
            CollectedPackCorrelationResolver correlationResolver) {
        this(DEFAULT_DIMENSIONS, packPlanFactory, correlationResolver);
    }

    public DefaultCollectedPackPlanFactory(
            PackDimensions packDimensions,
            DspPackPlanFactory packPlanFactory,
            CollectedPackCorrelationResolver correlationResolver) {
        this(packDimensions, packPlanFactory, correlationResolver, null);
    }

    private DefaultCollectedPackPlanFactory(
            PackDimensions packDimensions,
            DspPackPlanFactory packPlanFactory,
            CollectedPackCorrelationResolver correlationResolver,
            PlannedSlotCollectedPackCorrelationResolver plannedSlotResolver) {
        if (packDimensions == null) {
            throw new IllegalArgumentException("packDimensions must not be null");
        }
        if (packPlanFactory == null) {
            throw new IllegalArgumentException("packPlanFactory must not be null");
        }
        if (correlationResolver == null) {
            throw new IllegalArgumentException("correlationResolver must not be null");
        }
        this.packDimensions = packDimensions;
        this.packPlanFactory = packPlanFactory;
        this.correlationResolver = correlationResolver;
        this.plannedSlotResolver = plannedSlotResolver;
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
            int packOrdinal = 1;
            String lineReference = collectedLine.line().lineReference();
            PlannedPackSlot slot = plannedSlotResolver == null ? null
                    : plannedSlotResolver.requirePlannedSlot(collectedLine, packOrdinal);
            String correlationId = slot == null
                    ? correlationResolver.resolve(collectedLine, packOrdinal)
                    : slot.bagKey().correlationId();
            if (correlationId == null || correlationId.isBlank()) {
                throw new IllegalStateException("Resolved correlationId must not be blank");
            }
            packPlans.add(packPlanFactory.createPackPlan(
                    "pack-" + lineReference + "-" + packOrdinal,
                    correlationId.trim(),
                    slot == null ? packDimensions : slot.dimensions(),
                    new PackSourceProvenance(
                            collectedLine.sourceOrderSheetKey(),
                            lineReference,
                            collectedLine.line().productId(),
                            collectedLine.sourceServiceCentreId(),
                            collectedLine.line().pharmacyId(),
                            collectedLine.line().patientId(),
                            collectedLine.line().prescriptionId())));
        }
        return List.copyOf(packPlans);
    }

    /** Builds the exact prospective batch without registering provenance. */
    public PreparedCollectedPackPlans preparePackPlans(List<AdaptedLineRecord> collectedLines) {
        if (collectedLines == null) {
            throw new IllegalArgumentException("collectedLines must not be null");
        }
        List<PackPlan> plans = new ArrayList<>(collectedLines.size());
        Map<String, PackSourceProvenance> provenance = new LinkedHashMap<>();
        for (AdaptedLineRecord line : collectedLines) {
            if (line == null) {
                throw new IllegalArgumentException("collectedLines must not contain null");
            }
            int ordinal = 1;
            String lineReference = line.line().lineReference();
            PlannedPackSlot slot = plannedSlotResolver == null ? null
                    : plannedSlotResolver.requirePlannedSlot(line, ordinal);
            String correlationId = slot == null
                    ? correlationResolver.resolve(line, ordinal)
                    : slot.bagKey().correlationId();
            if (correlationId == null || correlationId.isBlank()) {
                throw new IllegalStateException("Resolved correlationId must not be blank");
            }
            String packId = "pack-" + lineReference + "-" + ordinal;
            plans.add(new PackPlan(packId, correlationId.trim(),
                    slot == null ? packDimensions : slot.dimensions()));
            PackSourceProvenance prior = provenance.putIfAbsent(packId,
                    new PackSourceProvenance(
                            line.sourceOrderSheetKey(),
                            lineReference,
                            line.line().productId(),
                            line.sourceServiceCentreId(),
                            line.line().pharmacyId(),
                            line.line().patientId(),
                            line.line().prescriptionId()));
            if (prior != null) {
                throw new IllegalArgumentException("Duplicate collected physical pack ID: " + packId);
            }
        }
        return new PreparedCollectedPackPlans(plans, provenance);
    }
}
