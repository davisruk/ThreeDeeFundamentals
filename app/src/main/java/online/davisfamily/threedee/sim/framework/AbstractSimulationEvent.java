package online.davisfamily.threedee.sim.framework;

public abstract class AbstractSimulationEvent<P extends SimulationEventPayload> implements SimulationEvent<P> {
	private final P payload;
	protected AbstractSimulationEvent(P payload) {
		this.payload = payload;
	}
	
	@Override
	public P getPayload() {
		return payload;
	}
}
