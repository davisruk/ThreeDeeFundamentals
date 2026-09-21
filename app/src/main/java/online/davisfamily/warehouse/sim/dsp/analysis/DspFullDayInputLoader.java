package online.davisfamily.warehouse.sim.dsp.analysis;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.DeterministicBagPlanner;
import online.davisfamily.warehouse.sim.dsp.bagging.MaximumPackCountBagCapacityPolicy;
import online.davisfamily.warehouse.sim.dsp.analysis.input.DspFullDayInputPreflight;
import online.davisfamily.warehouse.sim.dsp.analysis.input.DspFullDayInputProjection;
import online.davisfamily.warehouse.sim.dsp.analysis.input.DspInputRejectionCatalog;
import online.davisfamily.warehouse.sim.dsp.io.DspDatasetAssembler;
import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.io.ProductMasterCsvLoader;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNDatasetLoader;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNLoadResult;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNMessageKindMapper;
import online.davisfamily.warehouse.sim.dsp.io.TwelveNOrderMapper;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderValidator;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.schedule.DspServiceCentreTimetable;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreSchedule;

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
        TwelveNLoadResult loadResult = twelveNDatasetLoader.loadRecovering(
                inputPaths.twelveNJsonPaths());
        LoadedDspData assembledData = datasetAssembler.assembleSourced(
                products,
                loadResult.messages());
        DspInputRejectionCatalog loadRejections = new DspInputRejectionCatalog(
                List.of(),
                loadResult.rejectedMessages());
        DspFullDayInputProjection projection = new DspFullDayInputPreflight().project(
                assembledData,
                loadRejections);
        LoadedDspData executableData = projection.executableData();
        validateLoadedData(executableData, profile.timetable());
        BagPlanningResult bagPlanningResult = new DeterministicBagPlanner(
                new MaximumPackCountBagCapacityPolicy(profile.maximumPacksPerBag()))
                        .plan(new DspFullDayBagPlanningRequestFactory().create(executableData));
        DspDatasetLoadReport report = executableData.report();
        return new DspFullDayLoadedInput(
                executableData,
                projection.reportableOrders(),
                projection.rejectionCatalog(),
                bagPlanningResult,
                report,
                profile.timetable());
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
