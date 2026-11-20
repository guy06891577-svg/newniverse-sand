// 뉴니버스 버스 중계 서버 (Cloudflare Worker, 무료 플랜)
// - 국토교통부 TAGO(공공데이터포털) 키는 서버 비밀 값(TAGO_KEY)에만 있음. 응답·로그에 절대 내보내지 않음.
// - 두 가지 조회만 중계: 가까운 정류장 / 정류장 도착 정보. 다른 주소는 부를 수 없음.
// - Cache API로 같은 질문을 묶어 하루 호출 한도(개발 계정 API당 약 1만 회)를 아낌.
const UP = 'https://apis.data.go.kr/1613000/';
const STOP_TTL = 86400, STOP_KEEP = 7 * 86400;   // 정류장: 하루 신선, 일주일까지 비상용
const ARR_TTL = 25, ARR_KEEP = 180;              // 도착: 25초 신선, 3분까지 비상용(stale 표시)
const GRID = 0.002;                              // 정류장 캐시 격자 (약 220m × 180m)
const VER = '2';

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
async function cached(ctx, key, fresh, keep, load, shared) {
  const cache = caches.default;
  const req = new Request(`https://newniverse-bus.cache/${VER}/${key}`);
  const got = await cache.match(req);
  let old = null;
  if (got) {
    const t = +got.headers.get('x-at') || 0, age = (Date.now() - t) / 1000;
    old = { data: await got.json(), at: t };
    if (age < fresh) return { ...old, cached: true, stale: false };
  }
  if (!shared && ctx.ip && !hit(ctx.ip, 'up', 20)) return old ? { ...old, cached: true, stale: true, upErr: 'rate' } : { err: 'rate' };
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

// ---------- 안동시 BIS (공식 공개 API, data.go.kr 15057790 · 키 없음) ----------
// TAGO는 안동(37040) 도착 시간을 0으로만 줘서, 안동은 시청 BIS에서 직접 받음. 실패하면 TAGO로 돌아감.
const BIS = 'https://bus.andong.go.kr/m01/s04.do';
const BIS_ARR_TTL = 20, BIS_STOP_TTL = 86400;
const ANDONG = '37040';
async function bisGet(qs) {
  try {
    const r = await fetch(`${BIS}?${qs}`, { headers: { Accept: 'application/json', 'User-Agent': 'newniverse-bus/1 (+https://guy06891577-svg.github.io/newniverse-sand/)' }, signal: AbortSignal.timeout(7000) });
    if (!r.ok) return { err: 'bis_http_' + r.status };
    const t = await r.text(); let j; try { j = JSON.parse(t); } catch (_) { return { err: 'bis_badjson' }; }
    return { list: Array.isArray(j) ? j : (j && typeof j === 'object' ? [j] : []) };
  } catch (e) { return { err: 'bis_timeout' }; }
}
// 정류장 목록(약 2천 곳)은 하루 캐시: 정류장 이름 채우기 + TAGO 정류장 조회가 안 될 때 비상용
async function bisStops(ctx) {
  return cached(ctx, 'bis/stops', BIS_STOP_TTL, 7 * 86400, async () => {
    const u = await bisGet('tab=1'); if (u.err) return u;
    const L = u.list.filter(x => x && x.stationId && x.useYn !== 'N' && isFinite(+x.gpsX) && isFinite(+x.gpsY) && +x.gpsY > 30)
      .map(x => ({ id: 'ADB' + x.stationId, name: String(x.stationNm || ''), no: x.mobiNum == null ? null : String(x.mobiNum), city: ANDONG, lat: +x.gpsY, lng: +x.gpsX }));
    return L.length ? { data: L } : { err: 'bis_empty' };
  }, true);
}
// predictTm = 분 (안동 BIS 누리집 '약 N분후 도착'과 같은 값), remainStation = 남은 정류장
async function bisArrivals(ctx, node) {
  const m = /^ADB(\d{6,12})$/.exec(node); if (!m) return { err: 'bis_id' };
  return cached(ctx, `bis/arr/${m[1]}`, BIS_ARR_TTL, 120, async () => {
    const u = await bisGet(`i=${m[1]}&tab=2`); if (u.err) return u;
    return { data: u.list.filter(x => x && (x.routeNum != null || x.routeName != null)).map(x => {
      const min = x.predictTm == null || x.predictTm === '' ? NaN : +x.predictTm;
      const st = x.remainStation != null ? +x.remainStation : (x.rstop != null ? +x.rstop : NaN);
      return { route: String(x.routeNum != null ? x.routeNum : x.routeName), routeId: x.routeId == null ? '' : String(x.routeId),
        type: '', vehicle: '', sec: isFinite(min) && min >= 0 ? Math.round(min * 60) : 0, stops: isFinite(st) && st >= 0 ? st : 0,
        stopName: '', has: isFinite(min) && min >= 0 };
    }) };
  });
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
  let src = 'tago';
  if (c.err) {   // TAGO 실패 + 안동 근처면 안동 BIS 정류장 목록으로 대신
    if (lat > 36.3 && lat < 36.9 && lng > 128.4 && lng < 129.2) { const b = await bisStops(ctx); if (!b.err) { c.data = b.data; c.at = b.at; c.cached = b.cached; c.stale = b.stale; delete c.err; src = 'andong-bis'; } }
    if (c.err) return errOut(c.err);
  }
  const list = c.data.map(s => ({ ...s, d: Math.round(dist(lat, lng, s.lat, s.lng)) })).sort((a, b) => a.d - b.d);
  const near = list.filter(s => s.d <= r);
  return out({ ok: true, mode: 'bus', source: src, r, at: new Date(c.at).toISOString(), cached: c.cached, stale: c.stale,
    stops: near.slice(0, 20), nearest: near.length ? null : (list[0] || null) }, 200, 30);
}

// arrtime·arrprevstationcnt 둘 다 0 = 시간 정보가 없는 노선(운행 노선만 알려줌). '0분'으로 보여주면 안 됨.
function busState(has, sec, cnt) {
  if (!has) return 'nodata';
  return sec <= 90 || cnt <= 1 ? 'soon' : 'live';
}
async function arrivals(url, env, ctx) {
  const city = url.searchParams.get('city') || url.searchParams.get('cityCode') || '';
  const node = url.searchParams.get('node') || url.searchParams.get('nodeId') || '';
  if (!/^\d{1,6}$/.test(city)) return bad('bad_city', 'city는 숫자 도시코드예요 (예: 37040)');
  if (!/^[A-Za-z0-9]{3,30}$/.test(node)) return bad('bad_node', 'node는 정류소ID예요 (예: ADB354000407)');
  let c = null, source = 'tago', fallback = '';
  if (city === ANDONG && /^ADB\d+$/i.test(node)) {
    const b = await bisArrivals(ctx, node.toUpperCase());
    if (!b.err) { c = b; source = 'andong-bis'; } else fallback = b.err;
  }
  if (!c) c = await cached(ctx, `arr/${city}/${node.toUpperCase()}`, ARR_TTL, ARR_KEEP, async () => {
    const u = await upstream(env, 'ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList', { cityCode: city, nodeId: node.toUpperCase(), numOfRows: '100', pageNo: '1' });
    if (u.err) return u;
    return { data: u.list.filter(x => x && x.routeno != null).map(x => ({
      route: String(x.routeno), routeId: x.routeid == null ? '' : String(x.routeid), type: String(x.routetp || ''),
      vehicle: String(x.vehicletp || ''), sec: Math.max(0, +x.arrtime || 0), stops: Math.max(0, +x.arrprevstationcnt || 0),
      stopName: String(x.nodenm || ''), has: (+x.arrtime || 0) > 0 })) };
  });
  if (c.err) return errOut(c.err);
  const age = Math.max(0, Math.round((Date.now() - c.at) / 1000));
  // 같은 노선은 가장 빠른 차 1대 + 다음 차 1대까지
  const by = {};
  for (const x of c.data) (by[x.route] = by[x.route] || []).push(x);
  const buses = [];
  for (const k in by) {
    const has = x => x.has !== undefined ? x.has : x.sec > 0;   // 예전 캐시 호환
    const L = by[k].sort((a, b) => (has(a) ? a.sec : 1e9) - (has(b) ? b.sec : 1e9));
    const f = L[0], n = L.find((x, i) => i > 0 && has(x));
    const sec = has(f) ? Math.max(0, f.sec - age) : 0;
    buses.push({ route: f.route, routeId: f.routeId, type: f.type, vehicle: f.vehicle,
      state: busState(has(f), sec, f.stops), sec: has(f) ? sec : null, stops: has(f) ? f.stops : null,
      next: n ? { sec: Math.max(0, n.sec - age), stops: n.stops } : null });
  }
  const rank = s => s.state === 'nodata' ? 1e9 : s.sec;
  buses.sort((a, b) => rank(a) - rank(b) || a.route.localeCompare(b.route, 'ko', { numeric: true }));
  let stopName = (c.data[0] && c.data[0].stopName) || '';
  if (!stopName && source === 'andong-bis') { const S = await bisStops(ctx); if (!S.err) { const f = S.data.find(x => x.id === node.toUpperCase()); if (f) stopName = f.name; } }
  return out({ ok: true, mode: 'bus', source, ...(fallback ? { fallback } : {}), city, node: node.toUpperCase(), stopName, at: new Date(c.at).toISOString(), age, cached: c.cached, stale: c.stale,
    live: buses.some(b => b.state !== 'nodata'), buses }, 200, 10);
}


// ---------- 미세먼지 (에어코리아 · 한국환경공단, 공공데이터포털 15073877 측정소정보 + 15073861 대기오염정보) ----------
// 같은 공공데이터포털 키(TAGO_KEY)를 씀. 두 서비스 모두 '활용신청'이 돼 있어야 함 (개발계정 하루 500회).
const AIR = 'https://apis.data.go.kr/B552584/';
const AIR_TTL = 1800, AIR_KEEP = 3 * 3600;        // 측정값: 30분 신선, 3시간까지 비상용
const AIR_ST_TTL = 86400, AIR_ST_KEEP = 7 * 86400; // 측정소 목록: 하루
const AIR_LBL = ['좋음', '보통', '나쁨', '매우나쁨'];
async function airUp(env, path, params) {
  const k = String(env.TAGO_KEY || '').trim();
  if (!k) return { err: 'nokey' };
  const ek = k.includes('%') ? k : encodeURIComponent(k);
  const qs = new URLSearchParams({ ...params, returnType: 'json' }).toString();
  let lastErr = 'upstream';
  for (let i = 0; i < 2; i++) {
    let txt = '', st = 0;
    try {
      const r = await fetch(`${AIR}${path}?serviceKey=${ek}&${qs}`, { headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(9000) });
      st = r.status; txt = await r.text();
    } catch (e) { lastErr = 'timeout'; continue; }
    const rc = (txt.match(/"returnReasonCode"\s*:\s*"?(\d+)/) || txt.match(/<returnReasonCode>(\d+)</) || [])[1];
    if (rc === '30' || rc === '20') return { err: 'not_registered' };
    if (rc) { lastErr = 'tago_' + (+rc); if (rc === '22') break; continue; }
    if (st === 401 || st === 403) return { err: 'not_registered' };
    let j; try { j = JSON.parse(txt); } catch (_) { lastErr = 'badjson'; continue; }
    const h = j && j.response && j.response.header;
    const c = h ? String(+h.resultCode) : '';
    if (c && c !== '0') { lastErr = 'tago_' + c; if (c === '1' || c === '99') { await new Promise(r => setTimeout(r, 400)); continue; } break; }
    const b = j.response && j.response.body;
    let it = b && b.items; if (it && it.item) it = it.item;
    if (!it) it = []; else if (!Array.isArray(it)) it = [it];
    return { list: it };
  }
  return { err: lastErr };
}
function airGrade(v, kind) {
  if (typeof v !== 'number' || !isFinite(v) || v < 0) return null;
  const t = kind === 'pm25' ? [15, 35, 75] : [30, 80, 150];   // 환경부 예보 기준 (㎍/㎥)
  const g = v <= t[0] ? 1 : v <= t[1] ? 2 : v <= t[2] ? 3 : 4;
  return { v, g, label: AIR_LBL[g - 1] };
}
async function airStations(ctx, env) {
  return cached(ctx, 'air-st', AIR_ST_TTL, AIR_ST_KEEP, async () => {
    const r = await airUp(env, 'MsrstnInfoInqireSvc/getMsrstnList', { numOfRows: '1000', pageNo: '1' });
    if (r.err) return r;
    const data = r.list.map(x => ({ name: String(x.stationName || ''), addr: String(x.addr || ''), lat: +x.dmX, lng: +x.dmY }))
      .filter(x => x.name && x.lat > 30 && x.lat < 40 && x.lng > 120 && x.lng < 135);
    return data.length ? { data } : { err: 'empty' };
  }, true);
}
async function airNow(url, env, ctx) {
  const lat = num(url.searchParams.get('lat')), lng = num(url.searchParams.get('lng'));
  if (!(lat >= 33 && lat <= 39 && lng >= 124 && lng <= 132)) return bad('bad_location', '한국 안의 위도(lat)·경도(lng)를 주세요');
  const S = await airStations(ctx, env);
  if (S.err) return airErr(S.err);
  const near = S.data.map(s => ({ ...s, d: Math.round(dist(lat, lng, s.lat, s.lng)) })).sort((a, b) => a.d - b.d).filter(s => s.d < 40000).slice(0, 3);
  if (!near.length) return bad('no_station', '가까운 측정소가 없어요', 404);
  let lastErr = null;
  for (const st of near) {
    const c = await cached(ctx, 'air-rt/' + encodeURIComponent(st.name), AIR_TTL, AIR_KEEP, async () => {
      const r = await airUp(env, 'ArpltnInforInqireSvc/getMsrstnAcctoRltmMesureDnsty', { stationName: st.name, dataTerm: 'DAILY', numOfRows: '1', pageNo: '1', ver: '1.3' });
      if (r.err) return r;
      const x = r.list[0];
      if (!x) return { err: 'empty' };
      return { data: { dataTime: String(x.dataTime || ''), pm10: num(String(x.pm10Value || '')), pm25: num(String(x.pm25Value || '')) } };
    });
    if (c.err) { lastErr = c.err; if (c.err === 'not_registered' || c.err === 'rate' || c.err === 'nokey') break; continue; }
    const pm10 = airGrade(c.data.pm10, 'pm10'), pm25 = airGrade(c.data.pm25, 'pm25');
    if (!pm10 && !pm25) { lastErr = 'no_value'; continue; }   // 점검 중인 측정소면 다음 가까운 곳
    return out({ ok: true, mode: 'air', source: 'airkorea', station: { name: st.name, addr: st.addr, d: st.d }, dataTime: c.data.dataTime,
      pm10, pm25, at: new Date(c.at).toISOString(), age: Math.round((Date.now() - c.at) / 1000), cached: c.cached, stale: c.stale,
      note: '에어코리아 실시간 측정값(확정 전 자료) · 등급은 환경부 기준' }, 200, 300);
  }
  return airErr(lastErr || 'upstream');
}
function airErr(e) {
  if (e === 'not_registered') return out({ ok: false, error: 'not_registered', message: '에어코리아 서비스 활용신청이 필요해요 (공공데이터포털)' }, 503);
  if (e === 'no_value') return out({ ok: false, error: 'no_value', message: '가까운 측정소 값이 지금 없어요' }, 502);
  return errOut(e);
}


// ---------- 날씨 (기상청 단기예보 조회서비스, 공공데이터포털 15084084) ----------
// 초단기실황(기온·강수형태·습도) + 초단기예보(하늘상태). 같은 키(TAGO_KEY). 격자(약 5km)별 30분 캐시를 모두가 같이 씀.
const WX = 'https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/';
const WX_TTL = 1800, WX_KEEP = 3 * 3600;
// 기상청 격자 변환 (단기예보 활용가이드의 Lambert 정각원추도법, 5km 격자)
function wxGrid(lat, lng) {
  const RE = 6371.00877 / 5.0, D = Math.PI / 180, s1 = 30 * D, s2 = 60 * D, olon = 126 * D, olat = 38 * D;
  let sn = Math.tan(Math.PI * 0.25 + s2 * 0.5) / Math.tan(Math.PI * 0.25 + s1 * 0.5); sn = Math.log(Math.cos(s1) / Math.cos(s2)) / Math.log(sn);
  let sf = Math.tan(Math.PI * 0.25 + s1 * 0.5); sf = Math.pow(sf, sn) * Math.cos(s1) / sn;
  let ro = Math.tan(Math.PI * 0.25 + olat * 0.5); ro = RE * sf / Math.pow(ro, sn);
  let ra = Math.tan(Math.PI * 0.25 + lat * D * 0.5); ra = RE * sf / Math.pow(ra, sn);
  let th = lng * D - olon; if (th > Math.PI) th -= 2 * Math.PI; if (th < -Math.PI) th += 2 * Math.PI; th *= sn;
  return { nx: Math.floor(ra * Math.sin(th) + 43 + 0.5), ny: Math.floor(ro - ra * Math.cos(th) + 136 + 0.5) };
}
function kst(ms) { const d = new Date(ms + 9 * 3600e3); const p = n => String(n).padStart(2, '0');
  return { date: `${d.getUTCFullYear()}${p(d.getUTCMonth() + 1)}${p(d.getUTCDate())}`, hh: p(d.getUTCHours()), mm: d.getUTCMinutes() }; }
async function wxUp(env, op, params) {
  const k = String(env.TAGO_KEY || '').trim();
  if (!k) return { err: 'nokey' };
  const ek = k.includes('%') ? k : encodeURIComponent(k);
  const qs = new URLSearchParams({ ...params, dataType: 'JSON', pageNo: '1' }).toString();
  let lastErr = 'upstream';
  for (let i = 0; i < 2; i++) {
    let txt = '', st = 0;
    try { const r = await fetch(`${WX}${op}?serviceKey=${ek}&${qs}`, { headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(9000) }); st = r.status; txt = await r.text(); }
    catch (e) { lastErr = 'timeout'; continue; }
    const rc = (txt.match(/"returnReasonCode"\s*:\s*"?(\d+)/) || txt.match(/<returnReasonCode>(\d+)</) || [])[1];
    if (rc === '30' || rc === '20') return { err: 'not_registered' };
    if (rc) { lastErr = 'tago_' + (+rc); if (rc === '22') break; continue; }
    if (st === 401 || st === 403) return { err: 'not_registered' };
    let j; try { j = JSON.parse(txt); } catch (_) { lastErr = 'badjson'; continue; }
    const h = j && j.response && j.response.header, c = h ? String(+h.resultCode) : '';
    if (c === '3') return { err: 'no_data' };               // 아직 발표 전
    if (c && c !== '0') { lastErr = 'tago_' + c; if (c === '1' || c === '99') { await new Promise(r => setTimeout(r, 400)); continue; } break; }
    let it = j.response.body && j.response.body.items && j.response.body.items.item;
    if (!it) it = []; else if (!Array.isArray(it)) it = [it];
    return { list: it };
  }
  return { err: lastErr };
}
const WX_PTY = { 1: '비', 2: '비/눈', 3: '눈', 5: '빗방울', 6: '진눈깨비', 7: '눈날림' };
const WX_SKY = { 1: '맑음', 3: '구름많음', 4: '흐림' };
async function wxLoad(env, nx, ny) {
  const now = Date.now();
  // 초단기실황: 매시 정각 자료, 약 40분 뒤 나옴 / 초단기예보: 매시 30분 자료, 약 45분 뒤 나옴
  const tryN = async back => { const t = kst(now - (40 + back * 60) * 60e3);
    return { r: await wxUp(env, 'getUltraSrtNcst', { numOfRows: '20', base_date: t.date, base_time: t.hh + '00', nx: String(nx), ny: String(ny) }), base: t.date + t.hh + '00' }; };
  let n = await tryN(0); if (n.r.err === 'no_data' || (n.r.list && !n.r.list.length)) n = await tryN(1);
  if (n.r.err) return { err: n.r.err };
  const O = {}; n.r.list.forEach(x => { O[x.category] = x.obsrValue; });
  const temp = num(String(O.T1H ?? '')); if (!isFinite(temp)) return { err: 'empty' };
  let sky = null, ptyF = null;
  const tryF = async back => { const t = kst(now - (45 + back * 60) * 60e3);
    return wxUp(env, 'getUltraSrtFcst', { numOfRows: '60', base_date: t.date, base_time: t.hh + '30', nx: String(nx), ny: String(ny) }); };
  let f = await tryF(0); if (f.err === 'no_data' || (f.list && !f.list.length)) f = await tryF(1);
  if (!f.err) { const first = (cat) => f.list.filter(x => x.category === cat).sort((a, b) => (a.fcstDate + a.fcstTime).localeCompare(b.fcstDate + b.fcstTime))[0];
    const s = first('SKY'), p = first('PTY'); sky = s ? +s.fcstValue : null; ptyF = p ? +p.fcstValue : null; }
  const pty = +O.PTY || 0;
  const text = pty ? (WX_PTY[pty] || '강수') : (sky && WX_SKY[sky]) || (ptyF ? WX_PTY[ptyF] : '') || '';
  return { data: { base: n.base, temp, pty, sky, text, reh: num(String(O.REH ?? '')), rn1: num(String(O.RN1 ?? '')), wsd: num(String(O.WSD ?? '')) } };
}
async function wxNow(url, env, ctx) {
  const lat = num(url.searchParams.get('lat')), lng = num(url.searchParams.get('lng'));
  if (!(lat >= 33 && lat <= 39 && lng >= 124 && lng <= 132)) return bad('bad_location', '한국 안의 위도(lat)·경도(lng)를 주세요');
  const { nx, ny } = wxGrid(lat, lng);
  const c = await cached(ctx, `wx/${nx}_${ny}`, WX_TTL, WX_KEEP, () => wxLoad(env, nx, ny), true);
  if (c.err) {
    if (c.err === 'not_registered') return out({ ok: false, error: 'not_registered', message: '기상청 단기예보 조회서비스 활용신청이 필요해요 (공공데이터포털)' }, 503);
    return errOut(c.err);
  }
  const d = c.data, nf = v => (isFinite(v) ? v : null);
  return out({ ok: true, mode: 'weather', source: 'kma', grid: { nx, ny }, base: d.base, temp: d.temp, text: d.text, pty: d.pty, sky: d.sky,
    reh: nf(d.reh), rn1: nf(d.rn1), wsd: nf(d.wsd), at: new Date(c.at).toISOString(), age: Math.round((Date.now() - c.at) / 1000), cached: c.cached, stale: c.stale,
    note: '기상청 초단기실황·초단기예보' }, 200, 300);
}

// 교통수단별 모듈. 지하철(subway)·철도(rail)는 나중에 같은 모양(stations/arrivals)으로 붙임.
const MODES = {
  bus: { ready: true, routes: { stops, arrivals } },
  subway: { ready: false, routes: {} },   // 예정: /v1/subway/stations, /v1/subway/arrivals
  rail: { ready: false, routes: {} },     // 예정: /v1/rail/stations, /v1/rail/departures
  air: { ready: true, routes: { now: airNow } },   // v33: 미세먼지 /v1/air?lat=&lng=
  weather: { ready: true, routes: { now: wxNow } },   // v34: 날씨 /v1/weather?lat=&lng=
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
      examples: { stops: '/v1/bus/stops?lat=36.5684&lng=128.7294[&r=200]', arrivals: '/v1/bus/arrivals?city=37040&node=ADB354000407', air: '/v1/air?lat=36.5684&lng=128.7294', weather: '/v1/weather?lat=36.5684&lng=128.7294' },
      source: '국토교통부 TAGO · 안동시 BIS · 에어코리아 · 기상청 (공공데이터포털)' }, 200, 3600);
    const seg = p.split('/');            // ['', 'v1', mode, op]
    if (seg[1] !== 'v1') return bad('not_found', '없는 주소예요', 404);
    let mode = seg[2], op = seg[3];
    if (ALIAS[mode]) { op = mode; mode = 'bus'; }   // 예전 모양 /v1/stops, /v1/arrivals
    if ((mode === 'air' || mode === 'weather') && !op) op = 'now';           // /v1/air?lat=&lng=
    const M = MODES[mode];
    if (!M) return bad('not_found', '없는 주소예요', 404);
    if (!M.ready) return out({ ok: false, error: 'not_ready', mode, message: '아직 준비 중인 교통수단이에요' }, 501);
    const fn = M.routes[op];
    if (!fn || seg.length > 4 || (mode === 'air' && seg.length > 4)) return bad('not_found', '없는 주소예요', 404);
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
