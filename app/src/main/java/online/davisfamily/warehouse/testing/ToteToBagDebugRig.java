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
import online.davisfamily.warehouse.sim.totebag.BaggingMachine;
import online.davisfamily.warehouse.sim.totebag.BagSpec;
import online.davisfamily.warehouse.sim.totebag.CompletedBag;
import online.davisfamily.warehouse.sim.totebag.ConveyorOccupancyModel;
import online.davisfamily.warehouse.sim.totebag.Pack;
import online.davisfamily.warehouse.sim.totebag.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.PackPlan;
import online.davisfamily.warehouse.sim.totebag.PcrConveyor;
import online.davisfamily.warehouse.sim.totebag.PrlConveyor;
import online.davisfamily.warehouse.sim.totebag.ReleasedPackGroup;
import online.davisfamily.warehouse.sim.totebag.SortingMachine;
import online.davisfamily.warehouse.sim.totebag.TippingMachine;
import online.davisfamily.warehouse.sim.totebag.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.ToteToBagAssignmentPlanner;
import online.davisfamily.warehouse.sim.totebag.ToteToBagFlowController;

public class ToteToBagDebugRig {
    private final List<RenderableObject> objects;
    private final SelectionInspectionRegistry inspectionRegistry;
    private final TriangleRenderer tr;

    private final TippingMachine tippingMachine;
    private final SortingMachine sortingMachine;
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
    private final Map<String, RenderableObject> packRenderablesById = new LinkedHashMap<>();
    private final Map<String, RenderableObject> completedBagRenderablesById = new LinkedHashMap<>();

    public ToteToBagDebugRig(
            TriangleRenderer tr,
            SimulationWorld sim,
            List<RenderableObject> objects,
            SelectionInspectionRegistry inspectionRegistry) {
        this.tr = tr;
        this.objects = objects;
        this.inspectionRegistry = inspectionRegistry;

        ToteLoadPlan toteLoadPlan = createDemoPlan();
        tippingMachine = new TippingMachine("tipper", 0.5d, 0.2d, 0.3d);
        sortingMachine = new SortingMachine("sorter", 0.15d);
        prls = List.of(
                new PrlConveyor("prl-1", 0.20f, new ConveyorOccupancyModel(1.8f, 0.06f, 0f)),
                new PrlConveyor("prl-2", 0.20f, new ConveyorOccupancyModel(1.8f, 0.06f, 0f)),
                new PrlConveyor("prl-3", 0.20f, new ConveyorOccupancyModel(1.8f, 0.06f, 0f)));
        pcrConveyor = new PcrConveyor("pcr", new ConveyorOccupancyModel(4.5f, 0.06f, 0.15f), 1.0d);
        baggingMachine = new BaggingMachine("bagger", new BagSpec(0.34f, 0.28f, 0.22f), 0.35d, 0.25d, 0.30d, 0.25d);
        flowController = new ToteToBagFlowController(
                toteLoadPlan,
                tippingMachine,
                sortingMachine,
                pcrConveyor,
                baggingMachine,
                new ToteToBagAssignmentPlanner(),
                prls);

        sim.addSimObject(tippingMachine);
        sim.addSimObject(sortingMachine);
        sim.addSimObject(pcrConveyor);
        sim.addSimObject(baggingMachine);
        sim.addController(flowController);

        tipperRenderable = createBox("tipping_machine", -5.3f, 0.25f, 0.2f, 0.7f, 0.5f, 0.8f, 0xFF8A5A44);
        sorterRenderable = createBox("sorting_machine", -3.6f, 0.18f, 0.2f, 1.0f, 0.36f, 0.9f, 0xFF5B6E7A);
        pdcRenderable = createBox("pdc", -0.9f, 0.05f, 0.2f, 4.6f, 0.10f, 0.45f, 0xFF4B5A45);
        pcrRenderable = createBox("pcr", -0.9f, 0.05f, -2.9f, 4.6f, 0.10f, 0.45f, 0xFF46555F);
        baggerRenderable = createBox("bagging_machine", 4.0f, 0.28f, -2.9f, 0.8f, 0.56f, 0.9f, 0xFF6F5E49);

        objects.add(tipperRenderable);
        objects.add(sorterRenderable);
        objects.add(pdcRenderable);
        objects.add(pcrRenderable);
        objects.add(baggerRenderable);

        createPrlRenderables();
        registerInspectableRoots();
    }

    public void syncVisuals() {
        ensurePackRenderablesExist();

        int sorterIndex = 0;
        for (Pack pack : sortingMachine.getQueuedPacks()) {
            positionPack(pack, -3.7f + (sorterIndex * 0.18f), 0.34f, 0.2f);
            sorterIndex++;
        }

        int prlIndex = 0;
        for (PrlConveyor prl : prls) {
            int packIndex = 0;
            float prlX = -2.2f + (prlIndex * 1.45f);
            for (Pack pack : prl.getPacks()) {
                positionPack(pack, prlX, 0.18f, -0.45f - (packIndex * 0.36f));
                packIndex++;
            }
            prlIndex++;
        }

        int pcrPackIndex = 0;
        for (ReleasedPackGroup group : pcrConveyor.getTravellingGroups()) {
            for (Pack pack : group.packs()) {
                positionPack(pack, -2.5f + (pcrPackIndex * 0.42f), 0.18f, -2.9f);
                pcrPackIndex++;
            }
        }
        for (ReleasedPackGroup group : pcrConveyor.getReadyGroups()) {
            for (Pack pack : group.packs()) {
                positionPack(pack, -2.5f + (pcrPackIndex * 0.42f), 0.18f, -2.9f);
                pcrPackIndex++;
            }
        }

        if (baggingMachine.getCurrentGroup() != null) {
            int baggerPackIndex = 0;
            for (Pack pack : baggingMachine.getCurrentGroup().packs()) {
                positionPack(pack, 4.0f + (baggerPackIndex * 0.10f), 0.48f, -2.9f);
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
                bagRenderable.transformation.zTranslation = -2.9f;
            }
            completedIndex++;
        }

        for (Pack pack : flowController.getObservedPacks()) {
            if (pack.getState() == Pack.PackMotionState.CONSUMED) {
                positionPack(pack, -50f, -50f, -50f);
            }
        }

        for (Pack pack : flowController.getObservedPacks()) {
            if (pack.getState() == Pack.PackMotionState.MOVING && !isPackPositionedOnPcr(pack)) {
                positionPack(pack, -2.0f, 0.16f, 0.2f);
            }
        }
    }

    private void createPrlRenderables() {
        for (int i = 0; i < prls.size(); i++) {
            float x = -2.2f + (i * 1.45f);
            RenderableObject prlRenderable = createBox(
                    prls.get(i).getId(),
                    x,
                    0.05f,
                    -1.25f,
                    0.45f,
                    0.10f,
                    2.4f,
                    0xFF4C5C52);
            prlRenderablesById.put(prls.get(i).getId(), prlRenderable);
            objects.add(prlRenderable);
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
                "Flow packs observed: " + flowController.getObservedPacks().size(),
                "PRLs assigned: " + prls.size()));

        inspectionRegistry.register(pcrRenderable, () -> List.of(
                "Type: PCR",
                String.format("Occupied / usable length: %.2f / %.2f",
                        pcrConveyor.getOccupiedLength(),
                        pcrConveyor.getUsableLength()),
                "Travelling groups: " + pcrConveyor.getTravellingGroups().size(),
                "Ready groups: " + pcrConveyor.getReadyGroups().size()));

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
                        new PackPlan("pack-b1", "bag-b", new PackDimensions(0.24f, 0.12f, 0.10f)),
                        new PackPlan("pack-c1", "bag-c", new PackDimensions(0.16f, 0.10f, 0.08f)),
                        new PackPlan("pack-a2", "bag-a", new PackDimensions(0.20f, 0.12f, 0.10f)),
                        new PackPlan("pack-b2", "bag-b", new PackDimensions(0.22f, 0.12f, 0.10f)),
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

    private void positionPack(Pack pack, float x, float y, float z) {
        RenderableObject renderable = packRenderablesById.get(pack.getId());
        if (renderable == null) {
            return;
        }
        renderable.transformation.xTranslation = x;
        renderable.transformation.yTranslation = y;
        renderable.transformation.zTranslation = z;
    }

    private boolean isPackPositionedOnPcr(Pack pack) {
        for (ReleasedPackGroup group : pcrConveyor.getTravellingGroups()) {
            if (group.packs().contains(pack)) {
                return true;
            }
        }
        for (ReleasedPackGroup group : pcrConveyor.getReadyGroups()) {
            if (group.packs().contains(pack)) {
                return true;
            }
        }
        return baggingMachine.getCurrentGroup() != null && baggingMachine.getCurrentGroup().packs().contains(pack);
    }

    private int colourForCorrelation(String correlationId) {
        return switch (correlationId) {
            case "bag-a" -> 0xFFE67E22;
            case "bag-b" -> 0xFF4AA3DF;
            case "bag-c" -> 0xFF7ABF66;
            default -> 0xFFBBBBBB;
        };
    }
}
