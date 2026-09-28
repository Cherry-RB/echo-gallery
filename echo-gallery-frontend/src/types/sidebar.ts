export interface SidebarStats {
  totalCards: number;
  totalIssues: number;
  unfinishedIssues: number;
  highSnoozeCards: number;
  seedCards: number;
  growingCards: number;
  matureCards: number;
}

export interface TagRanking {
  id: number;
  name: string;
  cardCount: number;
}
