package online.davisfamily.warehouse.sim.dsp.p2p.arrival;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueue;

public final class DspP2pArrivalConsumerRuntimeFactory {

    public DspP2pArrivalConsumerRuntime create(
            SimulationWorld simulationWorld,
            List<P2pArrivalConsumerBinding> bindings) {
        if (simulationWorld == null) {
            throw new IllegalArgumentException("simulationWorld must not be null");
        }
        List<P2pArrivalConsumerBinding> validatedBindings = validateBindings(bindings);

        List<P2pArrivalConsumerController> controllers = new ArrayList<>();
        List<P2pTipperArrivalTarget> targets = new ArrayList<>();
        for (P2pArrivalConsumerBinding binding : validatedBindings) {
            controllers.add(new P2pArrivalConsumerController(
                    binding.sourceQueue(),
                    binding.admissionPolicy(),
                    binding.routeBinding(),
                    binding.payloadFactory(),
                    binding.target()));
            targets.add(binding.target());
        }

        controllers.forEach(simulationWorld::addController);
        return new DspP2pArrivalConsumerRuntime(controllers, targets);
    }

    private static List<P2pArrivalConsumerBinding> validateBindings(
            List<P2pArrivalConsumerBinding> bindings) {
        if (bindings == null) {
            throw new IllegalArgumentException("bindings must not be null");
        }
        List<P2pArrivalConsumerBinding> copyBuilder = new ArrayList<>();
        for (P2pArrivalConsumerBinding binding : bindings) {
            if (binding == null) {
                throw new IllegalArgumentException("bindings must not contain null");
            }
            copyBuilder.add(binding);
        }
        List<P2pArrivalConsumerBinding> copy = List.copyOf(copyBuilder);
        Set<String> targetIds = new HashSet<>();
        Set<StationRoutedToteArrivalQueue> sourceQueues =
                Collections.newSetFromMap(new IdentityHashMap<>());
        Set<TipperInputQueue> targetQueues =
                Collections.newSetFromMap(new IdentityHashMap<>());
        for (P2pArrivalConsumerBinding binding : copy) {
            if (!sourceQueues.add(binding.sourceQueue())) {
                throw new IllegalArgumentException(
                        "Duplicate P2P station arrival source queue: "
                                + binding.destination().targetId());
            }
            if (!targetQueues.add(binding.target().inputQueue())) {
                throw new IllegalArgumentException(
                        "Duplicate P2P tipper input target queue: "
                                + binding.destination().targetId());
            }
            if (!targetIds.add(binding.destination().targetId())) {
                throw new IllegalArgumentException(
                        "Duplicate P2P arrival target ID: "
                                + binding.destination().targetId());
            }
        }
        return copy;
    }
}
