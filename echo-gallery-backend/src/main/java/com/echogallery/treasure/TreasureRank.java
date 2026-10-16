package com.echogallery.treasure;

public enum TreasureRank {
    STONE, BRONZE, SILVER, GOLD;

    public TreasureRank next() {
        return this == GOLD ? null : values()[ordinal() + 1];
    }
}
