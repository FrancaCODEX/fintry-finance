import type { Series } from '../features/planning/types';
export function seriesLabel(row: Series) {
  if (row.stopped) return 'Encerrada';
  if (row.paused) return 'Pausada';
  if (row.pending || row.partial) return 'Em andamento';
  if (row.paid === row.occurrences) return 'Concluída';
  if (row.cancelled === row.occurrences) return 'Cancelada';
  return 'Encerrada';
}
export function canPauseSeries(row: Series, today: string) {
  return (
    row.kind === 'RECURRING' &&
    !row.stopped &&
    !row.paused &&
    row.pending > 0 &&
    row.lastDueDate > today
  );
}
