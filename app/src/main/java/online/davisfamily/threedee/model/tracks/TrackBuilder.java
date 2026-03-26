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
		return build(path, spec, 0f, path.getTotalLength(), spec.includeGuides, spec.includeRollers);
	}

	public static TrackBuildResult build(PathSegment3 path,
			TrackSpec spec,
			float startDistance,
			float endDistance,
			boolean includeGuides,
			boolean includeRollers) {
		List<SampleFrame> frames = sampleFrames(path, startDistance, endDistance, spec.sampleStep);
		Mesh deck = buildDeck(spec,frames);
		Mesh guides = null;
		List<ObjectTransformation> rollers = null;
		if (includeGuides) {
			guides = buildGuides(path, spec, startDistance, endDistance);
		}
		if (includeRollers) {
			rollers = buildRollers(path, spec, startDistance, endDistance);
		}
		return new TrackBuildResult(deck, guides, rollers);
	}
	
	private static List<SampleFrame> sampleFrames(PathSegment3 path, float startDistance, float endDistance, float sampleStep){
		List<SampleFrame> frames = new ArrayList<>();
		float total = path.getTotalLength();
		float start = clamp(startDistance, 0f, total);
		float end = clamp(endDistance, 0f, total);

		if (end < start) {
			float tmp = start;
			start = end;
			end = tmp;
		}

		for (float d = start; d < end; d += sampleStep) {
			frames.add(createFrame(path,d));
		}
		frames.add(createFrame(path,end));
		if (frames.size() == 1) {
			frames.add(createFrame(path,end));
		}
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

		for (int i = 0; i < frames.size(); i++) {
			SampleFrame f = frames.get(i);
			Vec3 left = f.centre.subtract(f.side.scale(half));
			Vec3 right = f.centre.add(f.side.scale(half));
			tl[i] = mb.addVertex(left.x, topY, left.z);
			tr[i] = mb.addVertex(right.x, topY, right.z);
			bl[i] = mb.addVertex(left.x, bottomY, left.z);
			br[i] = mb.addVertex(right.x, bottomY, right.z);
		}

		for (int i = 0; i < frames.size() - 1; i++) {
			mb.addQuad(tl[i], tr[i], tr[i + 1], tl[i + 1]);       // top
			mb.addQuad(bl[i + 1], br[i + 1], br[i], bl[i]);       // bottom
			mb.addQuad(bl[i], tl[i], tl[i + 1], bl[i + 1]);       // left side
			mb.addQuad(tr[i], br[i], br[i + 1], tr[i + 1]);       // right side
		}

		// start cap
		mb.addQuad(tl[0], tr[0], br[0], bl[0]);

		// end cap
		int last = frames.size() - 1;
		mb.addQuad(bl[last], br[last], tr[last], tl[last]);

		return mb.build("Deck");
	}
	
	public static Mesh buildGuide(
	        PathSegment3 path,
	        TrackSpec spec,
	        float startDistance,
	        float endDistance,
	        GuideSide side) {

	    if (!spec.includeGuides) {
	        return null;
	    }

	    List<SampleFrame> frames = sampleFrames(path, startDistance, endDistance, spec.sampleStep);
	    if (frames.size() < 2) {
	        return null;
	    }

	    TrackMeshBuilder mb = new TrackMeshBuilder();

	    float innerHalf = spec.getGuideInnerWidth() * 0.5f;
	    float outerHalf = innerHalf + spec.guideThickness;
	    float y0 = spec.deckTopY;
	    float y1 = spec.deckTopY + spec.guideHeight;

	    if (side == GuideSide.LEFT) {
	        buildLongSideStrip(frames, -innerHalf, -outerHalf, y0, y1, mb);
	    } else {
	        buildLongSideStrip(frames, outerHalf, innerHalf, y0, y1, mb);
	    }

	    return mb.build("Guide-" + side.name());
	}
	
	public static Mesh buildGuides(PathSegment3 path, TrackSpec spec, float startDistance, float endDistance) {
	    if (!spec.includeGuides) {
	        return null;
	    }

	    List<SampleFrame> frames = sampleFrames(path, startDistance, endDistance, spec.sampleStep);
	    if (frames.size() < 2) {
	        return null;
	    }

	    TrackMeshBuilder mb = new TrackMeshBuilder();

	    float innerHalf = spec.getGuideInnerWidth() * 0.5f;
	    float outerHalf = innerHalf + spec.guideThickness;

	    float y0 = spec.deckTopY;
	    float y1 = spec.deckTopY + spec.guideHeight;

	    buildLongSideStrip(frames, -innerHalf, -outerHalf, y0, y1, mb);
	    buildLongSideStrip(frames, outerHalf, innerHalf, y0, y1, mb);

	    return mb.build("Guides");
	}
	
	
	public static TrackBuildResult buildInterval(PathSegment3 path, TrackInterval interval, TrackSpec spec) {
		float start = interval.getStartDistance();
		float end = interval.getEndDistance();

		if (end <= start) {
			return new TrackBuildResult(null, null, List.of());
		}

		List<SampleFrame> frames = sampleFrames(path, start, end, spec.sampleStep);
		if (frames.size() < 2) {
			return new TrackBuildResult(null, null, List.of());
		}

		Mesh deck = buildDeck(spec, frames);

		boolean suppressRollers =
				interval.getType() == TrackIntervalType.TRANSFER
				&& spec.suppressRollersInTransferZones;

		List<ObjectTransformation> rollers = List.of();
		if (spec.includeRollers && !suppressRollers) {
			rollers = buildRollers(path, spec, start, end);
		}

		return new TrackBuildResult(deck, null, rollers);
	}

	private static void buildLongSideStrip(
	        List<SampleFrame> frames,
	        float innerOffset,
	        float outerOffset,
	        float y0,
	        float y1,
	        TrackMeshBuilder mb) {

		int[] ib = new int[frames.size()];
		int[] it = new int[frames.size()];
		int[] ob = new int[frames.size()];
		int[] ot = new int[frames.size()];

		for (int i = 0; i < frames.size(); i++) {
			SampleFrame f = frames.get(i);
			Vec3 inner = f.centre.add(f.side.scale(innerOffset));
			Vec3 outer = f.centre.add(f.side.scale(outerOffset));
			ib[i] = mb.addVertex(inner.x, y0, inner.z);
			it[i] = mb.addVertex(inner.x, y1, inner.z);
			ob[i] = mb.addVertex(outer.x, y0, outer.z);
			ot[i] = mb.addVertex(outer.x, y1, outer.z);
		}

		for (int i = 0; i < frames.size() - 1; i++) {
			mb.addQuad(it[i], ot[i], ot[i + 1], it[i + 1]);       // top
			mb.addQuad(ob[i], ib[i], ib[i + 1], ob[i + 1]);       // bottom
			mb.addQuad(ib[i], it[i], it[i + 1], ib[i + 1]);       // inner face
			mb.addQuad(ot[i], ob[i], ob[i + 1], ot[i + 1]);       // outer face
		}

		// start cap
		mb.addQuad(ob[0], ot[0], it[0], ib[0]);

		// end cap
		int last = frames.size() - 1;
		mb.addQuad(ib[last], it[last], ot[last], ob[last]);
	}
	
	private static List<Mat4.ObjectTransformation> buildRollers(PathSegment3 path, TrackSpec spec, float startDistance, float endDistance) {
		List<Mat4.ObjectTransformation> transforms = new ArrayList<>();
		if (endDistance <= startDistance) {
			return transforms;
		}

		float d = startDistance;
		while (d <= endDistance) {
			SampleFrame f = createFrame(path, d);
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
			d += spec.rollerPitch;
		}

		return transforms;
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
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
