package online.davisfamily.warehouse.sim.totebag.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.totebag.Pack;
import online.davisfamily.warehouse.sim.totebag.PackDimensions;

class ToteToBagCoreLayoutTest {

    @Test
    void shouldResolveAttachmentPoseFromCoreAnchors() {
        ToteToBagCoreLayout layout = new ToteToBagCoreLayout(ToteToBagCoreLayoutSpec.debugDefaults());

        ToteToBagAttachmentPose pose = layout.resolveAttachmentPose(
                new MachineAttachmentSpec(ToteToBagAttachmentPoint.PCR_OUTFEED, 2.6f, 0.26f, 0f, 0f));

        assertEquals(4.0f, pose.x(), 0.0001f);
        assertEquals(0.28f, pose.y(), 0.0001f);
        assertEquals(-2.9f, pose.z(), 0.0001f);
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
