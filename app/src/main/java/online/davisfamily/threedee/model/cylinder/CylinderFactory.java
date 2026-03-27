package online.davisfamily.threedee.model.cylinder;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;

public final class CylinderFactory {

    private CylinderFactory() {
    }

    public static Mesh buildCylinder(float radius, float length, int segments, boolean capEnds) {
        if (radius <= 0f) {
            throw new IllegalArgumentException("radius must be > 0");
        }
        if (length <= 0f) {
            throw new IllegalArgumentException("length must be > 0");
        }
        if (segments < 3) {
            throw new IllegalArgumentException("segments must be >= 3");
        }

        float halfLen = length * 0.5f;

        int sideVertexCount = segments * 2;
        int totalVertexCount = capEnds ? sideVertexCount + 2 : sideVertexCount;

        Vec4[] vertices = new Vec4[totalVertexCount];
        double step = (Math.PI * 2.0) / segments;
        // Build rings
        for (int i = 0; i < segments; i++) {
            double angle = i * step;
            float y = radius * (float) Math.cos(angle);
            float z = radius * (float) Math.sin(angle);

            vertices[left(i)] = new Vec4(-halfLen, y, z, 1.0f);
            vertices[right(i, segments)] = new Vec4(+halfLen, y, z, 1.0f);
        }

        int leftCentre = -1;
        int rightCentre = -1;

        if (capEnds) {
            leftCentre = sideVertexCount;
            rightCentre = sideVertexCount + 1;

            vertices[leftCentre] = new Vec4(-halfLen, 0f, 0f, 1.0f);
            vertices[rightCentre] = new Vec4(+halfLen, 0f, 0f, 1.0f);
        }

        List<int[]> triangles = new ArrayList<>();

        // Side faces
        for (int i = 0; i < segments; i++) {
            int next = (i + 1) % segments;

            int li = left(i);
            int ln = left(next);
            int ri = right(i, segments);
            int rn = right(next, segments);

            addTri(triangles, li, rn, ri);
            addTri(triangles, li, ln, rn);
        }

        if (capEnds) {
            // Left cap (-X)
            for (int i = 0; i < segments; i++) {
                int next = (i + 1) % segments;
                addTri(triangles, leftCentre, left(next), left(i));
            }

            // Right cap (+X)
            for (int i = 0; i < segments; i++) {
                int next = (i + 1) % segments;
                addTri(triangles, rightCentre, right(i, segments), right(next, segments));
            }
        }

        return new Mesh(vertices, triangles.toArray(new int[0][]), "Cylinder");
    }

    private static int left(int i) {
        return i;
    }

    private static int right(int i, int segments) {
        return segments + i;
    }

    private static void addTri(List<int[]> triangles, int a, int b, int c) {
        triangles.add(new int[] { a, b, c });
    }
}