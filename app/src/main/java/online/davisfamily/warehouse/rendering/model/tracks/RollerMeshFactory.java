package online.davisfamily.warehouse.rendering.model.tracks;

import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.model.cylinder.CylinderFactory;

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
            {0, 1, 2}, {0, 2, 3},

            // front (+X)
            {4, 7, 6}, {4, 6, 5},

            // left (-Z)
            {0, 3, 7}, {0, 7, 4},

            // right (+Z)
            {1, 5, 6}, {1, 6, 2},

            // bottom (-Y)
            {0, 4, 5}, {0, 5, 1},

            // top (+Y)
            {3, 2, 6}, {3, 6, 7}
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
    
    public static Mesh createCylinderRollerMesh(TrackSpec spec) {
        float widthAcross = spec.getRunningWidth() - (2f * spec.rollerWidthInset);
        float radius = Math.max(spec.rollerHeight, spec.rollerDepthAlongPath) * 1.2f;
        int segments = 8;
        return CylinderFactory.buildCylinder(radius, widthAcross, segments, true);
    }
}