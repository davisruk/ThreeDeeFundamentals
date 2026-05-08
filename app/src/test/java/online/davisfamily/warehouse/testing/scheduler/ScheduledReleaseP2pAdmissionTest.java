package online.davisfamily.warehouse.testing.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.p2p.P2pAdmissionResult;
import online.davisfamily.warehouse.sim.dsp.p2p.P2pAdmissionSnapshot;
import online.davisfamily.warehouse.sim.totebag.assignment.ToteToBagAssignmentPlanner;
import online.davisfamily.warehouse.sim.totebag.conveyor.ConveyorOccupancyModel;
import online.davisfamily.warehouse.sim.totebag.conveyor.PdcConveyor;
import online.davisfamily.warehouse.sim.totebag.conveyor.PcrConveyor;
import online.davisfamily.warehouse.sim.totebag.conveyor.PrlConveyor;
import online.davisfamily.warehouse.sim.totebag.device.PdcDiversionDevice;
import online.davisfamily.warehouse.sim.totebag.handoff.PackGroupReceiver;
import online.davisfamily.warehouse.sim.totebag.handoff.PackGroupReservation;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteToBagBatchPlan;
import online.davisfamily.warehouse.sim.totebag.transfer.ReleasedPackGroup;
import online.davisfamily.warehouse.sim.totebag.control.ToteToBagFlowController;

class ScheduledReleaseP2pAdmissionTest {

    @Test
    void shouldAcceptWhenFlowControllerCanAdmitScheduledToteLoadPlan() {
        AdmissionFixture fixture = createAdmissionFixture();
        ScheduledReleaseP2pAdmission admission = new ScheduledReleaseP2pAdmission(
                new ScheduledTipperToteReleaseCatalog(List.of(release("order-1", admissibleToteLoadPlan(), new AtomicInteger()))),
                fixture.controller());

        P2pAdmissionResult result = admission.canAdmit(order("order-1"), p2pSnapshot());

        assertTrue(result.accepted());
        assertTrue(result.rejectionReason().isBlank());
    }

    @Test
    void shouldRejectWhenFlowControllerCannotAdmitScheduledToteLoadPlan() {
        AdmissionFixture fixture = createAdmissionFixture();
        ScheduledReleaseP2pAdmission admission = new ScheduledReleaseP2pAdmission(
                new ScheduledTipperToteReleaseCatalog(List.of(release("order-2", blockedToteLoadPlan(), new AtomicInteger()))),
                fixture.controller());

        P2pAdmissionResult result = admission.canAdmit(order("order-2"), p2pSnapshot());

        assertFalse(result.accepted());
        assertEquals("P2P cannot admit tote blocked-tote", result.rejectionReason());
    }

    @Test
    void shouldRejectWhenNoScheduledReleaseExistsForOrder() {
        AdmissionFixture fixture = createAdmissionFixture();
        ScheduledReleaseP2pAdmission admission = new ScheduledReleaseP2pAdmission(
                new ScheduledTipperToteReleaseCatalog(List.of(release("other-order", admissibleToteLoadPlan(), new AtomicInteger()))),
                fixture.controller());

        P2pAdmissionResult result = admission.canAdmit(order("missing-order"), p2pSnapshot());

        assertFalse(result.accepted());
        assertEquals("No scheduled P2P tote load plan for order missing-order", result.rejectionReason());
    }

    @Test
    void shouldNotCreatePayloadDuringAdmissionCheck() {
        AdmissionFixture fixture = createAdmissionFixture();
        AtomicInteger payloadFactoryCalls = new AtomicInteger();
        ScheduledReleaseP2pAdmission admission = new ScheduledReleaseP2pAdmission(
                new ScheduledTipperToteReleaseCatalog(List.of(release("order-1", admissibleToteLoadPlan(), payloadFactoryCalls))),
                fixture.controller());

        P2pAdmissionResult result = admission.canAdmit(order("order-1"), p2pSnapshot());

        assertTrue(result.accepted());
        assertEquals(0, payloadFactoryCalls.get());
    }

    private static ScheduledTipperToteRelease release(
            String orderId,
            ToteLoadPlan toteLoadPlan,
            AtomicInteger payloadFactoryCalls) {
        return new ScheduledTipperToteRelease(
                orderId,
                toteLoadPlan,
                () -> {
                    payloadFactoryCalls.incrementAndGet();
                    return null;
                });
    }

    private static NotionalToteOrder order(String orderId) {
        return new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                "sc-1",
                1,
                OrderType.ASSOCIATED,
                List.of(new DspOrderItem("item-" + orderId, "product-1", 1)),
                0);
    }

    private static P2pAdmissionSnapshot p2pSnapshot() {
        return new P2pAdmissionSnapshot(
                "p2p-1",
                2,
                Set.of("bag-a"),
                Set.of("bag-a"),
                true);
    }

    private static ToteLoadPlan admissibleToteLoadPlan() {
        return new ToteLoadPlan(
                "candidate-tote",
                List.of(
                        new PackPlan("pack-f1", "bag-f", packDimensions()),
                        new PackPlan("pack-g1", "bag-g", packDimensions())));
    }

    private static ToteLoadPlan blockedToteLoadPlan() {
        return new ToteLoadPlan(
                "blocked-tote",
                List.of(new PackPlan("pack-z1", "bag-z", packDimensions())));
    }

    private static AdmissionFixture createAdmissionFixture() {
        PackDimensions packDimensions = packDimensions();
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
                                new PackPlan("pack-e1", "bag-e", packDimensions),
                                new PackPlan("pack-f1", "bag-f", packDimensions),
                                new PackPlan("pack-g1", "bag-g", packDimensions))));

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
        prls.get(0).getAssignment().clear();
        prls.get(1).getAssignment().clear();

        return new AdmissionFixture(controller);
    }

    private static PackDimensions packDimensions() {
        return new PackDimensions(0.20f, 0.10f, 0.08f);
    }

    private record AdmissionFixture(ToteToBagFlowController controller) {
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
