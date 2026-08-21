package online.davisfamily.warehouse.sim.dsp.runtime.operational;

import java.util.Optional;
import java.util.function.Supplier;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandHandler;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseDecision;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseEvaluation;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseSnapshot;

public final class DspOperationalReleaseController
        implements SimulationController, AutoCloseable {
    private final OperationalReleaseEvaluationSource evaluationSource;
    private final Supplier<DspOperationalReleaseSnapshot> snapshotSupplier;
    private final SchedulerCommandHandler commandHandler;
    private DspOperationalReleaseControllerSnapshot controllerSnapshot;

    public DspOperationalReleaseController(
            OperationalReleaseEvaluationSource evaluationSource,
            Supplier<DspOperationalReleaseSnapshot> snapshotSupplier,
            SchedulerCommandHandler commandHandler) {
        if (evaluationSource == null) {
            throw new IllegalArgumentException("evaluationSource must not be null");
        }
        if (snapshotSupplier == null) {
            throw new IllegalArgumentException("snapshotSupplier must not be null");
        }
        if (commandHandler == null) {
            throw new IllegalArgumentException("commandHandler must not be null");
        }
        this.evaluationSource = evaluationSource;
        this.snapshotSupplier = snapshotSupplier;
        this.commandHandler = commandHandler;
        this.controllerSnapshot = DspOperationalReleaseControllerSnapshot.initial(
                evaluationSource.modeLabel(), evaluationSource.evaluationInFlight());
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        recordEvaluationSourceState();

        if (evaluationSource.canSubmit()) {
            DspOperationalReleaseSnapshot freshSnapshot = snapshotSupplier.get();
            if (freshSnapshot == null) {
                throw new IllegalStateException("snapshotSupplier returned null");
            }
            evaluationSource.submit(freshSnapshot);
        }

        OperationalReleaseEvaluationResult completedResult =
                evaluationSource.pollResult().orElse(null);
        if (completedResult == null) {
            return;
        }
        recordCompletedEvaluation(completedResult);

        Optional<DspOperationalReleaseDecision> releaseDecision =
                completedResult.evaluation().releaseDecision();
        if (releaseDecision.isEmpty()) {
            return;
        }
        DspOperationalReleaseDecision decision = releaseDecision.orElseThrow();
        SchedulerCommandApplicationResult applicationResult = commandHandler.apply(
                decision.command());
        if (applicationResult == null) {
            throw new IllegalStateException("commandHandler returned null");
        }
        recordCommandApplication(
                decision.candidate().physicalCandidate().physicalToteId(),
                applicationResult);
    }

    public DspOperationalReleaseControllerSnapshot snapshot() {
        return controllerSnapshot;
    }

    @Override
    public void close() {
        evaluationSource.close();
    }

    private void recordEvaluationSourceState() {
        controllerSnapshot = new DspOperationalReleaseControllerSnapshot(
                evaluationSource.modeLabel(),
                evaluationSource.evaluationInFlight(),
                controllerSnapshot.lastCompletedEvaluationSequence(),
                controllerSnapshot.lastEvaluation(),
                controllerSnapshot.lastCommandApplicationResult(),
                controllerSnapshot.lastPhysicalToteId());
    }

    private void recordCompletedEvaluation(OperationalReleaseEvaluationResult result) {
        controllerSnapshot = new DspOperationalReleaseControllerSnapshot(
                controllerSnapshot.evaluationMode(),
                controllerSnapshot.evaluationInFlight(),
                Optional.of(result.sequence()),
                Optional.of(result.evaluation()),
                controllerSnapshot.lastCommandApplicationResult(),
                controllerSnapshot.lastPhysicalToteId());
    }

    private void recordCommandApplication(
            PhysicalToteId physicalToteId,
            SchedulerCommandApplicationResult applicationResult) {
        controllerSnapshot = new DspOperationalReleaseControllerSnapshot(
                controllerSnapshot.evaluationMode(),
                controllerSnapshot.evaluationInFlight(),
                controllerSnapshot.lastCompletedEvaluationSequence(),
                controllerSnapshot.lastEvaluation(),
                Optional.of(applicationResult),
                Optional.of(physicalToteId));
    }
}
