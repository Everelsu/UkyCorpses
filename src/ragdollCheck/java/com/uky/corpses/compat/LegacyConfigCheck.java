package com.uky.corpses.compat;

import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraftforge.common.config.Configuration;

import com.uky.corpses.RuntimeFiles;

public final class LegacyConfigCheck {

    public static void run() throws Exception {
        assert LegacyConfig.comment(null)
            .isEmpty();
        Path folder = Files.createTempDirectory("uky-config-");
        Path file = folder.resolve("corpses.cfg");
        java.lang.reflect.Field home = cpw.mods.fml.relauncher.FMLInjectionData.class.getDeclaredField("minecraftHome");
        home.setAccessible(true);
        Object previous = home.get(null);
        home.set(null, folder.toFile());
        try {
            Configuration config = new Configuration(file.toFile());
            config.get("blood", "enabled", false)
                .set(false);
            config.get("cruor", "enabled", true)
                .set(true);
            config.get("cruor.blood", "stain_lifetime", 600)
                .set(123);
            config.get("ragdoll", "enabled", true)
                .set(false);
            config.get("ragdollified.physics", "gravity", 15D)
                .set(19D);
            config.get("physics.solver", "gravity", 15D)
                .set(11D);
            config.get("ragdollifiedreactions.triggers", "explosions", true)
                .set(false);
            config.get("ragdollified.humanoidJointLimits.neck", "minPitch", -40)
                .set(-27);
            config.save();
            byte[] original = Files.readAllBytes(file);
            assert LegacyConfig.migrate(config);
            assert !config.get("legacy.blood", "enabled", true)
                .getBoolean();
            assert config.get("blood", "enabled", false)
                .getBoolean();
            assert config.get("blood.stains", "stain_lifetime", 600)
                .getInt() == 123;
            assert !config.get("physics", "enabled", true)
                .getBoolean();
            assert config.get("physics.solver", "gravity", 15D)
                .getDouble() == 11D;
            assert !config.get("reactions.triggers", "explosions", true)
                .getBoolean();
            assert config.get("physics.joints.neck", "minPitch", -40)
                .getInt() == -27;
            assert java.util.Arrays
                .equals(original, Files.readAllBytes(folder.resolve("corpses.cfg.before-layout.bak")));
            assert !LegacyConfig.migrate(config) : "new blood section was remigrated as legacy";
            config.get("corpse.tape", "enabled", true);
            config.get("deathcam", "enabled", false);
            config.get("corpse", "asBlock", false);
            config.get("corpse.rules", "enableCorpseCompass", true)
                .set(false);
            config.get("corpse.performance", "mobRagdolls", true)
                .set(false);
            LegacyConfig.discardUnused(config);
            config.save();
            config = new Configuration(file.toFile());
            assert !config.hasCategory("corpse.tape") && !config.hasCategory("deathcam")
                && !config.hasKey("corpse", "asBlock");
            assert !config.get("corpse.rules", "enableCorpseCompass", true)
                .getBoolean();
            assert !config.get("corpse.performance", "mobRagdolls", true)
                .getBoolean();
            assert !LegacyConfig.comment("Cruor cruor:hit_1 / Ragdollified Reactions 0.1.3")
                .toLowerCase()
                .matches(".*(cruor|ragdollified).*");
            config.get("downed.rules", "knockedHealth", 50D)
                .set(50D);
            config.get("downed.rules", "downedInvulnerableTicks", 40)
                .set(40);
            config.get("gameplay", "healthRegen", false)
                .set(false);
            config.get("dismemberment.general", "enableWhileAlive", true)
                .set(true);
            LegacyConfig.gameplayDefaults(config);
            assert config.get("downed.rules", "knockedHealth", 12D)
                .getDouble() == 12D;
            assert config.get("downed.rules", "downedInvulnerableTicks", 10)
                .getInt() == 10;
            assert config.get("gameplay", "healthRegen", true)
                .getBoolean();
            assert !config.get("dismemberment.general", "enableWhileAlive", false)
                .getBoolean();
            config.get("gameplay", "healthRegen", true)
                .set(false);
            LegacyConfig.gameplayDefaults(config);
            assert !config.get("gameplay", "healthRegen", true)
                .getBoolean() : "later user overrides survive";
            Path game = folder.resolve("cache-migration");
            Files.createDirectories(game.resolve(".ukycorpses"));
            Files.write(game.resolve(".ukycorpses/marker.bin"), new byte[] { 1, 2, 3 });
            Path cache = RuntimeFiles.cache(game);
            assert cache.equals(game.resolve("ukycorpses/cache")) && !Files.exists(game.resolve(".ukycorpses"));
            assert java.util.Arrays.equals(Files.readAllBytes(cache.resolve("marker.bin")), new byte[] { 1, 2, 3 });
            Files.createDirectories(game.resolve("cruor/world"));
            Files.write(game.resolve("cruor/world/dimension-0.stains"), new byte[] { 4 });
            Path blood = RuntimeFiles.blood(game);
            assert Files.exists(blood.resolve("world/dimension-0.stains")) && !Files.exists(game.resolve("cruor"));
            Files.createDirectories(game.resolve("cruor/world"));
            Files.write(game.resolve("cruor/world/dimension-0.stains"), new byte[] { 5 });
            RuntimeFiles.blood(game);
            Path oldBlood = RuntimeFiles.legacyBlood(game, "world", 0);
            assert oldBlood != null && !Files.exists(game.resolve("cruor"));
            assert Files.readAllBytes(blood.resolve("world/dimension-0.stains"))[0] == 4;
            assert Files.readAllBytes(oldBlood)[0] == 5 : "conflicting legacy cache must be preserved";
            Files.delete(oldBlood);
            Files.delete(oldBlood.getParent());
            Files.delete(
                oldBlood.getParent()
                    .getParent());
            Files.delete(blood.resolve("world/dimension-0.stains"));
            Files.delete(blood.resolve("world"));
            Files.delete(blood);
            Files.delete(cache.resolve("marker.bin"));
            Files.delete(cache);
            Files.delete(cache.getParent());
            Files.delete(game);
            System.out.println(
                "Config layout: typed legacy migration, collisions, new-value precedence, backup and idempotence passed");
        } finally {
            home.set(null, previous);
            Files.deleteIfExists(folder.resolve("corpses.cfg.before-layout.bak"));
            Files.deleteIfExists(folder.resolve("corpses.cfg.before-gameplay-defaults.bak"));
            Files.deleteIfExists(file);
            Files.deleteIfExists(folder);
        }
    }

    private LegacyConfigCheck() {}
}
