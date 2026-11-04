import { useState } from 'react';
import type { Bridge } from '../../app/bridge';
import { useResource } from '../../shared/useResource';
import { PageHeading, Panel, QueryState } from '../../shared/components';
import {
  Controls,
  useCommand,
  Feedback,
  mutation,
  timestamp,
  type Values,
} from '../../shared/workspace';
interface Company {
  id: string;
  tradeName: string;
  legalName: string;
  document: string;
  phone: string;
  email: string;
  createdAt: string;
}
export function Company({ bridge }: { bridge: Bridge }) {
  const resource = useResource('company', (signal) =>
    bridge.request<Company>('/companies/me', { signal }),
  );
  return (
    <>
      <PageHeading
        title="Empresa"
        description="Mantenha a identificação e os contatos da sua empresa atualizados."
      />
      <QueryState {...resource} />
      {resource.data && (
        <CompanyForm
          key={resource.data.id}
          bridge={bridge}
          data={resource.data}
        />
      )}
    </>
  );
}
function CompanyForm({ bridge, data }: { bridge: Bridge; data: Company }) {
  const [values, setValues] = useState<Values>({
    tradeName: data.tradeName || '',
    legalName: data.legalName || '',
    document: data.document || '',
    phone: data.phone || '',
    email: data.email || '',
  });
  const cmd = useCommand();
  const [saved, setSaved] = useState(false);
  return (
    <div className="workspace-analysis">
      <Panel title="Identificação e contato">
        <form
          className="workspace-finance-form"
          onSubmit={async (e) => {
            e.preventDefault();
            setSaved(false);
            if (
              await cmd.run(async () => {
                const updated = await bridge.request<Company>(
                  '/companies/me',
                  mutation('PUT', values),
                );
                bridge.companyChanged(updated.tradeName || updated.legalName);
              })
            )
              setSaved(true);
          }}
        >
          <Controls
            values={values}
            setValues={setValues}
            fields={[
              { name: 'tradeName', label: 'Nome fantasia', required: true },
              { name: 'legalName', label: 'Razão social', required: true },
              {
                name: 'document',
                label: 'CNPJ ou documento',
                required: true,
                maxLength: 20,
              },
              {
                name: 'phone',
                label: 'Telefone',
                type: 'tel',
                required: true,
                maxLength: 20,
              },
              {
                name: 'email',
                label: 'E-mail da empresa',
                type: 'email',
                required: true,
              },
            ].map((field) => ({
              ...field,
              disabled: !bridge.canWrite || cmd.busy,
            }))}
          />
          <Feedback error={cmd.error} />
          {saved && <p role="status">Dados da empresa atualizados.</p>}
          {bridge.canWrite ? (
            <button className="btn" disabled={cmd.busy}>
              Salvar dados da empresa
            </button>
          ) : (
            <p className="workspace-caption">
              Acesso de consulta. Para editar, regularize o plano.
            </p>
          )}
        </form>
      </Panel>
      <Panel title="Identidade no Fintry">
        <div className="workspace-empty">
          <h2>{values.tradeName || values.legalName}</h2>
          <p>{values.legalName}</p>
          <p>Cadastro: {timestamp(data.createdAt)}</p>
          <p>Alterações ficam registradas no histórico da empresa.</p>
        </div>
      </Panel>
    </div>
  );
}
