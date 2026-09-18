package online.davisfamily.warehouse.sim.dsp.thirdparty;

@FunctionalInterface
public interface ThirdPartyPackCorrelationResolver {
    String resolve(ThirdPartyVisit visit, ThirdPartyLineWork lineWork, int packOrdinal);
}
