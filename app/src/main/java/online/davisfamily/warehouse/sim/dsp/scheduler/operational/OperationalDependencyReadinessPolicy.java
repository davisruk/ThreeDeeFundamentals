package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseAvailability;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseCandidate;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

public final class OperationalDependencyReadinessPolicy {

    public List<OperationalReleaseBlock> findBlocks(
            DspOperationalReleaseCandidate candidate,
            DspOperationalReleaseSnapshot snapshot) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }

        List<OperationalReleaseBlock> blocks = new ArrayList<>();
        addActiveSheetAssignmentBlock(candidate.physicalCandidate(), blocks);

        NotionalToteOrder logicalOrder = candidate.logicalOrderState().order();
        OrderType orderType = logicalOrder.orderType();
        if (orderType == OrderType.EMPTY) {
            throw new IllegalArgumentException(
                    "EMPTY orders cannot be operational physical release candidates");
        }
        if (orderType != OrderType.ASSOCIATED) {
            return List.copyOf(blocks);
        }

        for (DspOrderItem item : logicalOrder.items()) {
            if (item.lineType() != DspOrderLineType.ADAPTED) {
                continue;
            }
            PreparedLineKey requiredKey = PreparedLineKey.forDispatchLine(logicalOrder, item);
            if (!snapshot.preparedLineKeys().contains(requiredKey)) {
                blocks.add(new OperationalReleaseBlock(
                        OperationalReleaseBlockType.ADAPTED_DEPENDENCY,
                        "Adapted dependency is not ready for order "
                                + logicalOrder.orderId()
                                + " sheet "
                                + logicalOrder.sheetNumber()
                                + " line "
                                + item.lineReference()));
            }
        }
        return List.copyOf(blocks);
    }

    private static void addActiveSheetAssignmentBlock(
            OsrProcessingReleaseCandidate physicalCandidate,
            List<OperationalReleaseBlock> blocks) {
        if (physicalCandidate.availability()
                != OsrProcessingReleaseAvailability.BLOCKED_BY_ACTIVE_SHEET_ASSIGNMENT) {
            return;
        }
        String blockingPhysicalToteId = physicalCandidate.blockingPhysicalToteId()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Blocked physical candidate must identify its active assignment"))
                .value();
        blocks.add(new OperationalReleaseBlock(
                OperationalReleaseBlockType.ACTIVE_SHEET_ASSIGNMENT,
                "Physical tote " + physicalCandidate.physicalToteId().value()
                        + " is blocked by active physical tote " + blockingPhysicalToteId));
    }
}
