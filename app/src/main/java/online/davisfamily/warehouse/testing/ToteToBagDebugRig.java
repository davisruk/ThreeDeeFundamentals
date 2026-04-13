package online.davisfamily.warehouse.testing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import online.davisfamily.threedee.debug.SelectionInspectionRegistry;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.rendering.appearance.OneColourStrategyImpl;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.rendering.model.tracks.RollerMeshFactory;
import online.davisfamily.warehouse.rendering.model.tracks.ConveyorRuntimeState;
import online.davisfamily.warehouse.rendering.model.tracks.StraightConveyorFactory;
import online.davisfamily.warehouse.rendering.model.tracks.StraightConveyorFactory.ConveyorVisualSpeed;
import online.davisfamily.warehouse.rendering.model.tracks.StraightConveyorFactory.StraightConveyorSpec;
import online.davisfamily.warehouse.rendering.model.tracks.TrackAppearance;
import online.davisfamily.warehouse.sim.totebag.BaggingMachine;
import online.davisfamily.warehouse.sim.totebag.BagSpec;
import online.davisfamily.warehouse.sim.totebag.CompletedBag;
import online.davisfamily.warehouse.sim.totebag.ConveyorOccupancyModel;
import online.davisfamily.warehouse.sim.totebag.Pack;
import online.davisfamily.warehouse.sim.totebag.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.PackPlan;
import online.davisfamily.warehouse.sim.totebag.PdcConveyor;
import online.davisfamily.warehouse.sim.totebag.PdcTransfer;
import online.davisfamily.warehouse.sim.totebag.PcrConveyor;
import online.davisfamily.warehouse.sim.totebag.PrlConveyor;
import online.davisfamily.warehouse.sim.totebag.PrlToPcrTransfer;
import online.davisfamily.warehouse.sim.totebag.SortingMachine;
import online.davisfamily.warehouse.sim.totebag.TippingMachine;
import online.davisfamily.warehouse.sim.totebag.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.ToteToBagAssignmentPlanner;
import online.davisfamily.warehouse.sim.totebag.ToteToBagFlowController;

public class ToteToBagDebugRig {
    private static final float PRL_BELT_SPEED = 1.80f;
    private static final float PRL_INDEX_DISTANCE = 0.34f;
    private static final double SORTER_RELEASE_INTERVAL_SECONDS = 0.95d;
    private static final float PDC_BELT_SPEED = 1.55f;
    private static final float PDC_TRANSFER_SPEED = 1.55f;
    private static final float PDC_Z = 0.2f;
    private static final float PCR_Z = -2.9f;
    private static final float CONVEYOR_Y = 0.02f;
    private static final float PACK_Y = 0.16f;
    private static final float PRL_START_Z = -0.42f;
    private static final float SINGLE_PACK_CONVEYOR_WIDTH = 0.18f;
    private static final float PRL_LENGTH = 2.4f;
    private static final float MAIN_CONVEYOR_LENGTH = 4.6f;
    private static final double PCR_TRAVEL_DURATION_SECONDS = MAIN_CONVEYOR_LENGTH / PRL_BELT_SPEED;
    private static final float BUMPER_REST_Z = 0.13f;
    private static final float BUMPER_ACTIVE_Z = 0.04f;
    private static final float PRL_TO_PCR_TRANSFER_SPEED = MAIN_CONVEYOR_LENGTH / (float) PCR_TRAVEL_DURATION_SECONDS;

    private final List<RenderableObject> objects;
    private final SelectionInspectionRegistry inspectionRegistry;
    private final TriangleRenderer tr;

    private final TippingMachine tippingMachine;
    private final SortingMachine sortingMachine;
    private final PdcConveyor pdcConveyor;
    private final PcrConveyor pcrConveyor;
    private final BaggingMachine baggingMachine;
    private final List<PrlConveyor> prls;
    private final ToteToBagFlowController flowController;

    private final RenderableObject tipperRenderable;
    private final RenderableObject sorterRenderable;
    private final RenderableObject pdcRenderable;
    private final RenderableObject pcrRenderable;
    private final RenderableObject baggerRenderable;
    private final Map<String, RenderableObject> prlRenderablesById = new LinkedHashMap<>();
    private final Map<String, RenderableObject> pdcBumperRenderablesByPrlId = new LinkedHashMap<>();
    private final Map<String, RenderableObject> packRenderablesById = new LinkedHashMap<>();
    private final Map<String, RenderableObject> completedBagRenderablesById = new LinkedHashMap<>();
    private final ConveyorRuntimeState pdcRuntimeState = new ConveyorRuntimeState();
    private final ConveyorRuntimeState pcrRuntimeState = new ConveyorRuntimeState();
    private final Map<String, ConveyorRuntimeState> prlRuntimeStatesById = new LinkedHashMap<>();
    private final TrackAppearance conveyorAppearance;

    public ToteToBagDebugRig(
            TriangleRenderer tr,
            SimulationWorld sim,
            List<RenderableObject> objects,
            SelectionInspectionRegistry inspectionRegistry) {
        this.tr = tr;
        this.objects = objects;
        this.inspectionRegistry = inspectionRegistry;
        this.conveyorAppearance = new TrackAppearance(
                new OneColourStrategyImpl(0xFF596A54),
                new OneColourStrategyImpl(0xFF2A2A2A),
                new OneColourStrategyImpl(0xFF303030),
                new OneColourStrategyImpl(0xFFB8B8B8),
                new OneColourStrategyImpl(0xFF596A54),
                new OneColourStrategyImpl(0xFF596A54));
        pdcRuntimeState.setRunning(true);
        pcrRuntimeState.setRunning(false);

        ToteLoadPlan toteLoadPlan = createDemoPlan();
        tippingMachine = new TippingMachine("tipper", 0.5d, 0.2d, 0.3d);
        sortingMachine = new SortingMachine("sorter", SORTER_RELEASE_INTERVAL_SECONDS);
        pdcConveyor = new PdcConveyor("pdc", new ConveyorOccupancyModel(MAIN_CONVEYOR_LENGTH, 0.06f, 0f), PDC_BELT_SPEED);
        prls = List.of(
                new PrlConveyor("prl-1", PRL_INDEX_DISTANCE, new ConveyorOccupancyModel(1.8f, 0.06f, 0f), PRL_BELT_SPEED),
                new PrlConveyor("prl-2", PRL_INDEX_DISTANCE, new ConveyorOccupancyModel(1.8f, 0.06f, 0f), PRL_BELT_SPEED),
                new PrlConveyor("prl-3", PRL_INDEX_DISTANCE, new ConveyorOccupancyModel(1.8f, 0.06f, 0f), PRL_BELT_SPEED));
        pcrConveyor = new PcrConveyor("pcr", new ConveyorOccupancyModel(4.5f, 0.06f, 0.15f), PCR_TRAVEL_DURATION_SECONDS);
        baggingMachine = new BaggingMachine("bagger", new BagSpec(0.34f, 0.28f, 0.22f), 0.35d, 0.25d, 0.30d, 0.25d);
        flowController = new ToteToBagFlowController(
                toteLoadPlan,
                tippingMachine,
                sortingMachine,
                pdcConveyor,
                pcrConveyor,
                baggingMachine,
                new ToteToBagAssignmentPlanner(),
                prls,
                this::pdcTransferDurationFor,
                this::pdcDiversionFrontDistanceFor,
                this::prlToPcrTransferDurationFor,
                this::prlToPcrEntryFrontDistanceFor);

        sim.addSimObject(tippingMachine);
        sim.addSimObject(sortingMachine);
        sim.addSimObject(pcrConveyor);
        sim.addSimObject(baggingMachine);
        sim.addController(flowController);

        tipperRenderable = createBox("tipping_machine", -5.3f, 0.25f, 0.2f, 0.7f, 0.5f, 0.8f, 0xFF8A5A44);
        sorterRenderable = createBox("sorting_machine", -3.6f, 0.18f, 0.2f, 1.0f, 0.36f, 0.9f, 0xFF5B6E7A);
        pdcRenderable = createConveyor("pdc", -0.9f, CONVEYOR_Y, PDC_Z, MAIN_CONVEYOR_LENGTH, SINGLE_PACK_CONVEYOR_WIDTH, 0f, pdcRuntimeState);
        pcrRenderable = createConveyor("pcr", -0.9f, CONVEYOR_Y, PCR_Z, MAIN_CONVEYOR_LENGTH, SINGLE_PACK_CONVEYOR_WIDTH, 0f, pcrRuntimeState);
        baggerRenderable = createBox("bagging_machine", 4.0f, 0.28f, PCR_Z, 0.8f, 0.56f, 0.9f, 0xFF6F5E49);

        objects.add(tipperRenderable);
        objects.add(sorterRenderable);
        objects.add(pdcRenderable);
        objects.add(pcrRenderable);
        objects.add(baggerRenderable);

        createPrlRenderables();
        createPdcBumpers();
        registerInspectableRoots();
    }

    public void syncVisuals() {
        syncConveyorRuntimeStates();
        ensurePackRenderablesExist();

        int sorterIndex = 0;
        for (Pack pack : sortingMachine.getQueuedPacks()) {
            positionPack(pack, -3.7f + (sorterIndex * 0.18f), 0.28f, PDC_Z);
            sorterIndex++;
        }

        for (var entry : flowController.getPdcLaneEntries()) {
            positionPackOnPdc(entry.pack(), entry.frontDistance());
        }

        int prlIndex = 0;
        for (PrlConveyor prl : prls) {
            float prlX = prlCenterX(prlIndex);
            for (var entry : prl.getLaneEntries()) {
                positionPackOnPrl(entry.pack(), prlX, entry.frontDistance());
            }
            prlIndex++;
        }

        for (var entry : pcrConveyor.getLaneEntries()) {
            positionPackOnPcr(entry.pack(), entry.frontDistance());
        }

        if (baggingMachine.getCurrentGroup() != null) {
            int baggerPackIndex = 0;
            for (Pack pack : baggingMachine.getCurrentGroup().packs()) {
                positionPack(pack, 4.0f + (baggerPackIndex * 0.10f), 0.48f, PCR_Z);
                baggerPackIndex++;
            }
        }

        ensureCompletedBagRenderablesExist();
        int completedIndex = 0;
        for (CompletedBag completedBag : baggingMachine.getCompletedBags()) {
            RenderableObject bagRenderable = completedBagRenderablesById.get(completedBag.correlationId());
            if (bagRenderable != null) {
                bagRenderable.transformation.xTranslation = 5.6f + (completedIndex * 0.42f);
                bagRenderable.transformation.yTranslation = 0.16f;
                bagRenderable.transformation.zTranslation = PCR_Z;
            }
            completedIndex++;
        }

        for (Pack pack : flowController.getObservedPacks()) {
            if (pack.getState() == Pack.PackMotionState.CONSUMED) {
                positionPack(pack, -50f, -50f, -50f);
            }
        }

        for (PdcTransfer transfer : flowController.getActivePdcTransfers()) {
            positionActivePdcTransfer(transfer);
        }

        for (PrlToPcrTransfer transfer : flowController.getActivePrlToPcrTransfers()) {
            positionActivePrlToPcrTransfer(transfer);
        }

        for (Pack pack : flowController.getObservedPacks()) {
            if (pack.getState() == Pack.PackMotionState.STAGED) {
                positionPack(pack, -50f, -50f, -50f);
            }
        }

        syncBumperVisuals();
    }

    private void createPrlRenderables() {
        for (int i = 0; i < prls.size(); i++) {
            float x = prlCenterX(i);
            ConveyorRuntimeState runtimeState = new ConveyorRuntimeState();
            runtimeState.setRunning(false);
            prlRuntimeStatesById.put(prls.get(i).getId(), runtimeState);
            RenderableObject prlRenderable = createConveyor(
                    prls.get(i).getId(),
                    x,
                    CONVEYOR_Y,
                    -1.25f,
                    PRL_LENGTH,
                    SINGLE_PACK_CONVEYOR_WIDTH,
                    (float) (Math.PI / 2.0),
                    runtimeState);
            prlRenderablesById.put(prls.get(i).getId(), prlRenderable);
            objects.add(prlRenderable);
        }
    }

    private void createPdcBumpers() {
        for (int i = 0; i < prls.size(); i++) {
            PrlConveyor prl = prls.get(i);
            float x = prlCenterX(i);
            RenderableObject bumper = createBox(
                    "pdc_bumper_" + prl.getId(),
                    x,
                    0.12f,
                    BUMPER_REST_Z,
                    0.10f,
                    0.14f,
                    0.08f,
                    0xFFCC8844);
            pdcBumperRenderablesByPrlId.put(prl.getId(), bumper);
            objects.add(bumper);
            inspectionRegistry.register(bumper, () -> List.of(
                    "Type: PDC bumper",
                    "Target PRL: " + prl.getId(),
                    "Active: " + hasActiveTransferForPrl(prl.getId())));
        }
    }

    private void ensurePackRenderablesExist() {
        for (Pack pack : flowController.getObservedPacks()) {
            packRenderablesById.computeIfAbsent(pack.getId(), ignored -> {
                RenderableObject renderable = createPackRenderable(pack);
                objects.add(renderable);
                inspectionRegistry.register(renderable, () -> List.of(
                        "Type: Pack",
                        "Id: " + pack.getId(),
                        "Correlation: " + pack.getCorrelationId(),
                        "State: " + pack.getState(),
                        String.format("Size L/W/H: %.2f / %.2f / %.2f",
                                pack.getDimensions().length(),
                                pack.getDimensions().width(),
                                pack.getDimensions().height())));
                return renderable;
            });
        }
    }

    private void registerInspectableRoots() {
        inspectionRegistry.register(tipperRenderable, () -> List.of(
                "Type: Tipping machine",
                "State: " + tippingMachine.getState(),
                "Active tote: " + String.valueOf(tippingMachine.getActiveToteId()),
                "Remaining packs: " + tippingMachine.getRemainingPackCount()));

        inspectionRegistry.register(sorterRenderable, () -> List.of(
                "Type: Sorting machine",
                "State: " + sortingMachine.getState(),
                "Queued packs: " + sortingMachine.getQueuedPacks().size()));

        inspectionRegistry.register(pdcRenderable, () -> List.of(
                "Type: PDC",
                "Running: " + pdcRuntimeState.isRunning(),
                "Lane packs: " + flowController.getPdcLaneEntries().size(),
                "PRLs assigned: " + prls.size(),
                "Active transfers: " + flowController.getActivePdcTransfers().size()));

        inspectionRegistry.register(pcrRenderable, () -> List.of(
                "Type: PCR",
                "Running: " + pcrRuntimeState.isRunning(),
                String.format("Occupied / usable length: %.2f / %.2f",
                        pcrConveyor.getOccupiedLength(),
                        pcrConveyor.getUsableLength()),
                "Travelling groups: " + pcrConveyor.getTravellingGroups().size(),
                "Ready groups: " + pcrConveyor.getReadyGroups().size(),
                "Incoming transfers: " + flowController.getActivePrlToPcrTransfers().size()));

        inspectionRegistry.register(baggerRenderable, () -> List.of(
                "Type: Bagging machine",
                "State: " + baggingMachine.getState(),
                "Current group: " + (baggingMachine.getCurrentGroup() == null
                        ? "None"
                        : baggingMachine.getCurrentGroup().correlationId()),
                "Completed bags: " + baggingMachine.getCompletedCorrelationIds()));

        for (PrlConveyor prl : prls) {
            RenderableObject prlRenderable = prlRenderablesById.get(prl.getId());
            inspectionRegistry.register(prlRenderable, () -> List.of(
                    "Type: PRL",
                    "Id: " + prl.getId(),
                    "Running: " + prlRuntimeStatesById.get(prl.getId()).isRunning(),
                    "State: " + prl.getAssignment().getState(),
                    "Correlation: " + String.valueOf(prl.getAssignment().getCorrelationId()),
                    "Expected / received: " + prl.getAssignment().getExpectedPackCount()
                            + " / " + prl.getAssignment().getReceivedPackCount(),
                    String.format("Indexed distance: %.2f", prl.getIndexedDistance())));
        }
    }

    private ToteLoadPlan createDemoPlan() {
        return new ToteLoadPlan(
                "tote-bag-demo-1",
                List.of(
                        new PackPlan("pack-a1", "bag-a", new PackDimensions(0.18f, 0.12f, 0.10f)),
                        new PackPlan("pack-c1", "bag-c", new PackDimensions(0.16f, 0.10f, 0.08f)),
                        new PackPlan("pack-b1", "bag-b", new PackDimensions(0.24f, 0.12f, 0.10f)),
                        new PackPlan("pack-b2", "bag-b", new PackDimensions(0.22f, 0.12f, 0.10f)),
                        new PackPlan("pack-a2", "bag-a", new PackDimensions(0.20f, 0.12f, 0.10f)),
                        new PackPlan("pack-c2", "bag-c", new PackDimensions(0.19f, 0.11f, 0.09f))));
    }

    private RenderableObject createPackRenderable(Pack pack) {
        PackDimensions dimensions = pack.getDimensions();
        return RenderableObject.create(
                pack.getId(),
                tr,
                RollerMeshFactory.createBoxRollerMesh(
                        dimensions.length(),
                        dimensions.height(),
                        dimensions.width()),
                new ObjectTransformation(0f, 0f, 0f, -50f, -50f, -50f, new Mat4()),
                new OneColourStrategyImpl(colourForCorrelation(pack.getCorrelationId())),
                true);
    }

    private void ensureCompletedBagRenderablesExist() {
        for (CompletedBag completedBag : baggingMachine.getCompletedBags()) {
            completedBagRenderablesById.computeIfAbsent(completedBag.correlationId(), ignored -> {
                RenderableObject renderable = RenderableObject.create(
                        "bag_" + completedBag.correlationId(),
                        tr,
                        RollerMeshFactory.createBoxRollerMesh(
                                completedBag.bagSpec().depth(),
                                completedBag.bagSpec().height(),
                                completedBag.bagSpec().width()),
                        new ObjectTransformation(0f, 0f, 0f, -50f, -50f, -50f, new Mat4()),
                        new OneColourStrategyImpl(0xFFD8C6A0),
                        true);
                objects.add(renderable);
                inspectionRegistry.register(renderable, () -> List.of(
                        "Type: Bag",
                        "Correlation: " + completedBag.correlationId(),
                        "Pack count: " + completedBag.packCount(),
                        String.format("Size W/H/D: %.2f / %.2f / %.2f",
                                completedBag.bagSpec().width(),
                                completedBag.bagSpec().height(),
                                completedBag.bagSpec().depth())));
                return renderable;
            });
        }
    }

    private RenderableObject createBox(
            String id,
            float x,
            float y,
            float z,
            float length,
            float height,
            float width,
            int colour) {
        return RenderableObject.create(
                id,
                tr,
                RollerMeshFactory.createBoxRollerMesh(length, height, width),
                new ObjectTransformation(0f, 0f, 0f, x, y, z, new Mat4()),
                new OneColourStrategyImpl(colour),
                true);
    }

    private RenderableObject createConveyor(
            String id,
            float x,
            float y,
            float z,
            float length,
            float width,
            float yawRadians,
            ConveyorRuntimeState runtimeState) {
        RenderableObject conveyor = StraightConveyorFactory.create(
                id,
                tr,
                new StraightConveyorSpec(
                        length,
                        width,
                        0.05f,
                        0.01f,
                        0.10f,
                        0.08f,
                        0.004f,
                        ConveyorVisualSpeed.fixed(0.8d)),
                runtimeState,
                conveyorAppearance);
        conveyor.transformation.xTranslation = x;
        conveyor.transformation.yTranslation = y;
        conveyor.transformation.zTranslation = z;
        conveyor.transformation.angleY = yawRadians;
        configureConveyorSelection(conveyor);
        return conveyor;
    }

    private void syncConveyorRuntimeStates() {
        pdcRuntimeState.setRunning(true);
        pcrRuntimeState.setRunning(pcrConveyor.isRunning() || baggingMachine.getCurrentGroup() != null);
        for (PrlConveyor prl : prls) {
            ConveyorRuntimeState runtimeState = prlRuntimeStatesById.get(prl.getId());
            if (runtimeState != null) {
                runtimeState.setRunning(prl.isRunning());
            }
        }
    }

    private void configureConveyorSelection(RenderableObject conveyorRoot) {
        for (RenderableObject child : conveyorRoot.children) {
            if (child.id.endsWith("_top_belt")) {
                child.setSelectable(true);
                child.setSelectionTarget(conveyorRoot);
                break;
            }
        }
    }

    private void positionPack(Pack pack, float x, float y, float z) {
        RenderableObject renderable = packRenderablesById.get(pack.getId());
        if (renderable == null) {
            return;
        }
        renderable.transformation.xTranslation = x;
        renderable.transformation.yTranslation = y;
        renderable.transformation.zTranslation = z;
    }

    private void positionActivePdcTransfer(PdcTransfer transfer) {
        double progress = transfer.getProgress();
        int prlIndex = indexOfPrl(transfer.getTargetPrlId());
        float targetX = prlCenterX(prlIndex);
        float pdcStartX = pdcStartX();
        float startX = pdcStartX + transfer.getSourcePdcFrontDistance() - (transfer.getPack().getDimensions().length() * 0.5f);
        float x = (float) (startX + ((targetX - startX) * progress));
        float z = (float) (PDC_Z + ((PRL_START_Z - PDC_Z) * progress));
        positionPack(transfer.getPack(), x, PACK_Y, z);
    }

    private void positionActivePrlToPcrTransfer(PrlToPcrTransfer transfer) {
        double progress = transfer.getProgress();
        int prlIndex = indexOfPrl(transfer.getSourcePrlId());
        float prlX = prlCenterX(prlIndex);
        float joinX = pdcStartX() + transfer.getTargetPcrFrontDistance() - (transfer.getPack().getDimensions().length() * 0.5f);
        float startZ = PRL_START_Z - 1.8f + (transfer.getPack().getDimensions().length() * 0.5f);

        float x = (float) (prlX + ((joinX - prlX) * progress));
        float z = (float) (startZ + ((PCR_Z - startZ) * progress));
        positionPack(transfer.getPack(), x, PACK_Y, z);
    }

    private void syncBumperVisuals() {
        for (PrlConveyor prl : prls) {
            RenderableObject bumper = pdcBumperRenderablesByPrlId.get(prl.getId());
            if (bumper == null) {
                continue;
            }
            boolean active = hasActiveTransferForPrl(prl.getId());
            bumper.transformation.zTranslation = active ? BUMPER_ACTIVE_Z : BUMPER_REST_Z;
            bumper.transformation.angleY = 0f;
        }
    }

    private boolean hasActiveTransferForPrl(String prlId) {
        return flowController.getActivePdcTransfers().stream()
                .anyMatch(transfer -> prlId.equals(transfer.getTargetPrlId()));
    }

    private int indexOfPrl(String prlId) {
        for (int i = 0; i < prls.size(); i++) {
            if (prls.get(i).getId().equals(prlId)) {
                return i;
            }
        }
        return 0;
    }

    private boolean isPackPositionedOnPcr(Pack pack) {
        for (var entry : pcrConveyor.getLaneEntries()) {
            if (entry.pack().equals(pack)) {
                return true;
            }
        }
        return baggingMachine.getCurrentGroup() != null && baggingMachine.getCurrentGroup().packs().contains(pack);
    }

    private void positionPackOnPrl(Pack pack, float prlX, float frontDistance) {
        float z = PRL_START_Z - frontDistance + (pack.getDimensions().length() * 0.5f);
        positionPack(pack, prlX, PACK_Y, z);
    }

    private void positionPackOnPcr(Pack pack, float frontDistance) {
        float x = pdcStartX() + frontDistance - (pack.getDimensions().length() * 0.5f);
        positionPack(pack, x, PACK_Y, PCR_Z);
    }

    private void positionPackOnPdc(Pack pack, float frontDistance) {
        float x = pdcStartX() + frontDistance - (pack.getDimensions().length() * 0.5f);
        positionPack(pack, x, PACK_Y, PDC_Z);
    }

    private int colourForCorrelation(String correlationId) {
        return switch (correlationId) {
            case "bag-a" -> 0xFFE67E22;
            case "bag-b" -> 0xFF4AA3DF;
            case "bag-c" -> 0xFF7ABF66;
            default -> 0xFFBBBBBB;
        };
    }

    private float prlCenterX(int index) {
        return -2.2f + (index * 1.45f);
    }

    private float pdcStartX() {
        return -0.9f - (MAIN_CONVEYOR_LENGTH * 0.5f);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private double pdcTransferDurationFor(String prlId) {
        int prlIndex = indexOfPrl(prlId);
        float targetX = prlCenterX(prlIndex);
        float startX = pdcStartX() + pdcDiversionFrontDistanceFor(prlId, new Pack("probe", "probe", new PackDimensions(0.18f, 0.1f, 0.1f)))
                - (0.18f * 0.5f);
        float dx = targetX - startX;
        float lateralLength = Math.abs(PRL_START_Z - PDC_Z);
        float travelLength = (float) Math.sqrt((dx * dx) + (lateralLength * lateralLength));
        return travelLength / PDC_TRANSFER_SPEED;
    }

    private float pdcDiversionFrontDistanceFor(String prlId, Pack pack) {
        int prlIndex = indexOfPrl(prlId);
        float bumperX = clamp(prlCenterX(prlIndex), pdcStartX() + 0.20f, pdcStartX() + MAIN_CONVEYOR_LENGTH - 0.20f);
        return (bumperX - pdcStartX()) + (pack.getDimensions().length() * 0.5f);
    }

    private double prlToPcrTransferDurationFor(String prlId) {
        int prlIndex = indexOfPrl(prlId);
        float prlX = prlCenterX(prlIndex);
        float joinX = pdcStartX() + prlToPcrEntryFrontDistanceFor(prlId, new Pack("probe", "probe", new PackDimensions(0.18f, 0.1f, 0.1f)))
                - (0.18f * 0.5f);
        float startZ = PRL_START_Z - 1.8f + (0.18f * 0.5f);
        float dx = joinX - prlX;
        float dz = PCR_Z - startZ;
        float travelLength = (float) Math.sqrt((dx * dx) + (dz * dz));
        return travelLength / PRL_TO_PCR_TRANSFER_SPEED;
    }

    private float prlToPcrEntryFrontDistanceFor(String prlId, Pack pack) {
        int prlIndex = indexOfPrl(prlId);
        float joinX = prlCenterX(prlIndex);
        return (joinX - pdcStartX()) + (pack.getDimensions().length() * 0.5f);
    }
}
