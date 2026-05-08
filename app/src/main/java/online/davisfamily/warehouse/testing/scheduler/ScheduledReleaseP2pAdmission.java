package online.davisfamily.warehouse.testing.scheduler;

import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.p2p.P2pAdmission;
import online.davisfamily.warehouse.sim.dsp.p2p.P2pAdmissionResult;
import online.davisfamily.warehouse.sim.dsp.p2p.P2pAdmissionSnapshot;
import online.davisfamily.warehouse.sim.totebag.control.ToteToBagFlowController;

public class ScheduledReleaseP2pAdmission implements P2pAdmission {
    private final ScheduledTipperToteReleaseCatalog releaseCatalog;
    private final ToteToBagFlowController flowController;

    public ScheduledReleaseP2pAdmission(
            ScheduledTipperToteReleaseCatalog releaseCatalog,
            ToteToBagFlowController flowController) {
        if (releaseCatalog == null) {
            throw new IllegalArgumentException("releaseCatalog must not be null");
        }
        if (flowController == null) {
            throw new IllegalArgumentException("flowController must not be null");
        }
        this.releaseCatalog = releaseCatalog;
        this.flowController = flowController;
    }

    @Override
    public P2pAdmissionResult canAdmit(NotionalToteOrder order, P2pAdmissionSnapshot snapshot) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }

        ScheduledTipperToteRelease release = releaseCatalog.findByOrderId(order.orderId()).orElse(null);
        if (release == null) {
            return P2pAdmissionResult.rejectedResult("No scheduled P2P tote load plan for order " + order.orderId());
        }
        if (flowController.canAdmit(release.toteLoadPlan())) {
            return P2pAdmissionResult.acceptedResult();
        }
        return P2pAdmissionResult.rejectedResult("P2P cannot admit tote " + release.toteLoadPlan().getToteId());
    }
}
