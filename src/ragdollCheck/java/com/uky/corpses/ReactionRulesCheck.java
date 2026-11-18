package com.uky.corpses;

public final class ReactionRulesCheck {

    public static void main(String[] args) {
        assert detect(new double[] { 20, 0, 0 }, new double[] { 0, 0, 0 }, true, false, false, false)
            == ReactionRules.IMPACT;
        assert detect(new double[] { 0, -30, 0 }, new double[] { 0, 0, 0 }, false, true, true, false)
            == ReactionRules.LANDING;
        assert detect(new double[] { 20, 0, 0 }, new double[] { 0, 0, 0 }, false, false, false, false)
            == ReactionRules.NONE : "braking is not knockback";
        assert detect(new double[] { 20, 0, 0 }, new double[] { 20, 35, 0 }, false, false, false, false)
            == ReactionRules.KNOCKBACK;
        assert detect(new double[] { 0, 0, 0 }, new double[] { 0, 35, 0 }, false, false, false, true)
            == ReactionRules.NONE : "ordinary jump";
        assert ReactionRules.detect(
            new double[] { 0, -30, 0 },
            new double[] { 0, 0, 0 },
            false,
            true,
            true,
            false,
            true,
            false,
            true,
            true,
            true,
            16,
            24,
            30) == 0 : "bouncy landing";
        assert ReactionRules.wallDamage(12, 10, .25F) == .5F;
        assert ReactionRules.wallDamage(11, 10, .25F) == 0;
        assert ReactionRules.wallDamage(100, 10, .25F) == 17.5F;
        assert ReactionRules.wallDamage(Float.NaN, 10, .25F) == 0;
        System.out
            .println("Installed reaction braking, landing, external push, jump, bouncy and wall-damage rules passed");
    }

    private static int detect(double[] a, double[] b, boolean h, boolean v, boolean below, boolean ground) {
        return ReactionRules.detect(a, b, h, v, below, ground, false, false, true, true, true, 16, 24, 30);
    }
}
