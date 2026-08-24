package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import java.util.List;

public final class DspP2pArrivalConsumerRuntime implements AutoCloseable {
    private final List<P2pArrivalConsumerController> controllers;
    private final List<P2pTipperArrivalTarget> targets;
    private boolean closed;

    DspP2pArrivalConsumerRuntime(
            List<P2pArrivalConsumerController> controllers,
            List<P2pTipperArrivalTarget> targets) {
        if (controllers == null || targets == null) {
            throw new IllegalArgumentException("runtime dependencies must not be null");
        }
        if (controllers.stream().anyMatch(controller -> controller == null)
                || targets.stream().anyMatch(target -> target == null)) {
            throw new IllegalArgumentException("runtime dependencies must not contain null");
        }
        if (controllers.size() != targets.size()) {
            throw new IllegalArgumentException("controller and target counts must match");
        }
        this.controllers = List.copyOf(controllers);
        this.targets = List.copyOf(targets);
    }

    public List<P2pArrivalConsumerControllerSnapshot> controllerSnapshots() {
        return controllers.stream()
                .map(P2pArrivalConsumerController::snapshot)
                .toList();
    }

    public List<P2pTipperArrivalTargetSnapshot> targetSnapshots() {
        return targets.stream()
                .map(P2pTipperArrivalTarget::snapshot)
                .toList();
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
    }
}
