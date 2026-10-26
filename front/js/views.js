/* =========================================================================
   Telas
   ========================================================================= */
const V = {};

V.welcome = () => `
  <div class="welcome">
    <div class="big">${ic('pin', 'lg')}</div>
    <h2>Saiba se o remédio está na UBS <span style="color:var(--brand)">antes de sair de casa</span>.</h2>
    <p class="muted" style="margin:0">Consulte o estoque das unidades de saúde perto de você, acompanhe a chegada do seu medicamento e receba um aviso quando ele estiver disponível.</p>
    <div class="feat"><span class="ava">${ic('map')}</span><div><b>UBS perto de você</b>Estoque por proximidade: disponível, reservado ou em falta.</div></div>
    <div class="feat"><span class="ava">${ic('truck')}</span><div><b>Rastreie seu remédio</b>Veja se a próxima remessa já saiu da central.</div></div>
    <div class="feat"><span class="ava">${ic('bookmark')}</span><div><b>Lista de desejos</b>Vinculada à sua UBS de referência, com aviso quando chegar.</div></div>
    <div style="flex:1;min-height:24px"></div>
    <div class="stack">
      <button class="btn block" data-act="locate">${ic('locate')} Usar minha localização</button>
      <div class="card p">
        <label class="lab" for="bairro">Ou escolha seu bairro</label>
        <div class="row">
          <select class="field grow" id="bairro">
            ${['Benedito Bentes', 'Cidade Universitária', 'Clima Bom', 'Tabuleiro do Martins', 'Santos Dumont'].map(b => `<option ${b === S.bairro ? 'selected' : ''}>${b}</option>`).join('')}
          </select>
          <button class="btn sec" data-act="pick-bairro">OK</button>
        </div>
      </div>
      <p class="tiny faint" style="text-align:center;margin:4px 0 0">Sua localização é usada só para ordenar as UBS por distância.</p>
    </div>
  </div>`;

V.home = () => {
  const ref = ubsById(S.ref);
  const syncMin = Math.max(0, (Date.now() - S.lastSync) / 60000);
  const wishCards = S.wish.length ? S.wish.map((w) => {
    const m = medById(w.med), u = ubsById(w.ubs); if (!m || !u) return '';
    const st = stock(w.med, w.ubs), tr = track(w.med, w.ubs);
    let sub = '';
    if (st.s === 'd') sub = `<div class="s">Na ${u.nome} · ~${st.q} un.</div>`;
    else {
      const alt = nearestWith(w.med, w.ubs);
      sub = `<div class="s">${tr.active ? `${ic('truck', 'sm')} Chega na sua UBS ${fmtWeekday(tr.eta)}` : 'Sem remessa programada na sua UBS'}</div>`
        + (alt ? `<div class="s" style="color:var(--ok);font-weight:600">Tem na ${alt.nome} · ${fmtKm(dist(alt))}</div>` : '');
    }
    return `<a class="item" href="#/med/${m.id}?ubs=${u.id}">
      <span class="ava">${ic('pill')}</span>
      <span class="grow"><div class="t">${medName(m)}</div>${sub}</span>${pill(st.s)}</a>`;
  }).join('') : `<div class="item"><span class="grow muted small">Você ainda não salvou nenhum remédio.</span><a class="link" href="#/buscar">Adicionar</a></div>`;

  return `
  <header class="appbar" style="border-bottom:0">
    <div class="logo"><span class="mark">${ic('pin', 'sm')}</span>UBS Conecta</div>
    ${bellBtn()}
  </header>
  <section class="hero">
    <div class="muted small">Olá, ${esc(S.nome)}</div>
    <h2>Qual remédio você procura?</h2>
    <a class="searchbox" href="#/buscar">${ic('search')}<span>Buscar por nome, ex.: Metformina</span></a>
    <div class="loc">${ic('pin', 'sm')} Perto de <b>${esc(S.bairro)}, Maceió</b> · <a class="link" href="#/boas-vindas">alterar</a></div>
  </section>

  <div class="section" style="padding-top:12px">
    <div class="row small muted" style="justify-content:space-between">
      <span class="sync ${isOffline() ? 'warn' : ''}"><span class="dot"></span>${isOffline() ? 'Offline · dados salvos' : 'Estoque atualizado'} ${ago(syncMin)}</span>
      <button class="link row" style="gap:4px" data-act="sync">${ic('refresh', 'sm')} Atualizar</button>
    </div>
  </div>

  <div class="section">
    <div class="sec-h"><h2>Meus remédios de uso contínuo</h2><a href="#/lista">Ver lista</a></div>
    <div class="list">${wishCards}</div>
  </div>

  <div class="section">
    <div class="sec-h"><h2>Minha UBS de referência</h2><a href="#/perfil">Trocar</a></div>
    <div class="list">${ubsRow(ref)}</div>
  </div>

  <div class="section">
    <div class="sec-h"><h2>UBS perto de você</h2><a href="#/mapa">Ver no mapa</a></div>
    <div class="list">${byDist().filter(u => u.id !== S.ref).slice(0, 3).map(u => ubsRow(u)).join('')}</div>
  </div>

  <div class="section">
    <div class="sec-h"><h2>Buscar por condição</h2></div>
    <div class="chips" style="flex-wrap:wrap">${CATS.map(c => `<a class="chip" href="#/buscar?cat=${encodeURIComponent(c)}">${c}</a>`).join('')}</div>
  </div>
  <div style="height:8px"></div>`;
};

V.search = (q) => {
  const cat = q.get('cat') || '';
  return `
  <header class="appbar">
    <button class="iconbtn" data-act="back" aria-label="Voltar">${ic('back')}</button>
    <div class="searchbox" style="margin:0;height:42px;flex:1">${ic('search', 'sm')}<input id="q" type="search" placeholder="Nome do medicamento" value="${esc(ui.q)}" autocomplete="off" aria-label="Buscar medicamento"></div>
  </header>
  <div class="section" style="padding-top:12px">
    <div class="chips">
      <a class="chip ${!cat ? 'on' : ''}" href="#/buscar">Todos</a>
      ${CATS.map(c => `<a class="chip ${c === cat ? 'on' : ''}" href="#/buscar?cat=${encodeURIComponent(c)}">${c}</a>`).join('')}
    </div>
  </div>
  <div id="results"></div>`;
};
function searchResults(cat) {
  const term = ui.q.trim().toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
  const norm = s => s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
  let meds = MEDS.filter(m => (!cat || m.cat === cat) && (!term || norm(m.nome + ' ' + m.dose + ' ' + m.cat).includes(term)));
  const near = byDist().filter(u => dist(u) <= 6);
  let head = '';
  if (!term && !cat) head = `<div class="section"><div class="sec-h"><h2>Buscas recentes</h2></div>
      <div class="chips">${S.recent.map(r => `<button class="chip" data-act="set-q" data-q="${esc(r)}">${ic('clock', 'sm')}${esc(r)}</button>`).join('')}</div></div>`;
  const rows = meds.map(m => {
    const avail = near.filter(u => stock(m.id, u.id).s === 'd');
    const n = avail[0];
    return `<a class="item" href="#/med/${m.id}" data-act="remember" data-q="${esc(m.nome)}">
      <span class="ava">${ic('pill')}</span>
      <span class="grow"><div class="t">${medName(m)}</div>
        <div class="s">${m.forma} · ${m.cat}</div>
        <div class="s" style="margin-top:4px">${avail.length ? `<b style="color:var(--ok)">Em ${avail.length} de ${near.length} UBS perto</b> · mais perto a ${fmtKm(dist(n))}` : `<b style="color:var(--bad)">Em falta nas UBS perto</b>`}</div>
      </span><span class="chev">${ic('chev', 'sm')}</span></a>`;
  }).join('');
  return head + `<div class="section"><div class="sec-h"><h2>${term || cat ? `${meds.length} resultado${meds.length === 1 ? '' : 's'}` : 'Mais procurados na Atenção Básica'}</h2></div>
    ${meds.length ? `<div class="list">${rows}</div>` : `<div class="card empty"><div class="ava gray">${ic('search', 'lg')}</div><b>Nada encontrado</b><p class="small">Confira a grafia ou procure pelo princípio ativo (nome genérico). Só aparecem itens da lista RENAME/REMUME.</p></div>`}
  </div>`;
}

V.med = (id, q) => {
  const m = medById(id); if (!m) return V.notfound();
  const trackUbs = q.get('ubs') || ui.trackUbs || S.ref;
  ui.trackUbs = trackUbs;
  const tu = ubsById(trackUbs) || ubsById(S.ref);
  const all = byDist();
  const avail = all.filter(u => stock(id, u.id).s === 'd');
  const list = ui.onlyAvail ? avail : all;
  const inWish = S.wish.some(w => w.med === id && w.ubs === tu.id);
  const st = stock(id, tu.id), tr = track(id, tu.id);

  let trackHtml;
  if (tr.active) trackHtml = `
      <div class="row" style="justify-content:space-between;margin-bottom:12px">
        <div><div class="small muted">Próxima remessa · ~${tr.qty} un.</div><div style="font-weight:700;font-size:16px">Previsão: ${fmtWeekday(tr.eta)}</div></div>
        <span class="pill t">${tr.stage === 2 ? 'Em transporte' : tr.stage === 1 ? 'Na central' : 'Pedido feito'}</span>
      </div>
      <ol class="tl">${tr.steps.map(s => `<li class="${s.state}"><span class="b">${s.state === 'done' ? ic('check', 'sm') : s.state === 'now' ? ic(tr.stage === 2 ? 'truck' : 'box', 'sm') : ''}</span><div><div class="lbl">${s.label}</div><div class="when">${s.when}</div></div></li>`).join('')}</ol>
      ${m.frio ? `<div class="note info" style="margin-top:12px">${ic('snow', 'sm')}<span>Transporte refrigerado (2 °C a 8 °C). A entrega pode atrasar se a cadeia do frio não for garantida.</span></div>` : ''}`;
  else if (tr.none) trackHtml = `<div class="note warn">${ic('alert', 'sm')}<span><b>Sem remessa programada</b> para esta unidade. Salvar na sua lista ajuda a gestão a enxergar a demanda e priorizar o remanejamento.</span></div>`;
  else trackHtml = `<div class="row"><span class="ava" style="background:var(--ok-bg);color:var(--ok)">${ic('check')}</span><div class="grow"><b>Disponível nesta unidade</b><div class="small muted">Última entrada em ${fmtDay(-tr.lastIn)} · +${tr.qtyIn} un.</div></div></div>`;

  return `
  ${appbar(medName(m))}
  <div class="pad" style="background:var(--card);border-bottom:1px solid var(--line)">
    <div class="row" style="align-items:flex-start">
      <span class="ava" style="width:52px;height:52px">${ic('pill', 'lg')}</span>
      <div class="grow">
        <div style="font-size:20px;font-weight:800;letter-spacing:-.01em">${m.nome}</div>
        <div class="muted small">${m.dose} · ${m.forma}</div>
        <div class="row" style="gap:6px;flex-wrap:wrap;margin-top:8px">
          <span class="tag brand">RENAME</span><span class="tag">CATMAT ${m.catmat}</span><span class="tag">${m.cat}</span>
          ${m.cont ? '<span class="tag">Uso contínuo</span>' : ''}${m.receita ? '<span class="tag">Receita retida</span>' : ''}
        </div>
      </div>
    </div>
    <div class="note ${avail.length ? 'brand' : 'bad'}" style="margin-top:14px">${ic(avail.length ? 'pin' : 'alert', 'sm')}
      <span>${avail.length ? `Disponível em <b>${avail.length} de ${all.length}</b> unidades. Mais perto: <b>${avail[0].nome}</b> (${fmtKm(dist(avail[0]))}).` : 'Em falta em todas as unidades da região.'}</span></div>
    <div class="btns" style="margin-top:12px">
      <a class="btn sec" href="#/mapa?med=${id}">${ic('map')} Ver no mapa</a>
      <button class="btn ${inWish ? 'soft' : ''}" data-act="toggle-wish" data-med="${id}" data-ubs="${tu.id}">${ic(inWish ? 'check' : 'bell')} ${inWish ? 'Na sua lista' : 'Me avise'}</button>
    </div>
  </div>

  <div class="section" style="padding-top:12px">
    <div class="sec-h"><h2>Rastrear remessa</h2></div>
    <div class="card p">
      <label class="lab" for="trackSel">Unidade</label>
      <select class="field" id="trackSel" style="margin-bottom:14px">
        ${all.map(u => `<option value="${u.id}" ${u.id === tu.id ? 'selected' : ''}>${u.nome}${u.id === S.ref ? ' (sua UBS)' : ''} — ${STATUS[stock(id, u.id).s]}</option>`).join('')}
      </select>
      ${trackHtml}
      <div class="row small" style="justify-content:space-between;margin-top:14px;padding-top:12px;border-top:1px solid var(--line)">${syncTag(tu)}<a class="link" href="#/sobre">Como funciona?</a></div>
    </div>
  </div>

  <div class="section">
    <div class="sec-h"><h2>Onde encontrar</h2>
      <button class="row small" style="gap:8px;font-weight:600;color:var(--ink-2)" data-act="only-avail">Só onde tem <span class="switch ${ui.onlyAvail ? 'on' : ''}"></span></button></div>
    <div class="list">${list.map(u => {
      const s = stock(id, u.id);
      return `<a class="item" href="#/ubs/${u.id}">
        <span class="grow"><div class="t ellipsis">${u.nome}</div>
        <div class="s">${fmtKm(dist(u))} · ${s.s === 'f' ? 'sem estoque' : `~${s.q} un.`}</div>
        <div style="margin-top:4px">${syncTag(u)}</div></span>
        ${pill(s.s)}</a>`;
    }).join('')}</div>
  </div>

  <div class="section">
    <div class="sec-h"><h2>Movimentação recente · ${tu.nome.replace(/ \(.*\)/, '')}</h2></div>
    <div class="list">${moves(id, tu.id).map(mv => `<div class="item"><span class="ava gray">${ic(mv.q > 0 ? 'box' : mv.q < 0 ? 'pill' : 'alert', 'sm')}</span><span class="grow"><div class="t" style="font-size:13.5px">${mv.t}</div><div class="s">${mv.when}</div></span><b class="small" style="color:${mv.q > 0 ? 'var(--ok)' : 'var(--ink-2)'}">${mv.q > 0 ? '+' : ''}${mv.q || '—'}</b></div>`).join('')}</div>
    <p class="tiny faint" style="margin:8px 2px 0">Registros do e-SUS AF. A baixa é feita pela farmácia no momento da entrega.</p>
  </div>`;
};

V.ubs = (id, q) => {
  const u = ubsById(id); if (!u) return V.notfound();
  const counts = { d: 0, r: 0, f: 0 };
  MEDS.forEach(m => counts[stock(m.id, u.id).s]++);
  const isRef = S.ref === u.id;
  const hl = q.get('med');
  return `
  ${appbar(u.nome)}
  <div class="pad" style="background:var(--card);border-bottom:1px solid var(--line)">
    <div class="row" style="gap:6px;flex-wrap:wrap;margin-bottom:10px">
      <span class="tag">${u.tipo}</span><span class="tag">CNES ${u.cnes}</span>${isRef ? '<span class="tag brand">Sua UBS de referência</span>' : ''}${u.rural ? '<span class="tag">Zona rural</span>' : ''}
    </div>
    <div class="kv">
      ${ic('pin', 'sm')}<div>${u.end} · ${u.bairro}<div class="small muted">${fmtKm(dist(u))} · ${travel(dist(u))}</div></div>
      ${ic('clock', 'sm')}<div>${u.horario}<div class="small" style="color:var(--ok);font-weight:600">Aberta agora · farmácia até 16h</div></div>
      ${ic('phone', 'sm')}<div>${u.tel}</div>
    </div>
    ${syncLevel(u.sync) === 'bad' ? `<div class="note bad" style="margin-top:12px">${ic('wifioff', 'sm')}<span><b>Sem sincronizar ${ago(u.sync)}.</b> A internet desta unidade está instável — os dados abaixo podem estar desatualizados. Ligue antes de ir.</span></div>`
      : syncLevel(u.sync) === 'warn' ? `<div class="note warn" style="margin-top:12px">${ic('clock', 'sm')}<span>Última sincronização ${ago(u.sync)}. O estoque pode ter mudado desde então.</span></div>`
      : `<div style="margin-top:12px">${syncTag(u)}</div>`}
    <div class="btns three" style="margin-top:14px">
      <button class="btn sm" data-act="route" data-ubs="${u.id}">${ic('nav', 'sm')} Rota</button>
      <button class="btn sm sec" data-act="call" data-ubs="${u.id}">${ic('phone', 'sm')} Ligar</button>
      <button class="btn sm ${isRef ? 'soft' : 'sec'}" data-act="set-ref" data-ubs="${u.id}">${ic(isRef ? 'check' : 'star', 'sm')} ${isRef ? 'Minha' : 'Fixar'}</button>
    </div>
  </div>

  <div class="section" style="padding-top:14px">
    <div class="sec-h"><h2>Estoque da farmácia</h2><span class="tiny faint">${MEDS.length} itens da REMUME</span></div>
    <div class="stats">
      ${['d', 'r', 'f'].map(s => `<button class="stat ${s} ${ui.ubsFilter === s ? 'on' : ''}" data-act="ubs-filter" data-f="${s}"><b>${counts[s]}</b><span>${STATUS[s]}</span></button>`).join('')}
    </div>
    <div class="searchbox" style="height:44px;margin-top:10px;background:var(--card)">${ic('search', 'sm')}<input id="ubsq" type="search" placeholder="Filtrar nesta UBS" value="${esc(ui.ubsQ)}" aria-label="Filtrar medicamentos"></div>
    ${ui.ubsFilter !== 'all' ? `<div class="row small" style="margin-top:10px;justify-content:space-between"><span class="muted">Mostrando: <b>${STATUS[ui.ubsFilter]}</b></span><button class="link" data-act="ubs-filter" data-f="all">Limpar filtro</button></div>` : ''}
    <div class="list" id="ubslist" style="margin-top:10px" data-ubs="${u.id}" data-hl="${hl || ''}"></div>
  </div>`;
};
function ubsList(ubsId, hl) {
  const norm = s => s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
  const term = norm(ui.ubsQ.trim());
  const rows = MEDS.filter(m => (ui.ubsFilter === 'all' || stock(m.id, ubsId).s === ui.ubsFilter) && (!term || norm(m.nome).includes(term)))
    .map(m => {
      const s = stock(m.id, ubsId), tr = track(m.id, ubsId);
      const sub = s.s === 'd' ? `~${s.q} un. em estoque` : s.s === 'r' ? `${s.q} un. · reservado p/ agendados` : tr.active ? `Chega ${fmtWeekday(tr.eta)}` : 'Sem previsão';
      return `<a class="item" href="#/med/${m.id}?ubs=${ubsId}" style="${m.id === hl ? 'background:var(--brand-50)' : ''}">
        <span class="grow"><div class="t">${medName(m)}</div><div class="s">${m.cat} · ${sub}</div></span>${pill(s.s)}</a>`;
    }).join('');
  return rows || `<div class="empty small">Nenhum medicamento com esse filtro.</div>`;
}

V.map = (q) => {
  const medId = q.get('med') || '';
  const R = ui.radius;
  const inR = byDist().filter(u => dist(u) <= R);
  const out = UBS.length - inR.length;
  const W = 368, H = 270, span = R + 0.6, sc = Math.min(W, H) / (2 * span);
  const P = (x, y) => [W / 2 + x * sc, H / 2 + y * sc];
  // ruas
  let streets = '';
  for (let n = -24; n <= 24; n++) {
    const k = n * 0.6, [x1] = P(k, 0), [, y1] = P(0, k);
    streets += `<line x1="${x1}" y1="0" x2="${x1 + 12}" y2="${H}" stroke="#fff" stroke-width="${n % 5 === 0 ? 5 : 2}"/>`;
    streets += `<line x1="0" y1="${y1}" x2="${W}" y2="${y1 - 8}" stroke="#fff" stroke-width="${n % 6 === 0 ? 5 : 2}"/>`;
  }
  const [ax1, ay1] = P(-14, 9), [ax2, ay2] = P(14, -7);
  const [bx1, by1] = P(-9, -14), [bx2, by2] = P(5, 14);
  const [sx] = P(9.2, 0);
  const [px, py] = P(-2.4, 2.2);
  const [lx, ly] = P(-5.5, -1.5);
  const sel = ui.mapSel && inR.find(u => u.id === ui.mapSel) ? ubsById(ui.mapSel) : null;
  const pins = inR.map(u => {
    const [x, y] = P(u.x, u.y);
    const c = medId ? COLORS[stock(medId, u.id).s] : COLORS.none;
    const on = sel && sel.id === u.id;
    return `<g class="pinbtn" data-act="map-pin" data-ubs="${u.id}" transform="translate(${x} ${y})">
      <path d="M0 0 C -9 -10 -11 -14 -11 -19 A 11 11 0 1 1 11 -19 C 11 -14 9 -10 0 0 Z" fill="${c}" stroke="#fff" stroke-width="${on ? 3 : 2}" ${on ? 'transform="scale(1.25)"' : ''}/>
      <circle cy="${on ? -23.5 : -19}" r="4" fill="#fff"/>
      ${u.id === S.ref ? `<circle cx="9" cy="${on ? -33 : -28}" r="5" fill="#facc15" stroke="#fff" stroke-width="1.5"/>` : ''}
    </g>`;
  }).join('');

  return `
  ${appbar('UBS perto de você', { back: false, right: bellBtn() })}
  <div class="pad" style="background:var(--card);padding-top:10px;padding-bottom:12px">
    <label class="lab" for="medSel">Ver disponibilidade de</label>
    <select class="field" id="medSel">
      <option value="">Todas as unidades (sem filtro)</option>
      ${MEDS.map(m => `<option value="${m.id}" ${m.id === medId ? 'selected' : ''}>${medName(m)}</option>`).join('')}
    </select>
    <div class="chips" style="margin-top:10px">
      ${[1, 3, 6, 10].map(r => `<button class="chip ${r === R ? 'on' : ''}" data-act="radius" data-r="${r}">Até ${r} km</button>`).join('')}
    </div>
  </div>
  <div class="map">
    <svg viewBox="0 0 ${W} ${H}" role="img" aria-label="Mapa esquemático com UBS próximas">
      <rect width="${W}" height="${H}" fill="#eef2ea"/>
      <rect x="${px}" y="${py}" width="${1.6 * sc}" height="${1.2 * sc}" rx="6" fill="#d9e8cf"/>
      <ellipse cx="${lx}" cy="${ly}" rx="${1.4 * sc}" ry="${0.8 * sc}" fill="#cfe3f2"/>
      ${streets}
      <line x1="${ax1}" y1="${ay1}" x2="${ax2}" y2="${ay2}" stroke="#fde68a" stroke-width="7"/>
      <line x1="${bx1}" y1="${by1}" x2="${bx2}" y2="${by2}" stroke="#fde68a" stroke-width="6"/>
      ${sx < W ? `<rect x="${sx}" y="0" width="${W}" height="${H}" fill="#cfe3f2"/><text x="${Math.min(W - 40, sx + 12)}" y="22" font-size="11" fill="#64748b" font-weight="600">Oceano</text>` : ''}
      <circle cx="${W / 2}" cy="${H / 2}" r="${R * sc}" fill="rgba(15,118,110,.06)" stroke="#0f766e" stroke-dasharray="5 5" stroke-width="1.5"/>
      <circle cx="${W / 2}" cy="${H / 2}" r="14" fill="rgba(37,99,235,.18)"/>
      <circle cx="${W / 2}" cy="${H / 2}" r="7" fill="#2563eb" stroke="#fff" stroke-width="2.5"/>
      ${pins}
    </svg>
    <button class="map-fab" data-act="recenter" aria-label="Centralizar">${ic('locate')}</button>
    <div class="map-legend">${medId ? `<span><i style="background:${COLORS.d}"></i>Tem</span><span><i style="background:${COLORS.r}"></i>Reservado</span><span><i style="background:${COLORS.f}"></i>Em falta</span>` : `<span><i style="background:#2563eb"></i>Você</span><span><i style="background:${COLORS.none}"></i>UBS</span><span><i style="background:#facc15"></i>Sua UBS</span>`}</div>
  </div>
  ${sel ? `<div class="selcard"><div class="list">${ubsRow(sel, medId)}</div></div>` : ''}
  <div class="section" style="padding-top:14px">
    <div class="sec-h"><h2>${inR.length} unidade${inR.length === 1 ? '' : 's'} em até ${R} km</h2>${out ? `<span class="tiny faint">+${out} fora do raio</span>` : ''}</div>
    ${inR.length ? `<div class="list">${inR.map(u => ubsRow(u, medId)).join('')}</div>` : `<div class="card empty small">Nenhuma UBS neste raio. <button class="link" data-act="radius" data-r="6">Aumentar para 6 km</button></div>`}
  </div>`;
};

V.list = () => {
  const items = S.wish.map((w, i) => {
    const m = medById(w.med), u = ubsById(w.ubs); if (!m || !u) return '';
    const st = stock(w.med, w.ubs), tr = track(w.med, w.ubs);
    const alt = st.s !== 'd' ? nearestWith(w.med, w.ubs) : null;
    return `<div class="card p">
      <div class="row" style="align-items:flex-start">
        <span class="ava">${ic('pill')}</span>
        <a class="grow" href="#/med/${m.id}?ubs=${u.id}"><div style="font-weight:700">${medName(m)}</div><div class="small muted">${u.nome}</div></a>
        ${pill(st.s)}
      </div>
      ${tr.active ? `<div class="note info" style="margin-top:12px">${ic('truck', 'sm')}<span>${STEPS[tr.stage]}. Previsão: <b>${fmtWeekday(tr.eta)}</b>.</span></div>` : ''}
      ${tr.none ? `<div class="note warn" style="margin-top:12px">${ic('alert', 'sm')}<span>Sem remessa programada. Sua inscrição entra no painel de demanda da gestão.</span></div>` : ''}
      ${alt ? `<a class="row small" href="#/ubs/${alt.id}?med=${m.id}" style="margin-top:10px;color:var(--ok);font-weight:600">${ic('pin', 'sm')} Disponível agora na ${alt.nome} (${fmtKm(dist(alt))})</a>` : ''}
      <div class="row" style="margin-top:12px;padding-top:12px;border-top:1px solid var(--line)">
        <button class="row grow small" style="gap:10px;font-weight:600" data-act="toggle-notify" data-i="${i}"><span class="switch ${w.notify ? 'on' : ''}"></span>Avisar quando chegar</button>
        <button class="iconbtn" data-act="remove-wish" data-i="${i}" aria-label="Remover">${ic('trash', 'sm')}</button>
      </div>
    </div>`;
  }).join('');
  return `
  ${appbar('Minha lista', { back: false, right: bellBtn() })}
  <div class="section" style="padding-top:14px">
    <div class="note brand">${ic('bookmark', 'sm')}<span>Seus remédios ficam vinculados à <b>${ubsById(S.ref).nome}</b>. Quando o item der entrada no estoque, você recebe um aviso.</span></div>
  </div>
  <div class="section stack">${items || `<div class="card empty"><div class="ava">${ic('bookmark', 'lg')}</div><b>Sua lista está vazia</b><p class="small">Busque um medicamento e toque em “Me avise”.</p></div>`}
    <a class="btn sec block" href="#/buscar">${ic('plus')} Adicionar medicamento</a>
  </div>`;
};

V.alerts = () => {
  const list = alerts();
  const tones = { d: ['var(--ok-bg)', 'var(--ok)'], t: ['var(--info-bg)', 'var(--info)'], r: ['var(--warn-bg)', 'var(--warn)'], g: ['var(--bg)', 'var(--ink-2)'] };
  const html = `
  ${appbar('Avisos', { right: `<button class="link" data-act="read-all">Marcar lidas</button>` })}
  <div class="section" style="padding-top:14px"><div class="list">
    ${list.map(a => `<a class="item" href="${a.href}" data-act="read" data-id="${a.id}" style="${S.read.includes(a.id) ? '' : 'background:#f8fffd'}">
      <span class="ava" style="background:${tones[a.tone][0]};color:${tones[a.tone][1]}">${ic(a.icon)}</span>
      <span class="grow"><div class="t" style="font-size:14px">${a.t}${S.read.includes(a.id) ? '' : ' <span style="color:var(--brand)">•</span>'}</div><div class="s">${a.s}</div><div class="tiny faint" style="margin-top:4px">${a.when}</div></span></a>`).join('')}
  </div></div>`;
  return html;
};

V.profile = () => `
  ${appbar('Perfil', { back: false, right: bellBtn() })}
  <div class="pad" style="background:var(--card);border-bottom:1px solid var(--line)">
    <div class="row">
      <span class="ava" style="width:56px;height:56px;border-radius:50%;font-weight:800;font-size:20px">${esc(S.nome[0])}</span>
      <div class="grow"><div style="font-weight:800;font-size:18px">Dona ${esc(S.nome)}</div><div class="small muted">${esc(S.bairro)}, Maceió · Cartão SUS •••• 4821</div></div>
    </div>
  </div>
  <div class="section" style="padding-top:14px">
    <div class="sec-h"><h2>UBS de referência</h2></div>
    <label class="lab" for="refSel" style="display:none">UBS de referência</label>
    <select class="field" id="refSel">${byDist().map(u => `<option value="${u.id}" ${u.id === S.ref ? 'selected' : ''}>${u.nome} — ${fmtKm(dist(u))}</option>`).join('')}</select>
  </div>
  <div class="section">
    <div class="sec-h"><h2>Preferências</h2></div>
    <div class="list">
      <button class="item" data-act="toggle" data-k="notif"><span class="ava gray">${ic('bell')}</span><span class="grow"><div class="t">Notificações</div><div class="s">Avisar quando um remédio da lista chegar</div></span><span class="switch ${S.notif ? 'on' : ''}"></span></button>
      <button class="item" data-act="toggle" data-k="bigFont"><span class="ava gray">${ic('text')}</span><span class="grow"><div class="t">Letra maior</div><div class="s">Facilita a leitura</div></span><span class="switch ${S.bigFont ? 'on' : ''}"></span></button>
      <button class="item" data-act="toggle" data-k="offline"><span class="ava gray">${ic('wifioff')}</span><span class="grow"><div class="t">Simular sem internet</div><div class="s">Demonstra o modo offline-first com dados em cache</div></span><span class="switch ${S.offline ? 'on' : ''}"></span></button>
    </div>
  </div>
  <div class="section">
    <div class="sec-h"><h2>Mais</h2></div>
    <div class="list">
      <a class="item" href="#/sobre"><span class="ava gray">${ic('db')}</span><span class="grow"><div class="t">De onde vêm os dados</div><div class="s">BNAFAR, e-SUS AF, CNES, CATMAT</div></span><span class="chev">${ic('chev', 'sm')}</span></a>
      <a class="item" href="#/gestor"><span class="ava gray">${ic('chart')}</span><span class="grow"><div class="t">Painel da gestão</div><div class="s">Visão do servidor (Seu Carlos) · demo</div></span><span class="chev">${ic('chev', 'sm')}</span></a>
      <button class="item" data-act="reset"><span class="ava gray">${ic('refresh')}</span><span class="grow"><div class="t">Reiniciar protótipo</div><div class="s">Volta para a tela de boas-vindas</div></span></button>
    </div>
  </div>`;

V.about = () => `
  ${appbar('De onde vêm os dados')}
  <div class="section stack" style="padding-top:14px">
    <div class="card p">
      <b>O que cada status significa</b>
      <div class="stack" style="margin-top:12px">
        <div class="row" style="align-items:flex-start">${pill('d')}<span class="small muted">Há estoque para retirada com receita válida.</span></div>
        <div class="row" style="align-items:flex-start">${pill('r')}<span class="small muted">Poucas unidades, separadas para pacientes já agendados. Ligue antes de ir.</span></div>
        <div class="row" style="align-items:flex-start">${pill('f')}<span class="small muted">Sem estoque. Veja a previsão da próxima remessa ou outra UBS.</span></div>
      </div>
    </div>
    <div class="card p">
      <div class="row">${ic('clock')}<b>Transparência sobre a atualização</b></div>
      <p class="small muted" style="margin:8px 0 0">O estoque muda quando a farmácia registra a entrega no e-SUS AF. Por isso mostramos sempre <b>quando cada unidade sincronizou pela última vez</b>.</p>
      <div class="stack small" style="margin-top:10px">
        <div><span class="sync"><span class="dot"></span>até 1 h</span> — confiável</div>
        <div><span class="sync warn"><span class="dot"></span>até 24 h</span> — pode ter mudado</div>
        <div><span class="sync bad"><span class="dot"></span>mais de 1 dia</span> — ligue antes de ir</div>
      </div>
    </div>
    <div class="card p">
      <div class="row">${ic('wifioff')}<b>Funciona sem internet</b></div>
      <p class="small muted" style="margin:8px 0 0">O app guarda a última consulta no celular (offline-first). No Agreste e no Sertão, onde o sinal cai, você ainda vê os dados salvos e o horário deles.</p>
    </div>
    <div class="card p">
      <div class="row">${ic('db')}<b>Fontes e identificadores</b></div>
      <div class="kv" style="margin-top:10px">
        ${ic('db', 'sm')}<div><b>BNAFAR / e-SUS AF</b><div class="small muted">Base nacional de estoque da Assistência Farmacêutica (Ministério da Saúde).</div></div>
        ${ic('building', 'sm')}<div><b>CNES</b><div class="small muted">Código único de cada unidade de saúde.</div></div>
        ${ic('pill', 'sm')}<div><b>CATMAT</b><div class="small muted">Código único de cada medicamento.</div></div>
        ${ic('bookmark', 'sm')}<div><b>RENAME / REMUME</b><div class="small muted">Listas oficiais de medicamentos do SUS.</div></div>
      </div>
    </div>
    <p class="tiny faint" style="text-align:center">Atende à Lei nº 14.654/2023 (transparência dos estoques do SUS). Dados deste protótipo são fictícios.</p>
  </div>`;

V.manager = () => {
  const faltas = [];
  MEDS.forEach(m => UBS.forEach(u => { if (stock(m.id, u.id).s === 'f') faltas.push({ m, u }); }));
  const suggestions = [];
  faltas.forEach(({ m, u }) => {
    const donors = UBS.filter(d => stock(m.id, d.id).s === 'd' && stock(m.id, d.id).q >= 150).sort((a, b) => distBetween(a, u) - distBetween(b, u));
    if (donors[0]) suggestions.push({ m, to: u, from: donors[0], q: Math.round(stock(m.id, donors[0].id).q * 0.3 / 10) * 10, km: distBetween(donors[0], u), dem: demand(m.id, u.id) });
  });
  suggestions.sort((a, b) => b.dem - a.dem);
  const demandRows = faltas.map(f => ({ ...f, n: demand(f.m.id, f.u.id) })).sort((a, b) => b.n - a.n).slice(0, 5);
  const maxN = Math.max(...demandRows.map(d => d.n));
  const stale = UBS.filter(u => u.sync > 60).sort((a, b) => b.sync - a.sync);
  const venc = [
    { m: medById('amoxicilina'), u: ubsById('u6'), q: 120, d: 42 },
    { m: medById('losartana'), u: ubsById('u6'), q: 160, d: 55 },
    { m: medById('dipirona'), u: ubsById('u1'), q: 90, d: 58 },
  ];
  const totalDem = faltas.reduce((s, f) => s + demand(f.m.id, f.u.id), 0);
  const tab = ui.mgrTab;
  let body = '';
  if (tab === 'remanejo') body = `
    <p class="small muted" style="margin:0 2px 10px">Unidades com sobra que podem abastecer unidades em falta próximas — antes que o remédio vença.</p>
    <div class="stack">${suggestions.slice(0, 5).map((s, i) => `<div class="card p">
      <div class="row"><span class="ava">${ic('swap')}</span><div class="grow"><b>${medName(s.m)}</b><div class="small muted">${s.dem} cidadãos aguardando no destino</div></div><span class="tag">${fmtKm(s.km)}</span></div>
      <div class="small" style="margin-top:10px;display:grid;grid-template-columns:auto 1fr;gap:4px 10px">
        <span class="faint">De</span><span>${s.from.nome} · <b style="color:var(--ok)">${stock(s.m.id, s.from.id).q} un.</b></span>
        <span class="faint">Para</span><span>${s.to.nome} · <b style="color:var(--bad)">em falta</b></span>
      </div>
      <div class="btns" style="margin-top:12px"><button class="btn sm sec" data-act="toast" data-msg="Sugestão descartada">Descartar</button><button class="btn sm" data-act="toast" data-msg="Remanejamento de ${s.q} un. solicitado ao CAF">${ic('truck', 'sm')} Remanejar ${s.q} un.</button></div>
    </div>`).join('')}</div>`;
  if (tab === 'demanda') body = `
    <p class="small muted" style="margin:0 2px 10px">Cidadãos que salvaram o item na lista de desejos vinculada à UBS — demanda que hoje é invisível.</p>
    <div class="list">${demandRows.map(d => `<div class="item"><span class="grow"><div class="t" style="font-size:14px">${medName(d.m)}</div><div class="s">${d.u.nome}</div><div class="bar"><i style="width:${Math.round(d.n / maxN * 100)}%"></i></div></span><b style="font-variant-numeric:tabular-nums">${d.n}</b></div>`).join('')}</div>`;
  if (tab === 'sync') body = `
    <p class="small muted" style="margin:0 2px 10px">Unidades com dados atrasados ficam marcadas para o cidadão. Priorize suporte de conectividade.</p>
    <div class="list">${stale.map(u => `<a class="item" href="#/ubs/${u.id}"><span class="ava gray">${ic(u.sync > 1440 ? 'wifioff' : 'clock')}</span><span class="grow"><div class="t" style="font-size:14px">${u.nome}</div><div style="margin-top:3px">${syncTag(u)}</div></span><span class="chev">${ic('chev', 'sm')}</span></a>`).join('')}</div>`;
  if (tab === 'venc') body = `
    <p class="small muted" style="margin:0 2px 10px">Lotes que vencem em até 60 dias. Remaneje ou priorize a dispensação.</p>
    <div class="list">${venc.map(v => `<div class="item"><span class="ava" style="background:var(--warn-bg);color:var(--warn)">${ic('cal')}</span><span class="grow"><div class="t" style="font-size:14px">${medName(v.m)} · ${v.q} un.</div><div class="s">${v.u.nome}</div></span><span class="pill r">${v.d} dias</span></div>`).join('')}</div>`;

  return `
  ${appbar('Painel da gestão')}
  <div class="pad" style="background:var(--card);border-bottom:1px solid var(--line);padding-top:12px">
    <div class="small muted">Secretaria Municipal de Saúde · Maceió</div>
    <div style="font-weight:800;font-size:18px">Rede de Atenção Básica</div>
    <div class="row small" style="margin-top:6px;gap:6px">${ic('db', 'sm')}<span class="muted">Integração BNAFAR / e-SUS AF · ${UBS.length} unidades · ${MEDS.length} itens</span></div>
  </div>
  <div class="section" style="padding-top:14px">
    <div class="kpis">
      <div class="kpi bad"><b>${faltas.length}</b><span>itens em falta na rede</span></div>
      <div class="kpi info"><b>${totalDem}</b><span>cidadãos aguardando</span></div>
      <div class="kpi warn"><b>${venc.length}</b><span>lotes vencendo em 60 dias</span></div>
      <div class="kpi"><b>${stale.length}</b><span>UBS com sync atrasada</span></div>
    </div>
  </div>
  <div class="section">
    <div class="seg">
      ${[['remanejo', 'Remanejar'], ['demanda', 'Demanda'], ['venc', 'Validade'], ['sync', 'Conexão']].map(([k, l]) => `<button class="${tab === k ? 'on' : ''}" data-act="mgr-tab" data-t="${k}">${l}</button>`).join('')}
    </div>
  </div>
  <div class="section">${body}</div>`;
};

V.notfound = () => `${appbar('Não encontrado')}<div class="empty"><p>Esta página não existe.</p><a class="btn" href="#/">Voltar ao início</a></div>`;

