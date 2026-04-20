package online.davisfamily.warehouse.testing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import online.davisfamily.threedee.debug.SelectionInspectionRegistry;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.rendering.appearance.OneColourStrategyImpl;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.rendering.model.tracks.RollerMeshFactory;
import online.davisfamily.warehouse.rendering.model.tracks.ConveyorRuntimeState;
import online.davisfamily.warehouse.rendering.model.tracks.TrackAppearance;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperEntryModule;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperEntryModuleBuilder;
import online.davisfamily.warehouse.sim.totebag.assignment.ToteToBagAssignmentPlanner;
import online.davisfamily.warehouse.sim.totebag.control.ToteToBagFlowController;
import online.davisfamily.warehouse.sim.totebag.conveyor.PdcConveyor;
import online.davisfamily.warehouse.sim.totebag.conveyor.PcrConveyor;
import online.davisfamily.warehouse.sim.totebag.conveyor.PrlConveyor;
import online.davisfamily.warehouse.sim.totebag.device.PdcDiversionDevice;
import online.davisfamily.warehouse.sim.totebag.device.PdcDiversionDeviceState;
import online.davisfamily.warehouse.sim.totebag.machine.BaggingMachine;
import online.davisfamily.warehouse.sim.totebag.machine.CompletedBag;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;
import online.davisfamily.warehouse.sim.totebag.plan.BagSpec;
import online.davisfamily.warehouse.sim.totebag.transfer.PdcTransfer;
import online.davisfamily.warehouse.sim.totebag.transfer.PrlToPcrTransfer;
import online.davisfamily.warehouse.sim.totebag.assembly.ToteToBagSubsystem;
import online.davisfamily.warehouse.sim.totebag.assembly.ToteToBagSubsystemBuilder;
import online.davisfamily.warehouse.sim.totebag.handoff.PackHandoffPoint;
import online.davisfamily.warehouse.sim.totebag.handoff.PackReceiveTarget;
import online.davisfamily.warehouse.sim.totebag.layout.ToteToBagCoreLayoutSpec;

public class ToteToBagDebugRig {
    private static final float BUMPER_REST_Z = 0.27f;
    private static final float BUMPER_ACTIVE_Z = 0.18f;

    private final List<RenderableObject> objects;
    private final SelectionInspectionRegistry inspectionRegistry;
    private final TriangleRenderer tr;
    private final ToteToBagSubsystem subsystem;
    private final ToteToBagCoreLayoutSpec layoutSpec;
    private final TipperEntryModule tipperEntryModule;
    private final PackHandoffPoint sorterOutfeedPoint;

    private final PdcConveyor pdcConveyor;
    private final PcrConveyor pcrConveyor;
    private final BaggingMachine baggingMachine;
    private final List<PrlConveyor> prls;
    private final List<PdcDiversionDevice> pdcDiversionDevices;
    private final ToteToBagFlowController flowController;

    private final RenderableObject pdcRenderable;
    private final RenderableObject pcrRenderable;
    private final RenderableObject baggerRenderable;
    private final Map<String, RenderableObject> prlRenderablesById = new LinkedHashMap<>();
    private final Map<String, RenderableObject> pdcBumperRenderablesByPrlId = new LinkedHashMap<>();
    private final Map<String, RenderableObject> completedBagRenderablesById = new LinkedHashMap<>();
    private ConveyorRuntimeState pdcRuntimeState;
    private ConveyorRuntimeState pcrRuntimeState;
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
        layoutSpec = ToteToBagCoreLayoutSpec.integratedDebugDefaults();
        subsystem = new ToteToBagSubsystemBuilder().buildCore(tr, conveyorAppearance, layoutSpec);

        pdcConveyor = subsystem.getPdcConveyor();
        prls = subsystem.getPrls();
        pdcDiversionDevices = subsystem.getPdcDiversionDevices();
        pcrConveyor = subsystem.getPcrConveyor();
        baggingMachine = new BaggingMachine("bagger", new BagSpec(0.34f, 0.28f, 0.22f), 0.35d, 0.25d, 0.30d, 0.25d);
        tipperEntryModule = new TipperEntryModuleBuilder().build(
                tr,
                sim,
                objects,
                inspectionRegistry,
                subsystem.getLayout().resolveTipperEntryLayoutSpec(),
                new PackReceiveTarget() {
                    @Override
                    public PackHandoffPoint handoffPoint() {
                        return sorterOutfeedPoint;
                    }

                    @Override
                    public boolean canAccept(Pack pack) {
                        return pdcConveyor.canAcceptIncomingPackAtFrontDistance(pack, sorterOutfeedFrontDistance(pack));
                    }

                    @Override
                    public void accept(Pack pack) {
                        pdcConveyor.acceptIncomingPackAtFrontDistance(pack, sorterOutfeedFrontDistance(pack));
                    }
                });
        sorterOutfeedPoint = tipperEntryModule.getSortingModule().outfeedPoint();
        flowController = new ToteToBagFlowController(
                tipperEntryModule.getTipperModule().getToteLoadPlan(),
                pdcConveyor,
                pcrConveyor,
                baggingMachine,
                new ToteToBagAssignmentPlanner(),
                prls,
                pdcDiversionDevices,
                this::pdcTransferDurationFor,
                this::pdcDiversionFrontDistanceFor,
                this::prlToPcrTransferDurationFor,
                this::prlToPcrEntryFrontDistanceFor);

        sim.addSimObject(pcrConveyor);
        sim.addSimObject(baggingMachine);
        sim.addController(flowController);

        pdcRenderable = subsystem.getPdcRenderable();
        pcrRenderable = subsystem.getPcrRenderable();
        baggerRenderable = createBox("bagging_machine", 0f, 0f, 0f, 0.8f, 0.56f, 0.9f, 0xFF6F5E49);
        subsystem.attachRenderable(baggerRenderable, layoutSpec.baggerMount());

        objects.add(baggerRenderable);
        objects.addAll(subsystem.getCoreRenderables());
        prlRenderablesById.putAll(subsystem.getPrlRenderablesById());
        pdcBumperRenderablesByPrlId.putAll(subsystem.getPdcBumpersByPrlId());
        pdcRuntimeState = subsystem.getPdcRuntimeState();
        pcrRuntimeState = subsystem.getPcrRuntimeState();
        prlRuntimeStatesById.putAll(subsystem.getPrlRuntimeStatesById());
        registerBumperInspection();
        registerInspectableRoots();
    }

    public void syncVisuals() {
        syncConveyorRuntimeStates();
        tipperEntryModule.syncVisuals();

        for (var entry : flowController.getPdcLaneEntries()) {
            positionPackOnPdc(entry.pack(), entry.frontDistance());
        }

        int prlIndex = 0;
        for (PrlConveyor prl : prls) {
            float prlX = subsystem.getLayout().prlCenterX(prlIndex);
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
                positionPack(pack, 4.0f + (baggerPackIndex * 0.10f), 0.48f, subsystem.getLayout().pcrZ());
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
                bagRenderable.transformation.zTranslation = subsystem.getLayout().pcrZ();
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

    private void registerBumperInspection() {
        for (PrlConveyor prl : prls) {
            RenderableObject bumper = pdcBumperRenderablesByPrlId.get(prl.getId());
            if (bumper == null) {
                continue;
            }
            inspectionRegistry.register(bumper, () -> List.of(
                    "Type: PDC bumper",
                    "Target PRL: " + prl.getId(),
                    "State: " + diversionDeviceStateForPrl(prl.getId())));
        }
    }

    private void registerInspectableRoots() {
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

    private void positionPack(Pack pack, float x, float y, float z) {
        RenderableObject renderable = tipperEntryModule.getPackRenderable(pack.getId());
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
        float targetX = subsystem.getLayout().prlCenterX(prlIndex);
        float pdcStartX = subsystem.getLayout().pdcStartX();
        float startX = pdcStartX + transfer.getSourcePdcFrontDistance() - (transfer.getPack().getDimensions().length() * 0.5f);
        float x = (float) (startX + ((targetX - startX) * progress));
        float z = (float) (layoutSpec.pdcZ() + ((subsystem.getLayout().prlStartZ() - layoutSpec.pdcZ()) * progress));
        positionPack(transfer.getPack(), x, layoutSpec.packY(), z);
    }

    private void positionActivePrlToPcrTransfer(PrlToPcrTransfer transfer) {
        double progress = transfer.getProgress();
        int prlIndex = indexOfPrl(transfer.getSourcePrlId());
        float prlX = subsystem.getLayout().prlCenterX(prlIndex);
        float joinX = subsystem.getLayout().pcrStartX() + transfer.getTargetPcrFrontDistance() - (transfer.getPack().getDimensions().length() * 0.5f);
        float startZ = subsystem.getLayout().prlStartZ() - 1.8f + (transfer.getPack().getDimensions().length() * 0.5f);

        float x = (float) (prlX + ((joinX - prlX) * progress));
        float z = (float) (startZ + ((subsystem.getLayout().pcrZ() - startZ) * progress));
        positionPack(transfer.getPack(), x, layoutSpec.packY(), z);
    }

    private void syncBumperVisuals() {
        for (PrlConveyor prl : prls) {
            RenderableObject bumper = pdcBumperRenderablesByPrlId.get(prl.getId());
            if (bumper == null) {
                continue;
            }
            boolean active = diversionDeviceStateForPrl(prl.getId()) != PdcDiversionDeviceState.IDLE;
            bumper.transformation.zTranslation = active ? BUMPER_ACTIVE_Z : BUMPER_REST_Z;
            bumper.transformation.angleY = 0f;
            bumper.transformation.angleZ = (float) (Math.PI / 2.0);
        }
    }

    private PdcDiversionDeviceState diversionDeviceStateForPrl(String prlId) {
        return flowController.getPdcDiversionDevices().stream()
                .filter(device -> prlId.equals(device.getTargetPrlId()))
                .map(PdcDiversionDevice::getState)
                .findFirst()
                .orElse(PdcDiversionDeviceState.IDLE);
    }

    private int indexOfPrl(String prlId) {
        for (int i = 0; i < prls.size(); i++) {
            if (prls.get(i).getId().equals(prlId)) {
                return i;
            }
        }
        return 0;
    }

    private void positionPackOnPrl(Pack pack, float prlX, float frontDistance) {
        float z = subsystem.getLayout().prlStartZ() - frontDistance + (pack.getDimensions().length() * 0.5f);
        positionPack(pack, prlX, layoutSpec.packY(), z);
    }

    private void positionPackOnPcr(Pack pack, float frontDistance) {
        float x = subsystem.getLayout().pcrStartX() + frontDistance - (pack.getDimensions().length() * 0.5f);
        positionPack(pack, x, layoutSpec.packY(), subsystem.getLayout().pcrZ());
    }

    private void positionPackOnPdc(Pack pack, float frontDistance) {
        float x = subsystem.getLayout().pdcStartX() + frontDistance - (pack.getDimensions().length() * 0.5f);
        positionPack(pack, x, layoutSpec.packY(), layoutSpec.pdcZ());
    }

    private float sorterOutfeedFrontDistance(Pack pack) {
        float alongPdc = sorterOutfeedPoint.worldPosition().x - subsystem.getLayout().pdcStartX();
        return alongPdc + (pack.getDimensions().length() * 0.5f);
    }

    private double pdcTransferDurationFor(String prlId) {
        return subsystem.getLayout().pdcTransferDurationFor(indexOfPrl(prlId));
    }

    private float pdcDiversionFrontDistanceFor(String prlId, Pack pack) {
        return subsystem.getLayout().pdcDiversionFrontDistanceFor(indexOfPrl(prlId), pack);
    }

    private double prlToPcrTransferDurationFor(String prlId) {
        return subsystem.getLayout().prlToPcrTransferDurationFor(indexOfPrl(prlId));
    }

    private float prlToPcrEntryFrontDistanceFor(String prlId, Pack pack) {
        return subsystem.getLayout().prlToPcrEntryFrontDistanceFor(indexOfPrl(prlId), pack);
    }
}
