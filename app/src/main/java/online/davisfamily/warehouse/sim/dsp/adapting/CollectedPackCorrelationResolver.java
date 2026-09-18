package online.davisfamily.warehouse.sim.dsp.adapting;

@FunctionalInterface
public interface CollectedPackCorrelationResolver {
    String resolve(AdaptedLineRecord collectedLine, int packOrdinal);
}
