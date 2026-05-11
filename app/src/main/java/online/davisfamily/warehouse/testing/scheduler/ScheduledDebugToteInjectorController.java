package online.davisfamily.warehouse.testing.scheduler;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerEvaluationResult;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerEvaluationSource;
import online.davisfamily.warehouse.sim.dsp.runtime.SynchronousSchedulerEvaluationSource;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspReleaseScheduler;
import online.davisfamily.warehouse.sim.dsp.scheduler.ReleaseOrderCommand;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;

public class ScheduledDebugToteInjectorController implements SimulationController {
    private final SchedulerEvaluationSource evaluationSource;
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
        this(
                new SynchronousSchedulerEvaluationSource(scheduler),
                runtimeState,
                releaseCatalog,
                releaseTarget,
                debugState);
    }

    public ScheduledDebugToteInjectorController(
            SchedulerEvaluationSource evaluationSource,
            DspSchedulerRuntimeState runtimeState,
            ScheduledTipperToteReleaseCatalog releaseCatalog,
            ScheduledToteReleaseTarget releaseTarget) {
        this(evaluationSource, runtimeState, releaseCatalog, releaseTarget, new SchedulerDebugState());
    }

    public ScheduledDebugToteInjectorController(
            SchedulerEvaluationSource evaluationSource,
            DspSchedulerRuntimeState runtimeState,
            ScheduledTipperToteReleaseCatalog releaseCatalog,
            ScheduledToteReleaseTarget releaseTarget,
            SchedulerDebugState debugState) {
        if (evaluationSource == null
                || runtimeState == null
                || releaseCatalog == null
                || releaseTarget == null
                || debugState == null) {
            throw new IllegalArgumentException("Scheduled debug tote injector inputs must not be null");
        }
        this.evaluationSource = evaluationSource;
        this.runtimeState = runtimeState;
        this.releaseCatalog = releaseCatalog;
        this.releaseTarget = releaseTarget;
        this.debugState = debugState;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        debugState.recordEvaluationSourceState(
                evaluationSource.modeLabel(),
                evaluationSource.evaluationInFlight());
        if (!releaseTarget.canAcceptRelease()) {
            return;
        }

        if (evaluationSource.canSubmit()) {
            evaluationSource.submit(runtimeState.snapshot());
        }

        SchedulerEvaluationResult evaluationResult = evaluationSource.pollResult().orElse(null);
        if (evaluationResult == null) {
            return;
        }

        debugState.recordEvaluation(
                evaluationResult.snapshot(),
                evaluationResult.evaluation(),
                evaluationResult.sequence());
        if (evaluationResult.evaluation().releaseDecision().isEmpty()) {
            return;
        }

        ReleaseOrderCommand command = evaluationResult.evaluation().releaseDecision().get().command();
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
