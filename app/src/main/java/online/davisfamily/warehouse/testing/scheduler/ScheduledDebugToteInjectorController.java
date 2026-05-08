package online.davisfamily.warehouse.testing.scheduler;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspReleaseScheduler;
import online.davisfamily.warehouse.sim.dsp.scheduler.ReleaseOrderCommand;
import online.davisfamily.warehouse.sim.dsp.scheduler.SchedulerEvaluation;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;

public class ScheduledDebugToteInjectorController implements SimulationController {
    private final DspReleaseScheduler scheduler;
    private final DspSchedulerRuntimeState runtimeState;
    private final ScheduledTipperToteReleaseCatalog releaseCatalog;
    private final ScheduledToteReleaseTarget releaseTarget;
    private final SchedulerDebugState debugState;

    public ScheduledDebugToteInjectorController(
            DspReleaseScheduler scheduler,
            DspSchedulerRuntimeState runtimeState,
            ScheduledTipperToteReleaseCatalog releaseCatalog,
            ScheduledToteReleaseTarget releaseTarget) {
        this(scheduler, runtimeState, releaseCatalog, releaseTarget, new SchedulerDebugState());
    }

    public ScheduledDebugToteInjectorController(
            DspReleaseScheduler scheduler,
            DspSchedulerRuntimeState runtimeState,
            ScheduledTipperToteReleaseCatalog releaseCatalog,
            ScheduledToteReleaseTarget releaseTarget,
            SchedulerDebugState debugState) {
        if (scheduler == null
                || runtimeState == null
                || releaseCatalog == null
                || releaseTarget == null
                || debugState == null) {
            throw new IllegalArgumentException("Scheduled debug tote injector inputs must not be null");
        }
        this.scheduler = scheduler;
        this.runtimeState = runtimeState;
        this.releaseCatalog = releaseCatalog;
        this.releaseTarget = releaseTarget;
        this.debugState = debugState;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        if (!releaseTarget.canAcceptRelease()) {
            return;
        }

        WarehouseSchedulerSnapshot snapshot = runtimeState.snapshot();
        SchedulerEvaluation evaluation = scheduler.evaluate(snapshot);
        debugState.recordEvaluation(snapshot, evaluation);
        if (evaluation.releaseDecision().isEmpty()) {
            return;
        }

        ReleaseOrderCommand command = evaluation.releaseDecision().get().command();
        ScheduledTipperToteRelease scheduledRelease = releaseCatalog.findByOrderId(command.orderId())
                .orElseThrow(() -> new IllegalStateException("No scheduled tipper release for orderId " + command.orderId()));

        TipperTotePayload payload = scheduledRelease.createPayload();
        SchedulerCommandApplicationResult result = releaseTarget.release(payload);
        if (result.applied()) {
            runtimeState.markReleased(command.orderId());
            debugState.recordApplied(command.orderId());
            return;
        }
        if (result.deferred()) {
            debugState.recordDeferred(command.orderId(), result.reason());
            return;
        }
        debugState.recordRejected(command.orderId(), result.reason());
        throw new IllegalStateException("Scheduled tote release rejected: " + result.reason());
    }

    public SchedulerDebugSnapshot debugSnapshot() {
        return debugState.snapshot();
    }
}
