package online.davisfamily.warehouse.sim.dsp.p2p;

import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;

public interface P2pAdmission {
    P2pAdmissionResult canAdmit(NotionalToteOrder order, P2pAdmissionSnapshot snapshot);
}
