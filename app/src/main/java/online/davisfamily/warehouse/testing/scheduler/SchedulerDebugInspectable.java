package online.davisfamily.warehouse.testing.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import online.davisfamily.threedee.debug.Inspectable;

public class SchedulerDebugInspectable implements Inspectable {
    private final ScheduledDebugToteInjectorController controller;

    public SchedulerDebugInspectable(ScheduledDebugToteInjectorController controller) {
        if (controller == null) {
            throw new IllegalArgumentException("controller must not be null");
        }
        this.controller = controller;
    }

    @Override
    public List<String> describe() {
        SchedulerDebugSnapshot snapshot = controller.debugSnapshot();
        List<String> lines = new ArrayList<>();
        lines.add("Scheduler: debug");
        lines.add("Active SC: " + snapshot.activeServiceCentreId().orElse("none"));
        lines.add("Waiting: " + formatList(snapshot.waitingOrderIds()));
        lines.add("Release: " + snapshot.releaseOrderId().orElse("none"));
        lines.add("Blocked SC: " + snapshot.blockedServiceCentreId().orElse("none"));
        lines.add("Blocked candidates: " + formatList(snapshot.blockedCandidateOrderIds()));
        snapshot.blockedReasons().stream()
                .limit(4)
                .forEach(reason -> lines.add("Block: " + reason));
        lines.add("Last applied: " + snapshot.lastAppliedOrderId().orElse("none"));
        lines.add("Last deferred: " + formatOrderAndReason(snapshot.lastDeferredOrderId(), snapshot.lastDeferredReason()));
        lines.add("Last rejected: " + formatOrderAndReason(snapshot.lastRejectedOrderId(), snapshot.lastRejectedReason()));
        return List.copyOf(lines);
    }

    private static String formatList(List<String> values) {
        if (values.isEmpty()) {
            return "none";
        }
        return values.stream().collect(Collectors.joining(", "));
    }

    private static String formatOrderAndReason(Optional<String> orderId, Optional<String> reason) {
        if (orderId.isEmpty()) {
            return "none";
        }
        return orderId.get() + reason.map(value -> " - " + value).orElse("");
    }
}
