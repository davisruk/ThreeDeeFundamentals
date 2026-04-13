package online.davisfamily.warehouse.sim.totebag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationWorld;

class ToteToBagFlowControllerTest {

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

        for (int i = 0; i < 300; i++) {
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
}
