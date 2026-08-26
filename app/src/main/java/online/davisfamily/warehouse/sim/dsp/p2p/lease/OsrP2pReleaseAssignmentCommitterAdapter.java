package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.osr.release.ReleasePhysicalToteFromOsrCommand;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;

/**
 * Compatibility adapter for callers that still provide the manifest-based OSR
 * committer contract.
 */
public final class OsrP2pReleaseAssignmentCommitterAdapter
        implements OperationalP2pReleaseAssignmentCommitter {
    private final InboundToteManifestCatalog manifestCatalog;
    private final P2pReleaseAssignmentCommitter delegate;

    public OsrP2pReleaseAssignmentCommitterAdapter(
            InboundToteManifestCatalog manifestCatalog,
            P2pReleaseAssignmentCommitter delegate) {
        if (manifestCatalog == null) {
            throw new IllegalArgumentException("manifestCatalog must not be null");
        }
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.manifestCatalog = manifestCatalog;
        this.delegate = delegate;
    }

    @Override
    public P2pReleaseAssignmentCommit prepare(P2pReleaseAssignmentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.source() != OperationalPhysicalToteSource.OSR) {
            throw new IllegalArgumentException(
                    "OSR P2P committer adapter cannot process source " + request.source());
        }

        InboundToteManifest manifest = manifestCatalog
                .findByPhysicalToteId(request.physicalToteId())
                .orElseThrow(() -> new IllegalStateException(
                        "No inbound manifest exists for OSR physical tote "
                                + request.physicalToteId().value()));
        if (!manifest.orderSheetKey().equals(request.orderSheetKey())
                || !manifest.serviceCentreId().equals(request.serviceCentreId())) {
            throw new IllegalStateException(
                    "OSR manifest identity does not match the source-neutral request");
        }

        ReleasePhysicalToteFromOsrCommand command =
                new ReleasePhysicalToteFromOsrCommand(
                        request.physicalToteId(),
                        request.orderSheetKey(),
                        request.serviceCentreId(),
                        request.releaseTargetId(),
                        request.proposedP2pAssignment());
        return delegate.prepare(command, manifest);
    }
}
