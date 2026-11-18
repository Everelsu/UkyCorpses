package com.uky.corpses.stains;

public final class ResourceIds {

    public static boolean valid(String text) {
        return text != null && text.matches("(?:[a-z0-9_.-]+:)?[a-z0-9/._-]+");
    }

    private ResourceIds() {}
}
