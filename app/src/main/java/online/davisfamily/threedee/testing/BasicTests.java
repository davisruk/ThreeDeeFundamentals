package online.davisfamily.threedee.testing;

import online.davisfamily.threedee.bresenham.BresenhamLineUtilities;
import online.davisfamily.threedee.camera.Camera;
import online.davisfamily.threedee.dimensions.ViewDimensions;
import online.davisfamily.threedee.input.keyboard.InputState;
import online.davisfamily.threedee.input.mouse.MouseEventDetail;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;

public class BasicTests {
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
	
	public void testKeyInput(InputState inputState) {
		String s = "Keys Pressed: ";
		if (inputState.a()) s+="A ";
		if (inputState.w()) s+="W ";
		if (inputState.s()) s+="S ";
		if (inputState.d()) s+="D ";
		if (inputState.up()) s+="UP ";
		if (inputState.down()) s+="DOWN ";
		
		if (!s.equals("Keys Pressed: ")) System.out.println(s);
	}
	
	public void testMouseMovement (MouseEventDetail mouseInfo, Camera camera) {
		if (mouseInfo != null && !mouseInfo.consumed && (mouseInfo.oldx != mouseInfo.x || mouseInfo.oldy != mouseInfo.y)) {
			mouseInfo.consumed = true;
			System.out.println(mouseInfo);
			camera.mouseUpdate(mouseInfo);
			System.out.println(camera);
		}
	}	
	public void testNormalizeAndCross() {
		Vec3 a = new Vec3(1,1,1);
		Vec3 b = new Vec3(1,2,3);
		Vec3 c = a.cross(b);
		System.out.println(c.immutableMult(a));
	}
		
	private float angleX, angleY;

	private void clear (int argb, int[] pixels) {
		for (int i=0; i<pixels.length;i++) pixels[i] = argb;
	}
	
	
	private void testLineDrawTriangle(ViewDimensions vd, int[] pixels, BresenhamLineUtilities bl) {
		int colour = 0xFFFFCC00;
		clear(0xFF101018, pixels);
		
		int dY = vd.vpMaxYExclusive - vd.vpMinY;
		int dX = vd.vpMaxXExclusive - vd.vpMinX;
		 
		// triangle with no clip
		bl.drawTriangle (vd.vpMinX, vd.vpMinY, vd.vpMinX, vd.vpMinY + (dY / 2), vd.vpMinX + (dX / 2), vd.vpMinY + (dY / 2), colour);	
	}
	
	
	private void testWireframeCube(int[][] cubeEdges, ViewDimensions vd, int[] pixels, BresenhamLineUtilities bl) {
		this.clear(0xFF000000, pixels);
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
	
	private final float twoPI = (float)Math.PI * 2;
	private float zTranslation = -1;
	private float zTranslationInc = -0.05f;
	
	private void testWireframeCubeWithMatrices(Vec4[] v4CubeVertices, int[][] cubeEdges, ViewDimensions vd, int[] pixels, BresenhamLineUtilities bl) {
		this.clear(0xFF000000, pixels);

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
}
