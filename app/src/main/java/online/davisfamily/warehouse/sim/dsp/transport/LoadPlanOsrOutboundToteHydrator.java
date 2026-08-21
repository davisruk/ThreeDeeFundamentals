package online.davisfamily.warehouse.sim.dsp.transport;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchRequest;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlanProvider;

public final class LoadPlanOsrOutboundToteHydrator implements OsrOutboundToteHydrator {
    private final ToteLoadPlanProvider loadPlanProvider;
    private final DetachedOutboundToteFactory detachedToteFactory;

    public LoadPlanOsrOutboundToteHydrator(
            ToteLoadPlanProvider loadPlanProvider,
            DetachedOutboundToteFactory detachedToteFactory) {
        if (loadPlanProvider == null) {
            throw new IllegalArgumentException("loadPlanProvider must not be null");
        }
        if (detachedToteFactory == null) {
            throw new IllegalArgumentException("detachedToteFactory must not be null");
        }
        this.loadPlanProvider = loadPlanProvider;
        this.detachedToteFactory = detachedToteFactory;
    }

    @Override
    public RoutedPhysicalTote hydrate(OsrOutboundRouteLaunchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }

        PhysicalToteId physicalToteId = request.physicalToteId();
        ToteLoadPlan loadPlan = loadPlanProvider.getLoadPlanFor(physicalToteId.value());
        if (loadPlan == null) {
            throw hydrationFailure(physicalToteId, "no tote load plan is available");
        }
        if (!physicalToteId.equals(loadPlan.physicalToteId())) {
            throw hydrationFailure(
                    physicalToteId,
                    "load plan physical ID does not match the launch request");
        }

        RoutedPhysicalTote routedTote;
        try {
            routedTote = detachedToteFactory.create(request, loadPlan);
        } catch (IllegalArgumentException exception) {
            throw new OsrOutboundToteHydrationException(
                    physicalToteId,
                    "detached tote factory produced an invalid payload",
                    exception);
        }
        if (routedTote == null) {
            throw hydrationFailure(physicalToteId, "detached tote factory returned null");
        }
        if (routedTote.launchRequest() != request) {
            throw hydrationFailure(
                    physicalToteId,
                    "detached tote does not retain the exact launch request");
        }
        if (routedTote.loadPlan() != loadPlan) {
            throw hydrationFailure(
                    physicalToteId,
                    "detached tote does not retain the exact load plan");
        }
        if (!physicalToteId.equals(routedTote.physicalToteId())) {
            throw hydrationFailure(
                    physicalToteId,
                    "detached tote physical ID does not match the launch request");
        }
        if (!request.destination().equals(routedTote.destination())) {
            throw hydrationFailure(
                    physicalToteId,
                    "detached tote destination does not match the launch request");
        }
        if (routedTote.tote().areLidsOpen()) {
            throw hydrationFailure(physicalToteId, "detached tote lids must be closed");
        }
        return routedTote;
    }

    private static OsrOutboundToteHydrationException hydrationFailure(
            PhysicalToteId physicalToteId,
            String detail) {
        return new OsrOutboundToteHydrationException(physicalToteId, detail);
    }
}
