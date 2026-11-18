package com.uky.corpses.stains;

import com.uky.corpses.stains.config.ConfigSpec;
import com.uky.corpses.stains.config.ConfigSpec.BooleanValue;
import com.uky.corpses.stains.config.ConfigSpec.Builder;
import com.uky.corpses.stains.config.ConfigSpec.DoubleValue;
import com.uky.corpses.stains.config.ConfigSpec.IntValue;

public final class SurfaceBloodConfig {

    public static final ConfigSpec SPEC;
    public static final BooleanValue SEND_HITS;
    public static final DoubleValue MIN_DAMAGE;
    public static final IntValue SEND_RANGE;

    private SurfaceBloodConfig() {}

    static {
        Builder b = new Builder();
        b.push("server");
        SEND_HITS = b.comment(
            "Send exact hit points to clients that have SurfaceBlood. Off, clients guess hits from vanilla packets instead.")
            .define("send_hits", true);
        MIN_DAMAGE = b.comment("Hits that take less health than this don't bleed.")
            .defineInRange("min_damage", 0.5, 0.0, 100.0);
        SEND_RANGE = b.comment("Players further than this many blocks from a hit aren't told about it.")
            .defineInRange("send_range", 64, 8, 256);
        b.pop();
        SPEC = b.build();
    }
}
