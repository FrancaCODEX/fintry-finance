import type { components } from './schema';
export type Account = components['schemas']['Account'];
export type Summary = components['schemas']['Summary'];
export type Entry = components['schemas']['Entry'];
export type EntryPage = components['schemas']['EntryPage'];
export type EntryDetails = components['schemas']['EntryDetails'];
export type Forecast = components['schemas']['Forecast'];
export type Transport = <T>(path: string, options?: RequestInit) => Promise<T>;
export function query(
  values: Record<string, string | number | boolean | undefined>,
) {
  return new URLSearchParams(
    Object.entries(values)
      .filter(([, v]) => v !== undefined && v !== '')
      .map(([k, v]) => [k, String(v)]),
  ).toString();
}
export function workspaceApi(request: Transport) {
  return {
    accounts: (signal?: AbortSignal) =>
      request<Account[]>('/finance/accounts', { signal }),
    summary: (month: string, signal?: AbortSignal) =>
      request<Summary>('/finance/summary?' + query({ month }), { signal }),
    entries: (
      filters: Record<string, string | number | undefined>,
      signal?: AbortSignal,
    ) => request<EntryPage>('/finance/entries?' + query(filters), { signal }),
    details: (id: string, signal?: AbortSignal) =>
      request<EntryDetails>('/finance/entries/' + encodeURIComponent(id), {
        signal,
      }),
    forecast: (
      days: number,
      accountId?: string,
      includeOverdue = false,
      signal?: AbortSignal,
    ) =>
      request<Forecast>(
        '/reports/forecast?' + query({ days, accountId, includeOverdue }),
        { signal },
      ),
  };
}

export type ScenarioInput = components['schemas']['ScenarioInput'];
export type ScenarioPreview = components['schemas']['ScenarioPreview'];
export type IndicatorExplanation =
  components['schemas']['IndicatorExplanation'];
export type IndicatorKind = components['schemas']['IndicatorKind'];
