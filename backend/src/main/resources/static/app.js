'use strict';
const root = document.querySelector('#root');
const modal = document.querySelector('#modal');
const state = { user:null, page:'dashboard', month:localDate().slice(0,7), accounts:[], entries:null, summary:null, entryPage:0, filters:{}, csrf:null };
const money = value => Number(value).toLocaleString('pt-BR',{style:'currency',currency:'BRL'});
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const e = escapeHtml;
function localDate() { return new Intl.DateTimeFormat('sv-SE',{timeZone:'America/Sao_Paulo'}).format(new Date()); }
const dateLabel = date => date ? new Intl.DateTimeFormat('pt-BR',{timeZone:'UTC'}).format(new Date(date+'T12:00:00Z')) : '—';
const icons = { dashboard:'<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>', entries:'<path d="M8 3h11v18H5V6z"/><path d="M9 8h6M9 12h6M9 16h4"/>', wallet:'<path d="M4 6h14a2 2 0 0 1 2 2v12H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h13v2"/><path d="M20 11h-6v5h6"/>', plus:'<path d="M12 5v14M5 12h14"/>', income:'<path d="M6 17L18 5M6 5h12v12"/>', expense:'<path d="M6 5l12 12M6 17h12V5"/>', trend:'<path d="m3 17 6-6 4 4 8-10M15 5h6v6"/>', exit:'<path d="M9 4H4v16h5M13 7l5 5-5 5M8 12h13"/>', company:'<path d="M4 21V3h11v18M15 10h5v11M8 7h3M8 11h3M8 15h3M2 21h20"/>', transfer:'<path d="M4 7h16m-4-4 4 4-4 4M20 17H4m4-4-4 4 4 4"/>' };
const icon = name => `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">${icons[name] || icons.wallet}</svg>`;
let toastTimer;
function toast(message) { const el=document.querySelector('#toast');el.textContent=message;el.classList.add('visible');clearTimeout(toastTimer);toastTimer=setTimeout(()=>el.classList.remove('visible'),4500); }
async function csrf() { const res=await fetch('/api/v1/auth/csrf');if(!res.ok) throw new Error('Não foi possível iniciar a sessão.');state.csrf=await res.json(); }
async function api(path, options={}) {
  const method=options.method || 'GET';
  if(method!=='GET' && !state.csrf) await csrf();
  const headers={...options.headers};
  if(options.body) headers['Content-Type']='application/json';
  if(method!=='GET') headers[state.csrf.headerName]=state.csrf.token;
  let res;
  try { res=await fetch('/api/v1'+path,{...options,headers}); } catch { throw new Error('Sem conexão com o servidor. Tente novamente.'); }
  let data=null;if(res.status!==204){ const text=await res.text();if(text){try{data=JSON.parse(text);}catch{data={};}} }
  if(!res.ok){
    if(res.status===401 && state.user){state.user=null;modal.close();renderAuth();}
    throw new Error(data?.message || (data?.errors ? Object.values(data.errors).join(' ') : 'Não foi possível concluir a operação.'));
  }
  return data;
}
const send=(path,body)=>api(path,{method:'POST',body:body ? JSON.stringify(body) : undefined});
function field(label,name,type='text',extra='',value='') { return `<label class="field">${label}<input name="${name}" type="${type}" value="${e(value)}" ${extra}></label>`; }
function formError(form,message){form.querySelector('.form-error').textContent=message;}
async function submitForm(event, action) {
  event.preventDefault();const form=event.currentTarget;const button=form.querySelector('[type=submit]');button.disabled=true;formError(form,'');
  try{await action(Object.fromEntries(new FormData(form)),form);}catch(err){if(form.isConnected)formError(form,err.message);else toast(err.message);}finally{button.disabled=false;}
}
function renderAuth(register=false){
  root.innerHTML=`<main class="auth"><section class="auth-story"><div class="brand"><img src="/favicon.svg" alt="">fintry<span>.</span></div><div><div class="eyebrow">Control today. Grow tomorrow.</div><h1>Seu financeiro.<br>Mais simples.<br><span>Mais claro.</span></h1><p>Organize o presente e enxergue os próximos passos da sua empresa. Tudo em um só lugar.</p><div class="preview-card"><span class="eyebrow">Clareza para crescer</span><strong>Decisões com direção.</strong><div class="mini-bars" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></div></div></div><footer>Feito para quem cuida de cada detalhe do negócio.</footer></section><section class="auth-panel"><div class="auth-form"><h2>${register?'Comece pela sua empresa':'Bom ter você por aqui.'}</h2><p class="muted">${register?'Crie seu acesso e organize suas finanças.':'Entre para acompanhar os números do seu negócio.'}</p><form id="auth-form">${register?field('Seu nome','name','text','required maxlength="120" autocomplete="name"')+field('Nome da empresa','companyName','text','required maxlength="255" autocomplete="organization"')+field('CNPJ (somente números)','document','text','required pattern="[0-9]{14}" maxlength="14" inputmode="numeric" placeholder="14 dígitos"'):''}${field('E-mail','email','email','required maxlength="255" autocomplete="username" placeholder="voce@empresa.com.br"')}${field('Senha','password','password',`required ${register?'minlength="10"':''} maxlength="64" autocomplete="${register?'new-password':'current-password'}" placeholder="${register?'Pelo menos 10 caracteres':'Sua senha'}"`)}<div class="form-error" role="alert"></div><button class="btn submit" type="submit">${register?'Criar minha conta':'Entrar no Fintry'} ${icon('income')}</button></form><div class="auth-switch">${register?'Já tem uma conta?':'Primeira vez por aqui?'} <button id="auth-switch">${register?'Entrar':'Criar conta'}</button></div><p class="auth-note">Fintry · Gestão financeira para pequenas empresas<br>Desenvolvido por Vinicius Azeredo França</p></div></section></main>`;
  document.querySelector('#auth-switch').onclick=()=>renderAuth(!register);
  document.querySelector('#auth-form').onsubmit=ev=>submitForm(ev,async data=>{
    if(register) await send('/auth/register',data);
    state.user=await send('/auth/login',{email:data.email,password:data.password});await csrf();state.page='dashboard';state.entryPage=0;state.filters={};
    await load();
  });
}
function shell(){
  const u=state.user;
  root.innerHTML=`<div class="shell"><aside class="sidebar"><div class="brand"><img src="/favicon.svg" alt="">fintry.</div><div class="nav-label">SEU NEGÓCIO</div><nav aria-label="Navegação principal">${[['dashboard','Visão geral','dashboard'],['entries','Lançamentos','entries'],['accounts','Contas','wallet']].map(([page,label,ic])=>`<button class="nav-btn ${state.page===page?'active':''}" data-nav="${page}" ${state.page===page?'aria-current="page"':''}>${icon(ic)}${label}</button>`).join('')}</nav><div class="side-bottom"><div class="side-note"><strong>Um passo de cada vez.</strong><p>O controle do seu negócio começa com boas informações.</p></div><div class="user-block"><div class="avatar">${e(u.name.slice(0,1).toUpperCase())}</div><div class="user-info"><strong>${e(u.name)}</strong><small>Administrador</small></div><button class="logout" data-action="logout" title="Sair" aria-label="Sair da conta">${icon('exit')}</button></div></div></aside><main class="main"><header class="topbar"><div class="company-label">${icon('company')} ${e(u.companyName)}</div><span class="muted">Seu negócio, em boa direção.</span><button class="btn text small" data-action="logout">Sair ${icon('exit')}</button></header><div class="main-content"><div id="page-content"></div><footer class="footer"><span>Fintry © ${new Date().getFullYear()} · Control today. Grow tomorrow.</span><a href="mailto:vfranca.dev@gmail.com">Criado por Vinicius Azeredo França</a></footer></div></main></div>`;
}
function monthInput(){return `<label><span class="eyebrow" hidden>Período</span><input class="period" id="month" type="month" aria-label="Mês de referência" value="${state.month}" min="2000-01" max="2100-12"></label>`;}
function head(title,subtitle,actions){return `<div class="page-head"><div><h1>${title}</h1><p class="muted">${subtitle}</p></div><div class="head-actions">${actions}</div></div>`;}
function metric(title,value,foot,ic,cls=''){return `<article class="metric ${cls}"><div class="metric-top"><span>${title}</span><span class="metric-icon">${icon(ic)}</span></div><div class="metric-value">${money(value)}</div><span class="metric-foot">${foot}</span></article>`;}
function empty(title,text,action='entry',button='Novo lançamento'){return `<div class="empty"><div class="empty-icon">${icon('wallet')}</div><h3>${title}</h3><p>${text}</p><div class="actions"><button class="btn" data-action="${action}">${icon('plus')}${button}</button>${!state.accounts.length?'<button class="btn secondary" data-action="sample">Explorar com dados de exemplo</button>':''}</div></div>`;}
function chart(flow){
  const max=Math.max(1,...flow.flatMap(m=>[Number(m.income),Number(m.expense)]));
  const compact=v=>Number(v).toLocaleString('pt-BR',{notation:'compact',maximumFractionDigits:1});
  return `<div class="chart"><svg role="img" aria-label="Receitas e despesas pagas nos últimos seis meses. Os valores também aparecem nas descrições das barras." viewBox="0 0 600 210">${[0,1,2,3].map(i=>`<line x1="48" x2="588" y1="${20+i*47}" y2="${20+i*47}" stroke="#edf0e9"/><text x="0" y="${24+i*47}">${compact(max*(1-i/3))}</text>`).join('')}${flow.map((m,i)=>{
    const x=62+i*88, hi=Number(m.income)/max*141, he=Number(m.expense)/max*141;
    const label=new Intl.DateTimeFormat('pt-BR',{month:'short',timeZone:'UTC'}).format(new Date(m.month+'-15T12:00:00Z')).replace('.','');
    return `<g><title>${label}: receitas ${money(m.income)}, despesas ${money(m.expense)}</title><rect x="${x}" y="${161-hi}" width="23" height="${hi}" rx="4" fill="#18785a"/><rect x="${x+28}" y="${161-he}" width="23" height="${he}" rx="4" fill="#b8c6b0"/><text x="${x+25}" y="188" text-anchor="middle">${label}</text></g>`;
  }).join('')}</svg></div>`;
}
function entriesTable(rows){
  if(!rows.length)return empty('Nenhum lançamento por aqui','Cadastre suas receitas e despesas para acompanhar cada movimento.');
  const names=new Map(state.accounts.map(a=>[a.id,a.name]));
  return `<div class="table-wrap"><table><thead><tr><th>DESCRIÇÃO / CATEGORIA</th><th>VENCIMENTO</th><th>CONTA</th><th>VALOR</th><th>STATUS</th><th>AÇÕES</th></tr></thead><tbody>${rows.map(row=>{
    const income=row.type==='INCOME', paid=row.status==='PAID',cancelled=row.status==='CANCELLED';
    const overdue=!paid&&!cancelled&&row.dueDate<localDate();
    return `<tr><td class="description"><strong>${e(row.description)}</strong><small>${e(row.category)}${row.transferId?' · Transferência':''}</small></td><td>${dateLabel(row.dueDate)}${paid?`<br><small class="muted">Baixa: ${dateLabel(row.paymentDate)}</small>`:''}</td><td>${e(names.get(row.accountId)||'Conta')}</td><td class="${income?'positive':'negative'}"><strong>${income?'+':'−'} ${money(row.amount)}</strong></td><td><span class="badge ${cancelled?'cancelled':paid?'':'pending'}">${cancelled?'Cancelado':paid?(income?'Recebido':'Pago'):overdue?'Em atraso':'Pendente'}</span></td><td><div class="row-actions">${!paid&&!cancelled?`<button class="btn soft small" data-action="pay" data-id="${row.id}">${income?'Receber':'Pagar'}</button>`:''}${!cancelled&&!row.transferId?`<button class="btn text small" data-action="cancel" data-id="${row.id}" aria-label="Cancelar ${e(row.description)}">Cancelar</button>`:row.transferId?'<small class="muted">Entre contas</small>':'—'}</div></td></tr>`;
  }).join('')}</tbody></table></div>`;
}
function renderDashboard(){
 const s=state.summary;
 return head(`Seu negócio, em perspectiva.`,`Olá, ${e(state.user.name.split(' ')[0])}. Acompanhe o que movimenta sua empresa.`,monthInput()+`<button class="btn" data-action="entry">${icon('plus')}Novo lançamento</button>`)+
 `<section class="metrics" aria-label="Indicadores financeiros">${metric('Saldo disponível',s.balance,'Saldo atual de todas as contas','wallet','primary')}${metric('Receitas do mês',s.income,'Recebimentos realizados no período','income')}${metric('Despesas do mês',s.expense,'Pagamentos realizados no período','expense')}${metric('Resultado do mês',s.result,'Receitas menos despesas realizadas','trend')}</section>`+
 `<section class="grid-two"><article class="card"><div class="card-head"><div><h2>O ritmo do seu caixa</h2><p>Receitas e despesas realizadas · últimos 6 meses</p></div><div class="legend"><span><i class="dot"></i>Receitas</span><span><i class="dot expense"></i>Despesas</span></div></div>${chart(s.flow)}<div class="chart-caption">Valores em reais. Transferências entre contas não entram no resultado.</div></article><article class="card"><div class="card-head"><div><h2>No seu radar</h2><p>Vencimentos pendentes no mês selecionado</p></div>${icon('entries')}</div><div class="pending"><div class="pending-row"><div><p>A receber</p><small>Entradas previstas</small></div><strong class="positive">${money(s.pendingIncome)}</strong></div><div class="pending-row"><div><p>A pagar</p><small>Compromissos do período</small></div><strong>${money(s.pendingExpense)}</strong></div></div><div class="callout">Pendências entram no saldo quando você registra o pagamento ou recebimento.</div></article></section>`+
 `<section class="card"><div class="card-head"><div><h2>Movimentações do período</h2><p>Organizadas pela data de vencimento</p></div><button class="btn text small" data-nav="entries">Ver todas →</button></div>${state.accounts.length?entriesTable(state.entries.content.slice(0,5)):empty('Vamos organizar seu financeiro?','Comece cadastrando uma conta ou explore um exemplo com dados fictícios.','account','Criar primeira conta')}</section>`;
}
function renderEntries(){
 const f=state.filters;
 return head('Cada movimento, no lugar.','Registre entradas, saídas e acompanhe os vencimentos.',monthInput()+`<button class="btn" data-action="entry">${icon('plus')}Novo lançamento</button>`)+
 `<section class="card"><form class="filters" id="filter-form"><input name="search" type="search" placeholder="Buscar pela descrição…" aria-label="Buscar lançamentos" maxlength="255" value="${e(f.search||'')}"><select name="type" aria-label="Tipo"><option value="">Todos os tipos</option><option value="INCOME" ${f.type==='INCOME'?'selected':''}>Receitas</option><option value="EXPENSE" ${f.type==='EXPENSE'?'selected':''}>Despesas</option></select><select name="status" aria-label="Status"><option value="">Todos os status</option>${[['PENDING','Pendentes'],['PAID','Realizados'],['CANCELLED','Cancelados']].map(([v,l])=>`<option value="${v}" ${f.status===v?'selected':''}>${l}</option>`).join('')}</select><button class="btn secondary" type="submit">Filtrar</button></form>${entriesTable(state.entries.content)}<div class="pagination"><span>${state.entries.totalElements} lançamento(s)</span><div class="row-actions"><button class="btn secondary small" data-action="prev" ${state.entryPage===0?'disabled':''}>Anterior</button><span>${state.entryPage+1} / ${Math.max(1,state.entries.totalPages)}</span><button class="btn secondary small" data-action="next" ${state.entryPage+1>=state.entries.totalPages?'disabled':''}>Próxima</button></div></div></section>`;
}
function renderAccounts(){return head('Onde seu dinheiro está.','Contas organizadas para acompanhar o saldo do seu negócio.',`<button class="btn secondary" data-action="transfer">${icon('transfer')}Transferir</button><button class="btn" data-action="account">${icon('plus')}Nova conta</button>`)+(state.accounts.length?`<section class="account-grid">${state.accounts.map(a=>`<article class="card account-card"><div class="metric-icon">${icon('wallet')}</div><h2>${e(a.name)}</h2><span class="eyebrow">Saldo atual</span><strong>${money(a.balance)}</strong><p>Saldo inicial: ${money(a.initialBalance)}</p></article>`).join('')}</section><p class="notice">O saldo é calculado pelo valor inicial mais os lançamentos realizados. Transferências movimentam as contas sem alterar o resultado da empresa.</p>`:`<section class="card">${empty('Sua primeira conta começa aqui','Cadastre uma conta bancária ou o caixa da empresa.','account','Criar conta')}</section>`);}
let requestId=0;
async function load(){
 const id=++requestId;
 if(!root.querySelector('.shell'))shell();
 const [year,month]=state.month.split('-').map(Number);const end=new Date(Date.UTC(year,month,0)).toISOString().slice(0,10);
 const query=new URLSearchParams({start:state.month+'-01',end,page:String(state.page==='dashboard'?0:state.entryPage),size:state.page==='dashboard'?'5':'20',...(state.page==='entries'?state.filters:{})});
 const [accounts,entries,summary]=await Promise.all([api('/finance/accounts'),api('/finance/entries?'+query),api('/finance/summary?month='+state.month)]);
 if(id!==requestId||!state.user)return;
 state.accounts=accounts;state.entries=entries;state.summary=summary;
 shell();document.querySelector('#page-content').innerHTML=state.page==='dashboard'?renderDashboard():state.page==='entries'?renderEntries():renderAccounts();
 document.querySelector('#month')?.addEventListener('change',async ev=>{if(!/^\d{4}-\d{2}$/.test(ev.target.value))return;state.month=ev.target.value;state.entryPage=0;await safeLoad();});
 document.querySelector('#filter-form')?.addEventListener('submit',async ev=>{ev.preventDefault();state.filters=Object.fromEntries(new FormData(ev.currentTarget));state.entryPage=0;await safeLoad();});
}
async function safeLoad(){try{await load();}catch(err){toast(err.message);if(state.user&&!document.querySelector('#page-content')?.children.length){document.querySelector('#page-content').innerHTML='<div class="empty"><h2>Não foi possível carregar os dados.</h2><button class="btn" data-action="reload">Tentar novamente</button></div>';}}}
function showDialog(title,subtitle,fields,action,submit='Salvar'){
 modal.innerHTML=`<div class="modal-head"><div><h2 id="modal-title">${title}</h2><p>${subtitle}</p></div><button class="close" data-action="close" aria-label="Fechar">×</button></div><form id="modal-form">${fields}<div class="form-error" role="alert"></div><div class="dialog-actions"><button type="button" class="btn secondary" data-action="close">Voltar</button><button class="btn" type="submit">${submit}</button></div></form>`;
 modal.showModal();modal.querySelector('form').onsubmit=ev=>submitForm(ev,async(data,form)=>{await action(data,form);modal.close();await safeLoad();});
}
function accountOptions(name,label){return `<label class="field">${label}<select name="${name}" required>${state.accounts.map(a=>`<option value="${a.id}">${e(a.name)}</option>`).join('')}</select></label>`;}
function accountDialog(){showDialog('Nova conta financeira','Um lugar para acompanhar suas movimentações.',field('Nome da conta','name','text','required maxlength="100" placeholder="Ex.: Banco principal, Caixa da loja"')+field('Saldo inicial (R$)','initialBalance','number','required step="0.01" min="-999999999999.99" max="999999999999.99"','0')+'<p class="notice">Informe o saldo anterior aos lançamentos que você vai cadastrar. Esse valor não conta como receita.</p>',async data=>{await send('/finance/accounts',data);toast('Conta criada com sucesso.');},'Criar conta');}
function entryDialog(){
 if(!state.accounts.length){accountDialog();return;}
 showDialog('Novo lançamento','Cada registro deixa seu financeiro mais claro.',`<div class="form-row"><label class="field">Tipo<select name="type"><option value="INCOME">Receita</option><option value="EXPENSE">Despesa</option></select></label>${field('Valor (R$)','amount','number','required min="0.01" max="999999999999.99" step="0.01" placeholder="0,00"')}</div>`+field('Descrição','description','text','required maxlength="255" placeholder="Ex.: Projeto entregue ao cliente"')+`<div class="form-row">${accountOptions('accountId','Conta')}${field('Categoria','category','text','required maxlength="80" list="categories" placeholder="Ex.: Serviços"')}</div><datalist id="categories"><option value="Serviços"><option value="Vendas"><option value="Aluguel"><option value="Fornecedores"><option value="Marketing"><option value="Salários"><option value="Impostos"><option value="Outros"></datalist>`+field('Vencimento','dueDate','date','required',localDate())+`<label class="check"><input type="checkbox" name="paid" id="paid">Já foi pago / recebido</label><div id="payment-field" hidden>${field('Data do pagamento / recebimento','paymentDate','date',`max="${localDate()}"`,localDate())}</div>`,async(data)=>{data.paid=data.paid==='on';if(!data.paid)data.paymentDate=null;await send('/finance/entries',data);toast('Lançamento registrado.');},'Registrar lançamento');
 const check=modal.querySelector('#paid');check.onchange=()=>{modal.querySelector('#payment-field').hidden=!check.checked;modal.querySelector('[name=paymentDate]').required=check.checked;};
}
function transferDialog(){
 if(state.accounts.length<2){toast('Cadastre pelo menos duas contas para transferir.');return;}
 showDialog('Transferir entre contas','Sem alterar as receitas e despesas da empresa.',accountOptions('sourceId','Conta de origem')+accountOptions('destinationId','Conta de destino')+`<div class="form-row">${field('Valor (R$)','amount','number','required min="0.01" max="999999999999.99" step="0.01"')}${field('Data','date','date',`required max="${localDate()}"`,localDate())}</div>`+field('Descrição','description','text','required maxlength="255"','Transferência entre contas'),async data=>{await send('/finance/transfers',data);toast('Transferência realizada.');},'Transferir');
 modal.querySelector('[name=destinationId]').selectedIndex=1;
}
root.addEventListener('click',async ev=>{
 const nav=ev.target.closest('[data-nav]');if(nav){state.page=nav.dataset.nav;state.entryPage=0;await safeLoad();return;}
 const btn=ev.target.closest('[data-action]');if(!btn)return;await act(btn);
});
modal.addEventListener('click',ev=>{if(ev.target.closest('[data-action=close]'))modal.close();});
async function act(btn){
 const action=btn.dataset.action;
 if(action==='entry')return entryDialog();if(action==='account')return accountDialog();if(action==='transfer')return transferDialog();
 if(action==='prev'||action==='next'){state.entryPage+=action==='next'?1:-1;return safeLoad();}
 if(action==='reload')return safeLoad();
 if(action==='pay'){showDialog('Registrar baixa','O saldo será atualizado ao confirmar.',field('Data do pagamento / recebimento','paymentDate','date',`required max="${localDate()}"`,localDate()),async data=>{await send(`/finance/entries/${btn.dataset.id}/pay`,data);toast('Baixa registrada.');},'Confirmar baixa');return;}
 if(action==='cancel'){showDialog('Cancelar lançamento?','O registro ficará no histórico como cancelado.','<p class="notice">Se já estiver pago, o valor deixará de compor o saldo. Para corrigir um lançamento, cancele e cadastre novamente.</p>',async()=>{await send(`/finance/entries/${btn.dataset.id}/cancel`);toast('Lançamento cancelado.');},'Confirmar cancelamento');return;}
 btn.disabled=true;
 try{
  if(action==='logout'){await send('/auth/logout');state.user=null;state.csrf=null;state.filters={};await csrf();renderAuth();}
  if(action==='sample'){await send('/finance/sample');toast('Dados fictícios adicionados. Explore o Fintry!');await load();}
 }catch(err){toast(err.message);}finally{btn.disabled=false;}
}
(async()=>{try{await csrf();try{state.user=await api('/auth/me');}catch{}if(state.user)await safeLoad();else renderAuth();}catch(err){root.innerHTML='<div class="empty"><h1>Vamos tentar de novo?</h1><p>Não foi possível conectar ao Fintry. Recarregue a página para tentar novamente.</p></div>';toast(err.message);}})();
