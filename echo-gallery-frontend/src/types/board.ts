export type BoardType =
  | "today"
  | "all"
  | "hot"
  | "random"
  | "archived"
  | "snoozed";

export function shouldMarkReviewedOnOpenDetail(boardType: BoardType): boolean {
  return boardType === "today";
}

export interface BoardCapabilities {
  canStar: boolean;
  canArchive: boolean;
  canSnooze: boolean;
}

// 🚧 規則尚未定案，先全部開放，之後只需要改這張表，不用動任何元件程式碼
const boardCapabilityMap: Record<BoardType, BoardCapabilities> = {
  today:    { canStar: true, canArchive: true, canSnooze: true },
  all:      { canStar: true, canArchive: true, canSnooze: true },
  hot:      { canStar: true, canArchive: true, canSnooze: true },
  random:   { canStar: true, canArchive: true, canSnooze: true },
  snoozed:  { canStar: true, canArchive: true, canSnooze: true },
  archived: { canStar: true, canArchive: true, canSnooze: true }, // TODO: 之後改成 false，只留取消封存
};

export function getBoardCapabilities(boardType: BoardType): BoardCapabilities {
  return boardCapabilityMap[boardType];
}
