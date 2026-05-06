package online.davisfamily.warehouse.sim.dsp.scheduler;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.warehouse.sim.dsp.model.DependencyType;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;

public class DspDependencyEvaluator {

    public List<DependencyBlock> findBlocks(DspSchedulerOrderState candidate, WarehouseSchedulerSnapshot snapshot) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }

        List<DependencyBlock> blocks = new ArrayList<>();
        addAdaptedCompletionBlockIfNeeded(candidate, snapshot, blocks);
        addSheetSequenceBlockIfNeeded(candidate, snapshot, blocks);
        addManualReadyBlockIfNeeded(candidate, snapshot, blocks);
        return List.copyOf(blocks);
    }

    private void addAdaptedCompletionBlockIfNeeded(
            DspSchedulerOrderState candidate,
            WarehouseSchedulerSnapshot snapshot,
            List<DependencyBlock> blocks) {
        OrderType orderType = candidate.order().orderType();
        if (orderType != OrderType.ASSOCIATED && orderType != OrderType.EMPTY) {
            return;
        }
        String notionalToteId = candidate.order().notionalToteId();
        if (!snapshot.completedAdaptedNotionalToteIds().contains(notionalToteId)) {
            blocks.add(new DependencyBlock(
                    DependencyType.ADAPTED_COMPLETION,
                    "Adapted work is not complete for notional tote " + notionalToteId));
        }
    }

    private void addSheetSequenceBlockIfNeeded(
            DspSchedulerOrderState candidate,
            WarehouseSchedulerSnapshot snapshot,
            List<DependencyBlock> blocks) {
        int candidateSheetNumber = candidate.order().sheetNumber();
        if (candidateSheetNumber <= 1) {
            return;
        }

        String notionalToteId = candidate.order().notionalToteId();
        int previousSheetNumber = candidateSheetNumber - 1;
        DspSchedulerOrderState previousSheet = snapshot.orderStates().stream()
                .filter(orderState -> orderState.order().notionalToteId().equals(notionalToteId))
                .filter(orderState -> orderState.order().sheetNumber() == previousSheetNumber)
                .findFirst()
                .orElse(null);

        if (previousSheet == null
                || (previousSheet.status() != DspOrderStatus.RELEASED
                && previousSheet.status() != DspOrderStatus.COMPLETED)) {
            blocks.add(new DependencyBlock(
                    DependencyType.SHEET_SEQUENCE,
                    "Previous sheet " + previousSheetNumber + " is not released or completed for notional tote " + notionalToteId));
        }
    }

    private void addManualReadyBlockIfNeeded(
            DspSchedulerOrderState candidate,
            WarehouseSchedulerSnapshot snapshot,
            List<DependencyBlock> blocks) {
        if (!candidate.routeRequirements().requiresManualMerge()) {
            return;
        }
        String notionalToteId = candidate.order().notionalToteId();
        if (!snapshot.manualReadyNotionalToteIds().contains(notionalToteId)) {
            blocks.add(new DependencyBlock(
                    DependencyType.MANUAL_READY,
                    "Manual work is not ready for notional tote " + notionalToteId));
        }
    }
}
