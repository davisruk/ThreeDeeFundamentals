package online.davisfamily.threedee.sim.framework;

public interface StatefulSimObject<S extends Enum<S>> extends SimObject{
	S getState();
}
