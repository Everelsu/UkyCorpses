package com.uky.corpses;

import com.uky.corpses.corpse.Skeleton;

/** Server rule boundary checks run without a Minecraft world or client renderer. */
public final class OriginalGameplayCheck {

    public static void main(String[] args) {
        assert OriginalGameplay.downedSeconds == 60 && OriginalGameplay.reviveSeconds == 10;
        assert OriginalGameplay.reviveDecaySeconds == 30 && OriginalGameplay.knockedHealth == 12;
        assert OriginalGameplay.downedInvulnerableTicks == 10 && OriginalGameplay.secondChanceCooldownSeconds == 120;
        assert OriginalGameplay.knockingDisabled(true, 1);
        assert !OriginalGameplay.knockingDisabled(true, 1, true) : "inventory adrenaline enables solo knockout";
        assert !OriginalGameplay.knockingDisabled(true, 2);
        assert !OriginalGameplay.knockingDisabled(false, 1);
        assert !OriginalGameplay.canBeDowned(99, 100, 40);
        assert OriginalGameplay.canBeDowned(100, 100, 40);
        assert !OriginalGameplay.canBeDowned(10, 0, 40);
        assert OriginalGameplay.knockedHealthAfter(50, 20, 39, 40) == 50;
        assert OriginalGameplay.knockedHealthAfter(50, 20, 40, 40) == 30;
        assert OriginalGameplay.knockedHealthAfter(5, Float.POSITIVE_INFINITY, 40, 40) == 0;
        assert OriginalGameplay.knockedHealthAfter(5, Float.NaN, 40, 40) == 5;
        double progress = 0;
        for (int tick = 0; tick < 100; tick++) progress = OriginalGameplay.advanceRevive(progress, true, 10, 30);
        assert Math.abs(progress - .5) < .000001;
        for (int tick = 0; tick < 150; tick++) progress = OriginalGameplay.advanceRevive(progress, false, 10, 30);
        assert Math.abs(progress - .25) < .000001 : "full-bar decay must take 30 seconds";
        for (int tick = 0; tick < 150; tick++) progress = OriginalGameplay.advanceRevive(progress, true, 10, 30);
        assert Math.abs(progress - 1) < .000001 : "interrupted revival resumes remaining progress";
        int held = 0;
        for (int tick = 0; tick < OriginalGameplay.GIVE_UP_TICKS - 1; tick++)
            held = OriginalGameplay.advanceGiveUp(held, 0);
        assert held == OriginalGameplay.GIVE_UP_TICKS - 1;
        assert OriginalGameplay.advanceGiveUp(held, 0) == OriginalGameplay.GIVE_UP_TICKS;
        assert OriginalGameplay.advanceGiveUp(held, 11) == 0 : "stale input cannot surrender";
        assert OriginalGameplay.draggablePart(Skeleton.HEAD) && OriginalGameplay.draggablePart(Skeleton.RLEG)
            && OriginalGameplay.draggablePart(Skeleton.LLEG);
        assert !OriginalGameplay.draggablePart(Skeleton.BODY) && !OriginalGameplay.draggablePart(Skeleton.RARM)
            && !OriginalGameplay.draggablePart(Skeleton.LARM);
        System.out.println(
            "Original PC/PR: defaults, LAN gating, cooldown, knocked health, revival decay, surrender and drag boundaries passed");
    }
}
