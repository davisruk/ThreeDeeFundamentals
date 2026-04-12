package online.davisfamily.threedee.debug;

import java.util.List;

@FunctionalInterface
public interface Inspectable {
    List<String> describe();
}
