package online.davisfamily.warehouse.sim.totebag.bag;

import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.warehouse.sim.totebag.plan.BagSpec;

public final class BagMeshFactory {
    private BagMeshFactory() {
    }

    public static Mesh createBagMesh(BagSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }

        float visualHeight = spec.height() * 1.75f;
        float visualWidth = Math.min(spec.width() * 0.62f, visualHeight * 0.48f);
        float visualDepth = Math.min(spec.depth() * 0.48f, visualHeight * 0.22f);
        float bottomHalfWidth = visualWidth * 0.5f;
        float bottomHalfDepth = visualDepth * 0.5f;
        float shoulderY = visualHeight * 0.70f;
        float panelTopY = visualHeight * 0.90f;
        float seamY = visualHeight;
        float shoulderHalfWidth = visualWidth * 0.42f;
        float topHalfWidth = visualWidth * 0.36f;
        float topHalfDepth = visualDepth * 0.42f;
        float seamHalfWidth = visualWidth * 0.38f;
        float seamHalfDepth = visualDepth * 0.10f;
        float flapBottomY = visualHeight * 0.66f;

        Vec4[] vertices = new Vec4[] {
                new Vec4(-bottomHalfWidth, 0f, -bottomHalfDepth, 1f),
                new Vec4(bottomHalfWidth, 0f, -bottomHalfDepth, 1f),
                new Vec4(bottomHalfWidth, 0f, bottomHalfDepth, 1f),
                new Vec4(-bottomHalfWidth, 0f, bottomHalfDepth, 1f),

                new Vec4(-shoulderHalfWidth, shoulderY, -topHalfDepth, 1f),
                new Vec4(shoulderHalfWidth, shoulderY, -topHalfDepth, 1f),
                new Vec4(shoulderHalfWidth, shoulderY, topHalfDepth, 1f),
                new Vec4(-shoulderHalfWidth, shoulderY, topHalfDepth, 1f),

                new Vec4(-seamHalfWidth, seamY, -seamHalfDepth, 1f),
                new Vec4(seamHalfWidth, seamY, -seamHalfDepth, 1f),
                new Vec4(seamHalfWidth, seamY, seamHalfDepth, 1f),
                new Vec4(-seamHalfWidth, seamY, seamHalfDepth, 1f),

                new Vec4(-topHalfWidth, panelTopY, -topHalfDepth * 0.98f, 1f),
                new Vec4(topHalfWidth, panelTopY, -topHalfDepth * 0.98f, 1f),
                new Vec4(topHalfWidth, flapBottomY, -topHalfDepth, 1f),
                new Vec4(-topHalfWidth, flapBottomY, -topHalfDepth, 1f),

                new Vec4(0f, visualHeight * 0.14f, -bottomHalfDepth, 1f),
                new Vec4(0f, visualHeight * 0.54f, -topHalfDepth, 1f),
                new Vec4(0f, visualHeight * 0.14f, bottomHalfDepth, 1f),
                new Vec4(0f, visualHeight * 0.54f, topHalfDepth, 1f),

                new Vec4(-topHalfWidth, panelTopY, -topHalfDepth, 1f),
                new Vec4(topHalfWidth, panelTopY, -topHalfDepth, 1f),
                new Vec4(topHalfWidth, panelTopY, topHalfDepth, 1f),
                new Vec4(-topHalfWidth, panelTopY, topHalfDepth, 1f)
        };

        int[][] triangles = new int[][] {
                {0, 1, 2}, {0, 2, 3},

                {0, 4, 5}, {0, 5, 1},
                {1, 5, 6}, {1, 6, 2},
                {2, 6, 7}, {2, 7, 3},
                {3, 7, 4}, {3, 4, 0},

                {4, 20, 21}, {4, 21, 5},
                {5, 21, 22}, {5, 22, 6},
                {6, 22, 23}, {6, 23, 7},
                {7, 23, 20}, {7, 20, 4},

                {20, 8, 9}, {20, 9, 21},
                {21, 9, 10}, {21, 10, 22},
                {22, 10, 11}, {22, 11, 23},
                {23, 11, 8}, {23, 8, 20},
                {8, 11, 10}, {8, 10, 9},

                {12, 13, 14}, {12, 14, 15},

                {0, 16, 4}, {16, 17, 4},
                {1, 5, 16}, {16, 5, 17},
                {3, 7, 18}, {18, 7, 19},
                {2, 18, 6}, {18, 19, 6}
        };
        return new Mesh(vertices, triangles, "bag");
    }
}
