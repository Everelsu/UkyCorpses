package com.uky.corpses.client;

public final class DragArmPoseCheck {

    public static void run() {
        float[] forward = DragArmPose.angles(0, 0, 1, 0);
        assert Math.abs(forward[0] + Math.PI / 2) < .000001 && forward[1] == 0;
        assert DragArmPose.angles(1, 0, 0, 0)[1] == -.8F;
        assert DragArmPose.angles(-1, 0, 0, 0)[1] == .8F;
        assert DragArmPose.angles(0, 100, 1, 0)[0] == -.42F;
        assert DragArmPose.angles(0, -100, 1, 0)[0] == -1.92F;
        float[] wrapped = DragArmPose.angles(0, 0, 1, 359), negative = DragArmPose.angles(0, 0, 1, -1);
        assert Math.abs(wrapped[1] - negative[1]) < .000001;
        for (int yaw = -720; yaw <= 720; yaw += 15) for (int y = -3; y <= 3; y++) {
            float[] angle = DragArmPose.angles(Math.sin(yaw * .13), y, Math.cos(yaw * .13), yaw);
            assert Float.isFinite(angle[0]) && Float.isFinite(angle[1]);
            assert angle[0] >= -1.92F && angle[0] <= -.42F && angle[1] >= -.8F && angle[1] <= .8F;
        }
        System.out.println("Revival drag arms: original pitch/yaw caps and yaw wrapping passed");
    }

    private DragArmPoseCheck() {}
}
