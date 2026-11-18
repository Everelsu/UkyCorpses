package com.uky.corpses.client;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * UkyUI's colours (com.console.uky.client.render.Theme), read by name so neither mod needs the other to build; ours
 * when it isn't installed. Its palette can change live, so every call reads it again.
 */
public final class Palette {

    public static final int OK = 0x6ECB63;
    private static final Map<String, Field> fields = new HashMap<>();
    private static Class<?> theme;
    private static boolean looked;

    private Palette() {}

    public static int danger() {
        return get("danger", 0xE04848);
    }

    public static int accent() {
        return get("accent", 0xC8A24B);
    }

    public static int text() {
        return get("text", 0xFFFFFF);
    }

    public static int dim() {
        return get("textDim", 0xA0A0A0);
    }

    public static int background() {
        return get("background", 0x0A0A0A);
    }

    /** UkyUI is installed: windows are drawn as part of its interface, else as vanilla's. */
    public static boolean uky() {
        get("accent", 0);
        return theme != null;
    }

    /** UkyUI's Theme.(name) as 0xRRGGBB, or own. */
    public static int get(String name, int own) {
        if (!looked) {
            looked = true;
            try {
                theme = Class.forName("com.console.uky.client.render.Theme");
            } catch (ReflectiveOperationException | LinkageError ex) {
                theme = null; // no UkyUI
            }
        }
        if (theme == null) return own;
        try {
            Field f = fields.get(name);
            if (f == null) fields.put(name, f = theme.getField(name));
            return f.getInt(null) & 0xFFFFFF;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return own;
        }
    }
}
