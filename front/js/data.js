/* =========================================================================
   Ícones
   ========================================================================= */
const ICONS = {
  home: '<path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z"/>',
  map: '<path d="M9 4 3 6v14l6-2 6 2 6-2V4l-6 2-6-2z"/><path d="M9 4v14M15 6v14"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
  bookmark: '<path d="M6 3h12v18l-6-4-6 4z"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 4-6 8-6s8 2 8 6"/>',
  bell: '<path d="M6 16v-5a6 6 0 1 1 12 0v5l2 2H4z"/><path d="M10 21h4"/>',
  pin: '<path d="M12 21s-7-6.5-7-12a7 7 0 1 1 14 0c0 5.5-7 12-7 12z"/><circle cx="12" cy="9" r="2.5"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  phone: '<path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a1 1 0 0 1-1 1A16 16 0 0 1 4 5a1 1 0 0 1 1-1z"/>',
  nav: '<path d="M3 11 21 3l-8 18-2-8z"/>',
  chev: '<path d="m9 6 6 6-6 6"/>',
  back: '<path d="m15 6-6 6 6 6"/>',
  sync: '<path d="M20 11a8 8 0 0 0-14.3-4.9L4 8"/><path d="M4 4v4h4"/><path d="M4 13a8 8 0 0 0 14.3 4.9L20 16"/><path d="M20 20v-4h-4"/>',
  wifioff: '<path d="M2 2l20 20"/><path d="M8.5 16.5a5 5 0 0 1 7 0"/><path d="M5 12.9a10 10 0 0 1 5-2.7"/><path d="M19 12.9a10 10 0 0 0-2.2-1.6"/><path d="M2 8.8a15 15 0 0 1 4.2-2.6"/><path d="M22 8.8A15 15 0 0 0 11 5"/><circle cx="12" cy="20" r=".8"/>',
  wifi: '<path d="M5 12.6a10 10 0 0 1 14 0"/><path d="M2 8.8a15 15 0 0 1 20 0"/><path d="M8.5 16.4a5 5 0 0 1 7 0"/><circle cx="12" cy="20" r=".8"/>',
  pill: '<path d="M10.5 20.5a5 5 0 0 1-7-7l6-6a5 5 0 0 1 7 7z"/><path d="m8.5 8.5 7 7"/>',
  truck: '<path d="M3 6h11v10H3zM14 10h4l3 3v3h-7"/><circle cx="7" cy="18" r="2"/><circle cx="17" cy="18" r="2"/>',
  check: '<path d="m5 12 5 5 9-10"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  x: '<path d="M6 6l12 12M18 6 6 18"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7.5v.01"/>',
  chart: '<path d="M4 20V10M10 20V4M16 20v-8M22 20H2"/>',
  snow: '<path d="M12 2v20M4.9 6.5l14.2 11M4.9 17.5l14.2-11"/>',
  building: '<path d="M4 21V8l8-5 8 5v13"/><path d="M9 21v-5h6v5M12 8v5M9.5 10.5h5"/>',
  alert: '<path d="M12 3 2 20h20z"/><path d="M12 10v4M12 17v.01"/>',
  locate: '<circle cx="12" cy="12" r="4"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3"/>',
  swap: '<path d="M4 8h15l-3-3M20 16H5l3 3"/>',
  text: '<path d="M4 18 9 6l5 12M6 14h6M15 18l3-7 3 7M16 16h4"/>',
  db: '<ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v14c0 1.7 3.6 3 8 3s8-1.3 8-3V5M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3"/>',
  trash: '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>',
  box: '<path d="M3 7l9-4 9 4v10l-9 4-9-4z"/><path d="M3 7l9 4 9-4M12 11v10"/>',
  users: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20c0-3.5 3-5.5 6.5-5.5s6.5 2 6.5 5.5"/><path d="M16 4.5a3.5 3.5 0 0 1 0 7M18 14.6c2 .6 3.5 2.4 3.5 5.4"/>',
  cal: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/>',
  refresh: '<path d="M3 12a9 9 0 0 1 15.5-6.2L21 8"/><path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-15.5 6.2L3 16"/><path d="M3 21v-5h5"/>',
  battery: '<rect x="2" y="7" width="18" height="10" rx="2"/><path d="M22 11v2"/><rect x="4" y="9" width="12" height="6" rx="1" fill="currentColor" stroke="none"/>',
  signal: '<path d="M4 18v-2M9 18v-5M14 18V9M19 18V5" stroke-width="2.6"/>',
};
const ic = (n, cls = '') => `<svg class="i ${cls}" viewBox="0 0 24 24" aria-hidden="true">${ICONS[n] || ''}</svg>`;

/* =========================================================================
   Dados fictícios (na versão real: BNAFAR / e-SUS AF via CNES + CATMAT)
   ========================================================================= */
const NOW = Date.now();
const UBS = [
  { id: 'u1', nome: 'UBS Benedito Bentes I',        tipo: 'UBS', bairro: 'Benedito Bentes',     end: 'Rua Projetada A, 120',          tel: '(82) 3000-0101', horario: 'Seg a Sex · 7h às 17h', cnes: '9000101', x: 0.3,  y: -0.4, sync: 12 },
  { id: 'u2', nome: 'USF Village Campestre II',     tipo: 'USF', bairro: 'Cidade Universitária', end: 'Av. Central do Village, 45',     tel: '(82) 3000-0102', horario: 'Seg a Sex · 7h às 16h', cnes: '9000102', x: -1.2, y: 1.0,  sync: 35 },
  { id: 'u3', nome: 'UBS Clima Bom',                tipo: 'UBS', bairro: 'Clima Bom',            end: 'Rua do Posto, 300',              tel: '(82) 3000-0103', horario: 'Seg a Sex · 7h às 17h', cnes: '9000103', x: -2.0, y: -1.3, sync: 190 },
  { id: 'u4', nome: 'UBS Tabuleiro do Martins',     tipo: 'UBS', bairro: 'Tabuleiro do Martins', end: 'Av. Menino Marcelo, s/n',        tel: '(82) 3000-0104', horario: 'Seg a Sex · 7h às 19h', cnes: '9000104', x: 2.6,  y: 1.7,  sync: 20 },
  { id: 'u5', nome: 'UBS Santos Dumont',            tipo: 'UBS', bairro: 'Santos Dumont',        end: 'Rua São Vicente, 88',            tel: '(82) 3000-0105', horario: 'Seg a Sex · 7h às 17h', cnes: '9000105', x: -3.2, y: 2.7,  sync: 70 },
  { id: 'u6', nome: 'UBS Cidade Universitária',     tipo: 'UBS', bairro: 'Cidade Universitária', end: 'Rua Eng. Mário de Gusmão, 10',   tel: '(82) 3000-0106', horario: 'Seg a Sex · 7h às 19h', cnes: '9000106', x: 4.1,  y: -3.4, sync: 8 },
  { id: 'u7', nome: 'USF Riacho Doce (zona rural)', tipo: 'USF', bairro: 'Zona rural',           end: 'Sítio Riacho Doce, km 4',        tel: '(82) 3000-0107', horario: 'Seg a Qui · 8h às 14h', cnes: '9000107', x: -6.5, y: -5.8, sync: 2900, rural: true },
];
const MEDS = [
  { id: 'metformina',        nome: 'Metformina',               dose: '850 mg',        forma: 'comprimido',     cat: 'Diabetes',     catmat: 'BR0267690', cont: true },
  { id: 'glibenclamida',     nome: 'Glibenclamida',            dose: '5 mg',          forma: 'comprimido',     cat: 'Diabetes',     catmat: 'BR0267612', cont: true },
  { id: 'insulina-nph',      nome: 'Insulina humana NPH',      dose: '100 UI/mL',     forma: 'frasco 10 mL',   cat: 'Diabetes',     catmat: 'BR0271157', cont: true, frio: true },
  { id: 'losartana',         nome: 'Losartana potássica',      dose: '50 mg',         forma: 'comprimido',     cat: 'Hipertensão',  catmat: 'BR0268856', cont: true },
  { id: 'hidroclorotiazida', nome: 'Hidroclorotiazida',        dose: '25 mg',         forma: 'comprimido',     cat: 'Hipertensão',  catmat: 'BR0267620', cont: true },
  { id: 'captopril',         nome: 'Captopril',                dose: '25 mg',         forma: 'comprimido',     cat: 'Hipertensão',  catmat: 'BR0267515', cont: true },
  { id: 'anlodipino',        nome: 'Anlodipino',               dose: '5 mg',          forma: 'comprimido',     cat: 'Hipertensão',  catmat: 'BR0267520', cont: true },
  { id: 'sinvastatina',      nome: 'Sinvastatina',             dose: '20 mg',         forma: 'comprimido',     cat: 'Colesterol',   catmat: 'BR0268280', cont: true },
  { id: 'amoxicilina',       nome: 'Amoxicilina',              dose: '500 mg',        forma: 'cápsula',        cat: 'Antibióticos', catmat: 'BR0269919', receita: true },
  { id: 'omeprazol',         nome: 'Omeprazol',                dose: '20 mg',         forma: 'cápsula',        cat: 'Estômago',     catmat: 'BR0270320' },
  { id: 'dipirona',          nome: 'Dipirona',                 dose: '500 mg',        forma: 'comprimido',     cat: 'Dor e febre',  catmat: 'BR0267543' },
  { id: 'salbutamol',        nome: 'Salbutamol',               dose: '100 mcg/dose',  forma: 'aerossol',       cat: 'Asma',         catmat: 'BR0268338', cont: true },
];
const CATS = ['Diabetes', 'Hipertensão', 'Colesterol', 'Antibióticos', 'Asma', 'Dor e febre', 'Estômago'];
// Estoque por UBS (ordem u1..u7): [status, quantidade] — d = disponível, r = reservado, f = em falta
const STOCK_RAW = {
  'metformina':        [['f',0],['r',14],['d',220],['d',85],['f',0],['d',310],['r',6]],
  'glibenclamida':     [['d',140],['d',60],['r',12],['d',90],['d',75],['f',0],['d',40]],
  'insulina-nph':      [['r',4],['f',0],['d',18],['d',22],['f',0],['d',30],['f',0]],
  'losartana':         [['d',260],['d',180],['d',95],['r',10],['d',150],['d',400],['d',55]],
  'hidroclorotiazida': [['d',200],['r',9],['d',120],['d',80],['d',60],['d',150],['d',30]],
  'captopril':         [['d',90],['d',70],['f',0],['d',45],['r',8],['d',110],['d',20]],
  'anlodipino':        [['f',0],['d',50],['d',80],['f',0],['d',65],['d',120],['f',0]],
  'sinvastatina':      [['r',11],['d',90],['d',40],['d',70],['f',0],['d',85],['r',5]],
  'amoxicilina':       [['d',75],['d',40],['r',10],['d',60],['d',55],['d',190],['f',0]],
  'omeprazol':         [['d',180],['d',140],['d',100],['d',95],['d',80],['d',210],['d',40]],
  'dipirona':          [['d',300],['d',220],['d',150],['d',190],['d',140],['d',350],['d',70]],
  'salbutamol':        [['d',12],['r',3],['f',0],['d',9],['d',14],['d',20],['f',0]],
};
const STATUS = { d: 'Disponível', r: 'Reservado', f: 'Em falta' };
const COLORS = { d: '#16a34a', r: '#d97706', f: '#dc2626', none: '#0f766e' };

const ubsById = id => UBS.find(u => u.id === id);
const medById = id => MEDS.find(m => m.id === id);
const stock = (medId, ubsId) => {
  const row = STOCK_RAW[medId]; const i = UBS.findIndex(u => u.id === ubsId);
  if (!row || i < 0) return { s: 'f', q: 0 };
  return { s: row[i][0], q: row[i][1] };
};
const dist = u => Math.hypot(u.x, u.y);
const distBetween = (a, b) => Math.hypot(a.x - b.x, a.y - b.y);
const byDist = () => [...UBS].sort((a, b) => dist(a) - dist(b));
const fmtKm = d => d.toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 }) + ' km';
const travel = d => d < 1.8 ? `${Math.max(3, Math.round(d * 13))} min a pé` : `~${Math.round(d * 4 + 8)} min de ônibus`;
const hash = s => { let h = 7; for (const c of s) h = (h * 31 + c.charCodeAt(0)) >>> 0; return h; };
const day = n => new Date(NOW + n * 864e5);
const fmtDay = n => day(n).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });
const fmtWeekday = n => n === 0 ? 'hoje' : n === 1 ? 'amanhã' : day(n).toLocaleDateString('pt-BR', { weekday: 'long', day: '2-digit', month: '2-digit' });
const ago = min => min < 1 ? 'agora' : min < 60 ? `há ${Math.round(min)} min` : min < 1440 ? `há ${Math.round(min / 60)} h` : `há ${Math.round(min / 1440)} dias`;
const syncLevel = min => min <= 60 ? '' : min <= 1440 ? 'warn' : 'bad';
const syncTag = u => `<span class="sync ${syncLevel(u.sync)}"><span class="dot"></span>Sincronizado ${ago(u.sync)}</span>`;
const pill = (s, extra = '') => `<span class="pill ${s}">${STATUS[s]}${extra}</span>`;
const medName = m => `${m.nome} ${m.dose}`;
const esc = s => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

// Rastreamento da próxima remessa (CAF → UBS)
const STEPS = ['Pedido registrado pela UBS', 'Separado no CAF Maceió', 'Em transporte para a UBS', 'Recebido e conferido na UBS', 'Disponível para retirada'];
function track(medId, ubsId) {
  const st = stock(medId, ubsId);
  const h = hash(medId + ubsId);
  if (st.s === 'd') return { active: false, lastIn: 2 + (h % 9), qtyIn: (Math.round(st.q / 50) + 2) * 50 };
  const key = medId + ':' + ubsId;
  const special = { 'metformina:u1': 2, 'insulina-nph:u1': 1, 'anlodipino:u1': 0, 'sinvastatina:u1': 1 };
  if (!(key in special) && h % 5 === 0) return { active: false, none: true };
  const stage = key in special ? special[key] : h % 3;
  const eta = [8, 4, 2][stage];
  const steps = STEPS.map((label, i) => {
    if (i < stage) return { label, state: 'done', when: `${fmtDay(-(stage - i) * 2)} · concluído` };
    if (i === stage) return { label, state: 'now', when: i === 2 ? `Saiu ${fmtDay(0)} às 08:10 · em rota` : `Desde ${fmtDay(-1)}` };
    const n = Math.round(eta * (i - stage) / (STEPS.length - 1 - stage));
    return { label, state: 'pend', when: `Previsto ${fmtDay(n)}` };
  });
  return { active: true, stage, eta, steps, qty: (h % 4 + 2) * 50 };
}
function moves(medId, ubsId) {
  const st = stock(medId, ubsId); const h = hash(ubsId + medId);
  const list = [];
  if (st.s !== 'f') list.push({ t: 'Dispensação', q: -(h % 4 + 1) * 2, when: `hoje, ${8 + h % 3}:${String(10 + h % 49).padStart(2, '0')}` });
  if (st.s === 'f') list.push({ t: 'Estoque zerado', q: 0, when: `${fmtDay(-(h % 6 + 3))}` });
  list.push({ t: 'Dispensação', q: -(h % 5 + 2) * 3, when: `ontem, ${13 + h % 4}:${String(5 + h % 50).padStart(2, '0')}` });
  list.push({ t: 'Entrada (remessa CAF)', q: (Math.round(st.q / 50) + 3) * 50, when: fmtDay(-(h % 9 + 8)) });
  return list;
}
const demand = (medId, ubsId) => medId === 'metformina' && ubsId === 'u1' ? 47 : (hash(medId + ubsId + 'd') % 30) + 3;

