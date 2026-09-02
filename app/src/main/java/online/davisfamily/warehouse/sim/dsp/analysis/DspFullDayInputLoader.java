package online.davisfamily.warehouse.sim.dsp.analysis;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningRequest;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningTote;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.DeterministicBagPlanner;
import online.davisfamily.warehouse.sim.dsp.bagging.DspPackPlanFactory;
import online.davisfamily.warehouse.sim.dsp.bagging.MaximumPackCountBagCapacityPolicy;
import online.davisfamily.warehouse.sim.dsp.bagging.PackProvenanceRegistry;
import online.davisfamily.warehouse.sim.dsp.bagging.PackSourceProvenance;
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
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.schedule.DspServiceCentreTimetable;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreSchedule;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

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
        LoadedDspData loadedData = datasetAssembler.assemble(products, messages);
        validateLoadedData(loadedData, profile.timetable());
        BagPlanningResult bagPlanningResult = createBagPlan(loadedData, profile.maximumPacksPerBag());
        DspDatasetLoadReport report = loadedData.report();
        return new DspFullDayLoadedInput(
                loadedData,
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

    private BagPlanningResult createBagPlan(LoadedDspData data, int maximumPacksPerBag) {
        PackProvenanceRegistry provenanceRegistry = new PackProvenanceRegistry();
        DspPackPlanFactory packPlanFactory = new DspPackPlanFactory(provenanceRegistry);
        Map<String, ProductMasterRecord> productsById = indexProducts(data.products());
        List<BagPlanningTote> planningTotes = new ArrayList<>();
        Set<String> generatedPackIds = new LinkedHashSet<>();

        for (InboundToteManifest manifest : data.inboundToteManifests()) {
            List<PackPlan> packPlans = new ArrayList<>();
            for (DspOrderItem line : manifest.items()) {
                if (line.numberOfPacksPicked() > line.quantity()) {
                    throw new IllegalArgumentException(
                            "numberOfPacksPicked exceeds quantity for line " + line.lineReference());
                }
                if (line.numberOfPacksPicked() == 0) {
                    continue;
                }
                ProductMasterRecord product = productsById.get(line.productId());
                if (product == null) {
                    // DspDatasetAssembler has already retained this as an unresolved load issue.
                    // Do not invent a physical pack for it.
                    continue;
                }
                PackDimensions dimensions = product.dimensions().orElse(null);
                if (dimensions == null) {
                    // Missing dimensions are not a reason to fabricate a physical object or pack.
                    continue;
                }
                for (int ordinal = 1; ordinal <= line.numberOfPacksPicked(); ordinal++) {
                    String packId = packId(manifest, line, ordinal);
                    if (!generatedPackIds.add(packId)) {
                        throw new IllegalArgumentException("Duplicate generated physical pack ID: " + packId);
                    }
                    packPlans.add(packPlanFactory.createPackPlan(
                            packId,
                            line.lineReference(),
                            dimensions,
                            new PackSourceProvenance(
                                    manifest.orderSheetKey(),
                                    line.lineReference(),
                                    line.productId(),
                                    manifest.serviceCentreId(),
                                    line.pharmacyId(),
                                    line.patientId(),
                                    line.prescriptionId())));
                }
            }
            planningTotes.add(new BagPlanningTote(
                    manifest.orderSheetKey(),
                    manifest.serviceCentreId(),
                    new ToteLoadPlan(manifest.physicalToteId(), packPlans)));
        }

        if (planningTotes.isEmpty()) {
            return new BagPlanningResult(List.of(), List.of(), List.of());
        }
        BagPlanningRequest request = new BagPlanningRequest(planningTotes);
        return new DeterministicBagPlanner(
                new MaximumPackCountBagCapacityPolicy(maximumPacksPerBag),
                provenanceRegistry.snapshot()).plan(request);
    }

    private static Map<String, ProductMasterRecord> indexProducts(List<ProductMasterRecord> products) {
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
        for (var order : data.orders()) {
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
            var order = data.orders().stream()
                    .filter(candidate -> candidate.orderSheetKey().equals(manifest.orderSheetKey()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Manifest has no matching logical order sheet: " + manifest.orderSheetKey()));
            if (schedule.priority() != order.orderPriority()) {
                throw new IllegalArgumentException(
                        "Manifest timetable priority does not match loaded service centre "
                                + manifest.serviceCentreId());
            }
        }
    }

    private static String packId(InboundToteManifest manifest, DspOrderItem line, int ordinal) {
        return "pack-" + manifest.physicalToteId().value() + "-" + line.lineReference() + "-" + ordinal;
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
