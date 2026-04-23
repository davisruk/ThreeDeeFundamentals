package online.davisfamily.warehouse.sim.totebag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.totebag.bag.Bag;
import online.davisfamily.warehouse.sim.totebag.handoff.RecordingBagReceiver;
import online.davisfamily.warehouse.sim.totebag.machine.BaggingMachine;
import online.davisfamily.warehouse.sim.totebag.pack.Pack;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.BagSpec;
import online.davisfamily.warehouse.sim.totebag.transfer.ReleasedPackGroup;

class BaggingMachineTest {
    @Test
    void shouldSynchronouslyDeliverCompletedBagToReceiverWhenConfigured() {
        RecordingBagReceiver receiver = new RecordingBagReceiver("receiver");
        BaggingMachine baggingMachine = new BaggingMachine(
                "bagger",
                new BagSpec(0.34f, 0.28f, 0.22f),
                0.05d,
                0.05d,
                0.05d,
                0.05d,
                receiver);
        ReleasedPackGroup group = releasedGroup("bag-a");

        baggingMachine.startBagging(group);
        baggingMachine.completeIncomingTransfer(group);
        for (int i = 0; i < 8; i++) {
            baggingMachine.update(null, 0.05d);
        }

        assertEquals(List.of("bag-a"), baggingMachine.getCompletedCorrelationIds());
        assertEquals(List.of("bag-a"), receiver.getCompletedCorrelationIds());
        assertEquals(1, baggingMachine.getCompletedRuntimeBags().size());
        Bag runtimeBag = baggingMachine.getCompletedRuntimeBags().getFirst();
        assertEquals("bag_bag-a", runtimeBag.getId());
        assertEquals("bag-a", runtimeBag.getCorrelationId());
        assertEquals(group.toPackPlans(), runtimeBag.getPackContents());
    }

    private static ReleasedPackGroup releasedGroup(String correlationId) {
        Pack pack = new Pack(
                "pack-1",
                correlationId,
                new PackDimensions(0.20f, 0.10f, 0.08f));
        return new ReleasedPackGroup(correlationId, "prl-1", List.of(pack), 0.25f);
    }
}
