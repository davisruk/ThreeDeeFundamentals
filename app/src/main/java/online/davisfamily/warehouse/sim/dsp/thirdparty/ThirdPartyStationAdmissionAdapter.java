package online.davisfamily.warehouse.sim.dsp.thirdparty;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;

public class ThirdPartyStationAdmissionAdapter {
    private static final String CAPACITY_BLOCKED_REASON = "Third Party area has no capacity";

    public StationAdmissionSnapshot admissionFor(
            Optional<ThirdPartyVisit> candidateVisit,
            ThirdPartyAreaSnapshot areaSnapshot) {
        if (candidateVisit == null) {
            throw new IllegalArgumentException("candidateVisit must not be null");
        }
        if (areaSnapshot == null) {
            throw new IllegalArgumentException("areaSnapshot must not be null");
        }

        StationCapacity capacity = new StationCapacity(
                areaSnapshot.config().maxConcurrentVisits(),
                areaSnapshot.config().waitingCapacity());
        StationSnapshot stationSnapshot = new StationSnapshot(
                StationType.THIRD_PARTY,
                areaSnapshot.activeCount(),
                areaSnapshot.waitingCount());
        boolean admissionOpen = candidateVisit.isEmpty() || capacity.canAccept(stationSnapshot);

        return new StationAdmissionSnapshot(
                StationType.THIRD_PARTY,
                capacity,
                stationSnapshot,
                admissionOpen,
                admissionOpen ? "" : CAPACITY_BLOCKED_REASON);
    }
}
