package com.echogallery.issue;

public interface IssueStatsProjection {

    long getTotalIssues();

    long getUnfinishedIssues();
}
