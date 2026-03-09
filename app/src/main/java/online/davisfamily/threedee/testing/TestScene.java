package online.davisfamily.threedee.testing;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

import javax.swing.JRootPane;

import online.davisfamily.threedee.Scene;
import online.davisfamily.threedee.SoftwareRenderer;
import online.davisfamily.threedee.bresenham.BresenhamLineUtilities;
import online.davisfamily.threedee.camera.Camera;
import online.davisfamily.threedee.dimensions.ViewDimensions;
import online.davisfamily.threedee.input.keyboard.CommandBindings;
import online.davisfamily.threedee.input.keyboard.InputState;
import online.davisfamily.threedee.input.keyboard.InputState.Mode;
import online.davisfamily.threedee.input.keyboard.KeyBindings;
import online.davisfamily.threedee.input.mouse.MouseEventConsumer;
import online.davisfamily.threedee.input.mouse.MouseEventDetail;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.triangles.TriangleRenderer;

public class TestScene implements Scene, MouseEventConsumer{

	private ViewDimensions vd;
	private BresenhamLineUtilities bl;
	private TriangleRenderer tr;
	int[] pixels;
	float[] zBuffer;
	private ObjectTransformation t1;
	private ObjectTransformation t2;
	private Mat4 model1, model2, view, projection, perspective,vp, mvp1, mvp2;
	private BufferedImage image;
	private float aspect;

	// Camera variables
	private MouseEventDetail mouseInfo;
	private Camera camera;
	private Vec3 move;
	private float speed = 3.0f;
	private DebugUtils debug;
	
	private InputState inputState;
	
	public TestScene (ViewDimensions dimensions, SoftwareRenderer renderer) {
		this.vd = dimensions;
		this.bl = renderer.getBresenhamLineImpl();
		this.tr = renderer.getTriangleRenderer();
		this.image = renderer.getImage();
		this.pixels = renderer.getPixels();
		this.zBuffer = new float[pixels.length];
		// testing vars
		this.steps = 270;
		this.step = 1;
		this.stepInc = 1;
		this.t = step / (float) steps;
		this.s = 1.0f - t;
		this.cx = vd.width/2;
		this.cy = vd.height/2;
		this.halfW = Math.round((vd.width / 2f) * s);
		this.halfH = Math.round((vd.height / 2f) * s);
		this.angleX = 0.4f;
		this.angleY = 0.6f;
		this.aspect = (float)vd.width / (float)vd.height;
		this.t1 = new ObjectTransformation(0.4f,0.6f,0f,0f,0f,-1f,0f,0f,-3.0f);
		this.t2 = new ObjectTransformation(0.2f,0.8f,0f,1f,0f,-6.5f,3.0f,0f,0f);
		this.perspective = Mat4.perspective((float) Math.toRadians(60), aspect, 0.1f, 100f);
		this.projection = Mat4.perspective((float) Math.toRadians(60), aspect, 0.1f, 100f);
		this.vp = new Mat4();
		this.model1 = new Mat4();
		this.model2 = new Mat4();
		this.mvp1 = new Mat4();
		this.mvp2 = new Mat4();
		this.view = Mat4.identity();
		this.camera = new Camera();
		this.move = new Vec3(0,0,0);
		this.inputState = new InputState();
		this.mouseInfo = new MouseEventDetail();
		JRootPane rootPane = renderer.getRootPane();
		KeyBindings.installKeyBindings(rootPane, this.inputState);
		CommandBindings.installCommandBindings(rootPane, this.inputState);		
		this.debug = new DebugUtils(bl,camera,vd, this.inputState);
	}

	// -- Testing variables --
	// these should be moved out to a test class

	// -- viewport testing variables ------
		private int steps, cx, cy, halfW, halfH, stepInc;
		private float t, s, step;
	// ------------------------------------

	// -- 3D models  ------

	// cube structure
	Vec3[] v3CubeVertices = {
			// bottom square
			new Vec3 (-0.5f, -0.5f, -0.5f),
			new Vec3 (-0.5f, -0.5f, 0.5f),
			new Vec3 (0.5f, -0.5f, 0.5f),
			new Vec3 (0.5f, -0.5f, -0.5f),
			// top square
			new Vec3 (-0.5f, 0.5f, -0.5f),
			new Vec3 (-0.5f, 0.5f, 0.5f),
			new Vec3 (0.5f, 0.5f, 0.5f),
			new Vec3 (0.5f, 0.5f, -0.5f),
		};
		
	Vec4[] v4CubeVertices = {
			// bottom square
			new Vec4 (-0.5f, -0.5f, -0.5f),
			new Vec4 (-0.5f, -0.5f, 0.5f),
			new Vec4 (0.5f, -0.5f, 0.5f),
			new Vec4 (0.5f, -0.5f, -0.5f),
			// top square
			new Vec4 (-0.5f, 0.5f, -0.5f),
			new Vec4 (-0.5f, 0.5f, 0.5f),
			new Vec4 (0.5f, 0.5f, 0.5f),
			new Vec4 (0.5f, 0.5f, -0.5f),
		};

		int[][] cubeEdges = {
				{0, 1}, {1, 2}, {2, 3}, {3, 0}, // bottom square edges
				{4, 5}, {5, 6}, {6, 7}, {7, 4}, // top square edges
				{0, 4}, {1, 5}, // left vertical edges
				{2, 6}, {3, 7}, // right vertical edges
		};	
	
		int[] cubeFaceColours = {
			    0xFFFF0000, // bottom - red
			    0xFF00FF00, // top - green
			    0xFF0000FF, // front - blue
			    0xFFFFFF00, // right - yellow
			    0xFFFF00FF, // back - magenta
			    0xFF00FFFF  // left - cyan
		};
		
		// must make sure these are CCW 
		int[][] cubeTriangles = {
		    // bottom (y = -0.5) outward normal -Y
		    {0, 2, 1}, {0, 3, 2},

		    // top (y = +0.5) outward normal +Y
		    {4, 5, 6}, {4, 6, 7},

		    // front (z = +0.5) outward normal +Z
		    {1, 2, 6}, {1, 6, 5},

		    // right (x = +0.5) outward normal +X
		    {2, 3, 7}, {2, 7, 6},

		    // back (z = -0.5) outward normal -Z
		    {0, 4, 7}, {0, 7, 3},

		    // left (x = -0.5) outward normal -X
		    {0, 1, 5}, {0, 5, 4},
		};
		
	private float angleX, angleY;

	private void clear (int argb) {
		for (int i=0; i<pixels.length;i++) pixels[i] = argb;
	}
	
	
	private void testLineDrawTriangle() {
		int colour = 0xFFFFCC00;
		clear(0xFF101018);
		
		int dY = vd.vpMaxYExclusive - vd.vpMinY;
		int dX = vd.vpMaxXExclusive - vd.vpMinX;
		 
		// triangle with no clip
		bl.drawTriangle (vd.vpMinX, vd.vpMinY, vd.vpMinX, vd.vpMinY + (dY / 2), vd.vpMinX + (dX / 2), vd.vpMinY + (dY / 2), colour);	
	}
	
	
	private void testWireframeCube() {
		this.clear(0xFF000000);
		Vec3[] t = new Vec3[8];
		this.angleX += 0.01f;
		this.angleY += 0.01f;
		for (int i = 0; i < 8; i++) {
			Vec3 v = v3CubeVertices[i];
			v = Vec3.rotateY(v, this.angleY);
			v = Vec3.rotateX(v, this.angleX);
			v = v.add(new Vec3(0,0,2));
			t[i] = v;
		}
		bl.drawCube(t, cubeEdges, 0xFF2E7D32);
	}
	
// -- Matrix tests
	private final float twoPI = (float)Math.PI * 2;
	private float zTranslation = -1;
	private float zTranslationInc = -0.05f;
	
	private void testWireframeCubeWithMatrices() {
		this.clear(0xFF000000);

		bl.drawCube(v4CubeVertices, cubeEdges, this.angleY, this.angleX, this.zTranslation, 0xFF2E7D32);
		this.angleX += 0.01f;
		this.angleY += 0.005f;
		// wrap the rotation angles into (0, 2pi) to avoid floating point degradation over time
		this.angleX %= twoPI;
		this.angleY %= twoPI;
		if (this.zTranslation < -10) {
			this.zTranslationInc = 0.05f;
		} else if (this.zTranslation > -1) {
			this.zTranslationInc = -0.05f;
		}
 
		this.zTranslation += this.zTranslationInc;

	}
	
	private void clearZBuffer() {
		this.zBuffer = new float[pixels.length];
		Arrays.fill(this.zBuffer, Float.POSITIVE_INFINITY);
	}
	
	private void buildVP() {
	    vp.set(perspective);
	    vp.mutableMultiply(camera.getView());
	}

	private void buildMVP(Mat4 out, Mat4 model, ObjectTransformation tx) {
	    model.setModel(tx);
	    out.set(vp);
	    out.mutableMultiply(model);
	}
	
	private void testFilledCubes(double tSeconds) {
		this.clear(0xFF000000);
		this.clearZBuffer();
		

		buildVP();
	    buildMVP(mvp1, model1, t1);
	    //drawWorldAxesAt(vp, 0f, 0f, -5f, 2.0f);
	    
	    tr.drawCube(v4CubeVertices, cubeTriangles, mvp1, cubeFaceColours, zBuffer);
	    buildMVP(mvp2, model2, t2);
	    tr.drawCube(v4CubeVertices, cubeTriangles, mvp2, cubeFaceColours, zBuffer);

	    float angularSpeedX = 0.6f;   // radians per second
	    float angularSpeedY = 0.3f;
	    float zSpeed = 3.0f;          // units per second
	    float xSpeed = 3.0f;

	    t1.angleX += angularSpeedX * tSeconds;
	    t1.angleY += angularSpeedY * tSeconds;
	    t1.zTranslation += t1.zTranslationInc * tSeconds;
		
	    if (t1.zTranslation < -10) {
			t1.zTranslationInc = 3.0f;
		} else if (t1.zTranslation > -3) {
			t1.zTranslationInc = -3.0f;
		}

	    t2.angleX += angularSpeedX * tSeconds;
	    t2.angleY += angularSpeedY * tSeconds;
	    t2.xTranslation += t2.xTranslationInc * tSeconds;
	    
		if (t2.xTranslation > 4) {
			t2.xTranslationInc = -3.0f;
		} else if (t2.xTranslation < -4) {
			t2.xTranslationInc = 3.0f;
		}   
	}

	public void testMouseMovement () {
		if (mouseInfo != null && !mouseInfo.consumed && (mouseInfo.oldx != mouseInfo.x || mouseInfo.oldy != mouseInfo.y)) {
			mouseInfo.consumed = true;
			System.out.println(mouseInfo);
			camera.mouseUpdate(mouseInfo);
			System.out.println(camera);
		}
	}
	
	private void updateCamera() {
		if (mouseInfo != null) {
			camera.mouseUpdate(mouseInfo);
			mouseInfo.dx = 0;
			mouseInfo.dy = 0;
		}
	}
	
	private void updatePosition(double dt) {
		move.setXYZ(0, 0, 0);
		if (mouseInfo == null)
			camera.updateBasis();

		if (inputState.w()) this.move.mutableAdd(camera.getForwardXZ());
		if (inputState.s()) this.move.mutableSubtract(camera.getForwardXZ());
		if (inputState.d()) this.move.mutableAdd(camera.getRightXZ());
		if (inputState.a()) this.move.mutableSubtract(camera.getRightXZ());
		if (inputState.up()) this.move.mutableAdd(camera.getUp());
		if (inputState.down()) this.move.mutableSubtract(camera.getUp());		

		if (move.lengthSquared() > 0) {
		    move.mutableNormalize();
		    move.mutableScale(speed * (float)dt);
		    camera.position.mutableAdd(move);
		}
	}
	
	public void testKeyInput() {
		String s = "Keys Pressed: ";
		if (inputState.a()) s+="A ";
		if (inputState.w()) s+="W ";
		if (inputState.s()) s+="S ";
		if (inputState.d()) s+="D ";
		if (inputState.up()) s+="UP ";
		if (inputState.down()) s+="DOWN ";
		
		if (!s.equals("Keys Pressed: ")) System.out.println(s);
	}
	
	public void testNormalizeAndCross() {
		Vec3 a = new Vec3(1,1,1);
		Vec3 b = new Vec3(1,2,3);
		Vec3 c = a.cross(b);
		System.out.println(c.immutableMult(a));
	}
	
	public void renderFrame(double tSeconds) {
	    updateCamera();
	    updatePosition(tSeconds);
	    testFilledCubes(tSeconds);
	    if (inputState.isSet(Mode.SHOW_CAMERA_AXES)) debug.drawCameraOverlayAxes(900, 500, 30);
	    if (inputState.isSet(Mode.SHOW_DEBUG_INFO)) debug.drawDebugText(image, tSeconds);
	    
	}

	@Override
	public void consume(MouseEventDetail detail) {
		mouseInfo.dx += detail.dx;
		mouseInfo.dy += detail.dy;
	}
}
