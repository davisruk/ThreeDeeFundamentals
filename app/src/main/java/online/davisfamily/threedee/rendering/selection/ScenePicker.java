package online.davisfamily.threedee.rendering.selection;

import java.util.List;

import online.davisfamily.threedee.camera.Camera;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.rendering.RenderableObject;

public class ScenePicker {

	private final PickRay worldRay = new PickRay();
	private final PickRay localRay = new PickRay();
	
	private final Mat4 worldModel = new Mat4();
	private final Mat4 inverseWorldModel = new Mat4();
	
	private final Vec3 tempVecA = new Vec3();
	private final Vec3 tempVecB = new Vec3();
	private final Vec3 tempVecC = new Vec3();
	private final Vec3 tempVecD = new Vec3();
	private final Vec3 tempVecE = new Vec3();
	private final Vec3 tempVecF = new Vec3();
	
	public PickHit pick (List<RenderableObject> roots, Camera camera, float fovYRadians, int mouseX, int mouseY, int viewportWidth, int viewportHeight) {
		PickHit bestHit = new PickHit();
		bestHit.reset();
		buildWorldRay(camera, fovYRadians, mouseX, mouseY, viewportWidth, viewportHeight, worldRay);
		
		for (RenderableObject ro:roots) {
			recursivePick(ro, null, worldRay, bestHit);
		}
		return bestHit.hasHit() ? bestHit : null;
	}
	
	private void buildWorldRay(Camera camera, float fovYRadians, int mouseX, int mouseY, int viewportWidth, int viewportHeight, PickRay out) {
		float ndcX = (2.0f * mouseX / (viewportWidth - 1)) - 1.0f;
        float ndcY = 1.0f - (2.0f * mouseY / (viewportHeight - 1));

        float aspect = (float) viewportWidth / (float) viewportHeight;
        float tanHalfFovY = (float) Math.tan(fovYRadians * 0.5f);

        float camX = ndcX * aspect * tanHalfFovY;
        float camY = ndcY * tanHalfFovY;

        out.origin.set(camera.position);

        out.direction
            .set(camera.getForward())
            .mutableAdd(camera.getRight().scale(camX, tempVecA))
            .mutableAdd(camera.getUp().scale(camY, tempVecB))
            .mutableNormalize();
	}

	private void recursivePick(RenderableObject ro, Mat4 parentModel, PickRay ray, PickHit bestHit) {
		ro.transformation.setupModel();
		worldModel.set(parentModel != null ? parentModel : Mat4.identity());
		worldModel.mutableMultiply(ro.transformation.model);
		if (ro.isSelectable() && ro.mesh != null) {
			inverseWorldModel.setRigidInverse(worldModel);
			inverseWorldModel.transformPoint(ray.origin, localRay.origin);
			inverseWorldModel.transformDirection(ray.direction, localRay.direction);
			localRay.direction.mutableNormalize();
			if (intersectsBoundingSphere(localRay, ro.mesh)) {
				intersectMesh(ro, worldModel, localRay, ray, bestHit);
			}
		}
		
		Mat4 currentWorld = new Mat4().set(worldModel);
		for (RenderableObject child: ro.children) {
			recursivePick(child, currentWorld, ray, bestHit);
		}
	}

	private boolean intersectsBoundingSphere(PickRay ray, Mesh mesh) {
	    Vec3 oc = tempVecC.set(ray.origin).mutableSubtract(mesh.boundsCentre);

	    float a = ray.direction.dot(ray.direction);
	    float b = 2.0f * oc.dot(ray.direction);
	    float c = oc.dot(oc) - mesh.boundsRadius * mesh.boundsRadius;

	    float discriminant = b * b - 4f * a * c;
	    if (discriminant < 0f) {
	        return false;
	    }

	    float sqrt = (float) Math.sqrt(discriminant);
	    float t1 = (-b - sqrt) / (2f * a);
	    float t2 = (-b + sqrt) / (2f * a);

	    return t1 >= 0f || t2 >= 0f;
	}
	
	private void intersectMesh(
            RenderableObject ro,
            Mat4 worldModel,
            PickRay localRay,
            PickRay worldRay,
            PickHit bestHit) {

        Mesh mesh = ro.mesh;

        for (int i = 0; i < mesh.triangles.length; i++) {
            int[] tri = mesh.triangles[i];

            Vec4 a4 = mesh.v4Vertices[tri[0]];
            Vec4 b4 = mesh.v4Vertices[tri[1]];
            Vec4 c4 = mesh.v4Vertices[tri[2]];

            tempVecA.setXYZ(a4.x, a4.y, a4.z);
            tempVecB.setXYZ(b4.x, b4.y, b4.z);
            tempVecC.setXYZ(c4.x, c4.y, c4.z);

            Float t = intersectRayTriangle(localRay, tempVecA, tempVecB, tempVecC);
            if (t == null || t <= 0f) {
                continue;
            }

            tempVecD.set(localRay.direction).mutableScale(t).mutableAdd(localRay.origin);
            worldModel.transformPoint(tempVecD, tempVecE);

            float worldDistance = tempVecE.subtract(worldRay.origin).length();

            if (worldDistance < bestHit.distance) {
                bestHit.object = ro.getSelectionTarget();
                bestHit.distance = worldDistance;
                bestHit.triangleIndex = i;
                bestHit.localHitPoint.set(tempVecD);
                bestHit.worldHitPoint.set(tempVecE);
            }
        }
    }
	
	private final Vec3 triEdge1 = new Vec3();
	private final Vec3 triEdge2 = new Vec3();
	private final Vec3 triH = new Vec3();
	private final Vec3 triS = new Vec3();
	private final Vec3 triQ = new Vec3();
	
	private Float intersectRayTriangle(PickRay ray, Vec3 v0, Vec3 v1, Vec3 v2) {
	    final float epsilon = 0.000001f;

	    triEdge1.set(v1).mutableSubtract(v0);
	    triEdge2.set(v2).mutableSubtract(v0);

	    triH.set(ray.direction.cross(triEdge2));
	    float a = triEdge1.dot(triH);

	    if (a > -epsilon && a < epsilon) {
	        return null;
	    }

	    float f = 1.0f / a;

	    triS.set(ray.origin).mutableSubtract(v0);
	    float u = f * triS.dot(triH);

	    if (u < 0.0f || u > 1.0f) {
	        return null;
	    }

	    triQ.set(triS.cross(triEdge1));
	    float v = f * ray.direction.dot(triQ);

	    if (v < 0.0f || u + v > 1.0f) {
	        return null;
	    }

	    float t = f * triEdge2.dot(triQ);
	    return t > epsilon ? t : null;
	}
}
