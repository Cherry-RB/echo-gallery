export function clearGoalRounds() {
  for (const key of Object.keys(sessionStorage)) if (key.startsWith('goal-round:')) sessionStorage.removeItem(key)
}
