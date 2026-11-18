package com.uky.corpses.amp;

import java.util.Random;

public final class AmputationRoll {

    private AmputationRoll() {}

    public static double aliveChance(Part part, float damage, boolean sharp, double armor) {
        if ((double) damage < (Double) AmputationConfig.ALIVE_MIN_DAMAGE.get()) {
            return 0.0;
        } else {
            double chance = base(
                part,
                (Double) AmputationConfig.ALIVE_CHANCE_UNARMORED.get(),
                (Double) AmputationConfig.ALIVE_CHANCE_ARMORED.get(),
                armor,
                sharp);
            return chance <= 0.0 ? 0.0
                : clamp(chance * Math.sqrt((double) damage / (Double) AmputationConfig.ALIVE_DAMAGE_REFERENCE.get()));
        }
    }

    public static double deathChance(Part part, float damage, float overkill, boolean sharp, double armor) {
        double chance = base(
            part,
            (Double) AmputationConfig.DEATH_CHANCE_UNARMORED.get(),
            (Double) AmputationConfig.DEATH_CHANCE_ARMORED.get(),
            armor,
            sharp);
        if (chance <= 0.0) {
            return 0.0;
        } else {
            chance *= Math
                .sqrt((double) Math.max(0.1F, damage) / (Double) AmputationConfig.DEATH_DAMAGE_REFERENCE.get());
            chance += (double) Math.max(0.0F, overkill) * AmputationConfig.DEATH_OVERKILL_BONUS.get()
                * AmputationConfig.partWeight(part);
            return clamp(chance);
        }
    }

    public static double deadChance(Part part, double impulse, double armor) {
        if (impulse < (Double) AmputationConfig.DEAD_MIN_IMPULSE.get()) {
            return 0.0;
        } else {
            double chance = base(
                part,
                (Double) AmputationConfig.DEAD_CHANCE_UNARMORED.get(),
                (Double) AmputationConfig.DEAD_CHANCE_ARMORED.get(),
                armor,
                true);
            return chance <= 0.0 ? 0.0
                : clamp(chance * (impulse / (Double) AmputationConfig.DEAD_IMPULSE_REFERENCE.get()));
        }
    }

    private static double base(Part part, double unarmored, double armored, double armor, boolean sharp) {
        if (part == null || !part.isSeverable()) {
            return 0.0;
        } else if (part == Part.HEAD && !(Boolean) AmputationConfig.HEADSHOTS_DECAPITATE.get()) {
            return 0.0;
        } else {
            double t = Math.max(0.0, Math.min(1.0, armor));
            double chance = (unarmored + (armored - unarmored) * t) * AmputationConfig.partWeight(part);
            if (!sharp) {
                if ((Boolean) AmputationConfig.REQUIRE_SHARP_WEAPON.get()) {
                    return 0.0;
                }

                chance *= AmputationConfig.BLUNT_WEAPON_MULTIPLIER.get();
            }

            return chance;
        }
    }

    public static boolean roll(Random random, double chance) {
        return chance > 0.0 && random.nextDouble() < chance;
    }

    private static double clamp(double chance) {
        return Math.max(0.0, Math.min(1.0, chance));
    }
}
