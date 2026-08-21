package online.davisfamily.warehouse.testing;

import java.util.List;

import javax.swing.JRootPane;

import online.davisfamily.threedee.camera.CameraPosition;
import online.davisfamily.threedee.dimensions.ViewDimensions;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.lights.DirectionalLight;
import online.davisfamily.threedee.scene.BaseScene;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.rendering.model.tote.RenderableToteFactory;
import online.davisfamily.warehouse.rendering.model.tote.ToteGeometry;

public class TestScene extends BaseScene{	

	private RenderableObject rTote;
	private final DirectionalLight lightDirection;
	private final DebugSceneKind activeSceneKind;
	private DebugSceneRuntime activeRuntime;

	
	public TestScene (JRootPane pane, ViewDimensions dimensions, DebugSceneOptions options) {
		super(pane, dimensions,	CameraPosition.backRight());
		lightDirection = new DirectionalLight(new Vec3(-0.2f, -0.8f, 1.0f), 0.55f, 0.45f);
		activeSceneKind = options.activeScene();
		activeRuntime = installScene(activeSceneKind);
	}

	void resetActiveScene() {
		activeRuntime.close();
		selectionManager.clear();
		inspectionRegistry.clear();
		objects.clear();
		rTote = null;
		sim = new SimulationWorld();
		activeRuntime = installScene(activeSceneKind);
	}

	DebugSceneKind activeSceneKind() {
		return activeSceneKind;
	}

	@Override
	protected void processFrameCommands() {
		if (inputState.consumeSimulationResetRequest()) {
			resetActiveScene();
		}
	}
		
	@Override
	public void executeChildRenderOperations(double dtSeconds) {
//		if (inputState.isSet(Mode.SHOW_PATH))
//			debug.drawPathForObject(rTote, camera.getView(), projection);
		activeRuntime.syncVisuals(dtSeconds);
		drawObject(objects, dtSeconds, lightDirection);
	}

	private DebugSceneRuntime installScene(DebugSceneKind sceneKind) {
		return switch (sceneKind) {
			case ADAPTING -> new AdaptingDebugRig(tr, sim, objects, inspectionRegistry);
			case THIRD_PARTY -> new ThirdPartyDebugRig(tr, sim, objects, inspectionRegistry);
			case DSP_WAREHOUSE_TRANSPORT -> new DspWarehouseTransportDebugRig(
					tr, sim, objects, inspectionRegistry);
			case TOTE_TO_BAG -> new ToteToBagDebugRig(tr, sim, objects, inspectionRegistry);
			case TIPPER_TRACK -> new ToteTrackTipperDebugRig(tr, sim, objects, inspectionRegistry);
			case TIPPER_TO_RECEIVER -> new TipperToReceiverDebugRig(tr, sim, objects, inspectionRegistry);
			case STRAIGHT_CONVEYOR -> {
				WarehouseTrackFactory.setupStraightConveyorTest(tr, sim, objects);
				yield DebugSceneRuntime.noop();
			}
			case OVAL_TRACK -> {
				ToteGeometry tote = setupTote();
				WarehouseTrackFactory.setupOvalTrack(tote, rTote, tr, sim, objects, inspectionRegistry);
				yield DebugSceneRuntime.noop();
			}
			case PARALLEL_TRACK -> {
				ToteGeometry tote = setupTote();
				WarehouseTrackFactory.setupParallelTracks(tote, rTote, tr, sim, objects, inspectionRegistry);
				yield DebugSceneRuntime.noop();
			}
			case INLINE_TRANSFER -> {
				WarehouseTrackFactory.setupInlineTransferTargets(tr, sim, objects, inspectionRegistry);
				yield DebugSceneRuntime.noop();
			}
		};
	}
	
	private ToteGeometry setupTote() {
		ToteGeometry tote = new ToteGeometry();
		rTote = RenderableToteFactory.createRenderableTote("Tote", tr, tote, true);
		objects.add(rTote);
		return tote;
	}
	
	// utility that creates a list of all renderable objects containing transferZones in the given object list
	public List<RenderableObject> processTransferZones(List<RenderableObject> rol, List<RenderableObject> result) {
	    for (RenderableObject ro : rol) {
	    	RenderableObject.traverseAndExtractAllWithIdStartingWith(ro, "transfer_", result);
	    }
	    return result;
	}
}
