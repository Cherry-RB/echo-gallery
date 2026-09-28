package com.echogallery.sidebar;

public record SidebarStatsResponse(
    long totalCards,
    long totalIssues,
    long unfinishedIssues,
    long highSnoozeCards,
    long seedCards,
    long growingCards,
    long matureCards
) {}
