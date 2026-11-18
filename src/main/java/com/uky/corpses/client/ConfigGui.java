package com.uky.corpses.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;

import com.uky.corpses.CorpseConfig;
import com.uky.corpses.UkyCorpses;

import cpw.mods.fml.client.IModGuiFactory;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * corpses.cfg in game: the mod list's Config button, and UkyUI's mod settings, which redraws any GuiConfig in its own
 * look. Saved changes are read again at once (CorpseConfig.sync); potion ids need a restart.
 */
public final class ConfigGui extends GuiConfig {

    /** The order the categories come in; anything else after them. */
    private static final List<String> ORDER = Arrays
        .asList("corpse", "penalty", "downed", "deathcam", "wounds", "medicine", "blood", "gameplay", "movement");

    public ConfigGui(GuiScreen parent) {
        super(
            parent,
            elements(),
            UkyCorpses.MODID,
            false,
            false,
            StatCollector.translateToLocal("ukycorpses.config.title"));
    }

    @SuppressWarnings("rawtypes")
    private static List<IConfigElement> elements() {
        Configuration c = CorpseConfig.config();
        List<String> names = new ArrayList<>(ORDER);
        for (String n : c.getCategoryNames()) if (!names.contains(n)) names.add(n);
        List<IConfigElement> out = new ArrayList<>();
        for (String n : names) {
            if (!c.hasCategory(n)) continue;
            ConfigCategory cat = c.getCategory(n);
            if (!cat.isChild()) out.add(new ConfigElement(cat));
        }
        return out;
    }

    /** Saved from this screen or UkyUI's: read it all again. */
    public static final class Changes {

        @SubscribeEvent
        public void onChanged(ConfigChangedEvent.OnConfigChangedEvent e) {
            if (UkyCorpses.MODID.equals(e.modID)) CorpseConfig.sync();
        }
    }

    public static final class Factory implements IModGuiFactory {

        @Override
        public void initialize(Minecraft mc) {}

        @Override
        public Class<? extends GuiScreen> mainConfigGuiClass() {
            return ConfigGui.class;
        }

        @Override
        public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
            return null;
        }

        @Override
        public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
            return null;
        }
    }
}
