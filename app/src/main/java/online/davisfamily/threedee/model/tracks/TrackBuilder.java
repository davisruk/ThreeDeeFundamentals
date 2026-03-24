package online.davisfamily.threedee.model.tracks;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.path.PathSegment3;

public class TrackBuilder {

	private static final Vec3 WORLD_UP = new Vec3(0f,1f,0f);
 
	public static TrackBuildResult build (PathSegment3 path, TrackSpec spec) {
		List<SampleFrame> frames = sampleFrames(path, spec.sampleStep);
		Mesh deck = buildDeck(spec,frames);
		Mesh guides = null;
		List<ObjectTransformation> rollers = null;
		if (spec.includeGuides) {
			guides = buildGuides(spec, frames);
		}
		if (spec.includeRollers) {
			rollers = buildRollers(path, spec);
		}
		return new TrackBuildResult(deck, guides, rollers);
	}
	
	private static List<SampleFrame> sampleFrames(PathSegment3 path, float sampleStep){
		List<SampleFrame> frames = new ArrayList<>();
		float total = path.getTotalLength();
		for (float d = 0f; d < total; d+= sampleStep) {
			frames.add(createFrame(path,d));
		}
		frames.add(createFrame(path,total));
		return frames;
	}
	
	private static SampleFrame createFrame(PathSegment3 path, float distance) {
		Vec3 centre = path.sampleByDistance(distance);
		Vec3 tangent = path.sampleTangentByDistance(distance).normalize();
		Vec3 side = WORLD_UP.cross(tangent).normalize();
		if (side.lengthSquared() == 0f) {
			side = new Vec3(1f,0f,0f);
		}
		return new SampleFrame(distance, centre, tangent, side, WORLD_UP);
	}
	
	private static Mesh buildDeck(TrackSpec spec, List<SampleFrame> frames) {
		TrackMeshBuilder mb = new TrackMeshBuilder();

		float half = spec.getRunningWidth() * 0.5f;
		float topY = spec.deckTopY;
		float bottomY = spec.deckTopY - spec.deckThickness;
		
		int[] tl = new int[frames.size()];
		int[] tr = new int[frames.size()];
		int[] bl = new int[frames.size()];
		int[] br = new int[frames.size()];
		
		for (int i = 0; i<frames.size(); i++) {
			SampleFrame f = frames.get(i);
			Vec3 left = f.centre.subtract(f.side.scale(half));
			Vec3 right = f.centre.add(f.side.scale(half));
			tl[i] = mb.addVertex(left.x, topY, left.z);
			tr[i] = mb.addVertex(right.x, topY, right.z);
			bl[i] = mb.addVertex(left.x, bottomY, left.z);
			br[i] = mb.addVertex(right.x, bottomY, right.z);
		}
		
		for (int i = 0; i<frames.size()-1; i++) {
			// top
			mb.addQuad(tl[i], tr[i], tr[i+1], tl[i+1]);
			// bottom
			mb.addQuad(bl[i+1], br[i+1], br[i], bl[i]);
			// left side
			mb.addQuad(bl[i], tl[i], tl[i+1], bl[i+1]);
			// right side
			mb.addQuad(tr[i], br[i], br[i+1], tr[i+1]);
		}
		return mb.build("Deck");
	}
	
	private static Mesh buildGuides(TrackSpec spec, List<SampleFrame> frames) {
		TrackMeshBuilder mb = new TrackMeshBuilder();
		float innerHalf = spec.getGuideInnerWidth() * 0.5f;
		float outerHalf = innerHalf + spec.guideThickness;
		float y0 = spec.deckTopY;
		float y1 = spec.deckTopY + spec.guideHeight;
		
		buildLongSideStrip(frames, -innerHalf, -outerHalf, y0, y1, mb); //left
		buildLongSideStrip(frames, outerHalf, innerHalf, y0, y1, mb); //right
		return mb.build("Guides");
	}
	
	private static void buildLongSideStrip(List<SampleFrame> frames, float innerOffset, float outerOffset, float y0, float y1, TrackMeshBuilder mb) {
		int[] ib = new int[frames.size()];
		int[] it = new int[frames.size()];
		int[] ob = new int[frames.size()];
		int[] ot = new int[frames.size()];

		for (int i = 0; i<frames.size(); i++) {
			SampleFrame f = frames.get(i);
			Vec3 inner = f.centre.add(f.side.scale(innerOffset));
			Vec3 outer = f.centre.add(f.side.scale(outerOffset));
			ib[i] = mb.addVertex(inner.x, y0, inner.z);
			it[i] = mb.addVertex(inner.x, y1, inner.z);
			ob[i] = mb.addVertex(outer.x, y0, outer.z);
			ot[i] = mb.addVertex(outer.x, y1, outer.z);
		}
		for (int i = 0; i<frames.size()-1; i++) {
			mb.addQuad(it[i], ot[i], ot[i+1], it[i+1]);
			mb.addQuad(ob[i], ib[i], ib[i+1], ob[i+1]);
			mb.addQuad(ib[i], it[i], it[i+1], ib[i+1]);
			mb.addQuad(ot[i], ob[i], ob[i+1], ot[i+1]);
		}
	}
	
	private static List<Mat4.ObjectTransformation> buildRollers(PathSegment3 path, TrackSpec spec) {
		List<Mat4.ObjectTransformation> transforms = new ArrayList<>();
		float total = path.getTotalLength();
		for (float d = 0f; d<=total; d+=spec.rollerPitch) {
			SampleFrame f = createFrame(path,d);
			float yaw = Vec3.yawFromDirection(f.tangent) + (float)(Math.PI / 2.0);
			Mat4.ObjectTransformation tx = new Mat4.ObjectTransformation(
				0f,
				yaw,
				0f,
				f.centre.x,
				spec.deckTopY + spec.rollerHeight * 0.5f,
				f.centre.z,
				new Mat4()
			);
			transforms.add(tx);
		}
		return transforms;
	}
	
	private static final class SampleFrame {
		final float distance;
		final Vec3 centre, tangent, side, up;
		public SampleFrame(float distance, Vec3 centre, Vec3 tangent, Vec3 side, Vec3 up) {
			super();
			this.distance = distance;
			this.centre = centre;
			this.tangent = tangent;
			this.side = side;
			this.up = up;
		}
	}
}
