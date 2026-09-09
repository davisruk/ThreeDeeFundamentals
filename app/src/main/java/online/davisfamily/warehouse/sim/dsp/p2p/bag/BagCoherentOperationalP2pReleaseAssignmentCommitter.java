package online.davisfamily.warehouse.sim.dsp.p2p.bag;

import java.util.Objects;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.osr.release.ReleasePhysicalToteFromOsrCommand;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.OperationalP2pReleaseAssignmentCommitter;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pReleaseAssignmentCommit;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pReleaseAssignmentCommitter;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pReleaseAssignmentRequest;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.StrictP2pReleaseAssignmentCommitter;

/**
 * Adds bag-correlation validation and commit to the existing exact P2P tote
 * assignment commit.
 *
 * <p>Both legacy OSR and source-neutral operational callers are supported. In
 * either form the existing lease/tote commit is prepared first, correlation
 * ownership is then prevalidated, and the returned action commits those two
 * changes in that order.</p>
 */
public final class BagCoherentOperationalP2pReleaseAssignmentCommitter
        implements OperationalP2pReleaseAssignmentCommitter,
        P2pReleaseAssignmentCommitter {
    private final P2pReleaseAssignmentCommitter legacyDelegate;
    private final OperationalP2pReleaseAssignmentCommitter operationalDelegate;
    private final P2pBagCorrelationRequirementCatalog requirementCatalog;
    private final P2pBagCorrelationAssignmentRegistry assignmentRegistry;

    public BagCoherentOperationalP2pReleaseAssignmentCommitter(
            P2pReleaseAssignmentCommitter legacyDelegate,
            OperationalP2pReleaseAssignmentCommitter operationalDelegate,
            P2pBagCorrelationRequirementCatalog requirementCatalog,
            P2pBagCorrelationAssignmentRegistry assignmentRegistry) {
        this.legacyDelegate = Objects.requireNonNull(
                legacyDelegate, "legacyDelegate must not be null");
        this.operationalDelegate = Objects.requireNonNull(
                operationalDelegate, "operationalDelegate must not be null");
        this.requirementCatalog = Objects.requireNonNull(
                requirementCatalog, "requirementCatalog must not be null");
        this.assignmentRegistry = Objects.requireNonNull(
                assignmentRegistry, "assignmentRegistry must not be null");
    }

    public BagCoherentOperationalP2pReleaseAssignmentCommitter(
            OperationalP2pReleaseAssignmentCommitter delegate,
            P2pBagCorrelationRequirementCatalog requirementCatalog,
            P2pBagCorrelationAssignmentRegistry assignmentRegistry) {
        this(
                delegate instanceof P2pReleaseAssignmentCommitter legacy
                        ? legacy
                        : P2pReleaseAssignmentCommitter.NO_OP,
                delegate,
                requirementCatalog,
                assignmentRegistry);
    }

    public BagCoherentOperationalP2pReleaseAssignmentCommitter(
            P2pReleaseAssignmentCommitter delegate,
            P2pBagCorrelationRequirementCatalog requirementCatalog,
            P2pBagCorrelationAssignmentRegistry assignmentRegistry) {
        this(
                delegate,
                delegate instanceof OperationalP2pReleaseAssignmentCommitter operational
                        ? operational
                        : OperationalP2pReleaseAssignmentCommitter.NO_OP,
                requirementCatalog,
                assignmentRegistry);
    }

    public BagCoherentOperationalP2pReleaseAssignmentCommitter(
            StrictP2pReleaseAssignmentCommitter delegate,
            P2pBagCorrelationRequirementCatalog requirementCatalog,
            P2pBagCorrelationAssignmentRegistry assignmentRegistry) {
        this(
                (P2pReleaseAssignmentCommitter) delegate,
                (OperationalP2pReleaseAssignmentCommitter) delegate,
                requirementCatalog,
                assignmentRegistry);
    }

    @Override
    public P2pReleaseAssignmentCommit prepare(P2pReleaseAssignmentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        P2pReleaseAssignmentCommit existingCommit = operationalDelegate.prepare(request);
        Runnable correlationCommit = prepareCorrelationCommit(request);
        return () -> {
            existingCommit.commit();
            correlationCommit.run();
        };
    }

    @Override
    public P2pReleaseAssignmentCommit prepare(
            ReleasePhysicalToteFromOsrCommand command,
            InboundToteManifest manifest) {
        if (command == null || manifest == null) {
            throw new IllegalArgumentException("command and manifest must not be null");
        }
        P2pReleaseAssignmentCommit existingCommit = legacyDelegate.prepare(command, manifest);
        P2pReleaseAssignmentRequest request = P2pReleaseAssignmentRequest.from(command);
        Runnable correlationCommit = prepareCorrelationCommit(request);
        return () -> {
            existingCommit.commit();
            correlationCommit.run();
        };
    }

    public P2pBagCorrelationAssignmentSnapshot assignmentSnapshot() {
        return assignmentRegistry.snapshot();
    }

    private Runnable prepareCorrelationCommit(P2pReleaseAssignmentRequest request) {
        var requirements = requirementCatalog.requirementsFor(request);
        Optional<P2pPhysicalToteAssignment> proposed = request.proposedP2pAssignment();
        if (requirements.isEmpty()) {
            return () -> { };
        }
        P2pPhysicalToteAssignment assignment = proposed.orElseThrow(() ->
                new IllegalStateException(
                        "A tote with bag correlations must carry a P2P assignment"));
        return assignmentRegistry.prepareCommit(requirements, assignment);
    }
}
