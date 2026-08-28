package online.davisfamily.warehouse.sim.dsp.station.processing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.transport.routing.StationRoutedToteArrivalQueue;

/**
 * Composes the production station-processing claimant, completion, and consume boundaries.
 *
 * <p>All supplied values are validated before the first controller is registered with the
 * simulation world. The factory does not construct station-domain machinery; callers provide the
 * exact targets and completion controllers that own those domains.</p>
 */
public final class DspStationProcessingRuntimeFactory {

    public DspStationProcessingRuntime create(
            SimulationWorld simulationWorld,
            StationProcessingCoordinator coordinator,
            List<StationProcessingBinding> bindings,
            List<StationProcessingCompletionController> completionControllers) {
        requireNonNull(simulationWorld, "simulationWorld");
        requireNonNull(coordinator, "coordinator");

        List<StationProcessingBinding> validatedBindings = validateBindings(bindings, coordinator);
        List<StationProcessingCompletionController> validatedCompletions =
                validateCompletionControllers(completionControllers, validatedBindings, coordinator);

        List<StationProcessingBinding> sortedBindings = new ArrayList<>(validatedBindings);
        sortedBindings.sort(Comparator
                .comparing((StationProcessingBinding binding) ->
                        binding.destination().stationType().name())
                .thenComparing(binding -> binding.destination().targetId()));

        List<StationProcessingCompletionController> sortedCompletions =
                new ArrayList<>(validatedCompletions);
        sortedCompletions.sort(Comparator.comparing(
                StationProcessingCompletionController::processingControllerId));

        List<StationArrivalClaimController> claimantControllers = new ArrayList<>();
        List<OperationalRouteDestination> destinations = new ArrayList<>();
        for (StationProcessingBinding binding : sortedBindings) {
            claimantControllers.add(new StationArrivalClaimController(binding));
            destinations.add(binding.destination());
        }

        StationConsumedToteController consumedToteController =
                new StationConsumedToteController(coordinator);

        for (StationArrivalClaimController controller : claimantControllers) {
            simulationWorld.addController(controller);
        }
        for (StationProcessingCompletionController controller : sortedCompletions) {
            simulationWorld.addController(controller);
        }
        simulationWorld.addController(consumedToteController);

        return new DspStationProcessingRuntime(
                claimantControllers,
                sortedCompletions,
                consumedToteController,
                coordinator,
                destinations);
    }

    private static List<StationProcessingBinding> validateBindings(
            List<StationProcessingBinding> bindings,
            StationProcessingCoordinator coordinator) {
        requireNonNull(bindings, "bindings");

        List<StationProcessingBinding> copyBuilder = new ArrayList<>();
        for (StationProcessingBinding binding : bindings) {
            if (binding == null) {
                throw new IllegalArgumentException("bindings must not contain null");
            }
            copyBuilder.add(binding);
        }
        List<StationProcessingBinding> copy = List.copyOf(copyBuilder);

        Set<StationRoutedToteArrivalQueue> sourceQueues =
                Collections.newSetFromMap(new IdentityHashMap<>());
        Set<StationProcessingTarget> targets =
                Collections.newSetFromMap(new IdentityHashMap<>());
        Set<OperationalRouteDestination> destinations = new HashSet<>();
        for (StationProcessingBinding binding : copy) {
            StationRoutedToteArrivalQueue sourceQueue = binding.sourceQueue();
            StationProcessingTarget target = binding.target();
            OperationalRouteDestination sourceDestination = sourceQueue.destination();
            OperationalRouteDestination targetDestination = target.destination();

            if (!sourceQueues.add(sourceQueue)) {
                throw new IllegalArgumentException(
                        "Duplicate station processing source queue: " + sourceDestination);
            }
            if (!targets.add(target)) {
                throw new IllegalArgumentException(
                        "Duplicate station processing target: " + targetDestination);
            }
            if (!destinations.add(sourceDestination)) {
                throw new IllegalArgumentException(
                        "Duplicate station processing destination: " + sourceDestination);
            }
            if (!sourceDestination.equals(targetDestination)) {
                throw new IllegalArgumentException(
                        "Station processing source and target destinations must match");
            }
            if (!isSupportedStationType(sourceDestination.stationType())) {
                throw new IllegalArgumentException(
                        "Unsupported station processing station type: "
                                + sourceDestination.stationType());
            }
            if (target.coordinator() != coordinator) {
                throw new IllegalArgumentException(
                        "Station processing target must use the supplied coordinator");
            }
        }
        return copy;
    }

    private static List<StationProcessingCompletionController> validateCompletionControllers(
            List<StationProcessingCompletionController> completionControllers,
            List<StationProcessingBinding> bindings,
            StationProcessingCoordinator coordinator) {
        requireNonNull(completionControllers, "completionControllers");

        List<StationProcessingCompletionController> copyBuilder = new ArrayList<>();
        for (StationProcessingCompletionController controller : completionControllers) {
            if (controller == null) {
                throw new IllegalArgumentException(
                        "completionControllers must not contain null");
            }
            copyBuilder.add(controller);
        }
        List<StationProcessingCompletionController> copy = List.copyOf(copyBuilder);

        Map<OperationalRouteDestination, StationType> bindingTypes = new HashMap<>();
        for (StationProcessingBinding binding : bindings) {
            bindingTypes.put(binding.destination(), binding.destination().stationType());
        }

        Set<String> ids = new HashSet<>();
        Map<OperationalRouteDestination, Integer> coverage = new HashMap<>();
        for (StationProcessingCompletionController controller : copy) {
            String processingControllerId = controller.processingControllerId();
            if (processingControllerId == null || processingControllerId.isBlank()) {
                throw new IllegalArgumentException(
                        "completion controller id must not be blank");
            }
            if (!ids.add(processingControllerId)) {
                throw new IllegalArgumentException(
                        "Duplicate station processing completion controller id: "
                                + processingControllerId);
            }
            if (controller.coordinator() != coordinator) {
                throw new IllegalArgumentException(
                        "Station processing completion controller must use the supplied coordinator");
            }

            Set<OperationalRouteDestination> controllerDestinations = controller.destinations();
            if (controllerDestinations == null || controllerDestinations.isEmpty()) {
                throw new IllegalArgumentException(
                        "completion controller destinations must not be null or empty");
            }
            for (OperationalRouteDestination destination : controllerDestinations) {
                if (destination == null) {
                    throw new IllegalArgumentException(
                            "completion controller destinations must not contain null");
                }
                StationType bindingType = bindingTypes.get(destination);
                if (bindingType == null) {
                    throw new IllegalArgumentException(
                            "Completion controller covers a destination absent from bindings: "
                                    + destination);
                }
                if (bindingType == StationType.P2P) {
                    throw new IllegalArgumentException(
                            "P2P destination must not be covered by a completion controller: "
                                    + destination);
                }
                coverage.merge(destination, 1, Integer::sum);
            }
        }

        for (Map.Entry<OperationalRouteDestination, StationType> entry : bindingTypes.entrySet()) {
            int count = coverage.getOrDefault(entry.getKey(), 0);
            if (entry.getValue() == StationType.P2P) {
                if (count != 0) {
                    throw new IllegalArgumentException(
                            "P2P destination must not be covered by a completion controller: "
                                    + entry.getKey());
                }
            } else if (count != 1) {
                throw new IllegalArgumentException(
                        "Third Party/Adapting destination must have exactly one completion controller: "
                                + entry.getKey());
            }
        }
        return copy;
    }

    private static boolean isSupportedStationType(StationType stationType) {
        return stationType == StationType.THIRD_PARTY
                || stationType == StationType.ADAPTING
                || stationType == StationType.P2P;
    }

    private static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
    }
}
