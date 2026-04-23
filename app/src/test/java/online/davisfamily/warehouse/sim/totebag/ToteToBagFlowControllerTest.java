package online.davisfamily.warehouse.sim.totebag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.totebag.assignment.PrlState;
import online.davisfamily.warehouse.sim.totebag.assignment.ToteToBagAssignmentPlanner;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;
import online.davisfamily.warehouse.sim.totebag.control.ToteTrackTipperFlowController;
import online.davisfamily.warehouse.sim.totebag.control.ToteToBagFlowController;
import online.davisfamily.warehouse.sim.totebag.conveyor.ConveyorOccupancyModel;
import online.davisfamily.warehouse.sim.totebag.conveyor.PdcConveyor;
import online.davisfamily.warehouse.sim.totebag.conveyor.PcrConveyor;
import online.davisfamily.warehouse.sim.totebag.conveyor.PrlConveyor;
import online.davisfamily.warehouse.sim.totebag.device.PdcDiversionDevice;
import online.davisfamily.warehouse.sim.totebag.device.PdcDiversionDeviceState;
import online.davisfamily.warehouse.sim.totebag.machine.BaggingMachine;
import online.davisfamily.warehouse.sim.totebag.machine.SortingMachine;
import online.davisfamily.warehouse.sim.totebag.machine.TippingMachine;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.BagSpec;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlanProvider;

class ToteToBagFlowControllerTest {

    @Test
    void shouldCaptureToteAtSegmentLocalTipperMidpoint() {
        RouteSegment infeedSegment = new RouteSegment(
                "infeed",
                new LinearSegment3(new Vec3(0f, 0f, 0f), new Vec3(2f, 0f, 0f), false));
        RouteSegment tipperSegment = new RouteSegment(
                "tipper",
                new LinearSegment3(new Vec3(2f, 0f, 0f), new Vec3(3.25f, 0f, 0f), false));
        RouteSegment exitSegment = new RouteSegment(
                "exit",
                new LinearSegment3(new Vec3(3.25f, 0f, 0f), new Vec3(5f, 0f, 0f), false));
        infeedSegment.connectTo(tipperSegment);
        tipperSegment.connectTo(exitSegment);

        RenderableObject toteRenderable = RenderableObject.create(
                "tote",
                null,
                anchorMesh(),
                new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);
        Tote tote = new Tote(
                "tote-1",
                new RouteFollower("tote-1", infeedSegment, 0f, 1.4d),
                toteRenderable,
                new Vec3(0f, 0f, 0f),
                0f);

        ToteLoadPlan toteLoadPlan = new ToteLoadPlan(
                "tote-1",
                List.of(new PackPlan("pack-1", "bag-a", new PackDimensions(0.20f, 0.10f, 0.08f))));
        ToteLoadPlanProvider toteLoadPlanProvider = toteId -> toteLoadPlan.getToteId().equals(toteId) ? toteLoadPlan : null;
        TippingMachine tippingMachine = new TippingMachine("tipper", 0.20d, 0.10d, 0.10d);
        SortingMachine sortingMachine = new SortingMachine("sorter", 0.10d);
        ToteTrackTipperFlowController controller = new ToteTrackTipperFlowController(
                tote,
                toteLoadPlanProvider,
                tipperSegment,
                0.625f,
                -1.02f,
                tippingMachine,
                sortingMachine,
                0.20d);

        SimulationWorld sim = new SimulationWorld();
        sim.addTrackableObject(tote);
        sim.addSimObject(tippingMachine);
        sim.addSimObject(sortingMachine);
        sim.addController(controller);

        boolean sawCaptureAtTipperMidpoint = false;
        for (int i = 0; i < 50; i++) {
            sim.update(0.05d);
            if (controller.isToteCaptured()) {
                sawCaptureAtTipperMidpoint = true;
                assertEquals(ToteMotionState.HELD, tote.getInteractionMode());
                assertSame(tipperSegment, tote.getLastSnapshot().currentSegment());
                assertEquals(0.625f, tote.getLastSnapshot().distanceAlongSegment(), 0.0001f);
                assertEquals("tote-1", tippingMachine.getActiveToteId());
                break;
            }
        }

        assertTrue(sawCaptureAtTipperMidpoint);
    }

    @Test
    void shouldRunSimpleToteToBagFlowEndToEnd() {
        ToteLoadPlan toteLoadPlan = new ToteLoadPlan(
                "tote-1",
                List.of(
                        new PackPlan("pack-1", "bag-a", new PackDimensions(0.20f, 0.10f, 0.08f)),
                        new PackPlan("pack-2", "bag-b", new PackDimensions(0.18f, 0.10f, 0.08f)),
                        new PackPlan("pack-3", "bag-a", new PackDimensions(0.22f, 0.10f, 0.08f)),
                        new PackPlan("pack-4", "bag-b", new PackDimensions(0.16f, 0.10f, 0.08f))));

        TippingMachine tippingMachine = new TippingMachine("tipper", 0.2d, 0.1d, 0.1d);
        SortingMachine sortingMachine = new SortingMachine("sorter", 0.05d);
        ConveyorOccupancyModel prlOccupancy = new ConveyorOccupancyModel(2.0f, 0.05f, 0.0f);
        ConveyorOccupancyModel pcrOccupancy = new ConveyorOccupancyModel(2.0f, 0.05f, 0.10f);
        PdcConveyor pdcConveyor = new PdcConveyor("pdc", new ConveyorOccupancyModel(2.0f, 0.05f, 0.0f), 1.2f);
        PrlConveyor prl1 = new PrlConveyor("prl-1", 0.15f, prlOccupancy);
        PrlConveyor prl2 = new PrlConveyor("prl-2", 0.15f, prlOccupancy);
        PcrConveyor pcrConveyor = new PcrConveyor("pcr", pcrOccupancy, 0.15d);
        BaggingMachine baggingMachine = new BaggingMachine("bagger", new BagSpec(0.34f, 0.28f, 0.22f), 0.05d, 0.05d, 0.05d, 0.05d);

        ToteToBagFlowController controller = new ToteToBagFlowController(
                toteLoadPlan,
                tippingMachine,
                sortingMachine,
                pdcConveyor,
                pcrConveyor,
                baggingMachine,
                new ToteToBagAssignmentPlanner(),
                List.of(prl1, prl2));

        SimulationWorld sim = new SimulationWorld();
        sim.addSimObject(tippingMachine);
        sim.addSimObject(sortingMachine);
        sim.addSimObject(pcrConveyor);
        sim.addSimObject(baggingMachine);
        sim.addController(controller);

        for (int i = 0; i < 600 && baggingMachine.getCompletedCorrelationIds().size() < 2; i++) {
            sim.update(0.05d);
        }

        assertEquals(List.of("bag-a", "bag-b"), baggingMachine.getCompletedCorrelationIds());
        assertEquals(PrlState.IDLE, prl1.getAssignment().getState());
        assertEquals(PrlState.IDLE, prl2.getAssignment().getState());
        assertTrue(pcrConveyor.isEmpty());
    }

    @Test
    void shouldUseExplicitPrlToPcrTransferBeforePackAppearsOnPcr() {
        ToteLoadPlan toteLoadPlan = new ToteLoadPlan(
                "tote-1",
                List.of(
                        new PackPlan("pack-1", "bag-a", new PackDimensions(0.20f, 0.10f, 0.08f)),
                        new PackPlan("pack-2", "bag-a", new PackDimensions(0.18f, 0.10f, 0.08f))));

        TippingMachine tippingMachine = new TippingMachine("tipper", 0.05d, 0.05d, 0.05d);
        SortingMachine sortingMachine = new SortingMachine("sorter", 0.05d);
        ConveyorOccupancyModel prlOccupancy = new ConveyorOccupancyModel(2.0f, 0.05f, 0.0f);
        ConveyorOccupancyModel pcrOccupancy = new ConveyorOccupancyModel(2.0f, 0.05f, 0.10f);
        PdcConveyor pdcConveyor = new PdcConveyor("pdc", new ConveyorOccupancyModel(2.0f, 0.05f, 0.0f), 1.2f);
        PrlConveyor prl1 = new PrlConveyor("prl-1", 0.15f, prlOccupancy);
        PcrConveyor pcrConveyor = new PcrConveyor("pcr", pcrOccupancy, 0.15d);
        BaggingMachine baggingMachine = new BaggingMachine("bagger", new BagSpec(0.34f, 0.28f, 0.22f), 0.20d, 0.20d, 0.20d, 0.20d);

        ToteToBagFlowController controller = new ToteToBagFlowController(
                toteLoadPlan,
                tippingMachine,
                sortingMachine,
                pdcConveyor,
                pcrConveyor,
                baggingMachine,
                new ToteToBagAssignmentPlanner(),
                List.of(prl1),
                List.of(new PdcDiversionDevice("diverter-1", "prl-1", 0d, 0.05d, 0.05d)),
                ignored -> 0.05d,
                (ignored, pack) -> pack.getDimensions().length(),
                ignored -> 0.80d,
                (ignored, pack) -> pack.getDimensions().length());

        SimulationWorld sim = new SimulationWorld();
        sim.addSimObject(tippingMachine);
        sim.addSimObject(sortingMachine);
        sim.addSimObject(pcrConveyor);
        sim.addSimObject(baggingMachine);
        sim.addController(controller);

        boolean sawPrlToPcrTransfer = false;
        boolean sawTransferBeforePcrEntry = false;
        for (int i = 0; i < 80; i++) {
            sim.update(0.05d);
            if (!controller.getActivePrlToPcrTransfers().isEmpty()) {
                sawPrlToPcrTransfer = true;
                if (pcrConveyor.getLaneEntries().isEmpty()) {
                    sawTransferBeforePcrEntry = true;
                }
                break;
            }
        }

        assertTrue(sawPrlToPcrTransfer);
        assertTrue(sawTransferBeforePcrEntry);
        assertTrue(pcrConveyor.getLaneEntries().isEmpty() || !controller.getActivePrlToPcrTransfers().isEmpty());

        for (int i = 0; i < 200; i++) {
            sim.update(0.05d);
        }

        assertEquals(List.of("bag-a"), baggingMachine.getCompletedCorrelationIds());
        assertTrue(controller.getActivePrlToPcrTransfers().isEmpty());
        assertTrue(pcrConveyor.isEmpty());
    }

    @Test
    void shouldKeepPackOnPdcUntilItsDiversionPointIsReached() {
        ToteLoadPlan toteLoadPlan = new ToteLoadPlan(
                "tote-1",
                List.of(new PackPlan("pack-1", "bag-a", new PackDimensions(0.20f, 0.10f, 0.08f))));

        TippingMachine tippingMachine = new TippingMachine("tipper", 0.05d, 0.05d, 0.05d);
        SortingMachine sortingMachine = new SortingMachine("sorter", 0.05d);
        PdcConveyor pdcConveyor = new PdcConveyor("pdc", new ConveyorOccupancyModel(3.0f, 0.05f, 0.0f), 1.0f);
        PrlConveyor prl1 = new PrlConveyor("prl-1", 0.15f, new ConveyorOccupancyModel(2.0f, 0.05f, 0.0f));
        PcrConveyor pcrConveyor = new PcrConveyor("pcr", new ConveyorOccupancyModel(3.0f, 0.05f, 0.10f), 0.25d);
        BaggingMachine baggingMachine = new BaggingMachine("bagger", new BagSpec(0.34f, 0.28f, 0.22f), 0.20d, 0.20d, 0.20d, 0.20d);

        ToteToBagFlowController controller = new ToteToBagFlowController(
                toteLoadPlan,
                tippingMachine,
                sortingMachine,
                pdcConveyor,
                pcrConveyor,
                baggingMachine,
                new ToteToBagAssignmentPlanner(),
                List.of(prl1),
                List.of(new PdcDiversionDevice("diverter-1", "prl-1", 0d, 0.05d, 0.05d)),
                ignored -> 0.30d,
                (ignored, pack) -> 1.60f,
                ignored -> 0.30d,
                (ignored, pack) -> pack.getDimensions().length());

        SimulationWorld sim = new SimulationWorld();
        sim.addSimObject(tippingMachine);
        sim.addSimObject(sortingMachine);
        sim.addSimObject(pcrConveyor);
        sim.addSimObject(baggingMachine);
        sim.addController(controller);

        boolean sawPackOnPdcBeforeDiversion = false;
        boolean sawActivePdcDiversion = false;
        for (int i = 0; i < 80; i++) {
            sim.update(0.05d);
            if (!controller.getPdcLaneEntries().isEmpty() && controller.getActivePdcTransfers().isEmpty()) {
                sawPackOnPdcBeforeDiversion = true;
            }
            if (!controller.getActivePdcTransfers().isEmpty()) {
                sawActivePdcDiversion = true;
                break;
            }
        }

        assertTrue(sawPackOnPdcBeforeDiversion);
        assertTrue(sawActivePdcDiversion);
    }

    @Test
    void shouldExposeDiversionDeviceStateBeforePdcTransferStarts() {
        ToteLoadPlan toteLoadPlan = new ToteLoadPlan(
                "tote-1",
                List.of(new PackPlan("pack-1", "bag-a", new PackDimensions(0.20f, 0.10f, 0.08f))));

        TippingMachine tippingMachine = new TippingMachine("tipper", 0.05d, 0.05d, 0.05d);
        SortingMachine sortingMachine = new SortingMachine("sorter", 0.05d);
        PdcConveyor pdcConveyor = new PdcConveyor("pdc", new ConveyorOccupancyModel(3.0f, 0.05f, 0.0f), 2.0f);
        PrlConveyor prl1 = new PrlConveyor("prl-1", 0.15f, new ConveyorOccupancyModel(2.0f, 0.05f, 0.0f));
        PcrConveyor pcrConveyor = new PcrConveyor("pcr", new ConveyorOccupancyModel(3.0f, 0.05f, 0.10f), 0.25d);
        BaggingMachine baggingMachine = new BaggingMachine("bagger", new BagSpec(0.34f, 0.28f, 0.22f), 0.20d, 0.20d, 0.20d, 0.20d);

        ToteToBagFlowController controller = new ToteToBagFlowController(
                toteLoadPlan,
                tippingMachine,
                sortingMachine,
                pdcConveyor,
                pcrConveyor,
                baggingMachine,
                new ToteToBagAssignmentPlanner(),
                List.of(prl1),
                List.of(new PdcDiversionDevice("diverter-1", "prl-1", 0.10d, 0.10d, 0.10d)),
                ignored -> 0.30d,
                (ignored, pack) -> pack.getDimensions().length(),
                ignored -> 0.30d,
                (ignored, pack) -> pack.getDimensions().length());

        SimulationWorld sim = new SimulationWorld();
        sim.addSimObject(tippingMachine);
        sim.addSimObject(sortingMachine);
        sim.addSimObject(pcrConveyor);
        sim.addSimObject(baggingMachine);
        sim.addController(controller);

        boolean sawArmedBeforeTransfer = false;
        boolean sawTransferWhileDeviceBusy = false;
        for (int i = 0; i < 80; i++) {
            sim.update(0.05d);
            PdcDiversionDeviceState state = controller.getPdcDiversionDevices().getFirst().getState();
            if (controller.getActivePdcTransfers().isEmpty() && state == PdcDiversionDeviceState.ARMED) {
                sawArmedBeforeTransfer = true;
            }
            if (!controller.getActivePdcTransfers().isEmpty()
                    && state != PdcDiversionDeviceState.IDLE) {
                sawTransferWhileDeviceBusy = true;
                break;
            }
        }

        assertTrue(sawArmedBeforeTransfer);
        assertTrue(sawTransferWhileDeviceBusy);
    }

    private static Mesh anchorMesh() {
        return new Mesh(
                new Vec4[] {
                        new Vec4(0f, 0f, 0f, 1f),
                        new Vec4(0f, 0f, 0f, 1f),
                        new Vec4(0f, 0f, 0f, 1f)
                },
                new int[][] { {0, 1, 2} },
                "anchor");
    }
}
