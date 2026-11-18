package com.uky.corpses.core;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.Name("UkyCorpses original-behavior hooks")
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions({ "com.uky.corpses.core.", "com.github.stephengold.joltjni." })
public final class CorePlugin implements IFMLLoadingPlugin {

    public String[] getASMTransformerClass() {
        return new String[] { "com.uky.corpses.core.CoreTransformer" };
    }

    public String getModContainerClass() {
        return null;
    }

    public String getSetupClass() {
        return null;
    }

    public void injectData(Map<String, Object> data) {}

    public String getAccessTransformerClass() {
        return null;
    }
}
