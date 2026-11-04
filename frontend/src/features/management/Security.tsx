import { useState } from 'react';
import type { Bridge } from '../../app/bridge';
import { PageHeading, Panel } from '../../shared/components';
import {
  Controls,
  useCommand,
  Feedback,
  mutation,
  type Values,
} from '../../shared/workspace';
export function Security({ bridge }: { bridge: Bridge }) {
  const [values, setValues] = useState<Values>({
    currentPassword: '',
    password: '',
    confirmation: '',
  });
  const cmd = useCommand();
  return (
    <>
      <PageHeading
        title="Minha segurança"
        description="Use uma senha exclusiva e confira suas permissões."
      />
      <div className="workspace-analysis">
        <Panel title="Alterar minha senha">
          <form
            className="workspace-finance-form"
            onSubmit={async (e) => {
              e.preventDefault();
              await cmd.run(async () => {
                if (values.password !== values.confirmation)
                  throw new Error('As senhas precisam ser iguais.');
                await bridge.request(
                  '/auth/change-password',
                  mutation('POST', {
                    currentPassword: values.currentPassword,
                    password: values.password,
                  }),
                );
                window.dispatchEvent(new Event('fintry:session-expired'));
              });
            }}
          >
            <Controls
              values={values}
              setValues={setValues}
              fields={[
                {
                  name: 'currentPassword',
                  label: 'Senha atual',
                  type: 'password',
                  required: true,
                  maxLength: 64,
                },
                {
                  name: 'password',
                  label: 'Nova senha',
                  type: 'password',
                  required: true,
                  maxLength: 64,
                  pattern: '.{10,64}',
                },
                {
                  name: 'confirmation',
                  label: 'Confirmar nova senha',
                  type: 'password',
                  required: true,
                  maxLength: 64,
                },
              ]}
            />
            <p className="workspace-caption">
              Use de 10 a 64 caracteres. A senha deve ser diferente da atual.
              Todas as sessões serão encerradas ao salvar.
            </p>
            <Feedback error={cmd.error} />
            <button className="btn" disabled={cmd.busy}>
              Salvar nova senha
            </button>
          </form>
        </Panel>
        <Panel title="Seu acesso">
          <div className="workspace-empty">
            <h2>{bridge.user.name}</h2>
            <p>{bridge.user.email}</p>
            <p>
              {bridge.isAdmin
                ? 'Administrador'
                : bridge.canCollect
                  ? 'Financeiro'
                  : 'Somente leitura'}
            </p>
            <p>
              Consulte finanças, relatórios e comprovantes.{' '}
              {bridge.canWrite &&
                'Você também pode gerenciar movimentos e cadastros.'}{' '}
              {bridge.isAdmin && 'Gerencie os acessos pela página Equipe.'}
            </p>
          </div>
        </Panel>
      </div>
    </>
  );
}
