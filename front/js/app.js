/* =========================================================================
   Roteador
   ========================================================================= */
const TABS = [
  ['#/', 'home', 'Início', r => r === ''],
  ['#/mapa', 'map', 'Mapa', r => r === 'mapa' || r === 'ubs'],
  ['#/buscar', 'search', 'Buscar', r => r === 'buscar' || r === 'med'],
  ['#/lista', 'bookmark', 'Minha lista', r => r === 'lista'],
  ['#/perfil', 'user', 'Perfil', r => ['perfil', 'sobre', 'gestor', 'alertas'].includes(r)],
];
let lastRoute = null;
let navDepth = 0; // quantas telas foram abertas nesta sessão (para o botão Voltar)

function parse() {
  const h = location.hash.replace(/^#/, '') || '/';
  const [path, qs] = h.split('?');
  return { parts: path.split('/').filter(Boolean), q: new URLSearchParams(qs || ''), key: h };
}

function render(keepScroll = false) {
  const { parts, q, key } = parse();
  const view = document.getElementById('view');
  const r = parts[0] || '';
  if (!S.onboarded && r !== 'boas-vindas') { location.replace('#/boas-vindas'); return; }
  if (key !== lastRoute) keepScroll = false;
  const scroll = view.scrollTop;

  let html;
  switch (r) {
    case '': html = V.home(); break;
    case 'boas-vindas': html = V.welcome(); break;
    case 'buscar': if (key !== lastRoute) ui.q = ui.q || ''; html = V.search(q); break;
    case 'med': if (key !== lastRoute && !q.get('ubs')) ui.trackUbs = null; html = V.med(parts[1], q); break;
    case 'ubs': if (key !== lastRoute) { ui.ubsFilter = 'all'; ui.ubsQ = ''; } html = V.ubs(parts[1], q); break;
    case 'mapa': html = V.map(q); break;
    case 'lista': html = V.list(); break;
    case 'alertas': html = V.alerts(); break;
    case 'perfil': html = V.profile(); break;
    case 'sobre': html = V.about(); break;
    case 'gestor': html = V.manager(); break;
    default: html = V.notfound();
  }
  view.innerHTML = html;
  lastRoute = key;

  // pós-render
  if (r === 'buscar') {
    const res = document.getElementById('results'), input = document.getElementById('q'), cat = q.get('cat') || '';
    res.innerHTML = searchResults(cat);
    input.addEventListener('input', () => { ui.q = input.value; res.innerHTML = searchResults(cat); });
    if (!keepScroll && window.matchMedia('(min-width: 761px)').matches) input.focus({ preventScroll: true });
  }
  if (r === 'ubs') {
    const box = document.getElementById('ubslist');
    if (box) {
      box.innerHTML = ubsList(box.dataset.ubs, box.dataset.hl);
      const inp = document.getElementById('ubsq');
      inp.addEventListener('input', () => { ui.ubsQ = inp.value; box.innerHTML = ubsList(box.dataset.ubs, box.dataset.hl); });
    }
  }
  const medSel = document.getElementById('medSel');
  if (medSel) medSel.addEventListener('change', () => { ui.mapSel = null; location.hash = medSel.value ? `#/mapa?med=${medSel.value}` : '#/mapa'; });
  const trackSel = document.getElementById('trackSel');
  if (trackSel) trackSel.addEventListener('change', () => { ui.trackUbs = trackSel.value; location.hash = `#/med/${parts[1]}?ubs=${trackSel.value}`; });
  const refSel = document.getElementById('refSel');
  if (refSel) refSel.addEventListener('change', () => setRef(refSel.value));

  view.scrollTop = keepScroll ? scroll : 0;
  renderChrome(r);
}

function renderChrome(r) {
  const tb = document.getElementById('tabbar');
  tb.classList.toggle('hide', r === 'boas-vindas');
  const n = unread();
  tb.innerHTML = TABS.map(([href, icon, label, match]) => `<a href="${href}" class="${match(r) ? 'on' : ''}">${ic(icon)}${label}${icon === 'bookmark' && S.wish.length ? `<span class="badge-dot" style="background:var(--brand)">${S.wish.length}</span>` : ''}</a>`).join('');
  document.getElementById('offline-banner').innerHTML = isOffline() && r !== 'boas-vindas'
    ? `<div class="offline">${ic('wifioff', 'sm')}<span><b>Sem internet.</b> Mostrando dados salvos ${ago((Date.now() - S.lastSync) / 60000)}.</span></div>` : '';
  document.documentElement.classList.toggle('big-font', !!S.bigFont);
  document.getElementById('sys').innerHTML = `${ic('signal')}${ic(isOffline() ? 'wifioff' : 'wifi')}${ic('battery')}`;
  document.querySelectorAll('#screen-index a').forEach(a => a.classList.toggle('on', a.getAttribute('href') === '#/' + (location.hash.replace(/^#\/?/, '').split('?')[0])));
}

/* =========================================================================
   Ações
   ========================================================================= */
let toastT;
function toast(msg, icon = 'check') {
  const t = document.getElementById('toast');
  t.innerHTML = `${ic(icon, 'sm')}<span>${msg}</span>`;
  t.classList.add('show'); clearTimeout(toastT);
  toastT = setTimeout(() => t.classList.remove('show'), 2600);
}
function setRef(id) {
  S.ref = id; S.wish.forEach(w => w.ubs = id); save();
  toast(`${ubsById(id).nome} é sua UBS de referência`, 'star'); render(true);
}

document.addEventListener('click', e => {
  const el = e.target.closest('[data-act]'); if (!el) return;
  const a = el.dataset.act;
  switch (a) {
    case 'back':
      if (navDepth > 0) history.back(); else location.hash = '#/';
      break;
    case 'locate':
      el.innerHTML = `${ic('refresh', 'spin')} Localizando…`;
      setTimeout(() => { S.onboarded = true; S.bairro = 'Benedito Bentes'; save(); location.hash = '#/'; toast('Localização: Benedito Bentes, Maceió', 'pin'); }, 900);
      break;
    case 'pick-bairro':
      S.onboarded = true; S.bairro = document.getElementById('bairro').value; save(); location.hash = '#/'; toast(`Bairro definido: ${S.bairro}`, 'pin');
      break;
    case 'sync':
      if (isOffline()) { toast('Sem internet — continue usando os dados salvos', 'wifioff'); break; }
      el.innerHTML = `${ic('refresh', 'sm spin')} Atualizando…`;
      setTimeout(() => { S.lastSync = Date.now(); save(); render(true); toast('Estoque sincronizado com o e-SUS AF'); }, 900);
      break;
    case 'set-q': ui.q = el.dataset.q; render(true); break;
    case 'remember': {
      const n = el.dataset.q; S.recent = [n, ...S.recent.filter(x => x !== n)].slice(0, 4); save(); break;
    }
    case 'toggle-wish': {
      const { med, ubs } = el.dataset;
      const i = S.wish.findIndex(w => w.med === med && w.ubs === ubs);
      if (i >= 0) { S.wish.splice(i, 1); toast('Removido da sua lista', 'x'); }
      else { S.wish.push({ med, ubs, notify: true }); toast(`Pronto! Avisaremos quando chegar na ${ubsById(ubs).nome}`, 'bell'); }
      save(); render(true); break;
    }
    case 'toggle-notify': { const w = S.wish[+el.dataset.i]; w.notify = !w.notify; save(); render(true); toast(w.notify ? 'Aviso ativado' : 'Aviso desativado', 'bell'); break; }
    case 'remove-wish': S.wish.splice(+el.dataset.i, 1); save(); render(true); toast('Removido da sua lista', 'trash'); break;
    case 'only-avail': ui.onlyAvail = !ui.onlyAvail; render(true); break;
    case 'ubs-filter': ui.ubsFilter = ui.ubsFilter === el.dataset.f ? 'all' : el.dataset.f; render(true); break;
    case 'set-ref': if (S.ref !== el.dataset.ubs) setRef(el.dataset.ubs); break;
    case 'route': toast(`Abrindo rota até ${ubsById(el.dataset.ubs).nome}…`, 'nav'); break;
    case 'call': toast(`Ligando para ${ubsById(el.dataset.ubs).tel}…`, 'phone'); break;
    case 'radius': ui.radius = +el.dataset.r; render(true); break;
    case 'map-pin': ui.mapSel = ui.mapSel === el.dataset.ubs ? null : el.dataset.ubs; render(true); break;
    case 'recenter': ui.mapSel = null; ui.radius = 3; render(true); toast('Mapa centralizado na sua localização', 'locate'); break;
    case 'toggle': {
      const k = el.dataset.k; S[k] = !S[k]; save(); render(true);
      if (k === 'offline') toast(S[k] ? 'Modo offline: usando dados salvos no celular' : 'Conectado novamente', S[k] ? 'wifioff' : 'wifi');
      break;
    }
    case 'read': if (!S.read.includes(el.dataset.id)) { S.read.push(el.dataset.id); save(); } break;
    case 'read-all': S.read = alerts().map(x => x.id); save(); render(true); break;
    case 'mgr-tab': ui.mgrTab = el.dataset.t; render(true); break;
    case 'toast': toast(el.dataset.msg); break;
    case 'reset':
      try { localStorage.removeItem('remedio-perto'); } catch (err) {}
      S = structuredClone(DEFAULT); Object.assign(ui, { ubsFilter: 'all', ubsQ: '', onlyAvail: false, radius: 3, mapSel: null, q: '', trackUbs: null });
      location.hash = '#/boas-vindas'; break;
  }
});

window.addEventListener('hashchange', () => {
  // cada entrada do histórico guarda sua profundidade; voltar/avançar reaproveita o valor salvo
  if (history.state && typeof history.state.d === 'number') navDepth = history.state.d;
  else { navDepth++; try { history.replaceState({ d: navDepth }, ''); } catch (e) {} }
  render();
});
try { history.replaceState({ d: 0 }, ''); } catch (e) {}
window.addEventListener('online', () => render(true));
window.addEventListener('offline', () => render(true));
const tick = () => { document.getElementById('clock').textContent = new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }); };
tick(); setInterval(tick, 30000);
render();
