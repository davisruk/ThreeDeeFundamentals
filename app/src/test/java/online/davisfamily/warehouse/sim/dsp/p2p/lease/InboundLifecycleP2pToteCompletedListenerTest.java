package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.tote.Tote;

class InboundLifecycleP2pToteCompletedListenerTest {

    @Test
    void shouldAdvanceAndConsumeExactInboundToteAtCurrentSimulationTime() {
        InboundToteManifest manifest = manifest("physical-1", OrderType.FULL_PACK);
        InboundToteLifecycleController lifecycle = lifecycle(manifest);
        lifecycle.activate(manifest.physicalToteId(), Duration.ofSeconds(1));
        SimulationContext context = new SimulationContext();
        context.setSimulationTimeSeconds(4.5d);

        new InboundLifecycleP2pToteCompletedListener(lifecycle)
                .onToteCompleted(tote(manifest.physicalToteId().value()), context);

        assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                lifecycle.snapshot().totes().get(manifest.physicalToteId()).state());
        assertEquals(Duration.ofMillis(4500), lifecycle.snapshot()
                .assignmentHistoryFor(manifest.orderSheetKey()).getLast()
                .terminatedAt().orElseThrow());
    }

    @Test
    void shouldConsumeAlreadyAdvancedToteAndRejectInvalidOrderTypeOrState() {
        InboundToteManifest fullPack = manifest("physical-1", OrderType.FULL_PACK);
        InboundToteLifecycleController lifecycle = lifecycle(fullPack);
        lifecycle.activate(fullPack.physicalToteId(), Duration.ZERO);
        lifecycle.advanceToPreP2p(fullPack.physicalToteId(), Duration.ofSeconds(1));
        SimulationContext context = new SimulationContext();
        context.setSimulationTimeSeconds(2d);
        InboundLifecycleP2pToteCompletedListener listener =
                new InboundLifecycleP2pToteCompletedListener(lifecycle);

        listener.onToteCompleted(tote(fullPack.physicalToteId().value()), context);

        assertThrows(IllegalStateException.class,
                () -> listener.onToteCompleted(tote(fullPack.physicalToteId().value()), context));

        InboundToteManifest adapted = manifest("adapted-1", OrderType.ADAPTED);
        InboundToteLifecycleController adaptedLifecycle = lifecycle(adapted);
        adaptedLifecycle.activate(adapted.physicalToteId(), Duration.ZERO);
        assertThrows(IllegalStateException.class,
                () -> new InboundLifecycleP2pToteCompletedListener(adaptedLifecycle)
                        .onToteCompleted(tote(adapted.physicalToteId().value()), context));
    }

    private static InboundToteLifecycleController lifecycle(InboundToteManifest manifest) {
        return new InboundToteLifecycleController(
                new PhysicalToteLifecycleLedger(),
                new InboundToteManifestCatalog(List.of(manifest)));
    }

    private static InboundToteManifest manifest(String toteId, OrderType type) {
        return new InboundToteManifest(
                new PhysicalToteId(toteId),
                new OrderSheetKey("order-" + toteId, 1),
                type,
                "SC-104",
                List.of(new DspOrderItem("line-" + toteId, "product-1", 1)),
                0);
    }

    private static Tote tote(String toteId) {
        RouteSegment segment = new RouteSegment(
                "segment-" + toteId,
                new LinearSegment3(new Vec3(0f, 0f, 0f), new Vec3(1f, 0f, 0f), false));
        RenderableObject renderable = RenderableObject.create(
                toteId, null, anchorMesh(),
                new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                ignored -> 0, false);
        return new Tote(toteId, new RouteFollower(toteId, segment, 0f, 1d),
                renderable, new Vec3(0f, 0f, 0f), 0f);
    }

    private static Mesh anchorMesh() {
        return new Mesh(
                new Vec4[] {new Vec4(), new Vec4(), new Vec4()},
                new int[][] {{0, 1, 2}},
                "anchor");
    }
}
