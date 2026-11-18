package com.uky.corpses.client;

import com.uky.corpses.corpse.Mat4;
import com.uky.corpses.corpse.Poses;
import com.uky.corpses.corpse.Skeleton;

public final class RagdollCameraCheck {

    public static void run() {
        Mat4 root = new Mat4(), head = new Mat4();
        float[] limbs = new float[15];
        Skeleton.place(root, Poses.LIE, 0, 0, 0, Float.NaN, 0);
        Skeleton.part(root, Skeleton.HEAD, limbs, head);
        double[] centre = head.apply(0, -4, 0, new double[3]), eye = new double[3];
        RagdollCamera.headView(head, 10, 20, 30, eye);
        assert Math.abs(eye[0] - centre[0] - 10) < 1e-6;
        assert Math.abs(eye[1] - centre[1] - 20 - .05) < 1e-6;
        assert Math.abs(eye[2] - centre[2] - 30 - .325) < 1e-6;
        float[] angles = RagdollCamera.angles(head);
        for (float value : angles) assert Math.abs(value) < 1e-5 : "neutral camera orientation";
        Skeleton.place(root.identity(), Poses.LIE, 90, 0, 0, Float.NaN, 0);
        Skeleton.part(root, Skeleton.HEAD, limbs, head);
        assert Math.abs(RagdollCamera.angles(head)[0] - 90) < 1e-4 : "yaw convention";
        // Detaching the head translates its camera anchor by exactly the same amount.
        RagdollCamera.headView(head, 0, 0, 0, eye);
        double x = eye[0], y = eye[1], z = eye[2];
        head.moved(3, 4, 5);
        RagdollCamera.headView(head, 0, 0, 0, eye);
        assert Math.abs(eye[0] - x - 3) < 1e-6 && Math.abs(eye[1] - y - 4) < 1e-6 && Math.abs(eye[2] - z - 5) < 1e-6;
    }

    private RagdollCameraCheck() {}
}
