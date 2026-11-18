package com.uky.corpses.stains.config;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.uky.corpses.CorpseConfig;

/** Maps blood settings onto the ordered Forge categories. */
public final class ConfigSpec {

    private final List<Runnable> readers;

    private ConfigSpec(List<Runnable> readers) {
        this.readers = readers;
    }

    public void load() {
        for (Runnable read : readers) read.run();
    }

    public static class ConfigValue<T> {

        protected T value;

        ConfigValue(T value) {
            this.value = value;
        }

        public T get() {
            return value;
        }
    }

    public static final class BooleanValue extends ConfigValue<Boolean> {

        BooleanValue(boolean v) {
            super(v);
        }
    }

    public static final class DoubleValue extends ConfigValue<Double> {

        DoubleValue(double v) {
            super(v);
        }
    }

    public static final class IntValue extends ConfigValue<Integer> {

        IntValue(int v) {
            super(v);
        }
    }

    public static final class EnumValue<T extends Enum<T>> extends ConfigValue<T> {

        EnumValue(T v) {
            super(v);
        }
    }

    public static final class Builder {

        private final Deque<String> path = new ArrayDeque<>();
        private final List<Runnable> readers = new ArrayList<>();
        private String comment = "";
        private final String namespace;

        public Builder() {
            this(com.uky.corpses.compat.LegacyIds.BLOOD_NAMESPACE);
        }

        public Builder(String namespace) {
            this.namespace = namespace;
        }

        public Builder push(String name) {
            path.addLast(name);
            return this;
        }

        public Builder pop() {
            path.removeLast();
            return this;
        }

        public Builder comment(String... lines) {
            comment = String.join("\n", lines);
            return this;
        }

        private String category() {
            return namespace + "." + String.join(".", path);
        }

        public BooleanValue define(String key, boolean def) {
            BooleanValue v = new BooleanValue(def);
            String category = category(), note = comment;
            readers.add(
                () -> v.value = CorpseConfig.section(category, "SurfaceBlood")
                    .on(key, def, note));
            return v;
        }

        public ConfigValue<String> define(String key, String def) {
            ConfigValue<String> v = new ConfigValue<>(def);
            String category = category(), note = comment;
            readers.add(
                () -> v.value = CorpseConfig.section(category, "SurfaceBlood")
                    .text(key, def, note));
            return v;
        }

        public IntValue defineInRange(String key, int def, int min, int max) {
            IntValue v = new IntValue(def);
            String category = category(), note = comment;
            readers.add(
                () -> v.value = CorpseConfig.section(category, "SurfaceBlood")
                    .num(key, def, min, max, note));
            return v;
        }

        public DoubleValue defineInRange(String key, double def, double min, double max) {
            DoubleValue v = new DoubleValue(def);
            String category = category(), note = comment;
            readers.add(
                () -> v.value = (double) CorpseConfig.section(category, "SurfaceBlood")
                    .num(key, (float) def, (float) min, (float) max, note));
            return v;
        }

        public <T extends Enum<T>> EnumValue<T> defineEnum(String key, T def) {
            EnumValue<T> v = new EnumValue<>(def);
            String category = category(), note = comment;
            String[] names = Arrays.stream(
                def.getDeclaringClass()
                    .getEnumConstants())
                .map(Enum::name)
                .toArray(String[]::new);
            readers.add(
                () -> v.value = Enum.valueOf(
                    def.getDeclaringClass(),
                    CorpseConfig.section(category, "SurfaceBlood")
                        .pick(key, def.name(), names, note)));
            return v;
        }

        public ConfigValue<List<? extends String>> defineListAllowEmpty(List<String> keys,
            Supplier<? extends List<? extends String>> defaults, Predicate<Object> valid) {
            List<? extends String> def = new ArrayList<>(defaults.get());
            ConfigValue<List<? extends String>> v = new ConfigValue<>(def);
            String category = category(), note = comment, key = String.join(".", keys);
            readers.add(() -> {
                String[] raw = CorpseConfig.section(category, "SurfaceBlood")
                    .strings(key, def.toArray(new String[0]), note);
                List<String> cleaned = new ArrayList<>();
                for (String s : raw) if (valid.test(s)) cleaned.add(s);
                v.value = cleaned;
            });
            return v;
        }

        public ConfigSpec build() {
            return new ConfigSpec(readers);
        }
    }
}
