package online.davisfamily.warehouse.sim.dsp.thirdparty;

public record ThirdPartyCompletion(ThirdPartyVisit visit) {

    public ThirdPartyCompletion {
        if (visit == null) {
            throw new IllegalArgumentException("visit must not be null");
        }
    }
}
