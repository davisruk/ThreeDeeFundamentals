package online.davisfamily.warehouse.sim.dsp.osr.release;

import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.scheduler.SchedulerCommand;

/**
 * Source-neutral command for releasing one physical tote into warehouse routing.
 */
public interface OperationalPhysicalToteReleaseCommand extends SchedulerCommand {
    PhysicalToteId physicalToteId();

    OrderSheetKey orderSheetKey();

    String serviceCentreId();

    String releaseTargetId();

    Optional<P2pPhysicalToteAssignment> proposedP2pAssignment();

    OperationalPhysicalToteSource source();
}
