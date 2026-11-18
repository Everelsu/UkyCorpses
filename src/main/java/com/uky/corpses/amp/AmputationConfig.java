package com.uky.corpses.amp;

import java.util.EnumMap;
import java.util.Map;

import com.uky.corpses.stains.config.ConfigSpec;
import com.uky.corpses.stains.config.ConfigSpec.BooleanValue;
import com.uky.corpses.stains.config.ConfigSpec.Builder;
import com.uky.corpses.stains.config.ConfigSpec.DoubleValue;
import com.uky.corpses.stains.config.ConfigSpec.IntValue;

public final class AmputationConfig {

    public static final ConfigSpec SPEC;
    public static final BooleanValue ENABLE_ALIVE;
    public static final BooleanValue ENABLE_ON_DEATH;
    public static final BooleanValue ENABLE_DEAD;
    public static final BooleanValue ENABLE_FOR_PLAYERS;
    public static final BooleanValue ENABLE_FOR_MOBS;
    public static final BooleanValue HEADSHOTS_DECAPITATE;
    public static final BooleanValue REQUIRE_SHARP_WEAPON;
    public static final DoubleValue BLUNT_WEAPON_MULTIPLIER;
    public static final DoubleValue FULL_HELMET_ARMOR;
    public static final DoubleValue FULL_CHESTPLATE_ARMOR;
    public static final DoubleValue FULL_LEG_ARMOR;
    public static final DoubleValue LIMB_DETACH_FORCE;
    public static final DoubleValue LIMB_HOP_SPEED;
    public static final DoubleValue LIMB_TUMBLE;
    public static final DoubleValue LIMB_MAX_LAUNCH_SPEED;
    public static final BooleanValue LOG_AMPUTATIONS;
    public static final DoubleValue ALIVE_CHANCE_UNARMORED;
    public static final DoubleValue ALIVE_CHANCE_ARMORED;
    public static final DoubleValue ALIVE_MIN_DAMAGE;
    public static final DoubleValue ALIVE_DAMAGE_REFERENCE;
    public static final IntValue ALIVE_LIMB_LIFETIME;
    public static final DoubleValue ALIVE_DECAPITATION_MAX_HEALTH;
    public static final IntValue DECAPITATION_DEATH_DELAY;
    public static final IntValue ALIVE_MAX_LIMBS_PER_ENTITY;
    public static final DoubleValue DEATH_CHANCE_UNARMORED;
    public static final DoubleValue DEATH_CHANCE_ARMORED;
    public static final DoubleValue DEATH_DAMAGE_REFERENCE;
    public static final DoubleValue DEATH_OVERKILL_BONUS;
    public static final IntValue DEATH_LIMB_LIFETIME;
    public static final IntValue DEATH_MAX_LIMBS;
    public static final DoubleValue DEAD_CHANCE_UNARMORED;
    public static final DoubleValue DEAD_CHANCE_ARMORED;
    public static final DoubleValue DEAD_MIN_IMPULSE;
    public static final DoubleValue DEAD_IMPULSE_REFERENCE;
    public static final IntValue DEAD_LIMB_LIFETIME;
    public static final IntValue DEAD_COOLDOWN_TICKS;
    public static final IntValue MAX_LOOSE_LIMBS;
    private static final Map<Part, DoubleValue> PART_WEIGHTS = new EnumMap<>(Part.class);

    private AmputationConfig() {}

    private static void definePart(Builder builder, Part part, String name, double defaultWeight) {
        PART_WEIGHTS.put(part, builder.defineInRange(name, defaultWeight, 0.0, 4.0));
    }

    public static double partWeight(Part part) {
        DoubleValue value = PART_WEIGHTS.get(part);
        return value == null ? 0.0 : (Double) value.get();
    }

    static {
        Builder builder = new Builder("dismemberment");
        builder
            .comment(
                new String[] { "Master switches and shared settings.",
                    "Armor counts per limb: helmet for the head, chestplate for the arms, leggings and",
                    "boots for the legs. A limb's armor against its full value below blends its chance",
                    "from baseChanceUnarmored to baseChanceArmored." })
            .push("general");
        ENABLE_ALIVE = builder.comment(new String[] { "Limbs can come off living entities." })
            .define("enableWhileAlive", false);
        ENABLE_ON_DEATH = builder.comment(new String[] { "The killing blow can take a limb." })
            .define("enableOnDeath", true);
        ENABLE_DEAD = builder.comment(new String[] { "Limbs can be knocked off corpses." })
            .define("enableWhenDead", true);
        ENABLE_FOR_PLAYERS = builder.comment(new String[] { "Players can lose limbs." })
            .define("enableForPlayers", true);
        ENABLE_FOR_MOBS = builder.comment(new String[] { "Humanoid mobs can lose limbs." })
            .define("enableForMobs", true);
        HEADSHOTS_DECAPITATE = builder.comment(new String[] { "Headshots can take the head off." })
            .define("headshotsCanDecapitate", true);
        REQUIRE_SHARP_WEAPON = builder.comment(new String[] { "Only edged weapons and projectiles can sever." })
            .define("requireSharpWeapon", true);
        BLUNT_WEAPON_MULTIPLIER = builder
            .comment(new String[] { "Chance multiplier for blunt weapons when requireSharpWeapon is off." })
            .defineInRange("bluntWeaponMultiplier", 0.35, 0.0, 1.0);
        FULL_HELMET_ARMOR = builder
            .comment(new String[] { "Helmet armor points for a fully armored head. 3 is diamond or netherite." })
            .defineInRange("fullHelmetArmor", 3.0, 0.5, 100.0);
        FULL_CHESTPLATE_ARMOR = builder
            .comment(new String[] { "Chestplate armor points for fully armored arms. 8 is diamond or netherite." })
            .defineInRange("fullChestplateArmor", 8.0, 0.5, 100.0);
        FULL_LEG_ARMOR = builder
            .comment(
                new String[] { "Leggings and boots armor points for fully armored legs. 9 is diamond or netherite." })
            .defineInRange("fullLegArmor", 9.0, 0.5, 100.0);
        LIMB_DETACH_FORCE = builder
            .comment(new String[] { "How hard a severed limb is thrown along the blow, in blocks per second." })
            .defineInRange("limbDetachForce", 1.5, 0.0, 20.0);
        LIMB_HOP_SPEED = builder.comment(new String[] { "How hard a severed limb pops upward, in blocks per second." })
            .defineInRange("limbHopSpeed", 1.8, 0.0, 10.0);
        LIMB_TUMBLE = builder
            .comment(new String[] { "How fast a limb off a living entity spins, in radians per second." })
            .defineInRange("limbTumble", 6.0, 0.0, 30.0);
        LIMB_MAX_LAUNCH_SPEED = builder
            .comment(new String[] { "Speed cap for the throw and hop together, in blocks per second." })
            .defineInRange("limbMaxLaunchSpeed", 5.0, 0.5, 40.0);
        LOG_AMPUTATIONS = builder.comment(new String[] { "Log every roll, for tuning chances." })
            .define("logAmputations", false);
        builder.pop();
        builder
            .comment(
                new String[] { "Limbs coming off living entities.",
                    "Chance = base chance * part weight * sqrt(damage / damageReference).",
                    "Damage is measured before armor." })
            .push("alive");
        ALIVE_CHANCE_UNARMORED = builder.comment(new String[] { "Base chance when the limb has no armor." })
            .defineInRange("baseChanceUnarmored", 0.08, 0.0, 1.0);
        ALIVE_CHANCE_ARMORED = builder.comment(new String[] { "Base chance when the limb is fully armored." })
            .defineInRange("baseChanceArmored", 0.04, 0.0, 1.0);
        ALIVE_MIN_DAMAGE = builder.comment(new String[] { "Hits weaker than this never sever." })
            .defineInRange("minimumDamage", 4.0, 0.0, 1000.0);
        ALIVE_DAMAGE_REFERENCE = builder.comment(new String[] { "Damage at which the base chance is used unchanged." })
            .defineInRange("damageReference", 8.0, 0.1, 1000.0);
        ALIVE_LIMB_LIFETIME = builder.comment(new String[] { "How long the severed limb stays, in ticks." })
            .defineInRange("limbLifetimeTicks", 1200, 20, 24000);
        ALIVE_DECAPITATION_MAX_HEALTH = builder
            .comment(
                new String[] { "A living entity only loses its head at or below this fraction of its max health.",
                    "1 always allows it, 0 never does." })
            .defineInRange("decapitationMaxHealthFraction", 0.2, 0.0, 1.0);
        DECAPITATION_DEATH_DELAY = builder
            .comment(new String[] { "Ticks a headless entity lives before dying. 0 means the next tick." })
            .defineInRange("decapitationDeathDelayTicks", 40, 0, 200);
        ALIVE_MAX_LIMBS_PER_ENTITY = builder.comment(new String[] { "Most limbs one living entity can lose." })
            .defineInRange("maxLimbsPerEntity", 2, 1, 5);
        builder.pop();
        builder
            .comment(
                new String[] { "Limbs taken by the killing blow.",
                    "Same chance as alive, plus overkillBonus per point of damage past the remaining health." })
            .push("onDeath");
        DEATH_CHANCE_UNARMORED = builder.comment(new String[] { "Base chance when the limb has no armor." })
            .defineInRange("baseChanceUnarmored", 0.35, 0.0, 1.0);
        DEATH_CHANCE_ARMORED = builder.comment(new String[] { "Base chance when the limb is fully armored." })
            .defineInRange("baseChanceArmored", 0.2, 0.0, 1.0);
        DEATH_DAMAGE_REFERENCE = builder.comment(new String[] { "Damage at which the base chance is used unchanged." })
            .defineInRange("damageReference", 8.0, 0.1, 1000.0);
        DEATH_OVERKILL_BONUS = builder
            .comment(new String[] { "Extra chance per point of damage past the remaining health." })
            .defineInRange("overkillBonus", 0.02, 0.0, 1.0);
        DEATH_LIMB_LIFETIME = builder.comment(new String[] { "How long the severed limb stays, in ticks." })
            .defineInRange("limbLifetimeTicks", 600, 20, 24000);
        DEATH_MAX_LIMBS = builder.comment(new String[] { "Most limbs one death can take." })
            .defineInRange("maxLimbsPerDeath", 2, 1, 5);
        builder.pop();
        builder
            .comment(
                new String[] { "Limbs knocked off corpses.",
                    "Chance = base chance * part weight * (impulse / impulseReference).",
                    "Corpses keep the armor they died in." })
            .push("dead");
        DEAD_CHANCE_UNARMORED = builder.comment(new String[] { "Base chance when the part had no armor." })
            .defineInRange("baseChanceUnarmored", 0.2, 0.0, 1.0);
        DEAD_CHANCE_ARMORED = builder.comment(new String[] { "Base chance when the part was fully armored." })
            .defineInRange("baseChanceArmored", 0.12, 0.0, 1.0);
        DEAD_MIN_IMPULSE = builder.comment(
            new String[] {
                "Weaker impulses never sever. Player-corpse melee: min(50, 75*sqrt(max(1, attackDamage)/5)); fist about 33.5, strong weapons 50." })
            .defineInRange("minimumImpulse", 14.0, 0.0, 100.0);
        DEAD_IMPULSE_REFERENCE = builder.comment(new String[] { "Impulse at which the base chance is used unchanged." })
            .defineInRange("impulseReference", 20.0, 0.1, 100.0);
        DEAD_LIMB_LIFETIME = builder.comment(new String[] { "How long the severed limb stays, in ticks." })
            .defineInRange("limbLifetimeTicks", 400, 20, 24000);
        DEAD_COOLDOWN_TICKS = builder
            .comment(new String[] { "Minimum ticks between two limbs coming off the same corpse." })
            .defineInRange("cooldownTicks", 10, 0, 200);
        builder.pop();
        builder.comment(new String[] { "Chance multiplier per part. 0 makes a part unseverable." })
            .push("partWeights");
        definePart(builder, Part.HEAD, "head", 0.5);
        definePart(builder, Part.LEFT_ARM, "leftArm", 1.0);
        definePart(builder, Part.RIGHT_ARM, "rightArm", 1.0);
        definePart(builder, Part.LEFT_LEG, "leftLeg", 0.7);
        definePart(builder, Part.RIGHT_LEG, "rightLeg", 0.7);
        builder.pop();
        builder.comment(new String[] { "Client side only." })
            .push("rendering");
        MAX_LOOSE_LIMBS = builder
            .comment(new String[] { "Most loose limbs shown at once. The oldest are removed first." })
            .defineInRange("maxLooseLimbs", 64, 0, 512);
        builder.pop();
        SPEC = builder.build();
    }
}
