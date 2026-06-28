package com.echogallery.sidebar;

public record SidebarStatsResponse(
    long totalCards,
    long todayEchoCards,
    long highSnoozeCards
) {}
