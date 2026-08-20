package online.davisfamily.warehouse.sim.dsp.supply;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig;

public final class DspServiceCentreSupplyPlanFactory {
    private static final Comparator<NotionalToteOrder> ORDER_SEQUENCE = Comparator
            .comparingLong(NotionalToteOrder::sequenceNumber)
            .thenComparing(NotionalToteOrder::orderId)
            .thenComparingInt(NotionalToteOrder::sheetNumber);

    private static final Comparator<InboundToteManifest> MANIFEST_SEQUENCE = Comparator
            .comparingLong(InboundToteManifest::sourceSequenceNumber)
            .thenComparing(manifest -> manifest.physicalToteId().value());

    public DspServiceCentreSupplyPlan create(
            LoadedDspData data,
            OsrInventoryConfig config) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }

        Map<String, List<NotionalToteOrder>> ordersByServiceCentre = groupOrders(data.orders());
        Map<String, List<InboundToteManifest>> manifestsByServiceCentre = groupManifests(
                data.inboundToteManifests(),
                ordersByServiceCentre.keySet());
        List<ServiceCentreSupplyIssue> issues = new ArrayList<>();
        Set<String> preloadServiceCentreIds = new LinkedHashSet<>(config.preloadServiceCentreIds());
        List<ServiceCentreSupplyBatch> batches = new ArrayList<>();

        for (Map.Entry<String, List<NotionalToteOrder>> entry : ordersByServiceCentre.entrySet()) {
            String serviceCentreId = entry.getKey();
            List<NotionalToteOrder> orders = entry.getValue();
            NotionalToteOrder earliestOrder = orders.stream()
                    .min(ORDER_SEQUENCE)
                    .orElseThrow(() -> new IllegalStateException(
                            "No orders retained for service centre: " + serviceCentreId));
            int priority = earliestOrder.orderPriority();
            if (priority <= 0) {
                throw new IllegalArgumentException(
                        "Service centre has no specified priority: " + serviceCentreId);
            }

            Set<Integer> observedPriorities = orders.stream()
                    .map(NotionalToteOrder::orderPriority)
                    .collect(Collectors.toCollection(TreeSet::new));
            if (observedPriorities.size() > 1) {
                issues.add(new ServiceCentreSupplyIssue(
                        ServiceCentreSupplyIssueType.INCONSISTENT_PRIORITIES,
                        priority,
                        List.of(serviceCentreId),
                        List.copyOf(observedPriorities)));
            }

            List<InboundToteManifest> manifests = orderManifests(
                    manifestsByServiceCentre.getOrDefault(serviceCentreId, List.of()));
            Set<OrderSheetKey> emptyOrderSheetKeys = orders.stream()
                    .filter(order -> order.orderType() == OrderType.EMPTY)
                    .sorted(ORDER_SEQUENCE)
                    .map(NotionalToteOrder::orderSheetKey)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            batches.add(new ServiceCentreSupplyBatch(
                    serviceCentreId,
                    priority,
                    earliestOrder.sequenceNumber(),
                    preloadServiceCentreIds.contains(serviceCentreId),
                    manifests,
                    emptyOrderSheetKeys));
        }

        batches.sort(Comparator
                .comparingInt(ServiceCentreSupplyBatch::priority)
                .reversed()
                .thenComparingLong(ServiceCentreSupplyBatch::firstSourceSequenceNumber)
                .thenComparing(ServiceCentreSupplyBatch::serviceCentreId));

        Map<Integer, List<ServiceCentreSupplyBatch>> batchesByPriority = batches.stream()
                .collect(Collectors.groupingBy(
                        ServiceCentreSupplyBatch::priority,
                        LinkedHashMap::new,
                        Collectors.toList()));
        for (Map.Entry<Integer, List<ServiceCentreSupplyBatch>> entry : batchesByPriority.entrySet()) {
            if (entry.getValue().size() > 1) {
                issues.add(new ServiceCentreSupplyIssue(
                        ServiceCentreSupplyIssueType.DUPLICATE_PRIORITY,
                        entry.getKey(),
                        entry.getValue().stream()
                                .map(ServiceCentreSupplyBatch::serviceCentreId)
                                .toList(),
                        List.of(entry.getKey())));
            }
        }

        Set<String> knownServiceCentreIds = ordersByServiceCentre.keySet();
        for (String preloadServiceCentreId : preloadServiceCentreIds) {
            if (!knownServiceCentreIds.contains(preloadServiceCentreId)) {
                throw new IllegalArgumentException(
                        "Configured preload service centre is missing from loaded data: "
                                + preloadServiceCentreId);
            }
        }

        return new DspServiceCentreSupplyPlan(batches, issues);
    }

    private static Map<String, List<NotionalToteOrder>> groupOrders(
            List<NotionalToteOrder> orders) {
        if (orders == null) {
            throw new IllegalArgumentException("orders must not be null");
        }
        Map<String, List<NotionalToteOrder>> grouped = new LinkedHashMap<>();
        for (NotionalToteOrder order : orders) {
            if (order == null) {
                throw new IllegalArgumentException("orders must not contain null");
            }
            grouped.computeIfAbsent(order.serviceCentreId(), ignored -> new ArrayList<>()).add(order);
        }
        return grouped;
    }

    private static Map<String, List<InboundToteManifest>> groupManifests(
            List<InboundToteManifest> manifests,
            Set<String> knownServiceCentreIds) {
        if (manifests == null) {
            throw new IllegalArgumentException("inboundToteManifests must not be null");
        }
        Map<String, List<InboundToteManifest>> grouped = new LinkedHashMap<>();
        Set<String> physicalToteIds = new LinkedHashSet<>();
        for (InboundToteManifest manifest : manifests) {
            if (manifest == null) {
                throw new IllegalArgumentException("inboundToteManifests must not contain null");
            }
            if (!knownServiceCentreIds.contains(manifest.serviceCentreId())) {
                throw new IllegalArgumentException(
                        "Physical manifest has no retained logical order for service centre: "
                                + manifest.serviceCentreId());
            }
            if (!physicalToteIds.add(manifest.physicalToteId().value())) {
                throw new IllegalArgumentException(
                        "Duplicate physical tote ID in loaded data: "
                                + manifest.physicalToteId().value());
            }
            grouped.computeIfAbsent(manifest.serviceCentreId(), ignored -> new ArrayList<>()).add(manifest);
        }
        return grouped;
    }

    private static List<InboundToteManifest> orderManifests(
            List<InboundToteManifest> manifests) {
        List<InboundToteManifest> adapted = new ArrayList<>();
        List<InboundToteManifest> fulfilment = new ArrayList<>();
        for (InboundToteManifest manifest : manifests) {
            switch (manifest.orderType()) {
                case ADAPTED -> adapted.add(manifest);
                case FULL_PACK, ASSOCIATED -> fulfilment.add(manifest);
                case EMPTY -> throw new IllegalArgumentException(
                        "EMPTY order cannot have a physical inbound manifest: "
                                + manifest.physicalToteId().value());
            }
        }
        adapted.sort(MANIFEST_SEQUENCE);
        fulfilment.sort(MANIFEST_SEQUENCE);
        List<InboundToteManifest> ordered = new ArrayList<>(adapted.size() + fulfilment.size());
        ordered.addAll(adapted);
        ordered.addAll(fulfilment);
        return ordered;
    }
}
