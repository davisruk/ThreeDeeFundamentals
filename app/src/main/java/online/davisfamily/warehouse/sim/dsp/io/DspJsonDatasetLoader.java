package online.davisfamily.warehouse.sim.dsp.io;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderValidator;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

public class DspJsonDatasetLoader {
    private final ProductMasterCsvLoader productMasterCsvLoader;
    private final TwelveNMessageKindMapper messageKindMapper;
    private final TwelveNOrderMapper orderMapper;
    private final TwelveNPreparedLineMapper preparedLineMapper;
    private final DspOrderValidator orderValidator;

    public DspJsonDatasetLoader(
            ProductMasterCsvLoader productMasterCsvLoader,
            TwelveNMessageKindMapper messageKindMapper,
            TwelveNOrderMapper orderMapper,
            TwelveNPreparedLineMapper preparedLineMapper,
            DspOrderValidator orderValidator) {
        if (productMasterCsvLoader == null) {
            throw new IllegalArgumentException("productMasterCsvLoader must not be null");
        }
        if (messageKindMapper == null) {
            throw new IllegalArgumentException("messageKindMapper must not be null");
        }
        if (orderMapper == null) {
            throw new IllegalArgumentException("orderMapper must not be null");
        }
        if (preparedLineMapper == null) {
            throw new IllegalArgumentException("preparedLineMapper must not be null");
        }
        if (orderValidator == null) {
            throw new IllegalArgumentException("orderValidator must not be null");
        }
        this.productMasterCsvLoader = productMasterCsvLoader;
        this.messageKindMapper = messageKindMapper;
        this.orderMapper = orderMapper;
        this.preparedLineMapper = preparedLineMapper;
        this.orderValidator = orderValidator;
    }

    public LoadedDspData load(Path productMasterPath, List<Path> twelveNPaths) {
        if (productMasterPath == null) {
            throw new IllegalArgumentException("productMasterPath must not be null");
        }
        if (twelveNPaths == null) {
            throw new IllegalArgumentException("twelveNPaths must not be null");
        }

        List<TwelveNMessageJson> messages = new ArrayList<>();
        for (Path twelveNPath : List.copyOf(twelveNPaths)) {
            if (twelveNPath == null) {
                throw new IllegalArgumentException("twelveNPaths must not contain null");
            }
            messages.add(JsonLoaderSupport.read(twelveNPath, TwelveNMessageJson.class));
        }

        return load(productMasterCsvLoader.load(productMasterPath), messages);
    }

    public LoadedDspData load(List<ProductMasterRecord> products, List<TwelveNMessageJson> messages) {
        if (products == null) {
            throw new IllegalArgumentException("products must not be null");
        }
        if (messages == null) {
            throw new IllegalArgumentException("messages must not be null");
        }

        List<NotionalToteOrder> dispatchOrders = new ArrayList<>();
        List<DspOrderItem> preparedLines = new ArrayList<>();
        Set<PreparedLineKey> loadedPreparedLineKeys = new LinkedHashSet<>();
        long dispatchSequenceNumber = 0L;

        for (TwelveNMessageJson message : List.copyOf(messages)) {
            if (message == null) {
                throw new IllegalArgumentException("messages must not contain null");
            }

            TwelveNMessageKind messageKind = messageKindMapper.map(message.toteIdentifier().payload());
            switch (messageKind) {
                case EMPTY_DISPATCH, ASSOCIATED_DISPATCH, FULL_PACK_DISPATCH -> {
                    NotionalToteOrder dispatchOrder = orderMapper.toDispatchOrder(message, dispatchSequenceNumber);
                    orderValidator.validateForScheduler(dispatchOrder);
                    dispatchOrders.add(dispatchOrder);
                    dispatchSequenceNumber++;
                }
                case MANUAL_PREPARATION, ADAPTED_PREPARATION -> {
                    List<DspOrderItem> lines = preparedLineMapper.toPreparedLines(message);
                    preparedLines.addAll(lines);
                    for (DspOrderItem line : lines) {
                        loadedPreparedLineKeys.add(PreparedLineKey.forPreparedLine(line));
                    }
                }
            }
        }

        return new LoadedDspData(products, dispatchOrders, preparedLines, loadedPreparedLineKeys);
    }
}
