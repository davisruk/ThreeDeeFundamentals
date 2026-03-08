package online.davisfamily.threedee.testing;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

import online.davisfamily.threedee.Scene;
import online.davisfamily.threedee.SoftwareRenderer;
import online.davisfamily.threedee.bresenham.BresenhamLineUtilities;
import online.davisfamily.threedee.camera.Camera;
import online.davisfamily.threedee.dimensions.ViewDimensions;
import online.davisfamily.threedee.input.keyboard.InputState;
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
		this.t1 = new ObjectTransformation(0.4f,0.6f,0f,0f,0f,-1f,0f,0f,-0.05f);
		this.t2 = new ObjectTransformation(0.2f,0.8f,0f,1f,0f,-6.5f,0.05f,0f,0f);
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
		KeyBindings.installKeyBindings(renderer, this.inputState);
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
		
		final int[] CUBE1_FACE_COLOURS = new int[] {
			    0xFFFF0000, // Front  - Red
			    0xFFFFFFFF, // Back   - White
			    0xFFFF0000, // Left   - Red
			    0xFFFFFFFF, // Right  - White
			    0xFFFF0000, // Top    - Red
			    0xFFFFFFFF  // Bottom - White
		};
		
		final int[] CUBE2_FACE_COLOURS = new int[] {
			    0xFF0000FF, // Front  - Blue
			    0xFFFFFF00, // Back   - Yellow
			    0xFF0000FF, // Left   - Blue
			    0xFFFFFF00, // Right  - Yellow
			    0xFF0000FF, // Top    - Blue
			    0xFFFFFF00  // Bottom - Yellow
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
	
	private Mat4 getImmutableMVP(ObjectTransformation tx) {
		float aspect = (float)vd.width / (float)vd.height;	
		Mat4 model = Mat4
				.translation(tx.xTranslation, tx.yTranslation, tx.zTranslation)
				.immutableMultiplyMatrix(Mat4.rotationYXZ(tx.angleY, tx.angleX, tx.angleZ)); // could do multiple Mat4.multiplyMatrix(Mat4.rotationY).multiplyMatrix(rotationX) etc.
		Mat4 view = Mat4.identity();
		Mat4 projection = Mat4.perspective((float) Math.toRadians(60), aspect, 0.1f, 100f);
		Mat4 mvp = projection.immutableMultiplyMatrix(view).immutableMultiplyMatrix(model);
		return mvp;
	}
	
	private Mat4 getMVPMutable(Mat4 model, ObjectTransformation tx) {
		model.setModel(tx);
		projection.set(perspective);
		projection.mutableMultiply(camera.getView()).mutableMultiply(model);
		return projection;
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
	
	private void testFilledCubes() {
		this.clear(0xFF000000);
		this.clearZBuffer();
		

		buildVP();
//		drawWorldAxesAt(vp, 0f, 0f, -5f, 2.0f);
//		drawLookLine(vp, camera, 5.0f, 0xFFFFFFFF);
//		drawLookMarker(vp, camera, 5.0f, 0.2f);

	    buildMVP(mvp1, model1, t1);
	    tr.drawCube(v4CubeVertices, cubeTriangles, mvp1, cubeFaceColours, zBuffer);
	    buildMVP(mvp2, model2, t2);
	    tr.drawCube(v4CubeVertices, cubeTriangles, mvp2, cubeFaceColours, zBuffer);
	    drawCameraOverlayAxes(60, 60, 30);
	    
//		tr.drawCube(v4CubeVertices, cubeTriangles, getMVPMutable(model1, t1), cubeFaceColours, zBuffer);
//		tr.drawCube(v4CubeVertices, cubeTriangles, getMVPMutable(model2, t2), cubeFaceColours, zBuffer);


		
		t1.angleX += 0.01f;
		t1.angleY += 0.005f;
		// wrap the rotation angles into (0, 2pi) to avoid floating point degradation over time
		t1.angleX %= twoPI;
		t1.angleY %= twoPI;
		if (t1.zTranslation < -10) {
			t1.zTranslationInc = 0.05f;
		} else if (t1.zTranslation > -3) {
			t1.zTranslationInc = -0.05f;
		}
 
		t1.zTranslation += t1.zTranslationInc;

		t2.angleX += 0.01f;
		t2.angleY += 0.005f;
		// wrap the rotation angles into (0, 2pi) to avoid floating point degradation over time
		t2.angleX %= twoPI;
		t2.angleY %= twoPI;
		if (t2.xTranslation > 4) {
			t2.xTranslationInc = -0.05f;
		} else if (t2.xTranslation < -4) {
			t2.xTranslationInc = 0.05f;
		}
 
		t2.xTranslation += t2.xTranslationInc;
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

		if (move.lengthSquared() > 0) {
		    move.mutableNormalize();
		    move.mutableScale(speed * (float)dt);
		    camera.position.mutableAdd(move);
		}
	}
	
	
	public void drawCameraOverlayAxes(int anchorX, int anchorY, int axisLength) {
	    // Camera basis in world space
	    Vec3 right = camera.getRight();
	    Vec3 up = camera.getUp();
	    Vec3 forward = camera.getForward();

	    // Draw X axis (red)
	    drawOverlayAxis(anchorX, anchorY, right, axisLength, 0xFFFF0000);

	    // Draw Y axis (green)
	    drawOverlayAxis(anchorX, anchorY, up, axisLength, 0xFF00FF00);

	    // Draw Z axis (blue)
	    drawOverlayAxis(anchorX, anchorY, forward, axisLength, 0xFF0000FF);
	}
	
	private void drawOverlayAxis(int x0, int y0, Vec3 dir, int scale, int colour) {
	    int x1 = Math.round(x0 + dir.x * scale);
	    int y1 = Math.round(y0 - dir.y * scale); // minus because screen Y grows downward

	    bl.drawLineUnsafeClipped(x0, y0, x1, y1, colour);
	}
	
	public void drawWorldAxesAt(Mat4 viewProjection, float ox, float oy, float oz, float axisLength) {
	    Vec4 origin = new Vec4(ox, oy, oz, 1f);

	    drawAxisLine(viewProjection, origin, new Vec4(ox + axisLength, oy, oz, 1f), 0xFFFF0000); // +X
	    drawAxisLine(viewProjection, origin, new Vec4(ox, oy + axisLength, oz, 1f), 0xFF00FF00); // +Y
	    drawAxisLine(viewProjection, origin, new Vec4(ox, oy, oz - axisLength, 1f), 0xFF0000FF); // -Z
	}
	
	public void drawLookMarker(Mat4 viewProjection, Camera camera, float distance, float size) {
	    Vec3 f = camera.getForward();
		Vec3 c = new Vec3(
	        camera.position.x + f.x * distance,
	        camera.position.y + f.y * distance,
	        camera.position.z + f.z * distance
	    );

	    // small cross aligned to world axes
	    drawAxisLine(viewProjection,
	        new Vec4(c.x - size, c.y, c.z, 1f),
	        new Vec4(c.x + size, c.y, c.z, 1f),
	        0xFFFF0000); // red X

	    drawAxisLine(viewProjection,
	        new Vec4(c.x, c.y - size, c.z, 1f),
	        new Vec4(c.x, c.y + size, c.z, 1f),
	        0xFF00FF00); // green Y

	    drawAxisLine(viewProjection,
	        new Vec4(c.x, c.y, c.z - size, 1f),
	        new Vec4(c.x, c.y, c.z + size, 1f),
	        0xFF0000FF); // blue Z
	}
	public void drawAxes(Mat4 viewProjection, float axisLength) {
	    Vec4 origin = new Vec4(0, 0, 0, 1);

	    drawAxisLine(viewProjection, origin, new Vec4(axisLength, 0, 0, 1), 0xFFFF0000); // +X bright red
	    drawAxisLine(viewProjection, origin, new Vec4(-axisLength, 0, 0, 1), 0xFF880000); // -X dark red

	    drawAxisLine(viewProjection, origin, new Vec4(0, axisLength, 0, 1), 0xFF00FF00); // +Y bright green
	    drawAxisLine(viewProjection, origin, new Vec4(0, -axisLength, 0, 1), 0xFF008800); // -Y dark green

	    drawAxisLine(viewProjection, origin, new Vec4(0, 0, -axisLength, 1), 0xFF0000FF); // forward / -Z bright blue
	    drawAxisLine(viewProjection, origin, new Vec4(0, 0, axisLength, 1), 0xFF000088); // +Z dark blue
	}
	
	private void drawAxisLine(Mat4 viewProjection, Vec4 a, Vec4 b, int colour) {
	    Vec4 clipA = viewProjection.multiplyVec(a);
	    Vec4 clipB = viewProjection.multiplyVec(b);

	    float epsilon = 0.001f;
	    if (clipA.w <= epsilon || clipB.w <= epsilon) {
	        return;
	    }

	    float ndcAx = clipA.x / clipA.w;
	    float ndcAy = clipA.y / clipA.w;

	    float ndcBx = clipB.x / clipB.w;
	    float ndcBy = clipB.y / clipB.w;

	    if (!Float.isFinite(ndcAx) || !Float.isFinite(ndcAy) ||
	        !Float.isFinite(ndcBx) || !Float.isFinite(ndcBy)) {
	        return;
	    }

	    int sx0 = Math.round((ndcAx * 0.5f + 0.5f) * (vd.width - 1));
	    int sy0 = Math.round((-ndcAy * 0.5f + 0.5f) * (vd.height - 1));

	    int sx1 = Math.round((ndcBx * 0.5f + 0.5f) * (vd.width - 1));
	    int sy1 = Math.round((-ndcBy * 0.5f + 0.5f) * (vd.height - 1));

	    bl.drawLineUnsafeClipped(sx0, sy0, sx1, sy1, colour);
	}
	
	public void drawCameraAxes(Mat4 viewProjection, Camera camera, float axisLength, float distanceAhead) {
	    Vec3 f = camera.getForward();
	    Vec3 r = camera.getRight();
	    Vec3 u = camera.getUp();
		Vec3 centre = new Vec3(
	        camera.position.x + f.x * distanceAhead,
	        camera.position.y + f.y * distanceAhead,
	        camera.position.z + f.z * distanceAhead
	    );

	    Vec4 origin = new Vec4(centre.x, centre.y, centre.z, 1f);

	    // X axis (camera right)
	    
	    drawAxisLine(
	        viewProjection,
	        origin,
	        new Vec4(
	            centre.x + r.x * axisLength,
	            centre.y + r.y * axisLength,
	            centre.z + r.z * axisLength,
	            1f
	        ),
	        0xFFFF0000
	    );

	    // Y axis (camera up)
	    drawAxisLine(
	        viewProjection,
	        origin,
	        new Vec4(
	            centre.x + u.x * axisLength,
	            centre.y + u.y * axisLength,
	            centre.z + u.z * axisLength,
	            1f
	        ),
	        0xFF00FF00
	    );

	    // Z axis (camera forward)
	    drawAxisLine(
	        viewProjection,
	        origin,
	        new Vec4(
	            centre.x + f.x * axisLength,
	            centre.y + f.y * axisLength,
	            centre.z + f.z * axisLength,
	            1f
	        ),
	        0xFF0000FF
	    );
	}
	
	private void drawDebugText(BufferedImage image) {
	    Graphics2D g = image.createGraphics();
	    try {
	        g.setColor(Color.WHITE);
	        g.setFont(new Font("Consolas", Font.PLAIN, 14));

	        int x = 10;
	        int y = 20;
	        int line = 18;
	        
	        g.setColor(new Color(0, 0, 0, 170));
	        g.fillRoundRect(5, 5, 260, 120, 10, 10);
	        g.setColor(Color.WHITE);
	        
	        g.drawString(String.format("Pos:   (%.3f, %.3f, %.3f)",
	                camera.position.x, camera.position.y, camera.position.z), x, y);
	        y += line;

	        g.drawString(String.format("Yaw:   %.3f rad (%.1f deg)",
	                camera.yaw, Math.toDegrees(camera.yaw)), x, y);
	        y += line;

	        g.drawString(String.format("Pitch: %.3f rad (%.1f deg)",
	                camera.pitch, Math.toDegrees(camera.pitch)), x, y);
	        y += line;

	        Vec3 f = camera.getForward();
	        g.drawString(String.format("Fwd:   (%.3f, %.3f, %.3f)",
	                f.x, f.y, f.z), x, y);
	        y += line;

	        Vec3 fxz = camera.getForwardXZ();
	        g.drawString(String.format("FwdXZ: (%.3f, %.3f, %.3f)",
	                fxz.x, fxz.y, fxz.z), x, y);
	        y += line;

	        Vec3 r = camera.getRightXZ();
	        g.drawString(String.format("RightXZ:(%.3f, %.3f, %.3f)",
	                r.x, r.y, r.z), x, y);
	    } finally {
	        g.dispose();
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
		if (tSeconds > 0.1) tSeconds = 0.1;
		updateCamera();
		updatePosition(tSeconds);
		testFilledCubes();
		drawCameraOverlayAxes(60, 60, 30);
		drawDebugText(image);		//testKeyInput();
	}


	@Override
	public void consume(MouseEventDetail detail) {
		mouseInfo.dx += detail.dx;
		mouseInfo.dy += detail.dy;
	}
}
