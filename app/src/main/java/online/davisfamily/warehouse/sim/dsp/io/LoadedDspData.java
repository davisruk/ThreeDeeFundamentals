package online.davisfamily.warehouse.sim.dsp.io;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

public record LoadedDspData(
        List<ProductMasterRecord> products,
        List<NotionalToteOrder> orders,
        List<DspOrderItem> preparedLines,
        Set<PreparedLineKey> loadedPreparedLineKeys,
        Set<PreparedLineKey> startupReadyPreparedLineKeys,
        List<InboundToteManifest> inboundToteManifests,
        DspDatasetLoadReport report,
        List<DspRetainedInputLine> retainedInputLines) {

    public LoadedDspData(
            List<ProductMasterRecord> products,
            List<NotionalToteOrder> orders,
            List<DspOrderItem> preparedLines,
            Set<PreparedLineKey> loadedPreparedLineKeys) {
        this(
                products,
                orders,
                preparedLines,
                loadedPreparedLineKeys,
                Set.of(),
                List.of(),
                DspDatasetLoadReport.empty());
    }

    public LoadedDspData(
            List<ProductMasterRecord> products,
            List<NotionalToteOrder> orders,
            List<DspOrderItem> preparedLines,
            Set<PreparedLineKey> loadedPreparedLineKeys,
            Set<PreparedLineKey> startupReadyPreparedLineKeys) {
        this(
                products,
                orders,
                preparedLines,
                loadedPreparedLineKeys,
                startupReadyPreparedLineKeys,
                List.of(),
                DspDatasetLoadReport.empty());
    }

    public LoadedDspData(
            List<ProductMasterRecord> products,
            List<NotionalToteOrder> orders,
            List<DspOrderItem> preparedLines,
            Set<PreparedLineKey> loadedPreparedLineKeys,
            Set<PreparedLineKey> startupReadyPreparedLineKeys,
            List<InboundToteManifest> inboundToteManifests,
            DspDatasetLoadReport report) {
        this(
                products,
                orders,
                preparedLines,
                loadedPreparedLineKeys,
                startupReadyPreparedLineKeys,
                inboundToteManifests,
                report,
                fixtureRetainedInputLines(orders));
    }

    public LoadedDspData {
        if (products == null) {
            throw new IllegalArgumentException("products must not be null");
        }
        if (orders == null) {
            throw new IllegalArgumentException("orders must not be null");
        }
        if (preparedLines == null) {
            throw new IllegalArgumentException("preparedLines must not be null");
        }
        if (loadedPreparedLineKeys == null) {
            throw new IllegalArgumentException("loadedPreparedLineKeys must not be null");
        }
        if (startupReadyPreparedLineKeys == null) {
            throw new IllegalArgumentException("startupReadyPreparedLineKeys must not be null");
        }
        if (inboundToteManifests == null) {
            throw new IllegalArgumentException("inboundToteManifests must not be null");
        }
        if (report == null) {
            throw new IllegalArgumentException("report must not be null");
        }
        if (retainedInputLines == null) {
            throw new IllegalArgumentException("retainedInputLines must not be null");
        }
        products = List.copyOf(products);
        orders = List.copyOf(orders);
        preparedLines = List.copyOf(preparedLines);
        loadedPreparedLineKeys = Set.copyOf(loadedPreparedLineKeys);
        startupReadyPreparedLineKeys = Set.copyOf(startupReadyPreparedLineKeys);
        inboundToteManifests = List.copyOf(inboundToteManifests);
        if (retainedInputLines.stream().anyMatch(line -> line == null)) {
            throw new IllegalArgumentException("retainedInputLines must not contain null");
        }
        retainedInputLines = List.copyOf(retainedInputLines);
    }

    private static List<DspRetainedInputLine> fixtureRetainedInputLines(
            List<NotionalToteOrder> orders) {
        if (orders == null) {
            return List.of();
        }

        List<DspRetainedInputLine> retainedInputLines = new ArrayList<>();
        for (int sourceMessageEncounterIndex = 0;
                sourceMessageEncounterIndex < orders.size();
                sourceMessageEncounterIndex++) {
            NotionalToteOrder order = orders.get(sourceMessageEncounterIndex);
            if (order == null) {
                continue;
            }
            for (int sourceLineIndex = 0; sourceLineIndex < order.items().size(); sourceLineIndex++) {
                DspOrderItem orderItem = order.items().get(sourceLineIndex);
                if (orderItem != null && orderItem.lineType() != DspOrderLineType.MANUAL) {
                    retainedInputLines.add(new DspRetainedInputLine(
                            orderItem,
                            order.orderSheetKey(),
                            order.orderType(),
                            sourceMessageEncounterIndex,
                            sourceLineIndex));
                }
            }
        }
        return List.copyOf(retainedInputLines);
    }
}
