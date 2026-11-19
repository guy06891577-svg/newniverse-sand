// 뉴니버스 버스 중계 서버 (Cloudflare Worker, 무료 플랜)
// - 국토교통부 TAGO(공공데이터포털) 키는 서버 비밀 값(TAGO_KEY)에만 있음. 응답·로그에 절대 내보내지 않음.
// - 두 가지 조회만 중계: 가까운 정류장 / 정류장 도착 정보. 다른 주소는 부를 수 없음.
// - Cache API로 같은 질문을 묶어 하루 호출 한도(개발 계정 API당 약 1만 회)를 아낌.
const UP = 'https://apis.data.go.kr/1613000/';
const STOP_TTL = 86400, STOP_KEEP = 7 * 86400;   // 정류장: 하루 신선, 일주일까지 비상용
const ARR_TTL = 25, ARR_KEEP = 180;              // 도착: 25초 신선, 3분까지 비상용(stale 표시)
const GRID = 0.002;                              // 정류장 캐시 격자 (약 220m × 180m)
const VER = '1';

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type',
  'Access-Control-Max-Age': '86400',
};
function out(obj, status = 200, maxAge = 0) {
  return new Response(JSON.stringify(obj), { status, headers: {
    ...CORS, 'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': maxAge ? `public, max-age=${maxAge}` : 'no-store',
    'X-Content-Type-Options': 'nosniff' } });
}
const bad = (code, msg, status = 400) => out({ ok: false, error: code, message: msg }, status);

function dist(a1, o1, a2, o2) {
  const R = 6371000, r = Math.PI / 180, dA = (a2 - a1) * r, dO = (o2 - o1) * r;
  const h = Math.sin(dA / 2) ** 2 + Math.cos(a1 * r) * Math.cos(a2 * r) * Math.sin(dO / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
}
function num(v) { if (v === '' || v == null || !/^-?\d{1,3}(\.\d{1,9})?$/.test(v)) return NaN; return +v; }

const UPMSG = { '1': '공공데이터 서버 오류', '10': '잘못된 요청', '12': '서비스 없음', '20': '서비스 접근 거부',
  '22': '오늘 조회 한도 초과', '30': '등록되지 않은 키', '31': '기한 만료 키', '32': '등록되지 않은 IP', '99': '공공데이터 서버가 바빠요' };

async function upstream(env, path, params) {
  const k = String(env.TAGO_KEY || '').trim();
  if (!k) return { err: 'nokey' };
  const ek = k.includes('%') ? k : encodeURIComponent(k);
  const qs = new URLSearchParams({ ...params, _type: 'json' }).toString();
  let lastErr = 'upstream';
  for (let i = 0; i < 2; i++) {
    let txt = '', st = 0;
    try {
      const r = await fetch(`${UP}${path}?serviceKey=${ek}&${qs}`, { headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(8000) });
      st = r.status; txt = await r.text();
    } catch (e) { lastErr = 'timeout'; continue; }
    if (st === 401 || st === 403) return { err: 'denied' };
    let j; try { j = JSON.parse(txt); } catch (_) {
      const m = txt.match(/<returnReasonCode>(\d+)</) || txt.match(/<resultCode>(\d+)</);
      lastErr = m ? 'tago_' + (+m[1]) : 'badjson'; if (m && +m[1] !== 99 && +m[1] !== 1) break; continue;
    }
    const h = j && j.response && j.response.header;
    const c = h ? String(+h.resultCode) : '';
    if (c && c !== '0') { lastErr = 'tago_' + c; if (c === '99' || c === '1') { await new Promise(r => setTimeout(r, 400)); continue; } break; }
    const b = j.response && j.response.body;
    let it = b && b.items && b.items.item;
    if (!it) it = []; else if (!Array.isArray(it)) it = [it];
    return { list: it };
  }
  return { err: lastErr };
}

// 분당 계수 (인스턴스 메모리). 새 데이터를 공공데이터에 묻는 횟수는 IP당 분당 20번까지만.
const BUCKET = new Map();
function hit(ip, kind, max) {
  const k = kind + '|' + ip, now = Date.now(), m = Math.floor(now / 60000);
  let b = BUCKET.get(k); if (!b || b.m !== m) { b = { m, n: 0 }; BUCKET.set(k, b); }
  if (BUCKET.size > 5000) for (const [kk, v] of BUCKET) if (v.m !== m) BUCKET.delete(kk);
  return ++b.n <= max;
}
// Cache API: 같은 키로 저장, 저장 시각을 헤더에 같이 둠
async function cached(ctx, key, fresh, keep, load) {
  const cache = caches.default;
  const req = new Request(`https://newniverse-bus.cache/${VER}/${key}`);
  const got = await cache.match(req);
  let old = null;
  if (got) {
    const t = +got.headers.get('x-at') || 0, age = (Date.now() - t) / 1000;
    old = { data: await got.json(), at: t };
    if (age < fresh) return { ...old, cached: true, stale: false };
  }
  if (ctx.ip && !hit(ctx.ip, 'up', 20)) return old ? { ...old, cached: true, stale: true, upErr: 'rate' } : { err: 'rate' };
  const r = await load();
  if (r.err) {
    if (old) return { ...old, cached: true, stale: true, upErr: r.err };
    return { err: r.err };
  }
  const at = Date.now();
  const res = new Response(JSON.stringify(r.data), { headers: { 'Content-Type': 'application/json', 'Cache-Control': `public, max-age=${keep}`, 'x-at': String(at) } });
  ctx.waitUntil(cache.put(req, res));
  return { data: r.data, at, cached: false, stale: false };
}

function errOut(e) {
  const c = String(e).replace('tago_', '');
  if (e === 'rate') return out({ ok: false, error: 'rate', message: '잠시 후 다시 시도해 주세요' }, 429);
  const status = e === 'nokey' ? 503 : (c === '22' ? 429 : 502);
  return out({ ok: false, error: e, message: UPMSG[c] || (e === 'timeout' ? '공공데이터 서버 응답이 늦어요' : '공공데이터 서버 오류') }, status);
}

async function stops(url, env, ctx) {
  const lat = num(url.searchParams.get('lat')), lng = num(url.searchParams.get('lng'));
  const r = Math.min(500, Math.max(30, Math.round(num(url.searchParams.get('r') || '200') || 200)));
  if (!(lat >= 33 && lat <= 38.9 && lng >= 124.5 && lng <= 132)) return bad('bad_coord', 'lat/lng가 한국 범위가 아니에요 (lat 33~38.9, lng 124.5~132)');
  const gLat = (Math.round(lat / GRID) * GRID).toFixed(3), gLng = (Math.round(lng / GRID) * GRID).toFixed(3);
  const c = await cached(ctx, `stops/${gLat}/${gLng}`, STOP_TTL, STOP_KEEP, async () => {
    const u = await upstream(env, 'BusSttnInfoInqireService/getCrdntPrxmtSttnList', { gpsLati: gLat, gpsLong: gLng, numOfRows: '100', pageNo: '1' });
    if (u.err) return u;
    return { data: u.list.filter(s => s && s.nodeid && isFinite(+s.gpslati) && isFinite(+s.gpslong)).map(s => ({
      id: String(s.nodeid), name: String(s.nodenm || ''), no: s.nodeno == null ? null : String(s.nodeno),
      city: String(s.citycode), lat: +s.gpslati, lng: +s.gpslong })) };
  });
  if (c.err) return errOut(c.err);
  const list = c.data.map(s => ({ ...s, d: Math.round(dist(lat, lng, s.lat, s.lng)) })).sort((a, b) => a.d - b.d);
  const near = list.filter(s => s.d <= r);
  return out({ ok: true, mode: 'bus', r, at: new Date(c.at).toISOString(), cached: c.cached, stale: c.stale,
    stops: near.slice(0, 20), nearest: near.length ? null : (list[0] || null) }, 200, 30);
}

// arrtime·arrprevstationcnt 둘 다 0 = 시간 정보가 없는 노선(운행 노선만 알려줌). '0분'으로 보여주면 안 됨.
function busState(sec, cnt) {
  if (sec > 0) return sec <= 90 || cnt === 1 ? 'soon' : 'live';
  return 'nodata';
}
async function arrivals(url, env, ctx) {
  const city = url.searchParams.get('city') || url.searchParams.get('cityCode') || '';
  const node = url.searchParams.get('node') || url.searchParams.get('nodeId') || '';
  if (!/^\d{1,6}$/.test(city)) return bad('bad_city', 'city는 숫자 도시코드예요 (예: 37040)');
  if (!/^[A-Za-z0-9]{3,30}$/.test(node)) return bad('bad_node', 'node는 정류소ID예요 (예: ADB354000407)');
  const c = await cached(ctx, `arr/${city}/${node.toUpperCase()}`, ARR_TTL, ARR_KEEP, async () => {
    const u = await upstream(env, 'ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList', { cityCode: city, nodeId: node.toUpperCase(), numOfRows: '100', pageNo: '1' });
    if (u.err) return u;
    return { data: u.list.filter(x => x && x.routeno != null).map(x => ({
      route: String(x.routeno), routeId: x.routeid == null ? '' : String(x.routeid), type: String(x.routetp || ''),
      vehicle: String(x.vehicletp || ''), sec: Math.max(0, +x.arrtime || 0), stops: Math.max(0, +x.arrprevstationcnt || 0),
      stopName: String(x.nodenm || '') })) };
  });
  if (c.err) return errOut(c.err);
  const age = Math.max(0, Math.round((Date.now() - c.at) / 1000));
  // 같은 노선은 가장 빠른 차 1대 + 다음 차 1대까지
  const by = {};
  for (const x of c.data) (by[x.route] = by[x.route] || []).push(x);
  const buses = [];
  for (const k in by) {
    const L = by[k].sort((a, b) => (a.sec || 1e9) - (b.sec || 1e9));
    const f = L[0], n = L.find((x, i) => i > 0 && x.sec > 0);
    const sec = f.sec > 0 ? Math.max(0, f.sec - age) : 0;
    buses.push({ route: f.route, routeId: f.routeId, type: f.type, vehicle: f.vehicle,
      state: busState(f.sec, f.stops), sec: f.sec > 0 ? sec : null, stops: f.sec > 0 ? f.stops : null,
      next: n ? { sec: Math.max(0, n.sec - age), stops: n.stops } : null });
  }
  const rank = s => s.state === 'nodata' ? 1e9 : s.sec;
  buses.sort((a, b) => rank(a) - rank(b) || a.route.localeCompare(b.route, 'ko', { numeric: true }));
  const stopName = (c.data[0] && c.data[0].stopName) || '';
  return out({ ok: true, mode: 'bus', city, node: node.toUpperCase(), stopName, at: new Date(c.at).toISOString(), age, cached: c.cached, stale: c.stale,
    live: buses.some(b => b.state !== 'nodata'), buses }, 200, 10);
}

// 교통수단별 모듈. 지하철(subway)·철도(rail)는 나중에 같은 모양(stations/arrivals)으로 붙임.
const MODES = {
  bus: { ready: true, routes: { stops, arrivals } },
  subway: { ready: false, routes: {} },   // 예정: /v1/subway/stations, /v1/subway/arrivals
  rail: { ready: false, routes: {} },     // 예정: /v1/rail/stations, /v1/rail/departures
};
const ALIAS = { stops: 1, arrivals: 1 };

export default {
  async fetch(req, env, ctx) {
    const url = new URL(req.url);
    if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers: CORS });
    if (req.method !== 'GET' && req.method !== 'HEAD') return bad('method', 'GET만 돼요', 405);
    if (url.search.length > 200) return bad('too_long', '요청이 너무 길어요', 414);
    const p = url.pathname.replace(/\/+$/, '') || '/';
    if (p === '/' || p === '/v1') return out({ ok: true, name: 'newniverse-bus', v: VER,
      modes: Object.fromEntries(Object.entries(MODES).map(([m, o]) => [m, o.ready ? Object.keys(o.routes).map(r => `/v1/${m}/${r}`) : 'planned'])),
      examples: { stops: '/v1/bus/stops?lat=36.5684&lng=128.7294[&r=200]', arrivals: '/v1/bus/arrivals?city=37040&node=ADB354000407' },
      source: '국토교통부 TAGO (공공데이터포털)' }, 200, 3600);
    const seg = p.split('/');            // ['', 'v1', mode, op]
    if (seg[1] !== 'v1') return bad('not_found', '없는 주소예요', 404);
    let mode = seg[2], op = seg[3];
    if (ALIAS[mode]) { op = mode; mode = 'bus'; }   // 예전 모양 /v1/stops, /v1/arrivals
    const M = MODES[mode];
    if (!M) return bad('not_found', '없는 주소예요', 404);
    if (!M.ready) return out({ ok: false, error: 'not_ready', mode, message: '아직 준비 중인 교통수단이에요' }, 501);
    const fn = M.routes[op];
    if (!fn || seg.length > 4) return bad('not_found', '없는 주소예요', 404);
        if (env.RL_IP) {
      const ip = req.headers.get('cf-connecting-ip') || 'x';
      try { const { success } = await env.RL_IP.limit({ key: ip }); if (!success) return out({ ok: false, error: 'rate', message: '잠시 후 다시 시도해 주세요' }, 429); } catch (_) {}
    }
    // 2차 보호: 이 서버 인스턴스 안 IP별 계수 (Cloudflare 제한은 느슨하고 늦게 맞춰짐)
    const ip = req.headers.get('cf-connecting-ip') || 'x';
    if (!hit(ip, 'req', 90)) return out({ ok: false, error: 'rate', message: '잠시 후 다시 시도해 주세요' }, 429);
    ctx.ip = ip;
    try { const r = await fn(url, env, ctx); return r; }
    catch (e) { return out({ ok: false, error: 'server', message: '중계 서버 오류' }, 500); }
  },
};
