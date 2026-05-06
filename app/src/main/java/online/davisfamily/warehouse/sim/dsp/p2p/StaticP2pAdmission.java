package online.davisfamily.warehouse.sim.dsp.p2p;

import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;

public class StaticP2pAdmission implements P2pAdmission {
    private final P2pAdmissionResult result;

    public StaticP2pAdmission(P2pAdmissionResult result) {
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        this.result = result;
    }

    @Override
    public P2pAdmissionResult canAdmit(NotionalToteOrder order, P2pAdmissionSnapshot snapshot) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        return result;
    }
}
