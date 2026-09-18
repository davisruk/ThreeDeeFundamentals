package online.davisfamily.warehouse.sim.dsp.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.bagging.BagPackDemand;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningRequest;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningTote;
import online.davisfamily.warehouse.sim.dsp.bagging.DspPackPlanFactory;
import online.davisfamily.warehouse.sim.dsp.bagging.PackProvenanceRegistry;
import online.davisfamily.warehouse.sim.dsp.bagging.PackSourceProvenance;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackSlotKey;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

/** Builds the immutable full-day logical bag demand and its initially physical load plans. */
public final class DspFullDayBagPlanningRequestFactory {

    public BagPlanningRequest create(LoadedDspData data) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }

        Map<String, ProductMasterRecord> productsById = indexProducts(data.products());
        Map<OrderSheetKey, NotionalToteOrder> ordersBySheet = indexOrders(data.orders());
        Map<PreparedLineKey, PreparedLineSource> preparedLinesByKey = indexPreparedLines(data.orders());
        Map<OrderSheetKey, Map<String, DspOrderItem>> orderLinesBySheet = indexOrderLines(data.orders());

        PackProvenanceRegistry provenanceRegistry = new PackProvenanceRegistry();
        DspPackPlanFactory packPlanFactory = new DspPackPlanFactory(provenanceRegistry);
        Map<ObservationKey, PhysicalPackObservation> observationsBySlot = new LinkedHashMap<>();
        Set<String> reservedPhysicalPackIds = new LinkedHashSet<>();
        List<BagPlanningTote> planningTotes = new ArrayList<>();

        for (InboundToteManifest manifest : data.inboundToteManifests()) {
            NotionalToteOrder order = ordersBySheet.get(manifest.orderSheetKey());
            if (order == null) {
                throw new IllegalArgumentException(
                        "Inbound manifest has no matching order sheet: " + manifest.orderSheetKey());
            }
            validateManifestIdentity(manifest, order, orderLinesBySheet.get(manifest.orderSheetKey()));

            List<PackPlan> physicalPackPlans = new ArrayList<>();
            for (DspOrderItem manifestLine : manifest.items()) {
                validatePickedCount(manifestLine, manifest.orderSheetKey());
                DspOrderItem orderLine = orderLinesBySheet.get(manifest.orderSheetKey())
                        .get(manifestLine.lineReference());
                if (orderLine.lineType() == DspOrderLineType.ADAPTED
                        || order.orderType() == OrderType.ADAPTED) {
                    continue;
                }
                if (manifestLine.numberOfPacksPicked() == 0) {
                    continue;
                }
                ProductMasterRecord product = requireProduct(
                        productsById,
                        manifestLine.productId(),
                        manifest.orderSheetKey(),
                        manifestLine.lineReference());
                PackDimensions dimensions = requireDimensions(product, manifest.orderSheetKey(),
                        manifestLine.lineReference());
                PackSourceProvenance sourceProvenance = sourceProvenance(
                        order,
                        manifestLine);
                for (int ordinal = 1; ordinal <= manifestLine.numberOfPacksPicked(); ordinal++) {
                    String packId = initialPhysicalPackId(manifest.physicalToteId(), manifestLine, ordinal);
                    if (!reservedPhysicalPackIds.add(packId)) {
                        throw new IllegalArgumentException(
                                "Duplicate reserved physical pack ID: " + packId);
                    }
                    ObservationKey slotKey = new ObservationKey(
                            manifest.orderSheetKey(), manifestLine.lineReference(), ordinal);
                    if (observationsBySlot.putIfAbsent(
                            slotKey,
                            new PhysicalPackObservation(
                                    packId,
                                    manifest.physicalToteId(),
                                    dimensions)) != null) {
                        throw new IllegalArgumentException(
                                "Duplicate initial physical observation for " + slotKey);
                    }
                    physicalPackPlans.add(packPlanFactory.createPackPlan(
                            packId,
                            manifestLine.lineReference(),
                            dimensions,
                            sourceProvenance));
                }
            }

            if (manifest.orderType() != OrderType.ADAPTED) {
                planningTotes.add(new BagPlanningTote(
                        manifest.orderSheetKey(),
                        manifest.serviceCentreId(),
                        new ToteLoadPlan(manifest.physicalToteId(), physicalPackPlans)));
            }
        }

        List<BagPackDemand> packDemands = new ArrayList<>();
        Set<ObservationKey> claimedObservations = new LinkedHashSet<>();
        for (NotionalToteOrder fulfilmentOrder : data.orders()) {
            if (fulfilmentOrder.orderType() == OrderType.ADAPTED) {
                continue;
            }
            for (DspOrderItem fulfilmentLine : fulfilmentOrder.items()) {
                if (fulfilmentLine.lineType() == DspOrderLineType.MANUAL) {
                    throw new IllegalArgumentException(
                            "MANUAL line is outside executable demand: "
                                    + fulfilmentOrder.orderSheetKey() + " line "
                                    + fulfilmentLine.lineReference());
                }
                validatePickedCount(fulfilmentLine, fulfilmentOrder.orderSheetKey());

                SourceLine sourceLine = resolveSourceLine(
                        fulfilmentOrder,
                        fulfilmentLine,
                        preparedLinesByKey);
                DspOrderItem source = sourceLine == null ? fulfilmentLine : sourceLine.line();
                NotionalToteOrder sourceOrder = sourceLine == null ? fulfilmentOrder : sourceLine.order();
                validateSourceAndFulfilment(
                        fulfilmentOrder,
                        fulfilmentLine,
                        sourceOrder,
                        source);

                ProductMasterRecord product = requireProduct(
                        productsById,
                        source.productId(),
                        fulfilmentOrder.orderSheetKey(),
                        fulfilmentLine.lineReference());
                PackDimensions dimensions = requireDimensions(
                        product,
                        fulfilmentOrder.orderSheetKey(),
                        fulfilmentLine.lineReference());

                boolean adaptedCollection = sourceLine != null;
                if (adaptedCollection) {
                    if (product.thirdParty() && source.numberOfPacksPicked() != 0) {
                        throw thirdPartyPickedCountError(sourceOrder, source);
                    }
                } else if (product.thirdParty()) {
                    if (fulfilmentLine.numberOfPacksPicked() != 0) {
                        throw thirdPartyPickedCountError(fulfilmentOrder, fulfilmentLine);
                    }
                } else if (fulfilmentLine.numberOfPacksPicked() != fulfilmentLine.quantity()) {
                    throw ordinaryPickedCountError(fulfilmentOrder, fulfilmentLine);
                }

                PackSourceProvenance provenance = sourceProvenance(sourceOrder, source);
                for (int ordinal = 1; ordinal <= source.quantity(); ordinal++) {
                    PlannedPackSlotKey slotKey = new PlannedPackSlotKey(
                            sourceOrder.orderSheetKey(), source.lineReference(), ordinal);
                    ObservationKey observationKey = new ObservationKey(
                            fulfilmentOrder.orderSheetKey(), fulfilmentLine.lineReference(), ordinal);
                    PhysicalPackObservation observation = adaptedCollection
                            ? null
                            : observationsBySlot.get(observationKey);
                    if (!adaptedCollection && product.thirdParty()) {
                        observation = null;
                    }

                    String reservedPackId;
                    java.util.Optional<PhysicalToteId> initialToteId;
                    if (observation != null) {
                        if (!claimedObservations.add(observationKey)) {
                            throw new IllegalArgumentException(
                                    "Initial physical observation claimed more than once: "
                                            + observationKey);
                        }
                        reservedPackId = observation.physicalPackId();
                        initialToteId = java.util.Optional.of(observation.physicalToteId());
                        if (!observation.dimensions().equals(dimensions)) {
                            throw new IllegalArgumentException(
                                    "Initial physical dimensions do not match demand for "
                                            + fulfilmentOrder.orderSheetKey() + " line "
                                            + fulfilmentLine.lineReference());
                        }
                    } else if (!adaptedCollection && !product.thirdParty()) {
                        throw new IllegalArgumentException(
                                "Picked slot has no initial observation for "
                                        + fulfilmentOrder.orderSheetKey() + " line "
                                        + fulfilmentLine.lineReference() + " ordinal " + ordinal);
                    } else {
                        reservedPackId = stationPendingPackId(source.lineReference(), ordinal);
                        initialToteId = java.util.Optional.empty();
                        if (!reservedPhysicalPackIds.add(reservedPackId)) {
                            throw new IllegalArgumentException(
                                    "Duplicate reserved physical pack ID: " + reservedPackId);
                        }
                    }

                    packDemands.add(new BagPackDemand(
                            slotKey,
                            reservedPackId,
                            dimensions,
                            provenance,
                            fulfilmentOrder.orderSheetKey(),
                            initialToteId));
                }
            }
        }

        for (ObservationKey observationKey : observationsBySlot.keySet()) {
            if (!claimedObservations.contains(observationKey)) {
                throw new IllegalArgumentException(
                        "Unclaimed initial physical observation for " + observationKey);
            }
        }

        if (packDemands.isEmpty() && planningTotes.isEmpty()) {
            throw new IllegalArgumentException(
                    "Full-day bag planning input contains no fulfilment demand or physical tote");
        }
        return new BagPlanningRequest(packDemands, planningTotes);
    }

    private static Map<String, ProductMasterRecord> indexProducts(
            List<ProductMasterRecord> products) {
        Map<String, ProductMasterRecord> productsById = new LinkedHashMap<>();
        for (ProductMasterRecord product : products) {
            if (product == null) {
                throw new IllegalArgumentException("products must not contain null");
            }
            if (productsById.putIfAbsent(product.productId(), product) != null) {
                throw new IllegalArgumentException("Duplicate productId: " + product.productId());
            }
        }
        return productsById;
    }

    private static Map<OrderSheetKey, NotionalToteOrder> indexOrders(
            List<NotionalToteOrder> orders) {
        Map<OrderSheetKey, NotionalToteOrder> ordersBySheet = new LinkedHashMap<>();
        for (NotionalToteOrder order : orders) {
            if (order == null) {
                throw new IllegalArgumentException("orders must not contain null");
            }
            if (ordersBySheet.putIfAbsent(order.orderSheetKey(), order) != null) {
                throw new IllegalArgumentException(
                        "Duplicate order sheet: " + order.orderSheetKey());
            }
        }
        return ordersBySheet;
    }

    private static Map<OrderSheetKey, Map<String, DspOrderItem>> indexOrderLines(
            List<NotionalToteOrder> orders) {
        Map<OrderSheetKey, Map<String, DspOrderItem>> linesBySheet = new LinkedHashMap<>();
        for (NotionalToteOrder order : orders) {
            Map<String, DspOrderItem> lines = new LinkedHashMap<>();
            for (DspOrderItem line : order.items()) {
                if (lines.putIfAbsent(line.lineReference(), line) != null) {
                    throw new IllegalArgumentException(
                            "Duplicate line reference " + line.lineReference()
                                    + " for order sheet " + order.orderSheetKey());
                }
            }
            linesBySheet.put(order.orderSheetKey(), lines);
        }
        return linesBySheet;
    }

    private static Map<PreparedLineKey, PreparedLineSource> indexPreparedLines(
            List<NotionalToteOrder> orders) {
        Map<PreparedLineKey, PreparedLineSource> preparedLinesByKey = new LinkedHashMap<>();
        for (NotionalToteOrder order : orders) {
            if (order.orderType() != OrderType.ADAPTED) {
                continue;
            }
            for (DspOrderItem line : order.items()) {
                PreparedLineKey key = PreparedLineKey.forPreparedLine(line);
                PreparedLineSource previous = preparedLinesByKey.putIfAbsent(
                        key,
                        new PreparedLineSource(order, line));
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "Duplicate ADAPTED prepared-line key: " + key);
                }
            }
        }
        return preparedLinesByKey;
    }

    private static SourceLine resolveSourceLine(
            NotionalToteOrder fulfilmentOrder,
            DspOrderItem fulfilmentLine,
            Map<PreparedLineKey, PreparedLineSource> preparedLinesByKey) {
        if (fulfilmentLine.lineType() != DspOrderLineType.ADAPTED) {
            return null;
        }
        PreparedLineKey key = PreparedLineKey.forDispatchLine(fulfilmentOrder, fulfilmentLine);
        PreparedLineSource source = preparedLinesByKey.get(key);
        if (source == null) {
            throw new IllegalArgumentException(
                    "Missing ADAPTED source line for " + fulfilmentOrder.orderSheetKey()
                            + " line " + fulfilmentLine.lineReference()
                            + " key " + key);
        }
        if (source.line().lineType() != DspOrderLineType.ADAPTED) {
            throw new IllegalArgumentException(
                    "ADAPTED source line has the wrong line type for " + key);
        }
        return new SourceLine(source.order(), source.line());
    }

    private static void validateSourceAndFulfilment(
            NotionalToteOrder fulfilmentOrder,
            DspOrderItem fulfilmentLine,
            NotionalToteOrder sourceOrder,
            DspOrderItem sourceLine) {
        if (!sourceLine.lineReference().equals(fulfilmentLine.lineReference())
                || !sourceLine.productId().equals(fulfilmentLine.productId())
                || sourceLine.quantity() != fulfilmentLine.quantity()
                || !sourceLine.pharmacyId().equals(fulfilmentLine.pharmacyId())
                || !sourceLine.patientId().equals(fulfilmentLine.patientId())
                || !sourceLine.prescriptionId().equals(fulfilmentLine.prescriptionId())
                || !sourceOrder.serviceCentreId().equals(fulfilmentOrder.serviceCentreId())) {
            throw new IllegalArgumentException(
                    "ADAPTED source and fulfilment line mismatch for "
                            + fulfilmentOrder.orderSheetKey() + " line "
                            + fulfilmentLine.lineReference());
        }
    }

    private static void validateManifestIdentity(
            InboundToteManifest manifest,
            NotionalToteOrder order,
            Map<String, DspOrderItem> orderLines) {
        if (manifest.orderType() != order.orderType()
                || !manifest.serviceCentreId().equals(order.serviceCentreId())) {
            throw new IllegalArgumentException(
                    "Inbound manifest identity does not match order sheet: "
                            + manifest.orderSheetKey());
        }
        for (DspOrderItem manifestLine : manifest.items()) {
            DspOrderItem orderLine = orderLines.get(manifestLine.lineReference());
            if (orderLine == null || !sameLineIdentity(orderLine, manifestLine)) {
                throw new IllegalArgumentException(
                        "Inbound manifest line does not match order sheet "
                                + manifest.orderSheetKey() + " line "
                                + manifestLine.lineReference());
            }
        }
    }

    private static boolean sameLineIdentity(DspOrderItem first, DspOrderItem second) {
        return first.lineReference().equals(second.lineReference())
                && first.productId().equals(second.productId())
                && first.quantity() == second.quantity()
                && first.pharmacyId().equals(second.pharmacyId())
                && first.patientId().equals(second.patientId())
                && first.prescriptionId().equals(second.prescriptionId())
                && first.lineType() == second.lineType()
                && first.referenceOrderId().equals(second.referenceOrderId())
                && first.referenceSheetNumber() == second.referenceSheetNumber();
    }

    private static ProductMasterRecord requireProduct(
            Map<String, ProductMasterRecord> productsById,
            String productId,
            OrderSheetKey orderSheetKey,
            String lineReference) {
        ProductMasterRecord product = productsById.get(productId);
        if (product == null) {
            throw new IllegalArgumentException(
                    "Executable line has no product master data for "
                            + orderSheetKey + " line " + lineReference
                            + " product " + productId);
        }
        return product;
    }

    private static PackDimensions requireDimensions(
            ProductMasterRecord product,
            OrderSheetKey orderSheetKey,
            String lineReference) {
        return product.dimensions().orElseThrow(() -> new IllegalArgumentException(
                "Executable line has no pack dimensions for "
                        + orderSheetKey + " line " + lineReference
                        + " product " + product.productId()));
    }

    private static void validatePickedCount(DspOrderItem line, OrderSheetKey orderSheetKey) {
        if (line.numberOfPacksPicked() > line.quantity()) {
            throw new IllegalArgumentException(
                    "numberOfPacksPicked exceeds quantity for "
                            + orderSheetKey + " line " + line.lineReference());
        }
    }

    private static IllegalArgumentException thirdPartyPickedCountError(
            NotionalToteOrder order,
            DspOrderItem line) {
        return new IllegalArgumentException(
                "Third Party line must have numberOfPacksPicked == 0 for "
                        + order.orderSheetKey() + " line " + line.lineReference());
    }

    private static IllegalArgumentException ordinaryPickedCountError(
            NotionalToteOrder order,
            DspOrderItem line) {
        String reason = line.numberOfPacksPicked() == 0
                ? "zero-picked"
                : "positive-partial";
        return new IllegalArgumentException(
                "Ordinary fulfilment line has " + reason + " count for "
                        + order.orderSheetKey() + " line " + line.lineReference()
                        + " (picked " + line.numberOfPacksPicked()
                        + " of " + line.quantity() + ")");
    }

    private static PackSourceProvenance sourceProvenance(
            NotionalToteOrder sourceOrder,
            DspOrderItem sourceLine) {
        return new PackSourceProvenance(
                sourceOrder.orderSheetKey(),
                sourceLine.lineReference(),
                sourceLine.productId(),
                sourceOrder.serviceCentreId(),
                sourceLine.pharmacyId(),
                sourceLine.patientId(),
                sourceLine.prescriptionId());
    }

    private static String initialPhysicalPackId(
            PhysicalToteId physicalToteId,
            DspOrderItem line,
            int ordinal) {
        return "pack-" + physicalToteId.value() + "-" + line.lineReference() + "-" + ordinal;
    }

    private static String stationPendingPackId(String lineReference, int ordinal) {
        return "pack-" + lineReference + "-" + ordinal;
    }

    private record PreparedLineSource(NotionalToteOrder order, DspOrderItem line) {
    }

    private record SourceLine(NotionalToteOrder order, DspOrderItem line) {
    }

    private record ObservationKey(
            OrderSheetKey fulfilmentOrderSheetKey,
            String lineReference,
            int packOrdinal) {
    }

    private record PhysicalPackObservation(
            String physicalPackId,
            PhysicalToteId physicalToteId,
            PackDimensions dimensions) {
    }
}
