package online.davisfamily.warehouse.testing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.debug.SelectionInspectionRegistry;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.rendering.appearance.OneColourStrategyImpl;
import online.davisfamily.warehouse.rendering.model.tote.RenderableToteFactory;
import online.davisfamily.warehouse.rendering.model.tote.ToteGeometry;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.rendering.model.tracks.ConveyorRuntimeState;
import online.davisfamily.warehouse.rendering.model.tracks.TrackAppearance;
import online.davisfamily.warehouse.sim.totebag.assembly.BaggingModule;
import online.davisfamily.warehouse.sim.totebag.assembly.BaggingInstallation;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperToSorterSection;
import online.davisfamily.warehouse.sim.totebag.bag.Bag;
import online.davisfamily.warehouse.sim.totebag.bag.BagDischarge;
import online.davisfamily.warehouse.sim.totebag.bag.BagMeshFactory;
import online.davisfamily.warehouse.sim.totebag.control.ToteToBagFlowController;
import online.davisfamily.warehouse.sim.totebag.conveyor.PdcConveyor;
import online.davisfamily.warehouse.sim.totebag.conveyor.PcrConveyor;
import online.davisfamily.warehouse.sim.totebag.conveyor.PrlConveyor;
import online.davisfamily.warehouse.sim.totebag.device.PdcDiversionDevice;
import online.davisfamily.warehouse.sim.totebag.device.PdcDiversionDeviceState;
import online.davisfamily.warehouse.sim.totebag.handoff.StoredBagReceiver;
import online.davisfamily.warehouse.sim.totebag.handoff.ToteBagReceiver;
import online.davisfamily.warehouse.sim.totebag.machine.BaggingMachine;
import online.davisfamily.warehouse.sim.totebag.machine.CompletedBag;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;
import online.davisfamily.warehouse.sim.totebag.plan.BagSpec;
import online.davisfamily.warehouse.sim.totebag.transfer.PdcTransfer;
import online.davisfamily.warehouse.sim.totebag.transfer.PrlToPcrTransfer;
import online.davisfamily.warehouse.sim.totebag.assembly.ToteToBagSubsystem;
import online.davisfamily.warehouse.sim.totebag.layout.ToteToBagCoreLayoutSpec;
import online.davisfamily.warehouse.sim.tote.Tote;

public class ToteToBagDebugRig implements DebugSceneRuntime {
    private static final float BUMPER_REST_Z = 0.27f;
    private static final float BUMPER_ACTIVE_Z = 0.18f;
    private static final float BAG_RECEIVER_LENGTH = 0.60f;
    private static final float BAG_RECEIVER_GAP_X = 0.12f;
    private static final double BAG_RECEIVER_AUTO_EMPTY_SECONDS = 3.0d;

    private final List<RenderableObject> objects;
    private final SelectionInspectionRegistry inspectionRegistry;
    private final TriangleRenderer tr;
    private final ToteToBagSubsystem subsystem;
    private final ToteToBagCoreLayoutSpec layoutSpec;
    private final TipperToSorterSection tipperToSorterSection;

    private final PdcConveyor pdcConveyor;
    private final PcrConveyor pcrConveyor;
    private final BaggingMachine baggingMachine;
    private final BaggingModule baggingModule;
    private final StoredBagReceiver bagReceiver;
    private final List<PrlConveyor> prls;
    private final List<PdcDiversionDevice> pdcDiversionDevices;
    private final ToteToBagFlowController flowController;

    private final RenderableObject pdcRenderable;
    private final RenderableObject pcrRenderable;
    private final RenderableObject baggerRenderable;
    private final RenderableObject bagReceiverRenderable;
    private final Map<String, RenderableObject> prlRenderablesById = new LinkedHashMap<>();
    private final Map<String, RenderableObject> pdcBumperRenderablesByPrlId = new LinkedHashMap<>();
    private final Map<String, RenderableObject> completedBagRenderablesById = new LinkedHashMap<>();
    private final Map<String, Double> baggerIntakeElapsedSecondsByPackId = new LinkedHashMap<>();
    private ConveyorRuntimeState pdcRuntimeState;
    private ConveyorRuntimeState pcrRuntimeState;
    private final Map<String, ConveyorRuntimeState> prlRuntimeStatesById = new LinkedHashMap<>();
    private final TrackAppearance conveyorAppearance;
    private final DebugBagReceiverAutoEmptyController bagReceiverAutoEmptyController;

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
        layoutSpec = ToteToBagCoreLayoutSpec.fifteenPrlIntegratedDebugDefaults();
        IntegratedToteToBagDebugInstallation installation = new IntegratedToteToBagDebugInstaller().install(
                tr,
                sim,
                objects,
                inspectionRegistry,
                conveyorAppearance,
                layoutSpec);

        subsystem = installation.getSubsystem();
        pdcConveyor = subsystem.getPdcConveyor();
        prls = subsystem.getPrls();
        pdcDiversionDevices = subsystem.getPdcDiversionDevices();
        pcrConveyor = subsystem.getPcrConveyor();
        tipperToSorterSection = installation.getTipperToSorterSection();
        flowController = installation.getFlowController();
        BaggingInstallation baggingInstallation = installation.getBaggingInstallation();
        baggingMachine = baggingInstallation.getBaggingMachine();
        baggingModule = baggingInstallation.getBaggingModule();
        bagReceiver = baggingInstallation.getBagReceiver();
        bagReceiverAutoEmptyController = new DebugBagReceiverAutoEmptyController(
                bagReceiver,
                BAG_RECEIVER_AUTO_EMPTY_SECONDS);

        pdcRenderable = subsystem.getPdcRenderable();
        pcrRenderable = subsystem.getPcrRenderable();
        baggerRenderable = baggingModule.getRenderable();
        bagReceiverRenderable = createBagReceiverRenderable();

        objects.addAll(subsystem.getCoreRenderables());
        objects.add(bagReceiverRenderable);
        prlRenderablesById.putAll(subsystem.getPrlRenderablesById());
        pdcBumperRenderablesByPrlId.putAll(subsystem.getPdcBumpersByPrlId());
        pdcRuntimeState = subsystem.getPdcRuntimeState();
        pcrRuntimeState = subsystem.getPcrRuntimeState();
        prlRuntimeStatesById.putAll(subsystem.getPrlRuntimeStatesById());
        registerBumperInspection();
        registerInspectableRoots();
        registerBagReceiverInspection();
    }

    @Override
    public void syncVisuals() {
        syncVisuals(0d);
    }

    @Override
    public void syncVisuals(double dtSeconds) {
        updateDebugBagReceiverAutoEmpty(dtSeconds);
        syncConveyorRuntimeStates();
        tipperToSorterSection.syncVisuals();

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
            positionPacksOnBaggerIntake(dtSeconds);
        } else {
            baggerIntakeElapsedSecondsByPackId.clear();
        }

        positionActiveBagDischarge();

        ensureCompletedBagRenderablesExist();
        int completedIndex = 0;
        for (Bag completedBag : bagReceiver.getReceivedBags()) {
            RenderableObject bagRenderable = completedBagRenderablesById.get(completedBag.getCorrelationId());
            if (bagRenderable != null) {
                positionCompletedBagInReceiver(bagRenderable, completedBag, completedIndex);
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
            ensureBagRenderableExists(
                    completedBag.correlationId(),
                    completedBag.bagSpec(),
                    completedBag.packCount());
        }
    }

    private void registerBagReceiverInspection() {
        inspectionRegistry.register(bagReceiverRenderable, () -> List.of(
                "Type: Tote bag receiver",
                "Tote id: " + toteReceiverId(),
                "Received bags: " + bagReceiver.getReceivedBags().size(),
                "Capacity: " + bagReceiver.getCapacity(),
                String.format("Full timer: %.1f / %.1f",
                        bagReceiverAutoEmptyController.getFullElapsedSeconds(),
                        bagReceiverAutoEmptyController.getFullDurationSeconds()),
                "Correlations: " + bagReceiver.getCompletedCorrelationIds()));
    }

    private String toteReceiverId() {
        return bagReceiver instanceof ToteBagReceiver toteBagReceiver
                ? toteBagReceiver.getToteId()
                : bagReceiver.getId();
    }

    private RenderableObject ensureBagRenderableExists(String correlationId, BagSpec bagSpec, int packCount) {
        return completedBagRenderablesById.computeIfAbsent(correlationId, ignored -> {
            RenderableObject renderable = RenderableObject.create(
                    "bag_" + correlationId,
                    tr,
                    BagMeshFactory.createBagMesh(bagSpec),
                    new ObjectTransformation(0f, 0f, 0f, -50f, -50f, -50f, new Mat4()),
                    new OneColourStrategyImpl(0xFFD8C6A0),
                    true);
            objects.add(renderable);
            inspectionRegistry.register(renderable, () -> List.of(
                    "Type: Bag",
                    "Correlation: " + correlationId,
                    "Pack count: " + packCount,
                    String.format("Size W/H/D: %.2f / %.2f / %.2f",
                            bagSpec.width(),
                            bagSpec.height(),
                            bagSpec.depth())));
            return renderable;
        });
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
        positionPack(pack, x, y, z, 0f, 0f, 0f);
    }

    private void positionPack(Pack pack, float x, float y, float z, float angleX, float angleY, float angleZ) {
        RenderableObject renderable = tipperToSorterSection.getPackRenderable(pack.getId());
        if (renderable == null) {
            return;
        }
        renderable.transformation.xTranslation = x;
        renderable.transformation.yTranslation = y;
        renderable.transformation.zTranslation = z;
        renderable.transformation.angleX = angleX;
        renderable.transformation.angleY = angleY;
        renderable.transformation.angleZ = angleZ;
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
        float startZ = subsystem.getLayout().prlToPcrTransferStartZ(transfer.getPack());

        float x = (float) (prlX + ((joinX - prlX) * progress));
        float z = (float) (startZ + ((subsystem.getLayout().pcrZ() - startZ) * progress));
        positionPack(transfer.getPack(), x, layoutSpec.packY(), z);
    }

    private void positionActiveBagDischarge() {
        BagDischarge discharge = baggingMachine.getActiveDischarge();
        if (discharge == null) {
            return;
        }

        Bag bag = discharge.getBag();
        RenderableObject bagRenderable = ensureBagRenderableExists(
                bag.getCorrelationId(),
                bag.getBagSpec(),
                bag.getPackCount());
        BaggingModule.BagDischargePose pose = baggingModule.resolveBagDischargePose(
                discharge.getProgress(),
                bag.getBagSpec());
        bagRenderable.transformation.xTranslation = pose.worldPosition().x;
        bagRenderable.transformation.yTranslation = pose.worldPosition().y;
        bagRenderable.transformation.zTranslation = pose.worldPosition().z;
        bagRenderable.transformation.angleY = pose.yawRadians();
        bagRenderable.transformation.angleZ = pose.angleZRadians();
    }

    private void positionCompletedBagInReceiver(RenderableObject bagRenderable, Bag bag, int completedIndex) {
        float stackX = bagReceiverRenderable.transformation.xTranslation
                - (BAG_RECEIVER_LENGTH * 0.25f)
                + (completedIndex * 0.16f);
        float stackY = bagReceiverRenderable.transformation.yTranslation
                + 0.04f
                + (bag.getBagSpec().height() * 0.5f);
        bagRenderable.transformation.xTranslation = stackX;
        bagRenderable.transformation.yTranslation = stackY;
        bagRenderable.transformation.zTranslation = bagReceiverRenderable.transformation.zTranslation;
        bagRenderable.transformation.angleY = bagReceiverRenderable.transformation.angleY;
        bagRenderable.transformation.angleZ = 0f;
    }

    private void updateDebugBagReceiverAutoEmpty(double dtSeconds) {
        for (Bag bag : bagReceiverAutoEmptyController.update(dtSeconds)) {
            RenderableObject bagRenderable = completedBagRenderablesById.get(bag.getCorrelationId());
            if (bagRenderable != null) {
                bagRenderable.transformation.xTranslation = -50f;
                bagRenderable.transformation.yTranslation = -50f;
                bagRenderable.transformation.zTranslation = -50f;
            }
        }
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

    private void positionPacksOnBaggerIntake(double dtSeconds) {
        if (baggingMachine.getCurrentGroup() == null) {
            return;
        }

        Set<Pack> packsStillOnPcr = packsStillOnPcr();
        Set<String> currentPackIds = baggingMachine.getCurrentGroup().packs().stream()
                .map(Pack::getId)
                .collect(java.util.stream.Collectors.toSet());
        baggerIntakeElapsedSecondsByPackId.keySet().removeIf(packId -> !currentPackIds.contains(packId));

        for (Pack pack : baggingMachine.getCurrentGroup().packs()) {
            if (packsStillOnPcr.contains(pack)) {
                continue;
            }
            double elapsedSeconds = baggerIntakeElapsedSecondsByPackId.merge(
                    pack.getId(),
                    Math.max(0d, dtSeconds),
                    Double::sum);
            float packFrontDistance = (float) Math.min(
                    baggingModule.intakeTravelDistanceFor(pack.getDimensions()),
                    pcrConveyor.getSpeedMetersPerSecond() * elapsedSeconds);
            BaggingModule.IntakePackPose pose = baggingModule.resolveIntakePackPose(packFrontDistance, pack.getDimensions());
            positionPack(
                    pack,
                    pose.worldPosition().x,
                    pose.worldPosition().y,
                    pose.worldPosition().z,
                    0f,
                    pose.yawRadians(),
                    pose.angleZRadians());
        }
    }

    private Set<Pack> packsStillOnPcr() {
        Set<Pack> packs = new HashSet<>();
        for (var entry : pcrConveyor.getLaneEntries()) {
            packs.add(entry.pack());
        }
        return packs;
    }

    private RenderableObject createBagReceiverRenderable() {
        var outfeed = baggingModule.bagOutfeedWorldPoint();
        RenderableObject renderable = RenderableToteFactory.createRenderableTote(
                "debug_output_tote_receiver",
                tr,
                new ToteGeometry(),
                true);
        renderable.transformation.xTranslation = outfeed.x + BAG_RECEIVER_GAP_X + (BAG_RECEIVER_LENGTH * 0.5f);
        renderable.transformation.yTranslation = outfeed.y - 0.04f;
        renderable.transformation.zTranslation = outfeed.z;
        renderable.transformation.angleY = baggerRenderable.transformation.angleY;
        openReceiverToteLids(renderable);
        return renderable;
    }

    private void openReceiverToteLids(RenderableObject renderable) {
        Tote tote = new Tote(
                renderable.id,
                new RouteFollower(renderable.id, staticReceiverRoute(), 0f, 0d),
                renderable,
                new Vec3(),
                renderable.yawOffsetRadians);
        tote.openLids();
    }

    private RouteSegment staticReceiverRoute() {
        return new RouteSegment(
                "debug_output_tote_receiver_route",
                new LinearSegment3(new Vec3(0f, 0f, 0f), new Vec3(1f, 0f, 0f), false));
    }

}
