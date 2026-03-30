package online.davisfamily.threedee.scene;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JRootPane;

import online.davisfamily.threedee.camera.Camera;
import online.davisfamily.threedee.camera.CameraPosition;
import online.davisfamily.threedee.dimensions.ViewDimensions;
import online.davisfamily.threedee.input.keyboard.CommandBindings;
import online.davisfamily.threedee.input.keyboard.InputState;
import online.davisfamily.threedee.input.keyboard.InputState.Mode;
import online.davisfamily.threedee.input.keyboard.KeyBindings;
import online.davisfamily.threedee.input.mouse.MouseEventConsumer;
import online.davisfamily.threedee.input.mouse.MouseEventDetail;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.rendering.TriangleRenderer;
import online.davisfamily.threedee.rendering.lights.DirectionalLight;
import online.davisfamily.threedee.rendering.selection.ScenePicker;
import online.davisfamily.threedee.rendering.selection.SelectionManager;
import online.davisfamily.threedee.rendering.utilities.lines.BresenhamLineUtilities;
import online.davisfamily.threedee.rendering.utilities.lines.CohenSutherlandLineClipper;
import online.davisfamily.threedee.testing.DebugUtils;

// provides a good starting point for a Scene
// implements a lot of boilerplate methods
// extending class should manage world models (structure, transformations etc) and lighting
public abstract class BaseScene implements Scene, MouseEventConsumer{
	// drawing variables
	protected JRootPane root;
	protected ViewDimensions vd;
	protected int[] pixels;
	protected float[] zBuffer;
	protected BresenhamLineUtilities bl;
	protected TriangleRenderer tr;

	// 3D pipeline variables
	protected Mat4 projection, perspective,vp; 
	protected BufferedImage image;
	protected float aspect;

	// Camera variables
	protected Camera camera;
	protected Vec3 move;
	protected float speed = 4.0f;
	
	//input variables
	protected MouseEventDetail mouseInfo;	
	protected InputState inputState;

	// debug variables
	protected DebugUtils debug;
	
	protected final SelectionManager selectionManager = new SelectionManager();
	protected final ScenePicker scenePicker = new ScenePicker();
	protected final float pickerFovYRadians = (float) Math.toRadians(60);
	protected List<RenderableObject> objects;
	
	public BaseScene (JRootPane pane, ViewDimensions dimensions, CameraPosition camPos) {
		root = pane;
		vd = dimensions;
		objects = new ArrayList<RenderableObject>();		
		this.inputState = new InputState();
		this.image = new BufferedImage(vd.width, vd.height, BufferedImage.TYPE_INT_ARGB);
		this.pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		this.zBuffer = new float[pixels.length];
		this.aspect = (float)vd.width / (float)vd.height;
		this.perspective = Mat4.perspective((float) Math.toRadians(60), aspect, 0.1f, 100f);
		this.projection = Mat4.perspective((float) Math.toRadians(60), aspect, 0.1f, 100f);
		this.camera = new Camera(camPos);
		this.move = new Vec3(0,0,0);
		this.mouseInfo = new MouseEventDetail();
		KeyBindings.installKeyBindings(root, this.inputState);
		CommandBindings.installCommandBindings(root, this.inputState);
		this.bl = new BresenhamLineUtilities(pixels, vd.width, new CohenSutherlandLineClipper(vd.vpMinX, vd.vpMinY, vd.vpMaxXExclusive-1, vd.vpMaxYExclusive-1));
		this.debug = new DebugUtils(bl,camera,vd, selectionManager);
		this.tr = new TriangleRenderer(pixels, vd.width, vd.vpMinX, vd.vpMinY, vd.vpMaxXExclusive-1, vd.vpMaxYExclusive-1, this.bl, inputState, debug, selectionManager);
		this.vp = new Mat4();
	}
	
	public BaseScene (JRootPane pane, ViewDimensions dimensions) {
		this (pane, dimensions, new CameraPosition());
	}
	
	// base classes must override this method
	// place additional render operations there
	// the method is called from renderFrame
	// for more control override renderFrame itself
	public abstract void executeChildRenderOperations(double dtSeconds);
	
	protected void clear (int argb) {
		for (int i=0; i<pixels.length;i++) pixels[i] = argb;
		clearZBuffer();
	}
	
	protected void clearZBuffer() {
		this.zBuffer = new float[pixels.length];
		// ZBuffer using ndcZ so comparison is <
		//Arrays.fill(this.zBuffer, Float.POSITIVE_INFINITY);
		
		// ZBuffer using invW so comparison is > 
		Arrays.fill(this.zBuffer, Float.NEGATIVE_INFINITY);
	}

	protected void updateCamera() {
		if (mouseInfo != null) {
			camera.mouseUpdate(mouseInfo);
			mouseInfo.dx = 0;
			mouseInfo.dy = 0;
		}
	}
	
	protected void updatePosition(double dt) {
		float x=0, y=0, z = 0;
		move.setXYZ(0, 0, 0);
/*
		if (mouseInfo == null)
			camera.updateBasis();
*/
		if (inputState.w()) z+=1;
		if (inputState.s()) z-=1;
		if (inputState.d()) x+=1;
		if (inputState.a()) x-=1;
		if (inputState.up()) y+=1;
		if (inputState.down()) y-=1;		
		
		camera.move(z, x, y, speed, (float) dt);
	}
	
	protected void buildVP() {
	    vp.set(perspective);
	    vp.mutableMultiply(camera.getView());
	}

	protected void drawObject(List<RenderableObject> objects, double dtSeconds, DirectionalLight lightDirection) {
		if (!inputState.isSet(Mode.PAUSE_TRANSFORMS))
			for (RenderableObject ro: objects)
				ro.update(dtSeconds);
		for (RenderableObject ro: objects)
			ro.draw(camera, perspective, zBuffer, lightDirection, null);
	}
	
	protected void updateDebug(double tSeconds) {
		if (inputState.isSet(Mode.SHOW_WORLD_AXES)) debug.drawWorldAxesAt(camera.getView(), projection, 0f, 0f, -1f, 20.0f);
		if (inputState.isSet(Mode.SHOW_GRID)) debug.drawWorldGrid(camera.getView(), perspective, 20, 1.0f);
	    if (inputState.isSet(Mode.SHOW_CAMERA_AXES)) debug.drawCameraOverlayAxes(900, 500, 30);
	    if (inputState.isSet(Mode.SHOW_DEBUG_INFO)) debug.drawDebugText(image, tSeconds, perspective);
	}
	
	protected void selectAt(List<RenderableObject> objects, int mouseX, int mouseY) {
	    var hit = scenePicker.pick(
	            objects,
	            camera,
	            pickerFovYRadians,
	            mouseX,
	            mouseY,
	            vd.width,
	            vd.height);

	    if (hit != null) {
	        selectionManager.setSelected(hit.object);
	    } else {
	        selectionManager.clear();
	    }
	}
	
	public RenderableObject getSelectedObject() {
		return selectionManager.getSelected();
	}
	
	@Override
	public void renderFrame(double tSeconds) {
/*
		if (!hasPrinted) {
	    	rTote.children.stream().forEach(ro -> System.out.println(ro.mesh));
	    	hasPrinted = true;
	    }
*/	    
		if (!inputState.isSet(Mode.PAUSE_ALL)) {
	    	updateCamera();
			updatePosition(tSeconds);
		    this.clear(0xFF000000);
			buildVP();
			executeChildRenderOperations(tSeconds);
			updateDebug(tSeconds);
	    }
	}
	
	@Override
	public BufferedImage getImage() {return image;}	
	
	@Override
	public void pickAt(int x, int y) {
		selectAt(objects, x, y);		
	}
	
	@Override
	public void consume(MouseEventDetail detail) {
		if (inputState.isSet(Mode.PAUSE_ALL)) return;
		mouseInfo.dx += detail.dx;
		mouseInfo.dy += detail.dy;
	}
}
