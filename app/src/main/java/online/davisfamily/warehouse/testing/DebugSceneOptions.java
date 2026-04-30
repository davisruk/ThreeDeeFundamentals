package online.davisfamily.warehouse.testing;

public record DebugSceneOptions(DebugSceneKind activeScene) {
    public static final DebugSceneKind DEFAULT_SCENE = DebugSceneKind.TOTE_TO_BAG;

    public DebugSceneOptions {
        if (activeScene == null) {
            throw new IllegalArgumentException("activeScene must not be null");
        }
    }

    public static DebugSceneOptions parse(String[] args) {
        if (args != null) {
            for (String arg : args) {
                if (arg == null || !arg.startsWith("--scene=")) {
                    continue;
                }
                DebugSceneKind kind = DebugSceneKind.fromCliValue(arg.substring("--scene=".length()).trim());
                if (kind != null) {
                    return new DebugSceneOptions(kind);
                }
            }
        }
        return new DebugSceneOptions(DEFAULT_SCENE);
    }
}
