package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingTargetSheetCatalog;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.PackSourceProvenance;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackSlot;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackSlotKey;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

/** Projects validated full-day bag slots into exact Adapting fulfilment sheets once. */
public final class DspFullDayAdaptingTargetSheetCatalogFactory {

    public AdaptingTargetSheetCatalog create(LoadedDspData executableData, BagPlanningResult bagPlan) {
        if (executableData == null || bagPlan == null) {
            throw new IllegalArgumentException("executableData and bagPlan must not be null");
        }

        Map<OrderSheetKey, Set<PreparedLineKey>> targetAliasesBySheet = new LinkedHashMap<>();
        for (NotionalToteOrder order : executableData.orders()) {
            if (order.orderType() != OrderType.ASSOCIATED && order.orderType() != OrderType.EMPTY) {
                continue;
            }
            Set<PreparedLineKey> aliases = new LinkedHashSet<>();
            for (DspOrderItem line : order.items()) {
                if (line.lineType() == DspOrderLineType.ADAPTED) {
                    aliases.add(PreparedLineKey.forDispatchLine(order, line));
                }
            }
            if (targetAliasesBySheet.putIfAbsent(order.orderSheetKey(), aliases) != null) {
                throw new IllegalStateException("Duplicate executable fulfilment sheet " + order.orderSheetKey());
            }
        }

        Map<PreparedLineKey, OrderSheetKey> targetSheetsByPreparedLine = new LinkedHashMap<>();
        for (NotionalToteOrder sourceOrder : executableData.orders()) {
            if (sourceOrder.orderType() != OrderType.ADAPTED) {
                continue;
            }
            for (DspOrderItem sourceLine : sourceOrder.items()) {
                PreparedLineKey key = PreparedLineKey.forPreparedLine(sourceLine);
                if (targetSheetsByPreparedLine.containsKey(key)) {
                    throw new IllegalStateException("Duplicate executable prepared line " + key);
                }
                PlannedPackSlotKey slotKey = new PlannedPackSlotKey(
                        sourceOrder.orderSheetKey(), sourceLine.lineReference(), 1);
                PlannedPackSlot slot = bagPlan.findPlannedPackSlot(slotKey).orElseThrow(() ->
                        new IllegalStateException("Missing planned pack slot for prepared line " + key
                                + " at " + slotKey));
                if (slot.initialPhysicalToteId().isPresent()) {
                    throw new IllegalStateException("Prepared line has an initial physical tote: " + key);
                }
                PackSourceProvenance expectedSource = new PackSourceProvenance(
                        sourceOrder.orderSheetKey(),
                        sourceLine.lineReference(),
                        sourceLine.productId(),
                        sourceOrder.serviceCentreId(),
                        sourceLine.pharmacyId(),
                        sourceLine.patientId(),
                        sourceLine.prescriptionId());
                if (!expectedSource.equals(slot.sourceProvenance())) {
                    throw new IllegalStateException("Planned pack source does not match prepared line " + key);
                }
                OrderSheetKey targetSheet = slot.fulfilmentOrderSheetKey();
                if (!targetSheet.orderId().equals(sourceLine.referenceOrderId())) {
                    throw new IllegalStateException("Planned target order does not match prepared line " + key);
                }
                Set<PreparedLineKey> aliases = targetAliasesBySheet.get(targetSheet);
                if (aliases == null || !aliases.contains(key)) {
                    throw new IllegalStateException("Planned target sheet has no executable ADAPTED alias for "
                            + key + " at " + targetSheet);
                }
                targetSheetsByPreparedLine.put(key, targetSheet);
            }
        }
        return new AdaptingTargetSheetCatalog(targetSheetsByPreparedLine);
    }
}
