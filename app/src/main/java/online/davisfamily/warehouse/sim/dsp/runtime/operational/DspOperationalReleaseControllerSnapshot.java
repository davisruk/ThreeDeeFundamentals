package online.davisfamily.warehouse.sim.dsp.runtime.operational;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseEvaluation;

public record DspOperationalReleaseControllerSnapshot(
        String evaluationMode,
        boolean evaluationInFlight,
        Optional<Long> lastCompletedEvaluationSequence,
        Optional<DspOperationalReleaseEvaluation> lastEvaluation,
        Optional<SchedulerCommandApplicationResult> lastCommandApplicationResult,
        Optional<PhysicalToteId> lastPhysicalToteId) {

    public DspOperationalReleaseControllerSnapshot {
        if (evaluationMode == null || evaluationMode.isBlank()) {
            throw new IllegalArgumentException("evaluationMode must not be blank");
        }
        if (lastCompletedEvaluationSequence == null) {
            throw new IllegalArgumentException(
                    "lastCompletedEvaluationSequence must not be null");
        }
        if (lastEvaluation == null) {
            throw new IllegalArgumentException("lastEvaluation must not be null");
        }
        if (lastCommandApplicationResult == null) {
            throw new IllegalArgumentException(
                    "lastCommandApplicationResult must not be null");
        }
        if (lastPhysicalToteId == null) {
            throw new IllegalArgumentException("lastPhysicalToteId must not be null");
        }
        if (lastCompletedEvaluationSequence.filter(sequence -> sequence < 0).isPresent()) {
            throw new IllegalArgumentException(
                    "lastCompletedEvaluationSequence must be >= 0 when present");
        }
        if (lastCommandApplicationResult.isPresent() != lastPhysicalToteId.isPresent()) {
            throw new IllegalArgumentException(
                    "command application result and physical tote ID must be present together");
        }
        evaluationMode = evaluationMode.trim();
    }

    public static DspOperationalReleaseControllerSnapshot initial(
            String evaluationMode,
            boolean evaluationInFlight) {
        return new DspOperationalReleaseControllerSnapshot(
                evaluationMode,
                evaluationInFlight,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }
}
