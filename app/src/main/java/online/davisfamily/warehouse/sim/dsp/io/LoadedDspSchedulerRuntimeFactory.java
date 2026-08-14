package online.davisfamily.warehouse.sim.dsp.io;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.routing.DspRouteDeriver;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

public class LoadedDspSchedulerRuntimeFactory {
    private final DspRouteDeriver routeDeriver;

    public LoadedDspSchedulerRuntimeFactory(DspRouteDeriver routeDeriver) {
        if (routeDeriver == null) {
            throw new IllegalArgumentException("routeDeriver must not be null");
        }
        this.routeDeriver = routeDeriver;
    }

    public DspSchedulerRuntimeState createRuntimeState(
            LoadedDspData data,
            Map<StationType, StationAdmissionSnapshot> stationAdmissions,
            Optional<String> activeServiceCentreId) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (stationAdmissions == null) {
            throw new IllegalArgumentException("stationAdmissions must not be null");
        }
        if (activeServiceCentreId == null) {
            throw new IllegalArgumentException("activeServiceCentreId must not be null");
        }

        List<DspSchedulerOrderState> orderStates = new ArrayList<>();
        for (var order : data.orders()) {
            RouteRequirements routeRequirements = routeDeriver.derive(order);
            orderStates.add(new DspSchedulerOrderState(order, routeRequirements, DspOrderStatus.WAITING));
        }

        return new DspSchedulerRuntimeState(new WarehouseSchedulerSnapshot(
                orderStates,
                stationAdmissions,
                data.startupReadyPreparedLineKeys(),
                activeServiceCentreId));
    }
}
