package online.davisfamily.warehouse.sim.dsp.osr.release.launch;

import java.util.List;

import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseTargetRegistry;

public interface OperationalRouteTargetAdmissionCatalog {
    List<OperationalRouteTargetAdmissionSnapshot> snapshotAdmissions();

    OsrProcessingReleaseTargetRegistry processingReleaseTargetRegistry();
}
