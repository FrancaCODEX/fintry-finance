export type Series = 'income' | 'expense' | 'result';
export interface ChartConfig {
  period: 3 | 6;
  type: 'bar' | 'line';
  series: Series[];
}
export interface ChartView extends ChartConfig {
  id: string;
  name: string;
}
export interface Preferences {
  active: string;
  current: ChartConfig;
  views: ChartView[];
}
export const defaults = (): ChartConfig => ({
  period: 6,
  type: 'bar',
  series: ['income', 'expense'],
});
export function normalize(input: unknown): Preferences {
  const raw = (input && typeof input === 'object' ? input : {}) as Record<
    string,
    unknown
  >;
  function config(value: unknown): ChartConfig {
    const v = (value && typeof value === 'object' ? value : {}) as Record<
      string,
      unknown
    >;
    const series = Array.isArray(v.series)
      ? (['income', 'expense', 'result'] as Series[]).filter(
          (s) => v.series instanceof Array && v.series.includes(s),
        )
      : [];
    return {
      period: v.period === 3 ? 3 : 6,
      type: v.type === 'line' ? 'line' : 'bar',
      series: series.length ? series : ['income', 'expense'],
    };
  }
  const views: ChartView[] = Array.isArray(raw.views)
    ? raw.views
        .filter(
          (v) => v && typeof v.id === 'string' && typeof v.name === 'string',
        )
        .slice(0, 10)
        .map((v) => ({ ...config(v), id: v.id, name: v.name.slice(0, 32) }))
    : [];
  const active =
    typeof raw.active === 'string' && views.some((v) => v.id === raw.active)
      ? raw.active
      : 'default';
  return { active, current: config(raw.current), views };
}
export function readPreferences(userId: string) {
  try {
    return normalize(
      JSON.parse(
        localStorage.getItem('fintry:dashboard-charts:' + userId) || '{}',
      ),
    );
  } catch {
    return normalize({});
  }
}
