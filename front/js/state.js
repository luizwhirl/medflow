/* =========================================================================
   Estado (persistido só neste navegador)
   ========================================================================= */
const DEFAULT = {
  onboarded: false, nome: 'Rita', bairro: 'Benedito Bentes', ref: 'u1',
  wish: [{ med: 'metformina', ubs: 'u1', notify: true }, { med: 'losartana', ubs: 'u1', notify: true }, { med: 'insulina-nph', ubs: 'u1', notify: false }],
  offline: false, bigFont: false, notif: true, lastSync: NOW - 4 * 60000,
  recent: ['Metformina', 'Insulina'], read: [],
};
let S = load();
function load() {
  try { const r = JSON.parse(localStorage.getItem('remedio-perto')); if (r && typeof r === 'object') return { ...structuredClone(DEFAULT), ...r }; } catch (e) {}
  return structuredClone(DEFAULT);
}
function save() { try { localStorage.setItem('remedio-perto', JSON.stringify(S)); } catch (e) {} }
const isOffline = () => S.offline || !navigator.onLine;
const ui = { ubsFilter: 'all', ubsQ: '', onlyAvail: false, radius: 3, mapSel: null, q: '', trackUbs: null, mgrTab: 'remanejo' };

/* =========================================================================
   Avisos
   ========================================================================= */
function alerts() {
  const list = [];
  S.wish.forEach(w => {
    const m = medById(w.med), u = ubsById(w.ubs); if (!m || !u) return;
    const st = stock(w.med, w.ubs), tr = track(w.med, w.ubs);
    if (st.s === 'd') list.push({ id: 'a-' + w.med, icon: 'check', tone: 'd', t: `${medName(m)} disponível`, s: `Chegou na ${u.nome}. Retire no horário de funcionamento.`, when: 'há 25 min', href: `#/med/${m.id}?ubs=${u.id}` });
    else if (tr.active && tr.stage === 2) list.push({ id: 'b-' + w.med, icon: 'truck', tone: 't', t: `${medName(m)} a caminho`, s: `Remessa saiu do CAF para a ${u.nome}. Previsão: ${fmtWeekday(tr.eta)}.`, when: 'há 1 h', href: `#/med/${m.id}?ubs=${u.id}` });
  });
  list.push({ id: 'c-sync', icon: 'wifioff', tone: 'r', t: 'Dados atrasados em uma unidade', s: 'USF Riacho Doce está sem sincronizar há 2 dias. Ligue antes de ir.', when: 'ontem', href: '#/ubs/u7' });
  list.push({ id: 'd-info', icon: 'info', tone: 'g', t: 'Dica: leve sua receita', s: 'Medicamentos de uso contínuo exigem receita válida (até 6 meses).', when: '3 dias', href: '#/sobre' });
  return list;
}
const unread = () => alerts().filter(a => !S.read.includes(a.id)).length;

/* =========================================================================
   Componentes
   ========================================================================= */
const appbar = (title, { back = true, right = '' } = {}) => `
  <header class="appbar">
    ${back ? `<button class="iconbtn" data-act="back" aria-label="Voltar">${ic('back')}</button>` : ''}
    <h1>${title}</h1>${right}
  </header>`;
const bellBtn = () => { const n = unread(); return `<a class="iconbtn" href="#/alertas" aria-label="Avisos">${ic('bell')}${n ? `<span class="badge-dot">${n}</span>` : ''}</a>`; };

function ubsRow(u, medId) {
  let right = `<span class="chev">${ic('chev', 'sm')}</span>`;
  if (medId) { const st = stock(medId, u.id); right = pill(st.s); }
  const ref = u.id === S.ref ? ' · <b style="color:var(--brand)">sua UBS</b>' : '';
  return `<a class="item" href="#/ubs/${u.id}${medId ? '?med=' + medId : ''}">
    <span class="ava">${ic('building')}</span>
    <span class="grow">
      <div class="t ellipsis">${u.nome}</div>
      <div class="s">${fmtKm(dist(u))} · ${travel(dist(u))}${ref}</div>
      <div style="margin-top:4px">${syncTag(u)}</div>
    </span>${right}</a>`;
}

function nearestWith(medId, exclude) {
  return byDist().find(u => u.id !== exclude && stock(medId, u.id).s === 'd');
}

