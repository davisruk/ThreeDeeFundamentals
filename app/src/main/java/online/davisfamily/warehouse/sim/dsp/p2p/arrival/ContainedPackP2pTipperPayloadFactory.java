package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import java.util.Map;

import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;
import online.davisfamily.warehouse.sim.totebag.layout.ContainedPackLayout;

public final class ContainedPackP2pTipperPayloadFactory implements P2pTipperPayloadFactory {
    private final float toteInteriorFloorLocalY;
    private final ContainedPackLayout containedPackLayout;

    public ContainedPackP2pTipperPayloadFactory(
            float toteInteriorWidth,
            float toteInteriorDepth,
            float toteInteriorFloorLocalY,
            float gapX,
            float gapZ,
            float gapY) {
        requirePositiveFinite("toteInteriorWidth", toteInteriorWidth);
        requirePositiveFinite("toteInteriorDepth", toteInteriorDepth);
        requireFinite("toteInteriorFloorLocalY", toteInteriorFloorLocalY);
        requireNonnegativeFinite("gapX", gapX);
        requireNonnegativeFinite("gapZ", gapZ);
        requireNonnegativeFinite("gapY", gapY);
        this.toteInteriorFloorLocalY = toteInteriorFloorLocalY;
        this.containedPackLayout = new ContainedPackLayout(
                toteInteriorWidth,
                toteInteriorDepth,
                toteInteriorFloorLocalY,
                gapX,
                gapZ,
                gapY);
    }

    @Override
    public TipperTotePayload create(RoutedPhysicalTote routedTote) {
        if (routedTote == null) {
            throw new IllegalArgumentException("routedTote must not be null");
        }
        Map<String, Vec3> packLayout = containedPackLayout.layoutPackPlans(
                routedTote.loadPlan().getPackPlans());
        return new TipperTotePayload(
                routedTote.tote(),
                routedTote.renderable(),
                toteInteriorFloorLocalY,
                packLayout);
    }

    private static void requirePositiveFinite(String name, float value) {
        requireFinite(name, value);
        if (value <= 0f) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
    }

    private static void requireNonnegativeFinite(String name, float value) {
        requireFinite(name, value);
        if (value < 0f) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
    }

    private static void requireFinite(String name, float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
