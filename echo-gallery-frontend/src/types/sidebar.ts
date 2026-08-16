export interface SidebarStats {
  totalCards: number;
  totalWorks: number;
  unfinishedWorks: number;
  todayEchoCards: number;
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
