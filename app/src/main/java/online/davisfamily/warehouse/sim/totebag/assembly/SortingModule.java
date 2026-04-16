package online.davisfamily.warehouse.sim.totebag.assembly;

import java.util.function.Supplier;

import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.warehouse.sim.totebag.handoff.PackHandoffPoint;
import online.davisfamily.warehouse.sim.totebag.machine.SortingMachine;

public class SortingModule {
    private final SortingMachine sortingMachine;
    private final RenderableObject renderable;
    private final Supplier<PackHandoffPoint> intakePointSupplier;
    private final Supplier<PackHandoffPoint> outfeedPointSupplier;

    public SortingModule(
            SortingMachine sortingMachine,
            RenderableObject renderable,
            Supplier<PackHandoffPoint> intakePointSupplier,
            Supplier<PackHandoffPoint> outfeedPointSupplier) {
        if (sortingMachine == null
                || renderable == null
                || intakePointSupplier == null
                || outfeedPointSupplier == null) {
            throw new IllegalArgumentException("SortingModule inputs must not be null");
        }
        this.sortingMachine = sortingMachine;
        this.renderable = renderable;
        this.intakePointSupplier = intakePointSupplier;
        this.outfeedPointSupplier = outfeedPointSupplier;
    }

    public SortingMachine getSortingMachine() {
        return sortingMachine;
    }

    public RenderableObject getRenderable() {
        return renderable;
    }

    public PackHandoffPoint intakePoint() {
        return intakePointSupplier.get();
    }

    public PackHandoffPoint outfeedPoint() {
        return outfeedPointSupplier.get();
    }
}
