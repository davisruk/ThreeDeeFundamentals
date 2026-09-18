package online.davisfamily.warehouse.sim.dsp.analysis;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.DeterministicBagPlanner;
import online.davisfamily.warehouse.sim.dsp.bagging.MaximumPackCountBagCapacityPolicy;
import online.davisfamily.warehouse.sim.dsp.io.DspDatasetAssembler;
import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.io.ProductMasterCsvLoader;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNDatasetLoader;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNMessageKindMapper;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNOrderMapper;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderValidator;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.schedule.DspServiceCentreTimetable;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreSchedule;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

/** Loads and validates the logical full-day input without creating runtime or render objects. */
public final class DspFullDayInputLoader {
    private final ProductMasterCsvLoader productMasterLoader;
    private final TwelveNDatasetLoader twelveNDatasetLoader;
    private final DspDatasetAssembler datasetAssembler;

    public DspFullDayInputLoader() {
        TwelveNMessageKindMapper messageKindMapper = new TwelveNMessageKindMapper();
        this.productMasterLoader = new ProductMasterCsvLoader();
        this.twelveNDatasetLoader = new TwelveNDatasetLoader();
        this.datasetAssembler = new DspDatasetAssembler(
                messageKindMapper,
                new TwelveNOrderMapper(messageKindMapper),
                new DspOrderValidator());
    }

    public DspFullDayInputLoader(
            ProductMasterCsvLoader productMasterLoader,
            TwelveNDatasetLoader twelveNDatasetLoader,
            DspDatasetAssembler datasetAssembler) {
        if (productMasterLoader == null) {
            throw new IllegalArgumentException("productMasterLoader must not be null");
        }
        if (twelveNDatasetLoader == null) {
            throw new IllegalArgumentException("twelveNDatasetLoader must not be null");
        }
        if (datasetAssembler == null) {
            throw new IllegalArgumentException("datasetAssembler must not be null");
        }
        this.productMasterLoader = productMasterLoader;
        this.twelveNDatasetLoader = twelveNDatasetLoader;
        this.datasetAssembler = datasetAssembler;
    }

    public DspFullDayLoadedInput load(
            DspFullDayInputPaths inputPaths,
            DspUncalibratedFullDayProfile profile) {
        if (inputPaths == null) {
            throw new IllegalArgumentException("inputPaths must not be null");
        }
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }

        validateReadableRegularFile(inputPaths.productMasterCsvPath(), "productMasterCsvPath");
        for (Path path : inputPaths.twelveNJsonPaths()) {
            validateReadableRegularFile(path, "twelveNJsonPath");
        }

        List<ProductMasterRecord> products = productMasterLoader.load(inputPaths.productMasterCsvPath());
        List<online.davisfamily.warehouse.sim.dsp.io.TwelveNMessageJson> messages =
                twelveNDatasetLoader.load(inputPaths.twelveNJsonPaths());
        LoadedDspData loadedData = executableData(datasetAssembler.assemble(products, messages));
        validateLoadedData(loadedData, profile.timetable());
        BagPlanningResult bagPlanningResult = new DeterministicBagPlanner(
                new MaximumPackCountBagCapacityPolicy(profile.maximumPacksPerBag()))
                        .plan(new DspFullDayBagPlanningRequestFactory().create(loadedData));
        DspDatasetLoadReport report = loadedData.report();
        return new DspFullDayLoadedInput(
                loadedData,
                bagPlanningResult,
                report,
                profile.timetable());
    }

    private static LoadedDspData executableData(LoadedDspData assembledData) {
        if (assembledData == null) {
            throw new IllegalArgumentException("assembledData must not be null");
        }

        Set<String> knownProductIds = new LinkedHashSet<>();
        for (ProductMasterRecord product : assembledData.products()) {
            knownProductIds.add(product.productId());
        }

        List<NotionalToteOrder> executableOrders = new ArrayList<>();
        for (NotionalToteOrder order : assembledData.orders()) {
            List<DspOrderItem> retainedItems = knownItems(order.items(), knownProductIds);
            if (!retainedItems.isEmpty()) {
                executableOrders.add(retainedItems.size() == order.items().size()
                        ? order
                        : withItems(order, retainedItems));
            }
        }

        List<DspOrderItem> executablePreparedLines = knownItems(
                assembledData.preparedLines(), knownProductIds);
        Set<PreparedLineKey> executablePreparedLineKeys = new LinkedHashSet<>();
        for (DspOrderItem preparedLine : executablePreparedLines) {
            executablePreparedLineKeys.add(PreparedLineKey.forPreparedLine(preparedLine));
        }
        Set<PreparedLineKey> executableStartupReadyKeys = new LinkedHashSet<>();
        for (PreparedLineKey startupReadyKey : assembledData.startupReadyPreparedLineKeys()) {
            if (executablePreparedLineKeys.contains(startupReadyKey)) {
                executableStartupReadyKeys.add(startupReadyKey);
            }
        }

        List<InboundToteManifest> executableManifests = new ArrayList<>();
        for (InboundToteManifest manifest : assembledData.inboundToteManifests()) {
            List<DspOrderItem> retainedItems = knownItems(manifest.items(), knownProductIds);
            if (!retainedItems.isEmpty()) {
                executableManifests.add(retainedItems.size() == manifest.items().size()
                        ? manifest
                        : manifest.withItems(retainedItems));
            }
        }

        return new LoadedDspData(
                assembledData.products(),
                executableOrders,
                executablePreparedLines,
                executablePreparedLineKeys,
                executableStartupReadyKeys,
                executableManifests,
                assembledData.report());
    }

    private static List<DspOrderItem> knownItems(
            List<DspOrderItem> items,
            Set<String> knownProductIds) {
        return items.stream()
                .filter(item -> knownProductIds.contains(item.productId()))
                .toList();
    }

    private static NotionalToteOrder withItems(
            NotionalToteOrder order,
            List<DspOrderItem> items) {
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

    public DspFullDayLoadedInput load(
            DspUncalibratedFullDayProfile profile,
            DspFullDayInputPaths inputPaths) {
        return load(inputPaths, profile);
    }

    public DspFullDayLoadedInput load(
            Path productMasterCsvPath,
            List<Path> twelveNJsonPaths,
            DspUncalibratedFullDayProfile profile) {
        return load(new DspFullDayInputPaths(productMasterCsvPath, twelveNJsonPaths), profile);
    }

    private static void validateLoadedData(
            LoadedDspData data,
            DspServiceCentreTimetable timetable) {
        if (data == null) {
            throw new IllegalArgumentException("dataset assembler returned null");
        }
        if (data.orders().isEmpty()) {
            throw new IllegalArgumentException("input contains no retained simulated work");
        }
        if (timetable == null) {
            throw new IllegalArgumentException("timetable must not be null");
        }
        Map<OrderSheetKey, NotionalToteOrder> ordersBySheet = new LinkedHashMap<>();
        for (var order : data.orders()) {
            if (ordersBySheet.putIfAbsent(order.orderSheetKey(), order) != null) {
                throw new IllegalArgumentException(
                        "Duplicate logical order sheet: " + order.orderSheetKey());
            }
            ServiceCentreSchedule schedule = timetable.find(order.serviceCentreId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No timetable entry for loaded service centre " + order.serviceCentreId()));
            if (schedule.priority() != order.orderPriority()) {
                throw new IllegalArgumentException(
                        "Timetable priority does not match loaded service centre "
                                + order.serviceCentreId() + ": " + schedule.priority()
                                + " vs " + order.orderPriority());
            }
        }
        for (InboundToteManifest manifest : data.inboundToteManifests()) {
            ServiceCentreSchedule schedule = timetable.find(manifest.serviceCentreId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No timetable entry for loaded service centre " + manifest.serviceCentreId()));
            NotionalToteOrder order = ordersBySheet.get(manifest.orderSheetKey());
            if (order == null) {
                throw new IllegalArgumentException(
                        "Manifest has no matching logical order sheet: "
                                + manifest.orderSheetKey());
            }
            if (schedule.priority() != order.orderPriority()) {
                throw new IllegalArgumentException(
                        "Manifest timetable priority does not match loaded service centre "
                                + manifest.serviceCentreId());
            }
        }
    }

    private static void validateReadableRegularFile(Path path, String fieldName) {
        if (path == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException(fieldName + " must be an existing regular file: " + path);
        }
    }
}
