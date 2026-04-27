package online.davisfamily.warehouse.sim.totebag;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.totebag.conveyor.ConveyorOccupancyModel;
import online.davisfamily.warehouse.sim.totebag.conveyor.PcrConveyor;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.transfer.ReleasedPackGroup;

class PcrConveyorTest {

    @Test
    void shouldKeepReleasedGroupActiveUntilEveryExpectedPackHasEnteredAndLeft() {
        Pack firstPack = new Pack("pack-1", "bag-a", new PackDimensions(0.08f, 0.05f, 0.04f));
        Pack secondPack = new Pack("pack-2", "bag-a", new PackDimensions(0.08f, 0.05f, 0.04f));
        ReleasedPackGroup group = new ReleasedPackGroup("bag-a", "prl-1", List.of(firstPack, secondPack), 0.175f);
        PcrConveyor pcr = new PcrConveyor("pcr", new ConveyorOccupancyModel(1.0f, 0.015f, 0.0f), 0d);

        pcr.startReceivingGroup(group);
        pcr.acceptIncomingPackAtDistance(firstPack, 1.0f);
        assertFalse(pcr.isGroupFullyAccepted(group));

        pcr.pollPackAtOutfeed();
        assertTrue(pcr.hasWorkInFlight());

        pcr.acceptIncomingPackAtDistance(secondPack, 1.0f);
        assertTrue(pcr.isGroupFullyAccepted(group));

        pcr.pollPackAtOutfeed();
        assertTrue(pcr.isEmpty());
    }
}
