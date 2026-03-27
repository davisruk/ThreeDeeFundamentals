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

        // near ring:  0 .. segments-1   (z = -halfLen)
        // far ring:   segments .. 2*segments-1   (z = +halfLen)
        double step = (Math.PI * 2.0) / segments;

        for (int i = 0; i < segments; i++) {
            double angle = i * step;
            float x = radius * (float) Math.cos(angle);
            float y = radius * (float) Math.sin(angle);

            vertices[near(i)] = new Vec4(x, y, -halfLen, 1.0f);
            vertices[far(i, segments)] = new Vec4(x, y, +halfLen, 1.0f);
        }

        int nearCentre = -1;
        int farCentre = -1;

        if (capEnds) {
            nearCentre = sideVertexCount;
            farCentre = sideVertexCount + 1;

            vertices[nearCentre] = new Vec4(0f, 0f, -halfLen, 1.0f);
            vertices[farCentre] = new Vec4(0f, 0f, +halfLen, 1.0f);
        }

        List<int[]> triangles = new ArrayList<>();

        // side faces
        for (int i = 0; i < segments; i++) {
            int next = (i + 1) % segments;

            int ni = near(i);
            int nn = near(next);
            int fi = far(i, segments);
            int fn = far(next, segments);

            addTri(triangles, ni, fn, fi);
            addTri(triangles, ni, nn, fn);
        }

        if (capEnds) {
            // near cap outward normal = -Z
            for (int i = 0; i < segments; i++) {
                int next = (i + 1) % segments;
                addTri(triangles, nearCentre, near(next), near(i));
            }

            // far cap outward normal = +Z
            for (int i = 0; i < segments; i++) {
                int next = (i + 1) % segments;
                addTri(triangles, farCentre, far(i, segments), far(next, segments));
            }
        }

        return new Mesh(vertices, triangles.toArray(new int[0][]), "cylinder");
    }

    private static int near(int i) {
        return i;
    }

    private static int far(int i, int segments) {
        return segments + i;
    }

    private static void addTri(List<int[]> triangles, int a, int b, int c) {
        triangles.add(new int[] { a, b, c });
    }
}