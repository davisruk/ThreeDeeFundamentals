package online.davisfamily.threedee.sim.framework.objects;

public interface StatefulSimObject<S extends Enum<S>> extends SimObject{
	S getState();
}
