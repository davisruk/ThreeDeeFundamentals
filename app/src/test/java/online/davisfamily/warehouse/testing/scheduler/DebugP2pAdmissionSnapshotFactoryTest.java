package online.davisfamily.warehouse.testing.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.totebag.assignment.ToteToBagAssignmentPlanner;
import online.davisfamily.warehouse.sim.totebag.conveyor.ConveyorOccupancyModel;
import online.davisfamily.warehouse.sim.totebag.conveyor.PdcConveyor;
import online.davisfamily.warehouse.sim.totebag.conveyor.PcrConveyor;
import online.davisfamily.warehouse.sim.totebag.conveyor.PrlConveyor;
import online.davisfamily.warehouse.sim.totebag.control.ToteToBagFlowController;
import online.davisfamily.warehouse.sim.totebag.device.PdcDiversionDevice;
import online.davisfamily.warehouse.sim.totebag.handoff.PackGroupReceiver;
import online.davisfamily.warehouse.sim.totebag.handoff.PackGroupReservation;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteToBagBatchPlan;
import online.davisfamily.warehouse.sim.totebag.transfer.ReleasedPackGroup;

class DebugP2pAdmissionSnapshotFactoryTest {

    @Test
    void shouldReportIdlePrlCount() {
        SnapshotFixture fixture = createFixture();

        var snapshot = fixture.factory().snapshot();

        assertEquals(2, snapshot.idlePrlCount());
    }

    @Test
    void shouldReportActiveCorrelationsFromAssignedPrls() {
        SnapshotFixture fixture = createFixture();

        var snapshot = fixture.factory().snapshot();

        assertEquals(Set.of("bag-a", "bag-b", "bag-c"), snapshot.activeBagCorrelations());
        assertEquals(Set.of("bag-a", "bag-b", "bag-c"), snapshot.admissibleKnownCorrelations());
    }

    @Test
    void shouldCreateP2pStationSnapshot() {
        SnapshotFixture fixture = createFixture();

        StationSnapshot snapshot = fixture.factory().stationSnapshot();

        assertEquals(StationType.P2P, snapshot.stationType());
        assertEquals(0, snapshot.inProgress());
        assertEquals(0, snapshot.queued());
    }

    @Test
    void shouldRejectNullOrBlankInputs() {
        ToteToBagFlowController controller = createFixture().controller();

        assertThrows(IllegalArgumentException.class, () -> new DebugP2pAdmissionSnapshotFactory(null, controller));
        assertThrows(IllegalArgumentException.class, () -> new DebugP2pAdmissionSnapshotFactory(" ", controller));
        assertThrows(IllegalArgumentException.class, () -> new DebugP2pAdmissionSnapshotFactory("p2p-1", null));
    }

    private static SnapshotFixture createFixture() {
        PackDimensions packDimensions = new PackDimensions(0.20f, 0.10f, 0.08f);
        ToteLoadPlan currentToteLoadPlan = new ToteLoadPlan(
                "current-tote",
                List.of(
                        new PackPlan("pack-a1", "bag-a", packDimensions),
                        new PackPlan("pack-b1", "bag-b", packDimensions),
                        new PackPlan("pack-c1", "bag-c", packDimensions)));
        ToteToBagBatchPlan batchPlan = ToteToBagBatchPlan.fromToteLoadPlan(
                new ToteLoadPlan(
                        "batch-tote",
                        List.of(
                                new PackPlan("pack-a1", "bag-a", packDimensions),
                                new PackPlan("pack-b1", "bag-b", packDimensions),
                                new PackPlan("pack-c1", "bag-c", packDimensions),
                                new PackPlan("pack-d1", "bag-d", packDimensions),
                                new PackPlan("pack-e1", "bag-e", packDimensions))));

        List<PrlConveyor> prls = List.of(
                new PrlConveyor("prl-1", 0.15f, new ConveyorOccupancyModel(2.0f, 0.05f, 0.0f)),
                new PrlConveyor("prl-2", 0.15f, new ConveyorOccupancyModel(2.0f, 0.05f, 0.0f)),
                new PrlConveyor("prl-3", 0.15f, new ConveyorOccupancyModel(2.0f, 0.05f, 0.0f)),
                new PrlConveyor("prl-4", 0.15f, new ConveyorOccupancyModel(2.0f, 0.05f, 0.0f)),
                new PrlConveyor("prl-5", 0.15f, new ConveyorOccupancyModel(2.0f, 0.05f, 0.0f)));

        ToteToBagFlowController controller = new ToteToBagFlowController(
                currentToteLoadPlan,
                batchPlan,
                null,
                null,
                new PdcConveyor("pdc", new ConveyorOccupancyModel(2.0f, 0.05f, 0.0f), 1.0f),
                new PcrConveyor("pcr", new ConveyorOccupancyModel(2.0f, 0.05f, 0.10f), 0.15d),
                new UnavailablePackGroupReceiver(),
                new ToteToBagAssignmentPlanner(),
                prls,
                List.of(
                        new PdcDiversionDevice("diverter-1", "prl-1", 0d, 0.05d, 0.05d),
                        new PdcDiversionDevice("diverter-2", "prl-2", 0d, 0.05d, 0.05d),
                        new PdcDiversionDevice("diverter-3", "prl-3", 0d, 0.05d, 0.05d),
                        new PdcDiversionDevice("diverter-4", "prl-4", 0d, 0.05d, 0.05d),
                        new PdcDiversionDevice("diverter-5", "prl-5", 0d, 0.05d, 0.05d)),
                ignored -> 0.0d,
                (ignored, pack) -> 0.0f,
                ignored -> 0.0d,
                (ignored, pack) -> pack.getDimensions().length());
        controller.update(null, 0.05d);
        prls.get(3).getAssignment().clear();
        prls.get(4).getAssignment().clear();

        DebugP2pAdmissionSnapshotFactory factory = new DebugP2pAdmissionSnapshotFactory("p2p-1", controller);
        return new SnapshotFixture(factory, controller);
    }

    private record SnapshotFixture(DebugP2pAdmissionSnapshotFactory factory, ToteToBagFlowController controller) {
    }

    private static final class UnavailablePackGroupReceiver implements PackGroupReceiver {
        @Override
        public boolean canReserveIncomingGroup(ReleasedPackGroup group) {
            return false;
        }

        @Override
        public PackGroupReservation reserveIncomingGroup(ReleasedPackGroup group) {
            throw new IllegalStateException("Receiver is unavailable");
        }

        @Override
        public boolean hasReservationFor(ReleasedPackGroup group) {
            return false;
        }

        @Override
        public void beginReceiving(PackGroupReservation reservation) {
            throw new IllegalStateException("Receiver is unavailable");
        }

        @Override
        public boolean isReceivingGroup(ReleasedPackGroup group) {
            return false;
        }

        @Override
        public void completeIncomingTransfer(ReleasedPackGroup group) {
            throw new IllegalStateException("Receiver is unavailable");
        }
    }
}
