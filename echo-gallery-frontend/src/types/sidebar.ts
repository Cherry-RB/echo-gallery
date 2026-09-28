export interface SidebarStats {
  totalCards: number;
  totalIssues: number;
  unfinishedIssues: number;
  highSnoozeCards: number;
}

export interface TagRanking {
  id: number;
  name: string;
  cardCount: number;
}
