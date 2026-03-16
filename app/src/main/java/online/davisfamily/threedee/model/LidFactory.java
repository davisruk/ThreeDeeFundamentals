package online.davisfamily.threedee.model;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.threedee.matrices.Vec4;


public final class LidFactory {

    private LidFactory() {
    }

    public static final class InterlockingLids {
        public final Mesh leftMesh;
        public final Mesh rightMesh;

        public InterlockingLids(Mesh leftMesh, Mesh rightMesh) {
            this.leftMesh = leftMesh;
            this.rightMesh = rightMesh;
        }
    }

    public static InterlockingLids buildInterlockingAngularLids(
            float openingWidth,
            float openingDepth,
            float thickness,
            int toothCount,
            float seamAmplitude,
            float valleyFlatFraction,
            float peakFlatFraction) {

        if (toothCount < 1 || toothCount % 2 == 0) {
            throw new IllegalArgumentException("toothCount must be odd, e.g. 3 or 5.");
        }
        if (thickness <= 0f) {
            throw new IllegalArgumentException("thickness must be > 0.");
        }
        if (valleyFlatFraction <= 0f || peakFlatFraction <= 0f) {
            throw new IllegalArgumentException("Flat fractions must be > 0.");
        }
        if (valleyFlatFraction + peakFlatFraction >= 1.0f) {
            throw new IllegalArgumentException("valleyFlatFraction + peakFlatFraction must be < 1.0.");
        }

        Seams seam = buildSeam(openingWidth, openingDepth, toothCount, seamAmplitude,
                valleyFlatFraction, peakFlatFraction);

        Mesh left = buildLeftFlap(openingWidth, thickness, seam);
        Mesh right = buildRightFlap(openingWidth, thickness, seam);

        return new InterlockingLids(left, right);
    }

    private static final class Seams {
        final float[] z;
        final float[] seamX;

        Seams(float[] z, float[] seamX) {
            this.z = z;
            this.seamX = seamX;
        }
    }

    /**
     * Builds a single shared seam polyline in world-local lid coordinates,
     * where x=0 is the centre seam between the two flaps.
     *
     * seamX oscillates around 0.
     */
    private static Seams buildSeam(
            float openingWidth,
            float openingDepth,
            int toothCount,
            float seamAmplitude,
            float valleyFlatFraction,
            float peakFlatFraction) {

        float halfDepth = openingDepth / 2f;
        float toothSpan = openingDepth / toothCount;

        List<Float> zList = new ArrayList<>();
        List<Float> seamList = new ArrayList<>();

        float xValley = -seamAmplitude;
        float xPeak = +seamAmplitude;

        for (int tooth = 0; tooth < toothCount; tooth++) {
            float z0 = -halfDepth + tooth * toothSpan;
            float z1 = z0 + toothSpan;

            float valleyLen = toothSpan * valleyFlatFraction;
            float peakLen = toothSpan * peakFlatFraction;
            float rampLen = (toothSpan - valleyLen - peakLen) / 2f;

            float zA = z0;
            float zB = zA + valleyLen;
            float zC = zB + rampLen;
            float zD = zC + peakLen;
            float zE = z1;

            if (tooth == 0) {
                zList.add(zA);
                seamList.add(xValley);
            }

            zList.add(zB);
            seamList.add(xValley);

            zList.add(zC);
            seamList.add(xPeak);

            zList.add(zD);
            seamList.add(xPeak);

            zList.add(zE);
            seamList.add(xValley);
        }

        float[] z = new float[zList.size()];
        float[] seamX = new float[seamList.size()];

        for (int i = 0; i < z.length; i++) {
            z[i] = zList.get(i);
            seamX[i] = seamList.get(i);
        }

        return new Seams(z, seamX);
    }

    /**
     * Left flap:
     * - local hinge edge lies on x = 0
     * - local free edge lies on x = halfWidth + seamX(z)
     *
     * This is the same hinge convention as your original rectangular flap.
     */
    private static Mesh buildLeftFlap(float openingWidth, float thickness, Seams seam) {
        float halfWidth = openingWidth / 2f;
        int sampleCount = seam.z.length;

        Vec4[] vertices = new Vec4[sampleCount * 4];

        int[] topHinge = new int[sampleCount];
        int[] topFree = new int[sampleCount];
        int[] botHinge = new int[sampleCount];
        int[] botFree = new int[sampleCount];

        for (int i = 0; i < sampleCount; i++) {
            float z = seam.z[i];
            float freeX = halfWidth + seam.seamX[i];

            if (freeX < 0.001f) {
                freeX = 0.001f;
            }

            int th = i;
            int tf = sampleCount + i;
            int bh = (sampleCount * 2) + i;
            int bf = (sampleCount * 3) + i;

            topHinge[i] = th;
            topFree[i] = tf;
            botHinge[i] = bh;
            botFree[i] = bf;

            vertices[th] = new Vec4(0.0f, 0.0f, z, 1.0f);
            vertices[tf] = new Vec4(freeX, 0.0f, z, 1.0f);
            vertices[bh] = new Vec4(0.0f, -thickness, z, 1.0f);
            vertices[bf] = new Vec4(freeX, -thickness, z, 1.0f);
        }

        List<int[]> triangles = new ArrayList<>();

        // top (+y)
        for (int i = 0; i < sampleCount - 1; i++) {
            int h0 = topHinge[i];
            int h1 = topHinge[i + 1];
            int f0 = topFree[i];
            int f1 = topFree[i + 1];

            addTri(triangles, h0, h1, f1);
            addTri(triangles, h0, f1, f0);
        }

        // bottom (-y)
        for (int i = 0; i < sampleCount - 1; i++) {
            int h0 = botHinge[i];
            int h1 = botHinge[i + 1];
            int f0 = botFree[i];
            int f1 = botFree[i + 1];

            addTri(triangles, h0, f1, h1);
            addTri(triangles, h0, f0, f1);
        }

        buildPerimeterWalls(triangles, topHinge, topFree, botHinge, botFree);

        return new Mesh(vertices, triangles.toArray(new int[0][]));
    }

    /**
     * Right flap:
     * - local hinge edge lies on x = 0
     * - local free edge lies on x = halfWidth - seamX(z)
     *
     * This is complementary to the left flap.
     *
     * Use the same placement idea as before, but you should no longer need
     * Y = PI to force the seam to match.
     */
    private static Mesh buildRightFlap(float openingWidth, float thickness, Seams seam) {
        float halfWidth = openingWidth / 2f;
        int sampleCount = seam.z.length;

        Vec4[] vertices = new Vec4[sampleCount * 4];

        int[] topHinge = new int[sampleCount];
        int[] topFree  = new int[sampleCount];
        int[] botHinge = new int[sampleCount];
        int[] botFree  = new int[sampleCount];

        for (int i = 0; i < sampleCount; i++) {
            float z = seam.z[i];

            // IMPORTANT:
            // right flap extends inward in NEGATIVE local X
            float freeX = -halfWidth + seam.seamX[i];

            // keep safely away from exactly zero-width geometry if ever needed
            if (freeX > -0.001f) {
                freeX = -0.001f;
            }

            int th = i;
            int tf = sampleCount + i;
            int bh = (sampleCount * 2) + i;
            int bf = (sampleCount * 3) + i;

            topHinge[i] = th;
            topFree[i]  = tf;
            botHinge[i] = bh;
            botFree[i]  = bf;

            vertices[th] = new Vec4(0.0f,   0.0f,      z, 1.0f);
            vertices[tf] = new Vec4(freeX,  0.0f,      z, 1.0f);
            vertices[bh] = new Vec4(0.0f,  -thickness, z, 1.0f);
            vertices[bf] = new Vec4(freeX, -thickness, z, 1.0f);
        }

        List<int[]> triangles = new ArrayList<>();

        // top (+y)
        for (int i = 0; i < sampleCount - 1; i++) {
            int h0 = topHinge[i];
            int h1 = topHinge[i + 1];
            int f0 = topFree[i];
            int f1 = topFree[i + 1];

            // reversed from left flap because flap extends in -x
            addTri(triangles, h0, f1, h1);
            addTri(triangles, h0, f0, f1);
        }

        // bottom (-y)
        for (int i = 0; i < sampleCount - 1; i++) {
            int h0 = botHinge[i];
            int h1 = botHinge[i + 1];
            int f0 = botFree[i];
            int f1 = botFree[i + 1];

            addTri(triangles, h0, h1, f1);
            addTri(triangles, h0, f1, f0);
        }

        buildPerimeterWallsRight(triangles, topHinge, topFree, botHinge, botFree);

        return new Mesh(vertices, triangles.toArray(new int[0][]));
    }
    private static void buildPerimeterWallsRight(
            List<int[]> triangles,
            int[] topHinge,
            int[] topFree,
            int[] botHinge,
            int[] botFree) {

        List<Integer> topLoop = new ArrayList<>();
        List<Integer> botLoop = new ArrayList<>();

        topLoop.add(topHinge[0]);
        botLoop.add(botHinge[0]);

        for (int i = 0; i < topFree.length; i++) {
            topLoop.add(topFree[i]);
            botLoop.add(botFree[i]);
        }

        topLoop.add(topHinge[topHinge.length - 1]);
        botLoop.add(botHinge[botHinge.length - 1]);

        int loopSize = topLoop.size();

        for (int i = 0; i < loopSize; i++) {
            int j = (i + 1) % loopSize;

            int topA = topLoop.get(i);
            int topB = topLoop.get(j);
            int botA = botLoop.get(i);
            int botB = botLoop.get(j);

            // reversed winding compared with left flap
            addTri(triangles, topA, botB, topB);
            addTri(triangles, topA, botA, botB);
        }
    }
    private static void buildPerimeterWalls(
            List<int[]> triangles,
            int[] topHinge,
            int[] topFree,
            int[] botHinge,
            int[] botFree) {

        List<Integer> topLoop = new ArrayList<>();
        List<Integer> botLoop = new ArrayList<>();

        topLoop.add(topHinge[0]);
        botLoop.add(botHinge[0]);

        for (int i = 0; i < topFree.length; i++) {
            topLoop.add(topFree[i]);
            botLoop.add(botFree[i]);
        }

        topLoop.add(topHinge[topHinge.length - 1]);
        botLoop.add(botHinge[botHinge.length - 1]);

        int loopSize = topLoop.size();

        for (int i = 0; i < loopSize; i++) {
            int j = (i + 1) % loopSize;

            int topA = topLoop.get(i);
            int topB = topLoop.get(j);
            int botA = botLoop.get(i);
            int botB = botLoop.get(j);

            addTri(triangles, topA, topB, botB);
            addTri(triangles, topA, botB, botA);
        }
    }

    private static void addTri(List<int[]> triangles, int a, int b, int c) {
        triangles.add(new int[] { a, b, c });
    }
}