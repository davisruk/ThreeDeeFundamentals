package online.davisfamily.warehouse.sim.totebag.assembly;

import online.davisfamily.warehouse.sim.totebag.machine.SortingMachine;

public class SortingInstallation {
    private final SortingMachine sortingMachine;
    private final SortingModule sortingModule;

    public SortingInstallation(SortingMachine sortingMachine, SortingModule sortingModule) {
        if (sortingMachine == null || sortingModule == null) {
            throw new IllegalArgumentException("Sorting installation inputs must not be null");
        }
        this.sortingMachine = sortingMachine;
        this.sortingModule = sortingModule;
    }

    public SortingMachine getSortingMachine() {
        return sortingMachine;
    }

    public SortingModule getSortingModule() {
        return sortingModule;
    }
}
