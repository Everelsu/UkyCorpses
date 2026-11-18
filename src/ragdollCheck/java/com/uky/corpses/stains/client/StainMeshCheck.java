package com.uky.corpses.stains.client;

public final class StainMeshCheck {

    public static void run() {
        float[] quad = { -1, -1, 0, 0, 1, -1, 1, 0, 1, 1, 1, 1, -1, 1, 0, 1 };
        float[] clipped = StainMesh.clip(quad, 0, .5F, -.5F, .5F);
        assert clipped.length == 16;
        for (int i = 0; i < clipped.length; i += 4) {
            assert clipped[i] >= 0 && clipped[i] <= .5 && clipped[i + 1] >= -.5 && clipped[i + 1] <= .5;
            assert Math.abs(clipped[i + 2] - (clipped[i] + 1) / 2) < .00001;
            assert Math.abs(clipped[i + 3] - (clipped[i + 1] + 1) / 2) < .00001;
        }
        assert StainMesh.clip(quad, 2, 3, 2, 3).length == 0 : "stain floated over an unsupported neighboring block";
        float[] diagonal = { 0, -2, 0, 0, 2, 0, 1, 0, 0, 2, 1, 1, -2, 0, 0, 1 };
        assert StainMesh.clip(diagonal, -1, 1, -1, 1).length >= 16;
        for (int axis = 0; axis < 3; axis++) for (int sign : new int[] { -1, 1 }) {
            int nx = axis == 0 ? sign : 0, ny = axis == 1 ? sign : 0;
            double[] mapped = StainMesh.coordinates(nx, ny, -4, -3, -2);
            double plane = axis == 0 ? -4 : axis == 1 ? -3 : -2;
            assert mapped[2] == plane;
            assert (StainMesh.lift(sign, mapped[2]) - plane) * sign > .0014 : "normal lift vanished";
        }
        System.out.println(
            "SurfaceBlood stain mesh: clipped UVs, unsupported faces, diagonal polygons and all six normal lifts passed");
    }

    private StainMeshCheck() {}
}
