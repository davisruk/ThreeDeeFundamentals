package online.davisfamily.warehouse.sim.dsp.transport.routing;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;

public final class SimulationWorldWarehouseTransportPublisher
        implements WarehouseTransportPublisher {
    private final SimulationWorld simulationWorld;
    private final List<RenderableObject> renderables;
    private final Set<PhysicalToteId> publishedPhysicalToteIds = new LinkedHashSet<>();

    public SimulationWorldWarehouseTransportPublisher(
            SimulationWorld simulationWorld,
            List<RenderableObject> renderables) {
        if (simulationWorld == null) {
            throw new IllegalArgumentException("simulationWorld must not be null");
        }
        if (renderables == null) {
            throw new IllegalArgumentException("renderables must not be null");
        }
        if (renderables.stream().anyMatch(renderable -> renderable == null)) {
            throw new IllegalArgumentException("renderables must not contain null");
        }
        this.simulationWorld = simulationWorld;
        this.renderables = renderables;
    }

    @Override
    public boolean contains(PhysicalToteId physicalToteId) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        return publishedPhysicalToteIds.contains(physicalToteId);
    }

    @Override
    public void publish(RoutedPhysicalTote routedTote) {
        if (routedTote == null) {
            throw new IllegalArgumentException("routedTote must not be null");
        }
        PhysicalToteId physicalToteId = routedTote.physicalToteId();
        if (!physicalToteId.value().equals(routedTote.tote().getId())
                || routedTote.tote().getRenderable() != routedTote.renderable()
                || !physicalToteId.value().equals(routedTote.renderable().id)) {
            throw new IllegalArgumentException(
                    "Routed tote publication identity is inconsistent: "
                            + physicalToteId.value());
        }
        if (publishedPhysicalToteIds.contains(physicalToteId)) {
            throw new IllegalArgumentException(
                    "Physical tote is already published: " + physicalToteId.value());
        }
        if (renderables.stream().anyMatch(renderable -> renderable == null)) {
            throw new IllegalStateException(
                    "renderables must not contain null during publication");
        }
        boolean renderableIdentityPresent = renderables.stream()
                .anyMatch(renderable -> renderable == routedTote.renderable());
        boolean renderableIdPresent = renderables.stream()
                .anyMatch(renderable -> physicalToteId.value().equals(renderable.id));
        if (renderableIdentityPresent || renderableIdPresent) {
            throw new IllegalArgumentException(
                    "Physical tote renderable is already present: "
                            + physicalToteId.value());
        }

        simulationWorld.addTrackableObject(routedTote.tote());
        renderables.add(routedTote.renderable());
        publishedPhysicalToteIds.add(physicalToteId);
    }
}
