package online.davisfamily.threedee.model.tracks;

import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;

public final class RollerMeshFactory {

    private RollerMeshFactory() {
    }

    /**
     * Creates a simple box roller mesh centred on the origin.
     *
     * Local axes:
     * X = along conveyor direction
     * Y = vertical
     * Z = across conveyor width
     *
     * @param depthAlongPath full size along local X
     * @param height         full size along local Y
     * @param widthAcross    full size along local Z
     */
    public static Mesh createBoxRollerMesh(float depthAlongPath, float height, float widthAcross) {
        float hx = depthAlongPath * 0.5f;
        float hy = height * 0.5f;
        float hz = widthAcross * 0.5f;

        Vec4[] vertices = new Vec4[] {
            // back face (x = -hx)
            new Vec4(-hx, -hy, -hz, 1f), // 0
            new Vec4(-hx, -hy,  hz, 1f), // 1
            new Vec4(-hx,  hy,  hz, 1f), // 2
            new Vec4(-hx,  hy, -hz, 1f), // 3

            // front face (x = +hx)
            new Vec4( hx, -hy, -hz, 1f), // 4
            new Vec4( hx, -hy,  hz, 1f), // 5
            new Vec4( hx,  hy,  hz, 1f), // 6
            new Vec4( hx,  hy, -hz, 1f)  // 7
        };

        int[][] triangles = new int[][] {
            // back (-X)
            {0, 2, 1}, {0, 3, 2},

            // front (+X)
            {4, 5, 6}, {4, 6, 7},

            // left (-Z)
            {0, 4, 7}, {0, 7, 3},

            // right (+Z)
            {1, 2, 6}, {1, 6, 5},

            // bottom (-Y)
            {0, 1, 5}, {0, 5, 4},

            // top (+Y)
            {3, 7, 6}, {3, 6, 2}
        };

        return new Mesh(vertices, triangles, "roller");
    }

    /**
     * Convenience overload using TrackSpec.
     */
    public static Mesh createBoxRollerMesh(TrackSpec spec) {
        float widthAcross = spec.getRunningWidth() - (2f * spec.rollerWidthInset);
        return createBoxRollerMesh(
            spec.rollerDepthAlongPath,
            spec.rollerHeight,
            widthAcross
        );
    }
}