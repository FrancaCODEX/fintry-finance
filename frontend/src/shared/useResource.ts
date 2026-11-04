import { useEffect, useState } from 'react';
export function useResource<T>(
  key: string,
  fetcher: (signal: AbortSignal) => Promise<T>,
) {
  const [value, setValue] = useState<{ key: string; data?: T; error?: string }>(
    { key: '' },
  );
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setValue({ key });
    fetcher(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setValue({ key, data });
      })
      .catch((error) => {
        if (!controller.signal.aborted)
          setValue({
            key,
            error:
              error instanceof Error
                ? error.message
                : 'Não foi possível consultar os dados.',
          });
      });
    return () => controller.abort();
    // The key is the complete identity of this query; fetcher closes over those values.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, revision]);
  const current = value.key === key ? value : { key };
  return {
    ...current,
    loading: !current.data && !current.error,
    retry: () => setRevision((v) => v + 1),
  };
}
