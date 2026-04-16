package online.davisfamily.warehouse.testing;

import java.util.List;
import java.util.function.Function;

import online.davisfamily.threedee.debug.SelectionInspectionRegistry;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.rendering.appearance.OneColourStrategyImpl;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.rendering.model.tracks.ConveyorRuntimeState;
import online.davisfamily.warehouse.rendering.model.tracks.StraightConveyorFactory;
import online.davisfamily.warehouse.rendering.model.tracks.StraightConveyorFactory.ConveyorVisualSpeed;
import online.davisfamily.warehouse.rendering.model.tracks.StraightConveyorFactory.StraightConveyorSpec;
import online.davisfamily.warehouse.rendering.model.tracks.TrackAppearance;
import online.davisfamily.warehouse.sim.totebag.control.PackSink;
import online.davisfamily.warehouse.sim.totebag.conveyor.ConveyorOccupancyModel;
import online.davisfamily.warehouse.sim.totebag.conveyor.PdcConveyor;
import online.davisfamily.warehouse.sim.totebag.handoff.PackHandoffPoint;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;

public class SorterOutfeedDebugConveyor {
    private static final float TOP_Y_OFFSET = 0.10f;
    private static final float USABLE_LENGTH = 2.4f;
    private static final float SPEED = 1.15f;
    private static final float VISUAL_LENGTH = 2.6f;
    private static final float VISUAL_WIDTH = 0.30f;
    private static final float ENTRY_CENTER_DISTANCE = 1.25f;

    private final PdcConveyor conveyor;
    private final RenderableObject renderable;
    private final ConveyorRuntimeState runtimeState;
    private final PackSink entrySink;

    public SorterOutfeedDebugConveyor(
            TriangleRenderer tr,
            SimulationWorld sim,
            List<RenderableObject> objects,
            SelectionInspectionRegistry inspectionRegistry,
            PackHandoffPoint outfeedPoint,
            PackSink downstreamSink) {
        if (tr == null || sim == null || objects == null || outfeedPoint == null) {
            throw new IllegalArgumentException("Sorter outfeed debug conveyor inputs must not be null");
        }

        conveyor = new PdcConveyor(
                "sorter_outfeed",
                new ConveyorOccupancyModel(USABLE_LENGTH, 0.04f, 0f),
                SPEED);
        runtimeState = new ConveyorRuntimeState();
        runtimeState.setRunning(false);
        renderable = StraightConveyorFactory.create(
                "sorter_outfeed_visual",
                tr,
                new StraightConveyorSpec(
                        VISUAL_LENGTH,
                        VISUAL_WIDTH,
                        0.05f,
                        0.008f,
                        0.10f,
                        0.08f,
                        0.004f,
                        ConveyorVisualSpeed.fixed(SPEED)),
                runtimeState,
                new TrackAppearance(
                        new OneColourStrategyImpl(0xFF596A54),
                        new OneColourStrategyImpl(0xFF2A2A2A),
                        new OneColourStrategyImpl(0xFF2F2F2F),
                        new OneColourStrategyImpl(0xFFB8B8B8),
                        new OneColourStrategyImpl(0xFF596A54),
                        new OneColourStrategyImpl(0xFF596A54)));
        renderable.transformation.xTranslation = outfeedPoint.worldPosition().x;
        renderable.transformation.yTranslation = outfeedPoint.worldPosition().y;
        renderable.transformation.zTranslation = outfeedPoint.worldPosition().z;
        renderable.transformation.angleY = outfeedPoint.yawRadians();
        objects.add(renderable);

        if (inspectionRegistry != null) {
            inspectionRegistry.register(renderable, () -> List.of(
                    "Type: Sorter outfeed",
                    "Running: " + runtimeState.isRunning(),
                    "Lane packs: " + conveyor.getLaneEntries().size()));
        }

        entrySink = new PackSink() {
            @Override
            public boolean canAccept(Pack pack) {
                float entryFrontDistance = ENTRY_CENTER_DISTANCE + (pack.getDimensions().length() * 0.5f);
                return conveyor.canAcceptIncomingPackAtFrontDistance(pack, entryFrontDistance);
            }

            @Override
            public void accept(Pack pack) {
                float entryFrontDistance = ENTRY_CENTER_DISTANCE + (pack.getDimensions().length() * 0.5f);
                conveyor.acceptIncomingPackAtFrontDistance(pack, entryFrontDistance);
            }
        };

        sim.addController(new SimulationController() {
            @Override
            public void update(SimulationContext context, double dtSeconds) {
                runtimeState.setRunning(!conveyor.getLaneEntries().isEmpty());
                conveyor.setRunning(!conveyor.getLaneEntries().isEmpty());
                conveyor.update(dtSeconds);
                drainOutfeed(downstreamSink);
            }
        });
    }

    public PackSink entrySink() {
        return entrySink;
    }

    public void syncVisuals(Function<String, RenderableObject> packRenderableResolver) {
        for (var entry : conveyor.getLaneEntries()) {
            RenderableObject renderableForPack = packRenderableResolver.apply(entry.pack().getId());
            if (renderableForPack == null) {
                continue;
            }
            float alongConveyor = -(conveyor.getUsableLength() * 0.5f)
                    + entry.frontDistance()
                    - (entry.pack().getDimensions().length() * 0.5f);
            Vec3 forward = Vec3.rotateY(new Vec3(1f, 0f, 0f), renderable.transformation.angleY);
            Vec3 outfeedWorld = new Vec3(
                    renderable.transformation.xTranslation + (forward.x * alongConveyor),
                    renderable.transformation.yTranslation
                            + TOP_Y_OFFSET
                            + (entry.pack().getDimensions().height() * 0.5f)
                            + 0.002f,
                    renderable.transformation.zTranslation + (forward.z * alongConveyor));
            renderableForPack.transformation.xTranslation = outfeedWorld.x;
            renderableForPack.transformation.yTranslation = outfeedWorld.y;
            renderableForPack.transformation.zTranslation = outfeedWorld.z;
        }
    }

    private void drainOutfeed(PackSink downstreamSink) {
        while (true) {
            Pack pack = conveyor.peekLeadingPackAtOutfeed().orElse(null);
            if (pack == null) {
                return;
            }
            if (downstreamSink != null && !downstreamSink.canAccept(pack)) {
                return;
            }
            pack = conveyor.pollLeadingPackAtOutfeed().orElse(null);
            if (pack == null) {
                return;
            }
            if (downstreamSink != null) {
                downstreamSink.accept(pack);
            } else {
                pack.setState(Pack.PackMotionState.CONSUMED);
            }
        }
    }
}
