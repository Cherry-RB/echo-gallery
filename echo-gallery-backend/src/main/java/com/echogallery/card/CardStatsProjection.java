package com.echogallery.card;

public interface CardStatsProjection {

    long getTotalCards();

    long getTodayEchoCards();

    long getHighSnoozeCards();

    long getSeedCards();

    long getGrowingCards();

    long getMatureCards();
}
