package online.davisfamily.warehouse.sim.dsp.io;

import java.util.List;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

public record LoadedDspData(
        List<ProductMasterRecord> products,
        List<NotionalToteOrder> orders,
        List<DspOrderItem> preparedLines,
        Set<PreparedLineKey> loadedPreparedLineKeys,
        Set<PreparedLineKey> startupReadyPreparedLineKeys,
        DspDatasetLoadReport report) {

    public LoadedDspData(
            List<ProductMasterRecord> products,
            List<NotionalToteOrder> orders,
            List<DspOrderItem> preparedLines,
            Set<PreparedLineKey> loadedPreparedLineKeys) {
        this(products, orders, preparedLines, loadedPreparedLineKeys, Set.of(), DspDatasetLoadReport.empty());
    }

    public LoadedDspData(
            List<ProductMasterRecord> products,
            List<NotionalToteOrder> orders,
            List<DspOrderItem> preparedLines,
            Set<PreparedLineKey> loadedPreparedLineKeys,
            Set<PreparedLineKey> startupReadyPreparedLineKeys) {
        this(products, orders, preparedLines, loadedPreparedLineKeys, startupReadyPreparedLineKeys, DspDatasetLoadReport.empty());
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
        if (report == null) {
            throw new IllegalArgumentException("report must not be null");
        }
        products = List.copyOf(products);
        orders = List.copyOf(orders);
        preparedLines = List.copyOf(preparedLines);
        loadedPreparedLineKeys = Set.copyOf(loadedPreparedLineKeys);
        startupReadyPreparedLineKeys = Set.copyOf(startupReadyPreparedLineKeys);
    }
}
