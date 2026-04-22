package online.davisfamily.warehouse.testing;

public enum DebugSceneKind {
    TOTE_TO_BAG("tote-to-bag"),
    TIPPER_TRACK("tipper-track"),
    TIPPER_TO_RECEIVER("tipper-to-receiver"),
    OVAL_TRACK("oval-track"),
    PARALLEL_TRACK("parallel-track"),
    STRAIGHT_CONVEYOR("straight-conveyor");

    private final String cliValue;

    DebugSceneKind(String cliValue) {
        this.cliValue = cliValue;
    }

    public String cliValue() {
        return cliValue;
    }

    public static DebugSceneKind fromCliValue(String value) {
        if (value == null) {
            return null;
        }
        for (DebugSceneKind kind : values()) {
            if (kind.cliValue.equalsIgnoreCase(value)) {
                return kind;
            }
        }
        return null;
    }
}
