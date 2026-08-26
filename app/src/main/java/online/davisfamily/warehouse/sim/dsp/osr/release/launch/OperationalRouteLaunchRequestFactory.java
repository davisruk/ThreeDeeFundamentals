package online.davisfamily.warehouse.sim.dsp.osr.release.launch;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseRequest;

/** Creates source-specific and source-neutral route launch requests. */
public final class OperationalRouteLaunchRequestFactory {
    private OperationalRouteLaunchRequestFactory() {
    }

    public static OperationalRouteLaunchRequest fromOsr(
            OsrProcessingReleaseRequest request,
            OperationalRouteDestination destination) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        InboundToteManifest manifest = request.manifest();
        OperationalPhysicalToteIdentity identity = new OperationalPhysicalToteIdentity(
                OperationalPhysicalToteSource.OSR,
                manifest.physicalToteId(),
                manifest.orderSheetKey(),
                manifest.orderType(),
                manifest.serviceCentreId(),
                PhysicalToteRole.INBOUND_PACK,
                manifest.sourceSequenceNumber());
        OperationalPhysicalToteReleaseRequest releaseRequest =
                new OperationalPhysicalToteReleaseRequest(
                        identity,
                        distinctPharmacyIds(manifest.items()),
                        request.releaseTime(),
                        request.p2pAssignment());
        return new OperationalRouteLaunchRequest(releaseRequest, destination);
    }

    public static OperationalRouteLaunchRequest fromOperational(
            OperationalPhysicalToteReleaseRequest releaseRequest,
            OperationalRouteDestination destination) {
        if (releaseRequest == null) {
            throw new IllegalArgumentException("releaseRequest must not be null");
        }
        if (releaseRequest.source() == OperationalPhysicalToteSource.OSR) {
            throw new IllegalArgumentException(
                    "OSR release requests must be created with fromOsr");
        }
        return new OperationalRouteLaunchRequest(releaseRequest, destination);
    }

    private static List<String> distinctPharmacyIds(List<DspOrderItem> items) {
        Set<String> pharmacyIds = new LinkedHashSet<>();
        for (DspOrderItem item : items) {
            if (item == null) {
                throw new IllegalArgumentException("manifest items must not contain null");
            }
            pharmacyIds.add(item.pharmacyId());
        }
        return new ArrayList<>(pharmacyIds);
    }
}
