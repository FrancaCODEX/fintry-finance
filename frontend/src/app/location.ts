import { validMonth } from '../shared/format';
export function readLocation(defaultMonth: string) {
  const params = new URLSearchParams(location.search);
  const month = params.get('month');
  return { month: validMonth(month) ? month : defaultMonth, params };
}
export function saveLocation(
  page: string,
  values: Record<string, string | number | undefined>,
  push = true,
) {
  const url = new URL(location.href);
  url.search = '';
  url.searchParams.set('page', page);
  for (const [key, value] of Object.entries(values))
    if (value !== undefined && value !== '')
      url.searchParams.set(key, String(value));
  if (url.href !== location.href)
    history[push ? 'pushState' : 'replaceState']({}, '', url);
}
