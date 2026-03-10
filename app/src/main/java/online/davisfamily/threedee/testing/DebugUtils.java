package online.davisfamily.threedee.testing;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import online.davisfamily.threedee.bresenham.BresenhamLineUtilities;
import online.davisfamily.threedee.bresenham.BresenhamLineUtilities.ClippedLine;
import online.davisfamily.threedee.camera.Camera;
import online.davisfamily.threedee.dimensions.ViewDimensions;
import online.davisfamily.threedee.input.keyboard.InputState;
import online.davisfamily.threedee.input.keyboard.InputState.Mode;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.triangles.TriangleRenderer.Vertex;

public class DebugUtils {

	private BresenhamLineUtilities bl;
	private Camera camera;
	private ViewDimensions vd;
	private InputState is;
	
	public DebugUtils(BresenhamLineUtilities lineUtilities, Camera cam, ViewDimensions dimensions, InputState input) {
		bl = lineUtilities;
		camera = cam;
		vd = dimensions;
		is = input;
	}
	
	// tested
	private void drawOverlayAxis(int x0, int y0, Vec3 dir, int scale, int colour) {
	    int x1 = Math.round(x0 + dir.x * scale);
	    int y1 = Math.round(y0 - dir.y * scale); // minus because screen Y grows downward

	    bl.drawLineUnsafeClipped(x0, y0, x1, y1, colour);
	}
	
	// tested
	private void drawAxisLine(Mat4 view, Mat4 projection, Vec4 a, Vec4 b, int colour) {
	    Vec4 viewA4 = view.multiplyVec(a);
	    Vec4 viewB4 = view.multiplyVec(b);
	    
	    Vertex viewA = new Vertex(viewA4);
	    Vertex viewB = new Vertex(viewB4);
	    
	    // clip against the near plane
	    
	    float near = 0.1f;
	    ClippedLine clippedLine = ClippedLine.clipLineNear(viewA, viewB, near);
	    if (!clippedLine.visible) return;
	    
	    Vec4 clipA = projection.multiplyVec(new Vec4(clippedLine.a));
	    Vec4 clipB = projection.multiplyVec(new Vec4(clippedLine.b));
	    
	    
	    float epsilon = 0.001f;
	    if (clipA.w <= epsilon || clipB.w <= epsilon) {
	        return;
	    }

	    float ndcAx = clipA.x / clipA.w;
	    float ndcAy = clipA.y / clipA.w;

	    float ndcBx = clipB.x / clipB.w;
	    float ndcBy = clipB.y / clipB.w;

	    // reject non-infinite results
	    if (!Float.isFinite(ndcAx) || !Float.isFinite(ndcAy) ||
	        !Float.isFinite(ndcBx) || !Float.isFinite(ndcBy)) {
	        return;
	    }
	    
	    //map to screen
	    int sx0 = Math.round((ndcAx * 0.5f + 0.5f) * (vd.width - 1));
	    int sy0 = Math.round((-ndcAy * 0.5f + 0.5f) * (vd.height - 1));

	    int sx1 = Math.round((ndcBx * 0.5f + 0.5f) * (vd.width - 1));
	    int sy1 = Math.round((-ndcBy * 0.5f + 0.5f) * (vd.height - 1));

	    bl.drawLineUnsafeClipped(sx0, sy0, sx1, sy1, colour);
	}
	
	// tested
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
	
	// tested
	public void drawLookMarker(Mat4 view, Mat4 projection, Camera camera, float distance, float size) {
	    Vec3 f = camera.getForward();
		Vec3 c = new Vec3(
	        camera.position.x + f.x * distance,
	        camera.position.y + f.y * distance,
	        camera.position.z + f.z * distance
	    );

	    // small cross aligned to world axes
	    drawAxisLine(view, projection,
	        new Vec4(c.x - size, c.y, c.z, 1f),
	        new Vec4(c.x + size, c.y, c.z, 1f),
	        0xFFFF0000); // red X

	    drawAxisLine(view, projection,
	        new Vec4(c.x, c.y - size, c.z, 1f),
	        new Vec4(c.x, c.y + size, c.z, 1f),
	        0xFF00FF00); // green Y

	    drawAxisLine(view, projection,
	        new Vec4(c.x, c.y, c.z - size, 1f),
	        new Vec4(c.x, c.y, c.z + size, 1f),
	        0xFF0000FF); // blue Z
	}

	public void drawCameraAxes(Mat4 view, Mat4 projection, Camera camera, float axisLength, float distanceAhead) {
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
	        view, projection,
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
	        view, projection,
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
	        view, projection,
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
	
	// tested
	public void drawDebugText(BufferedImage image, double tdelta) {
	    Graphics2D g = image.createGraphics();
	    try {
	        g.setColor(Color.WHITE);
	        g.setFont(new Font("Consolas", Font.PLAIN, 14));

	        int x = 10;
	        int y = 20;
	        int line = 18;
	        
	        g.setColor(new Color(0, 0, 0, 170));
	        g.fillRoundRect(5, 5, 260, 150, 10, 10);
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
	        y += line;
	        g.drawString(String.format("FPS: %.3f", 1.0 / tdelta), x, y);
	        y += line;
	        g.drawString(String.format("Show Camera Axes: %b", is.isSet(Mode.SHOW_CAMERA_AXES)), x, y);
	    } finally {
	        g.dispose();
	    }
	}
	
	// untested - may cause exceptions when axis goes through the camera
	public void drawWorldAxesAt(Mat4 view, Mat4 projection, float ox, float oy, float oz, float axisLength) {
	    Vec4 origin = new Vec4(ox, oy, oz, 1f);

	    drawAxisLine(view, projection, origin, new Vec4(ox + axisLength, oy, oz, 1f), 0xFFFF0000); // +X
	    drawAxisLine(view, projection, origin, new Vec4(ox, oy + axisLength, oz, 1f), 0xFF00FF00); // +Y
	    drawAxisLine(view, projection, origin, new Vec4(ox, oy, oz - axisLength, 1f), 0xFF0000FF); // -Z
	}
}
