package com.echogallery.demo;

import java.util.Arrays;

public enum DemoLibrary {
    TECH("tech", "技術與學習收藏"),
    VISUAL("visual", "視覺靈感與創作素材"),
    WRITING("writing", "文字片段與生活觀察");

    private final String key;
    private final String displayName;

    DemoLibrary(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String getKey() { return key; }
    public String getDisplayName() { return displayName; }

    public static DemoLibrary fromKey(String key) {
        return Arrays.stream(values()).filter(value -> value.key.equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支援的 Demo 內容庫"));
    }
}
