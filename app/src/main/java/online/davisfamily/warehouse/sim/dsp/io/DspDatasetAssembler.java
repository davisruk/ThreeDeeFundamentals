package online.davisfamily.warehouse.sim.dsp.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderValidator;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

public class DspDatasetAssembler {
    private final TwelveNMessageKindMapper messageKindMapper;
    private final TwelveNOrderMapper orderMapper;
    private final DspOrderValidator orderValidator;

    public DspDatasetAssembler(
            TwelveNMessageKindMapper messageKindMapper,
            TwelveNOrderMapper orderMapper,
            DspOrderValidator orderValidator) {
        if (messageKindMapper == null) {
            throw new IllegalArgumentException("messageKindMapper must not be null");
        }
        if (orderMapper == null) {
            throw new IllegalArgumentException("orderMapper must not be null");
        }
        if (orderValidator == null) {
            throw new IllegalArgumentException("orderValidator must not be null");
        }
        this.messageKindMapper = messageKindMapper;
        this.orderMapper = orderMapper;
        this.orderValidator = orderValidator;
    }

    public LoadedDspData assemble(List<ProductMasterRecord> products, List<TwelveNMessageJson> messages) {
        if (products == null) {
            throw new IllegalArgumentException("products must not be null");
        }
        if (messages == null) {
            throw new IllegalArgumentException("messages must not be null");
        }

        List<ProductMasterRecord> retainedProducts = new ArrayList<>();
        Set<String> knownProductIds = new LinkedHashSet<>();
        for (ProductMasterRecord product : products) {
            if (product == null) {
                throw new IllegalArgumentException("products must not contain null");
            }
            retainedProducts.add(product);
            knownProductIds.add(product.productId());
        }
        retainedProducts = List.copyOf(retainedProducts);

        Map<OrderSheetKey, LogicalOrderGroup> orderGroups = new LinkedHashMap<>();
        List<InboundToteManifest> inboundToteManifests = new ArrayList<>();
        List<DspOrderItem> preparedLines = new ArrayList<>();
        Set<PreparedLineKey> loadedPreparedLineKeys = new LinkedHashSet<>();
        List<UnresolvedProductLine> unresolvedProductLines = new ArrayList<>();
        int ignoredManualMessageCount = 0;
        int ignoredManualLineCount = 0;
        int omittedOrderCount = 0;
        long nextSourceSequenceNumber = 0;

        for (TwelveNMessageJson message : messages) {
            if (message == null) {
                throw new IllegalArgumentException("messages must not contain null");
            }
            TwelveNLineMappingSupport.validateMessage(message);
            TwelveNMessageKind messageKind = messageKindMapper.map(message.toteIdentifier().payload());
            if (messageKind == TwelveNMessageKind.MANUAL_PREPARATION) {
                ignoredManualMessageCount++;
                ignoredManualLineCount += message.orderDetail().orderLines().size();
                continue;
            }

            MappedTwelveNOrder mapped = orderMapper.map(message, nextSourceSequenceNumber);
            NotionalToteOrder mappedOrder = mapped.order();
            List<DspOrderItem> retainedLines = mappedOrder.items().stream()
                    .filter(line -> line.lineType() != DspOrderLineType.MANUAL)
                    .toList();
            ignoredManualLineCount += mappedOrder.items().size() - retainedLines.size();
            if (retainedLines.isEmpty()) {
                omittedOrderCount++;
                continue;
            }

            NotionalToteOrder retainedOrder = retainedLines.size() == mappedOrder.items().size()
                    ? mappedOrder
                    : withItems(mappedOrder, retainedLines);
            mapped.inboundToteManifest()
                    .map(manifest -> retainedLines.size() == mappedOrder.items().size()
                            ? manifest
                            : manifest.withItems(retainedLines))
                    .ifPresent(inboundToteManifests::add);
            orderGroups.computeIfAbsent(
                    retainedOrder.orderSheetKey(),
                    ignored -> new LogicalOrderGroup(retainedOrder))
                    .add(retainedOrder);
            nextSourceSequenceNumber++;

            for (DspOrderItem line : retainedLines) {
                if (!knownProductIds.contains(line.productId())) {
                    unresolvedProductLines.add(new UnresolvedProductLine(
                            retainedOrder.orderId(),
                            line.lineReference(),
                            line.productId()));
                }
            }

            if (retainedOrder.orderType() == OrderType.ADAPTED) {
                preparedLines.addAll(retainedLines);
                for (DspOrderItem line : retainedLines) {
                    loadedPreparedLineKeys.add(PreparedLineKey.forPreparedLine(line));
                }
            }
        }

        List<NotionalToteOrder> orders = orderGroups.values().stream()
                .map(LogicalOrderGroup::toOrder)
                .toList();
        orders.forEach(orderValidator::validateForScheduler);
        InboundToteManifestCatalog manifestCatalog = new InboundToteManifestCatalog(inboundToteManifests);

        DspDatasetLoadReport report = new DspDatasetLoadReport(
                ignoredManualMessageCount,
                ignoredManualLineCount,
                omittedOrderCount,
                unresolvedProductLines);
        return new LoadedDspData(
                retainedProducts,
                orders,
                preparedLines,
                loadedPreparedLineKeys,
                Set.of(),
                manifestCatalog.manifests(),
                report);
    }

    private NotionalToteOrder withItems(NotionalToteOrder order, List<DspOrderItem> items) {
        return new NotionalToteOrder(
                order.orderId(),
                order.notionalToteId(),
                order.serviceCentreId(),
                order.sheetNumber(),
                order.orderType(),
                items,
                order.orderPriority(),
                order.sequenceNumber());
    }

    private static final class LogicalOrderGroup {
        private final NotionalToteOrder firstContribution;
        private final List<DspOrderItem> items = new ArrayList<>();
        private final Set<String> lineReferences = new LinkedHashSet<>();

        private LogicalOrderGroup(NotionalToteOrder firstContribution) {
            this.firstContribution = firstContribution;
        }

        private void add(NotionalToteOrder contribution) {
            if (!firstContribution.serviceCentreId().equals(contribution.serviceCentreId())) {
                throw new IllegalArgumentException(
                        "Conflicting serviceCentreId for logical order sheet "
                                + contribution.orderSheetKey());
            }
            if (firstContribution.orderType() != contribution.orderType()) {
                throw new IllegalArgumentException(
                        "Conflicting orderType for logical order sheet " + contribution.orderSheetKey());
            }
            if (firstContribution.orderPriority() != contribution.orderPriority()) {
                throw new IllegalArgumentException(
                        "Conflicting orderPriority for logical order sheet "
                                + contribution.orderSheetKey());
            }
            for (DspOrderItem item : contribution.items()) {
                if (!lineReferences.add(item.lineReference())) {
                    throw new IllegalArgumentException(
                            "Duplicate lineReference " + item.lineReference()
                                    + " for logical order sheet " + contribution.orderSheetKey());
                }
                items.add(item);
            }
        }

        private NotionalToteOrder toOrder() {
            return new NotionalToteOrder(
                    firstContribution.orderId(),
                    firstContribution.notionalToteId(),
                    firstContribution.serviceCentreId(),
                    firstContribution.sheetNumber(),
                    firstContribution.orderType(),
                    items,
                    firstContribution.orderPriority(),
                    firstContribution.sequenceNumber());
        }
    }
}
