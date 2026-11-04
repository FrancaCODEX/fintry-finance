import { useState } from 'react';
import type { Bridge } from '../../app/bridge';
import { useResource } from '../../shared/useResource';
import { PageHeading, Panel, QueryState } from '../../shared/components';
import { Metrics, Confirm, mutation, timestamp } from '../../shared/workspace';
interface Status {
  version: string;
  production: boolean;
  startedAt: string;
  ready: boolean;
  database: string;
  mailMode: string;
  secureCookies: boolean;
}
export function Operations({ bridge }: { bridge: Bridge }) {
  const resource = useResource('operations', (signal) =>
    bridge.request<Status>('/operations', { signal }),
  );
  const [test, setTest] = useState(false),
    [message, setMessage] = useState('');
  const data = resource.data;
  return (
    <>
      <PageHeading
        title="Ambiente"
        description="Confira a disponibilidade da aplicação e o envio de e-mail."
      >
        <button className="btn secondary" onClick={resource.retry}>
          Atualizar diagnóstico
        </button>
      </PageHeading>
      <QueryState {...resource} />
      {data && (
        <>
          <Panel
            title={
              data.ready
                ? 'Aplicação disponível'
                : 'O ambiente precisa de atenção'
            }
          >
            <div className="workspace-empty">
              <span className="eyebrow">
                {data.production ? 'Produção' : 'Avaliação / desenvolvimento'}
              </span>
              <p>
                Versão {data.version} · Iniciado em {timestamp(data.startedAt)}
              </p>
            </div>
          </Panel>
          <Metrics
            items={[
              [
                'Banco de dados',
                data.database === 'UP' ? 'Disponível' : 'Indisponível',
              ],
              [
                'Acesso',
                data.secureCookies
                  ? 'Cookies seguros / HTTPS'
                  : 'Configuração local',
              ],
              [
                'E-mail',
                (
                  {
                    DISABLED: 'Desabilitado',
                    LOCAL: 'Caixa de testes local',
                    SMTP: 'SMTP configurado',
                  } as Record<string, string>
                )[data.mailMode] || data.mailMode,
              ],
            ]}
          />
          <Panel title="Conferir entrega de e-mail">
            <div className="workspace-empty">
              <p>
                {data.mailMode === 'LOCAL'
                  ? 'Mensagens ficam no capturador local.'
                  : data.mailMode === 'SMTP'
                    ? 'O aceite pelo SMTP não confirma a entrega. Confira a caixa de entrada e o spam.'
                    : 'Configure o envio neste ambiente para testar.'}
              </p>
              <button
                className="btn secondary"
                disabled={data.mailMode === 'DISABLED'}
                onClick={() => setTest(true)}
              >
                Testar meu e-mail
              </button>
              {message && <p role="status">{message}</p>}
            </div>
          </Panel>
        </>
      )}
      <p className="workspace-caption">
        Até três testes por pessoa a cada dez minutos. O painel não executa
        backups nem verifica restaurações.
      </p>
      {test && (
        <Confirm
          title="Testar seu e-mail?"
          description={`A mensagem será enviada somente para ${bridge.user.email}. Não altera sua senha nem dados financeiros.`}
          close={() => setTest(false)}
          label="Enviar teste para mim"
          confirm={async () => {
            const result = await bridge.request<{ message: string }>(
              '/operations/mail-test',
              mutation('POST', {}),
            );
            setMessage(result.message);
          }}
        />
      )}
    </>
  );
}
