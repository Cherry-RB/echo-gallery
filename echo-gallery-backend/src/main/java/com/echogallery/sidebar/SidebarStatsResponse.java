package com.echogallery.sidebar;

public record SidebarStatsResponse(
    long totalCards,
    long totalWorks,
    long unfinishedWorks,
    long todayEchoCards,
    long highSnoozeCards,
    long seedCards,
    long growingCards,
    long matureCards
) {}
