import { useQueryClient } from '@tanstack/vue-query'
export function useExplorationInvalidation() {
  const client = useQueryClient()
  return async (experimentId: number) => {
    await Promise.all([
      ...['experiment-exploration', 'experiment-activity-records', 'experiment-methods', 'experiment-history',
        'experiment-thread', 'experiment-threads', 'experiment-thread-summaries', 'experiment-thread-options', 'experiment']
        .map(key => client.invalidateQueries({ queryKey: [key, experimentId] })),
      ...['experiments', 'overview', 'cards', 'card-exploration-threads', 'work-sources']
        .map(key => client.invalidateQueries({ queryKey: [key] })),
    ])
  }
}
