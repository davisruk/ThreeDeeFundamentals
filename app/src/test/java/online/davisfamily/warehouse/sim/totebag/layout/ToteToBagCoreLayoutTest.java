package online.davisfamily.warehouse.sim.totebag.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.totebag.pack.Pack;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;

class ToteToBagCoreLayoutTest {

    @Test
    void shouldResolveAttachmentPoseFromCoreAnchors() {
        ToteToBagCoreLayoutSpec spec = ToteToBagCoreLayoutSpec.debugDefaults();
        ToteToBagCoreLayout layout = new ToteToBagCoreLayout(spec);

        ToteToBagAttachmentPose pose = layout.resolveAttachmentPose(spec.baggerMount());

        assertEquals(4.0f, pose.x(), 0.0001f);
        assertEquals(0.28f, pose.y(), 0.0001f);
        assertEquals(layout.pcrZ(), pose.z(), 0.0001f);
    }

    @Test
    void shouldIncreaseDiversionDistanceForLaterPrls() {
        ToteToBagCoreLayout layout = new ToteToBagCoreLayout(ToteToBagCoreLayoutSpec.debugDefaults());
        Pack probe = new Pack("probe", "probe", new PackDimensions(0.18f, 0.1f, 0.1f));

        float prl1Distance = layout.pdcDiversionFrontDistanceFor(0, probe);
        float prl3Distance = layout.pdcDiversionFrontDistanceFor(2, probe);

        assertTrue(prl3Distance > prl1Distance);
    }
}
