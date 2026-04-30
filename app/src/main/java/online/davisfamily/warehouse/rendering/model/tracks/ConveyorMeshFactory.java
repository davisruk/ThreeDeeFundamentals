package online.davisfamily.warehouse.rendering.model.tracks;

import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.model.cylinder.CylinderFactory;

public final class ConveyorMeshFactory {

    private ConveyorMeshFactory() {
    }

    public static Mesh createEndRollerMesh(TrackSpec spec) {
        float radius = getWrapRadius(spec);
        float widthAcross = spec.getRunningWidth() - (2f * spec.conveyorWidthInset);
        return CylinderFactory.buildCylinder(radius, widthAcross, 12, true);
    }

    public static Mesh createMarkerMesh(TrackSpec spec) {
        float widthAcross = spec.getRunningWidth() - (2f * spec.conveyorWidthInset);
        return RollerMeshFactory.createBoxRollerMesh(
                spec.conveyorMarkerLength,
                spec.conveyorMarkerThickness,
                widthAcross);
    }

    public static float getWrapRadius(TrackSpec spec) {
        float topCentreY = spec.deckTopY + (spec.conveyorBeltThickness * 0.5f);
        float returnCentreY = spec.deckTopY - spec.conveyorReturnDepth;
        float radius = (topCentreY - returnCentreY) * 0.5f;
        return Math.max(radius, spec.conveyorBeltThickness);
    }
}
