/* 뉴니버스 음력·공휴일·24절기 (kcal.js)
 * 인터넷 없이 폰 안에서만 계산해요. 날짜 표(kcal-data.js)는 한국천문연구원 달력자료(월력요항)에서 가져왔고,
 * 표에 없는 날짜는 절대 지어내지 않아요. 2026~2030년만 있어요.
 * 날짜는 모두 '1970-01-01 부터 센 날수'(dn)로 다뤄서 시간대·서머타임에 흔들리지 않아요. */
(function(root,factory){if(typeof module==='object'&&module.exports)module.exports=factory();else root.NVCAL=factory()})(typeof self!=='undefined'?self:this,function(){
'use strict';
var DAY=864e5;
function dn(y,m,d){return Math.floor(Date.UTC(y,m-1,d)/DAY)}
function ymd(n){var t=new Date(n*DAY);return {y:t.getUTCFullYear(),m:t.getUTCMonth()+1,d:t.getUTCDate()}}
function wd(n){return(((n+4)%7)+7)%7}                      // 0=일 … 6=토 (1970-01-01 은 목요일)
function p2(n){return(n<10?'0':'')+n}
function iso(n){var o=ymd(n);return o.y+'-'+p2(o.m)+'-'+p2(o.d)}
function parseIso(s){var m=/^(\d{4})-(\d{2})-(\d{2})/.exec(s);return m?dn(+m[1],+m[2],+m[3]):NaN}
var WD=['일','월','화','수','목','금','토'];
function hasBatchim(s){s=String(s||'').replace(/\([^)]*\)\s*$/,'');var c=s.charCodeAt(s.length-1);if(c>=0xAC00&&c<=0xD7A3)return(c-0xAC00)%28!==0;return /[0136-8]$/.test(s)}   // 숫자 1,3,6,7,8 은 받침 소리(일·삼·육·칠·팔)
function josa(w,pair){return w+(hasBatchim(w)?pair[0]:pair[1])}   // josa('추석',['은','는'])
function josaRo(w){var c=String(w||'').charCodeAt(String(w||'').length-1);var b=hasBatchim(w);if(b&&c>=0xAC00&&c<=0xD7A3&&(c-0xAC00)%28===8)b=false;return w+(b?'으로':'로')}
var TERM_NAMES=['소한','대한','입춘','우수','경칩','춘분','청명','곡우','입하','소만','망종','하지','소서','대서','입추','처서','백로','추분','한로','상강','입동','소설','대설','동지'];

/* ---------- 공휴일 규칙 (관공서의 공휴일에 관한 규정 제2·3조) ---------- */
// elig: wk=토·일·다른 공휴일과 겹치면 대체공휴일 / sun=설·추석 연휴: 일요일이나 다른 공휴일과 겹칠 때만 / none=없음(1월 1일·현충일·선거일)
var FIXED=[
  ['01-01','새해 첫날','new','none',0],['03-01','삼일절','mar1','wk',0],['05-01','노동절','labor','wk',2026],['05-05','어린이날','child','wk',0],
  ['06-06','현충일','memorial','none',0],['07-17','제헌절','constitution','wk',2026],['08-15','광복절','liberation','wk',0],
  ['10-03','개천절','foundation','wk',0],['10-09','한글날','hangul','wk',0],['12-25','성탄절','xmas','wk',0]];
// 선거일은 선거관리위원회·월력요항이 정해 알린 날만 넣어요 (2030년 선거일은 아직 공고 전이라 넣지 않음)
var ELECTIONS=[['2026-06-03','지방선거일','kasi'],['2028-04-12','국회의원선거일','kasi']];
var GROUP_LABEL={seol:'설날',chuseok:'추석',mar1:'삼일절',labor:'노동절',child:'어린이날',constitution:'제헌절',liberation:'광복절',foundation:'개천절',hangul:'한글날',xmas:'성탄절',buddha:'부처님오신날'};

function make(D){
  var C={data:D};
  /* ---------- 음력 표 ---------- */
  var MS=[],ly=null;
  D.months.forEach(function(r,i){var s=parseIso(r[0]),m=r[1],leap=!!r[2];
    if(ly===null)ly=2025;else if(m===1&&!leap)ly++;
    MS.push({s:s,m:m,leap:leap,len:r[3],y:ly})});
  var FIRST=MS.length?MS[0].s:0,LAST=MS.length?MS[MS.length-1].s+MS[MS.length-1].len-1:0;
  C.lunarRange=function(){return {from:FIRST,to:LAST}};
  C.solarToLunar=function(y,m,d){var n=typeof y==='number'&&m===undefined?y:dn(y,m,d);
    if(!MS.length||n<FIRST||n>LAST)return null;
    var lo=0,hi=MS.length-1;while(lo<hi){var mid=(lo+hi+1)>>1;if(MS[mid].s<=n)lo=mid;else hi=mid-1}
    var e=MS[lo];return {y:e.y,m:e.m,d:n-e.s+1,leap:e.leap,len:e.len}};
  C.lunarMonth=function(ly_,lm,leap){for(var i=0;i<MS.length;i++){var e=MS[i];if(e.y===ly_&&e.m===lm&&e.leap===!!leap)return e}return null};
  C.lunarToSolar=function(ly_,lm,ld,leap){var e=C.lunarMonth(ly_,lm,leap);if(!e||ld<1||ld>e.len)return null;return e.s+ld-1};
  C.lunarMonthsAll=function(){return MS.slice()};
  C.hasLeap=function(ly_,lm){return !!C.lunarMonth(ly_,lm,true)};
  /* ---------- 음력 생일·기념일 → 해마다 양력 ---------- */
  // 윤달 날짜는 그 해에 같은 윤달이 있으면 윤달에, 없으면 평달에 맞추고, 29일뿐인 달의 30일은 29일로 맞춰요.
  C.solarFor=function(ev,lyear){var e=null;if(ev.leap)e=C.lunarMonth(lyear,ev.lm,true);var adjLeap=false;if(!e){e=C.lunarMonth(lyear,ev.lm,false);adjLeap=!!ev.leap}
    if(!e)return null;var ld=ev.ld,adj=false;if(ld>e.len){ld=e.len;adj=true}return {n:e.s+ld-1,adj:adj,leapAdj:adjLeap,ld:ld}};
  C.occurrences=function(ev,fromN,toN){var out=[],a=ymd(fromN).y-1,b=ymd(toN).y;
    for(var y=a;y<=b;y++){var s=C.solarFor(ev,y);if(s&&s.n>=fromN&&s.n<=toN)out.push({n:s.n,adj:s.adj,leapAdj:s.leapAdj,ly:y})}
    out.sort(function(x,y){return x.n-y.n});return out};
  C.nextOccurrence=function(ev,fromN){var r=C.occurrences(ev,fromN,fromN+800);return r[0]||null};
  /* ---------- 공휴일 ---------- */
  var H={},HK=Object.keys(D.hol||{});
  HK.forEach(function(k){var v=D.hol[k];H[parseIso(k)]={name:v[0],kind:v[1],group:v[2],src:v[3]}});
  C.holiday=function(n){return H[n]||null};
  C.isHoliday=function(n){return !!H[n]};
  C.range=function(){return {from:parseIso(D.range[0]),to:parseIso(D.range[1])}};
  C.inRange=function(n){var r=C.range();return n>=r.from&&n<=r.to};
  C.holidayList=function(fromN,toN){var o=[];for(var n=fromN;n<=toN;n++)if(H[n])o.push({n:n,h:H[n]});return o};
  // 공휴일로 이어지는 덩어리(토·일도 같이 쉬는 날로 이어 붙임). 반환: {a,b,days(쉬는 날 수),names[]}
  C.run=function(n){if(!H[n])return null;var a=n,b=n;
    function off(x){return !!H[x]||wd(x)===0||wd(x)===6}
    while(off(a-1))a--;while(off(b+1))b++;
    var names=[],seen={};for(var x=a;x<=b;x++)if(H[x]){var k=H[x].group||H[x].name;var nm=H[x].kind==='sub'?H[x].name:(H[x].group==='seol'?'설날':H[x].group==='chuseok'?'추석':H[x].name);if(!seen[nm]){seen[nm]=1;names.push(nm)}}
    return {a:a,b:b,days:b-a+1,names:names}};
  C.nextHoliday=function(fromN,incl){for(var n=fromN+(incl?0:1);n<=C.range().to;n++)if(H[n]&&!(H[n].kind==='elec'&&false))return {n:n,h:H[n]};return null};
  // 이름으로 찾기: 설날·추석은 연휴 가운데(설날·추석 당일) 날을 대표로 삼아요
  C.holidayByName=function(key,fromN){ // key: group 이름 또는 'sub'
    var best=null;for(var n=fromN;n<=C.range().to;n++){var h=H[n];if(!h)continue;
      var ok=(h.group===key)&&(key==='seol'||key==='chuseok'?h.name===(key==='seol'?'설날':'추석'):true)&&h.kind!=='sub';if(ok){best=n;break}}
    return best};
  /* 연휴 범위 (설·추석): 전날·당일·다음날 */
  C.groupSpan=function(n){var h=H[n];if(!h)return null;var a=n,b=n;while(H[a-1]&&H[a-1].group===h.group&&H[a-1].kind!=='sub')a--;while(H[b+1]&&H[b+1].group===h.group&&H[b+1].kind!=='sub')b++;return {a:a,b:b}};
  C.subFor=function(n){var h=H[n];if(!h)return null;var sp=C.groupSpan(n),x=sp.b+1;var out=[];
    for(;x<=sp.b+8;x++){if(H[x]&&H[x].kind==='sub')out.push(x);else if(!H[x]&&wd(x)!==0&&wd(x)!==6)break;}
    return out};
  /* ---------- 24절기 ---------- */
  var TL=[];Object.keys(D.terms||{}).forEach(function(y){D.terms[y].forEach(function(t){TL.push({name:t[0],n:parseIso(t[1]),time:t[2],src:t[3]})})});TL.sort(function(a,b){return a.n-b.n||0});
  C.terms=TL;
  C.termOn=function(n){for(var i=0;i<TL.length;i++)if(TL[i].n===n)return TL[i];return null};
  C.termsCovered=function(){return TL.length?{from:TL[0].n,to:TL[TL.length-1].n}:null};
  C.nextTerm=function(fromN,incl){for(var i=0;i<TL.length;i++)if(TL[i].n>=fromN+(incl?0:1))return TL[i];return null};
  C.prevTerm=function(fromN){for(var i=TL.length-1;i>=0;i--)if(TL[i].n<=fromN)return TL[i];return null};
  C.termByName=function(name,fromN){for(var i=0;i<TL.length;i++)if(TL[i].name===name&&TL[i].n>=fromN)return TL[i];return null};
  /* ---------- 명절·절기 풍속 날 (음력으로 정해지는 날 + 달력자료에 있는 한식·삼복) ---------- */
  var MISC={};Object.keys(D.misc||{}).forEach(function(y){D.misc[y].forEach(function(r){var n=parseIso(r[1]);(MISC[n]=MISC[n]||[]).push(r[0])})});
  var LUNAR_DAYS=[['정월대보름',1,15],['단오',5,5],['칠석',7,7]];
  C.customDays=function(n){var o=[];if(MISC[n])MISC[n].forEach(function(x){if(!LUNAR_DAYS.some(function(l){return l[0]===x}))o.push(x)});
    var L=C.solarToLunar(n);if(L&&!L.leap)LUNAR_DAYS.forEach(function(l){if(L.m===l[1]&&L.d===l[2])o.push(l[0])});
    if(L&&!L.leap&&L.m===12&&L.d===L.len)o.push('섣달그믐');return o};
  C.customByName=function(name,fromN){var to=Math.max(C.range().to,LAST);for(var n=fromN;n<=to;n++){var c=C.customDays(n);if(c.indexOf(name)>=0)return n}return null};
  /* 기념일(날짜 고정, 달력자료 2026~2028이 모두 같은 날) */
  var DAYS={};(D.days||[]).forEach(function(r){var k=r[1]*100+r[2];(DAYS[k]=DAYS[k]||[]).push(r[0])});
  C.memorialDays=function(n){var o=ymd(n);return (DAYS[o.m*100+o.d]||[]).filter(function(x){return x!=='현충일'&&x!=='노동절'&&x!=='어린이 날'||false}).slice()};
  C.memorialsAll=function(n){var o=ymd(n);return (DAYS[o.m*100+o.d]||[]).slice()};
  C.memorialByName=function(name,fromN){var to=C.range().to;for(var n=fromN;n<=to;n++){if(C.memorialsAll(n).indexOf(name)>=0)return n}return null};
  C.memorialNames=function(){var o={};Object.keys(DAYS).forEach(function(k){DAYS[k].forEach(function(x){o[x]=1})});return Object.keys(o)};
  C.specials=function(n){return {holiday:H[n]||null,term:C.termOn(n),custom:C.customDays(n),memorial:C.memorialDays(n),lunar:C.solarToLunar(n)}};
  /* ---------- 보기 좋은 글 ---------- */
  C.dLabel=function(n,today){var o=ymd(n),k=today==null?null:n-today;var base=o.m+'월 '+o.d+'일('+WD[wd(n)]+')';return base};
  C.rel=function(n,today){var k=n-today;if(k===0)return '오늘';if(k===1)return '내일';if(k===2)return '모레';if(k===-1)return '어제';if(k===-2)return '그저께';return null};
  C.lunarLabel=function(L){if(!L)return '';return '음력 '+(L.leap?'윤':'')+L.m+'월 '+L.d+'일'};
  C.after=function(n,today){var k=n-today;if(k===0)return '오늘';if(k===1)return '내일';if(k>0)return k+'일 뒤';if(k===-1)return '어제';return Math.abs(k)+'일 전'};
  return C}

/* ---------- 규칙으로 공휴일 만들기 (표를 만들 때와 시험에서만 써요. 폰에서는 표만 읽어요) ---------- */
function ruleHolidays(C,year){
  var groups=[],H={};   // groups: {name,group,elig,days:[dn]}
  FIXED.forEach(function(f){if(f[4]&&year<f[4])return;var m=f[0].split('-');groups.push({name:f[1],group:f[2],elig:f[3],days:[dn(year,+m[0],+m[1])]})});
  var seol=C.lunarToSolar(year,1,1);if(seol!=null)groups.push({name:'설날',group:'seol',elig:'sun',days:[seol-1,seol,seol+1]});
  var bud=C.lunarToSolar(year,4,8);if(bud!=null)groups.push({name:'부처님오신날',group:'buddha',elig:'wk',days:[bud]});
  var chu=C.lunarToSolar(year,8,15);if(chu!=null)groups.push({name:'추석',group:'chuseok',elig:'sun',days:[chu-1,chu,chu+1]});
  var out={},cover={};
  groups.forEach(function(g){g.days.forEach(function(d){(cover[d]=cover[d]||[]).push(g);out[d]={name:(g.group==='seol'||g.group==='chuseok')?(d===g.days[1]?g.name:g.name+' 연휴'):g.name,kind:'h',group:g.group}})});
  ELECTIONS.forEach(function(e){var d=parseIso(e[0]);if(ymd(d).y===year){out[d]={name:e[1],kind:'elec',group:'election'};}});
  // 겹침 세기: 날마다 1건
  var confl=[];Object.keys(cover).map(Number).sort(function(a,b){return a-b}).forEach(function(d){var gs=cover[d],w=wd(d),need=false;
    gs.forEach(function(g){if(g.elig==='wk'&&(w===0||w===6))need=true;if(g.elig==='sun'&&w===0)need=true;if(g.elig!=='none'&&gs.length>1&&w!==0&&w!==6)need=true});
    if(need)confl.push({d:d,gs:gs})});
  var used={};
  confl.forEach(function(c){var anchor=c.d;c.gs.forEach(function(g){var last=g.days[g.days.length-1];if(last>anchor)anchor=last});
    var x=anchor+1;for(;;x++){if(out[x]||used[x])continue;if(wd(x)===0||wd(x)===6)continue;break}
    used[x]=1;var lab=null;c.gs.forEach(function(g){if(g.elig==='wk')lab=g.group});if(!lab)lab=c.gs[0].group;
    out[x]={name:c.gs.length>1?'대체공휴일':'대체공휴일('+(GROUP_LABEL[lab]||'')+')',kind:'sub',group:lab}});
  return out}

/* ---------- 말·글로 물어보기 ---------- */
function normQ(raw){return String(raw||'').toLowerCase().replace(/[\s?!.,~·…'’"“”]+/g,'')}
var KO_M={'십이':12,'십일':11,'시':10,'십':10,'일':1,'이':2,'삼':3,'사':4,'오':5,'유':6,'육':6,'칠':7,'팔':8,'구':9};
var KO_D1={'일':1,'이':2,'삼':3,'사':4,'오':5,'육':6,'칠':7,'팔':8,'구':9};
function koDay(w){if(/^[일이삼사오육칠팔구]$/.test(w))return KO_D1[w];var m=/^([이삼])?십([일이삼사오육칠팔구])?$/.exec(w);if(!m)return null;return(m[1]?KO_D1[m[1]]:1)*10+(m[2]?KO_D1[m[2]]:0)}
// 「삼월 오일」처럼 한글 숫자로 들린 날짜를 숫자로 (삼일절·이번 월요일 같은 말은 건드리지 않음)
function koDates(s){s=String(s||'');
  s=s.replace(/(?:(?<![가-힣])|(?<=[력윤]))(십이|십일|시|십|일|이|삼|사|오|유|육|칠|팔|구)\s*월(?![가-힣]*요일)/g,function(a,w){return(KO_M[w]||w)+'월'});
  s=s.replace(/(?:(?<![가-힣])|(?<=[월력윤\d]))((?:[이삼])?십[일이삼사오육칠팔구]?|[일이삼사오육칠팔구])\s*일(?!절|요일|반|부|본|단|상|찍|정|어|자|수|등)/g,function(a,w){var v=koDay(w);return v?v+'일':a});
  return s}
function mdOf(s){var m=/(\d{1,2})월(\d{1,2})일/.exec(s);return m?[+m[1],+m[2]]:null}
var QW_WHEN='(언제|며칠|몇일|몇월|날짜|알려|뭐야|몇일이|며칠이|언제야|언제지|언제임|언제인지|언제에요|언제예요)';
var NAME_ALIAS=[ // [별명들, 종류, 키]
  [['설날','설연휴','설','구정','음력설'],'hol','seol'],[['추석','한가위','중추절','추석연휴'],'hol','chuseok'],
  [['부처님오신날','부처님오신','석가탄신일','석가탄신','초파일','사월초파일','부처님오신날'],'hol','buddha'],
  [['어린이날'],'hol','child'],[['광복절'],'hol','liberation'],[['삼일절','3.1절','3·1절','31절'],'hol','mar1'],[['개천절'],'hol','foundation'],
  [['한글날'],'hol','hangul'],[['현충일'],'hol','memorial'],[['제헌절'],'hol','constitution'],[['노동절','근로자의날','메이데이'],'hol','labor'],
  [['성탄절','크리스마스','성탄','기독탄신일','크리스마스이브'],'hol','xmas'],[['신정','새해첫날','1월1일'],'hol','new'],
  [['정월대보름','대보름'],'cus','정월대보름'],[['단오','수릿날'],'cus','단오'],[['칠석'],'cus','칠석'],[['한식'],'cus','한식'],
  [['초복'],'cus','초복'],[['중복'],'cus','중복'],[['말복'],'cus','말복'],[['섣달그믐','제석'],'cus','섣달그믐'],[['복날','삼복'],'cus','복날']];
function nameLookup(s,C){ // s: 공백 뺀 글. 앞에서부터 이름이 나오고 뒤에 '언제' 같은 말이 와야 함
  var rest0=s,yoff=0,ymatch=/^(\d{4})년/.exec(rest0),year=null;
  if(ymatch){year=+ymatch[1];rest0=rest0.slice(ymatch[0].length)}
  else{var m=/^(올해|금년|내년|명년|내후년|작년|돌아오는|다가오는|이번|올)/.exec(rest0);if(m){var w=m[1];if(w==='내년'||w==='명년')yoff=1;else if(w==='내후년')yoff=2;else if(w==='작년')yoff=-1;rest0=rest0.slice(w.length);if(w!=='돌아오는'&&w!=='다가오는'&&w!=='이번'&&w!=='올')yoff=yoff;}
    if(m&&(m[1]==='올해'||m[1]==='금년'||m[1]==='올'))yoff=0}
  var cand=[];
  NAME_ALIAS.forEach(function(r){r[0].forEach(function(a){cand.push([normQ(a),r[1],r[2],a])})});
  C.terms.slice(0,24).forEach(function(t){cand.push([t.name,'term',t.name,t.name])});
  C.memorialNames().forEach(function(nm){var k=normQ(nm);if(k.length>=3)cand.push([k,'mem',nm,nm])});
  cand.sort(function(a,b){return b[0].length-a[0].length});
  for(var i=0;i<cand.length;i++){var c=cand[i];if(rest0.indexOf(c[0])===0){var rest=rest0.slice(c[0].length);
      if(!rest.length)return {k:c[1],key:c[2],label:c[3],year:year,yoff:yoff,alone:true};
      if(new RegExp('^(은|는|이|가|도|엔|에|날)?'+QW_WHEN).test(rest))return {k:c[1],key:c[2],label:c[3],year:year,yoff:yoff,alone:false}}}
  return null}
function parseQ(raw,C){var q0=koDates(raw),s=normQ(q0);if(!s||s.length<2)return null;
  if(/검색|찾아줘|찾아봐/.test(s))return null;
  var md=mdOf(s);
  var hasLunarDate=/음력(윤)?\d{1,2}월\d{1,2}일/.exec(s);
  var kindWord=/(생일|생신|기일|제사|기념일|결혼기념|환갑|칠순|팔순|돌잔치)/.exec(s);
  var addVerb=/(등록|저장|기억|추가|알림|알려줘|챙겨|넣어|적어|메모|잊지)/.test(s);
  // 1) 음력 생일 등록
  if(hasLunarDate&&kindWord&&addVerb&&!/언제|며칠|몇일|양력으로|양력|변환|몇월/.test(s)){
    var mm=/음력(윤)?(\d{1,2})월(\d{1,2})일/.exec(s),lm=+mm[2],ld=+mm[3],leap=!!mm[1];
    var nm=q0.replace(/음력\s*윤?\s*\d{1,2}\s*월\s*\d{1,2}\s*일/,' ').replace(/(등록|저장|기억|추가|알림|알려줘|알려|챙겨|넣어|적어|메모|잊지|말고|해줘|해|줘|좀|으로|를|을|에|도|은|는|이|가|마다|해마다|매년|매해)/g,' ').replace(/\s+/g,' ').trim();
    return {t:'add',lm:lm,ld:ld,leap:leap,label:nm||(kindWord?kindWord[1]:''),sc:152,auto:true,tile:'음력 날짜 등록'}}
  if(/음력/.test(s)&&kindWord&&addVerb&&!hasLunarDate&&!/언제|며칠|목록/.test(s))return {t:'form',sc:150,auto:true,tile:'음력 날짜 등록 화면'};
  if(/음력/.test(s)&&kindWord&&/(목록|리스트|뭐뭐|뭐있|등록한|저장한|내음력|보여|언제야|언제지|알려)/.test(s)&&!hasLunarDate)return {t:'evlist',sc:150,auto:true,tile:'등록한 음력 날짜 보기'};
  // 2) 음력 → 양력
  if(hasLunarDate&&/(양력|언제|며칠|몇일|변환|바꿔|몇월|날짜)/.test(s)){var m2=/음력(윤)?(\d{1,2})월(\d{1,2})일/.exec(s);return {t:'l2s',lm:+m2[2],ld:+m2[3],leap:!!m2[1],sc:150,auto:true,tile:'음력을 양력으로'}}
  // 윤달
  if(/윤달/.test(s)&&/(언제|있어|몇월|올해|내년|다음|며칠|알려|언제야)/.test(s)&&!hasLunarDate)return {t:'leap',sc:148,auto:true,tile:'윤달 언제인지 보기'};
  // 3) 절기
  if(/절기/.test(s)&&/(오늘|지금|다음|무슨|뭐|언제|어떤|이번)/.test(s)&&!/절기별/.test(s))return {t:'term',sc:148,auto:true,tile:'지금 절기 보기'};
  // 4) 이름 + 언제
  var nl=nameLookup(s,C);if(nl&&!nl.alone)return {t:'when',name:nl,sc:150,auto:true,tile:nl.label+' 언제인지 보기'};
  // 5) 음력 며칠
  if(/음력/.test(s)&&/(며칠|몇일|몇월|날짜|오늘|지금|내일|어제|모레|글피|무슨|뭐|알려)/.test(s)&&!hasLunarDate){var ref=dateRef(s,md);return {t:'lunar',ref:ref,sc:150,auto:true,tile:'음력 날짜 보기'}}
  // 대체공휴일·임시공휴일 (임시는 정부가 발표해야 알 수 있음)
  if(/(대체)(공휴일|휴일)/.test(s)&&/(언제|있어|알려|다음|뭐|며칠|보여|\?|$)/.test(s))return {t:'sub',sc:150,auto:true,tile:'대체공휴일 보기'};
  if(/(임시)(공휴일|휴일)/.test(s)&&/(언제|있어|알려|다음|뭐|며칠|보여|\?|$)/.test(s))return {t:'temp',sc:150,auto:true,tile:'임시공휴일 알아보기'};
  // 6) 다음 공휴일·목록
  if(/(다음|다음번|담|가장가까운|제일가까운|앞으로|곧)(공휴일|휴일|연휴|쉬는날|빨간날|명절)/.test(s)||/(공휴일|휴일|빨간날|쉬는날|연휴)(이?언제|뭐|있어|알려|며칠|몇일|보여)/.test(s)&&!/(이번달|[0-9]+월|올해|내년|한달)/.test(s)||/언제쉬(어|지|나|는)|언제놀(아|지)/.test(s))return {t:'next',sc:150,auto:true,tile:'다음 공휴일 보기'};
  var lm1=/(이번달|이달|다음달|지난달|(\d{1,2})월)(의)?(공휴일|휴일|빨간날|쉬는날|연휴)/.exec(s),ly1=/(올해|금년|내년|명년|(\d{4})년)(의)?(공휴일|휴일|빨간날|쉬는날|연휴)/.exec(s);
  if(lm1||ly1){var ml=null,yl=null;if(lm1){ml=lm1[1]==='이번달'||lm1[1]==='이달'?0:lm1[1]==='다음달'?1:lm1[1]==='지난달'?-1:+lm1[2];if(lm1[2]){ml={abs:+lm1[2]}}}
    if(ly1){yl=ly1[1]==='내년'||ly1[1]==='명년'?1:ly1[1]==='올해'||ly1[1]==='금년'?0:{abs:+ly1[2]}}
    return {t:'list',ml:lm1?ml:undefined,yl:ly1?yl:undefined,sc:150,auto:true,tile:'공휴일 목록 보기',hol:true}}
  // 7) 오늘 무슨 날
  var dayQ=/(무슨|뭔|어떤)(날|일)/.test(s)||/(공휴일|휴일|빨간날|쉬는날|쉬는날이)(이야|인가|인지|이니|일까|이냐|맞|이에요|이예요)/.test(s)||/(오늘|내일|모레|어제|글피)(은|는|이)?(공휴일|휴일|빨간날|쉬는날)/.test(s)||/(오늘|내일|모레|어제|글피)(이?)(며칠|몇일|몇월|몇일이|날짜|무슨요일|요일)/.test(s)||/(오늘|내일|모레|어제)(은|는|이)?(양력|음력)?(며칠|몇일)/.test(s)||(md&&/(무슨|뭔|며칠|음력|양력|어떤|공휴일|휴일)/.test(s)&&!/음력.*\d{1,2}월\d{1,2}일/.test(s)&&/(날|일|공휴일|음력|휴일)/.test(s));
  if(dayQ){var rf=dateRef(s,md);return {t:'day',ref:rf,sc:150,auto:true,tile:'오늘 무슨 날인지 보기'.replace('오늘',rf&&rf.rel?rf.rel:'그날')}}
  // 8) 낱말만(자동으로 열지 않음)
  if(/^(공휴일|휴일|빨간날|연휴|명절)$/.test(s))return {t:'next',sc:80,auto:false,tile:'다음 공휴일 보기'};
  if(/^(음력|음력날짜|절기|24절기|이십사절기)$/.test(s))return {t:/절기/.test(s)?'term':'lunar',ref:{rel:'오늘',off:0},sc:80,auto:false,tile:/절기/.test(s)?'지금 절기 보기':'오늘 음력 보기'};
  if(nl&&nl.alone&&(nl.k==='hol'&&(nl.key==='seol'||nl.key==='chuseok')||nl.k==='term'&&nl.label.length>=2&&['동지','입춘','하지','입추','입동','입하','춘분','추분'].indexOf(nl.label)>=0))return {t:'when',name:nl,sc:60,auto:false,tile:nl.label+' 언제인지 보기'};
  return null}
function dateRef(s,md){
  if(/그저께|엊그제/.test(s))return {off:-2,rel:'그저께'};if(/어제/.test(s))return {off:-1,rel:'어제'};if(/모레/.test(s))return {off:2,rel:'모레'};if(/글피/.test(s))return {off:3,rel:'글피'};
  if(/내일|낼/.test(s)&&!/내일모레/.test(s))return {off:1,rel:'내일'};if(/내일모레/.test(s))return {off:2,rel:'모레'};
  if(md)return {md:md};
  return {off:0,rel:'오늘'}}
function refToN(ref,today){if(!ref)return today;if(ref.md){var y=ymdOfN(today).y,n=dn(y,ref.md[0],ref.md[1]);if(ref.md[1]<1||ref.md[1]>31)return null;if(n<today-30)n=dn(y+1,ref.md[0],ref.md[1]);return n}return today+(ref.off||0)}
function ymdOfN(n){return ymd(n)}
/* ---------- 답 만들기 ---------- */
function fmt(n,today,o){o=o||{};var d=ymd(n),y=today==null?d.y:ymd(today).y;return (d.y!==y||o.year?d.y+'년 ':'')+d.m+'월 '+d.d+'일'+(o.noWd?'':'('+WD[wd(n)]+')')}
function md_(n){var d=ymd(n);return d.m+'월 '+d.d+'일'}
function relWord(n,today){var k=n-today;return k===0?'오늘':k===1?'내일':k===2?'모레':k===-1?'어제':k===-2?'그저께':null}
function spk(nm){return String(nm).replace(/^대체공휴일\((.+)\)$/,'$1 대체공휴일')}
function yw(n,today){var d=ymd(n).y-ymd(today).y;return d===0?'':d===1?'내년 ':d===-1?'작년 ':ymd(n).y+'년 '}
function eun(w){return josa(w,['은','는'])}
function ida(w){return w+(hasBatchim(w)?'이에요':'예요')}
function afterTxt(n,today){var k=n-today;return k===0?'오늘':k===1?'내일':k>1?k+'일 뒤':k===-1?'어제':(-k)+'일 전'}
function runTxt(C,n){var r=C.run(n);if(!r||r.days<2)return null;return {txt:r.days+'일 연속 쉬어요',a:r.a,b:r.b,days:r.days}}
function spanTxt(C,n,today){var sp=C.groupSpan(n);if(!sp||sp.a===sp.b)return null;return md_(sp.a)+'('+WD[wd(sp.a)]+')~'+(ymd(sp.b).m!==ymd(sp.a).m?md_(sp.b):ymd(sp.b).d+'일')+'('+WD[wd(sp.b)]+')'}
function holName(h){return h?h.name:''}
function termTime(t){return t&&t.src==='kasi'&&t.time?' '+(+t.time.slice(0,2))+'시 '+(+t.time.slice(3))+'분':''}
function nextHolRow(C,today){var h=C.nextHoliday(today,false);if(!h)return null;var r=C.run(h.n);return {n:h.n,h:h.h,run:r}}
function holLine(C,n,today,withName){var h=C.holiday(n);var nm=h.group==='seol'?'설날 연휴':h.group==='chuseok'?'추석 연휴':h.name;return fmt(n,today)+' '+nm}
var OUT='달력 표는 2026~2030년까지만 있어요';
function answer(C,it,today,store){
  var R={ic:'calendar',b:'',s:'',rows:[],say:'',meta:'한국천문연구원 달력자료 · 폰 안에서 계산'};
  var rg=C.range();
  function outOf(n){return n<rg.from||n>rg.to}
  if(it.t==='day'){var n=refToN(it.ref,today);
    if(n==null||outOf(n)){R.b=OUT;R.s='그 날짜는 표에 없어요';R.say=R.b;R.ic='calendar';return R}
    var sp=C.specials(n),h=sp.holiday,rel=relWord(n,today),L=sp.lunar,ylab=(rel?'':(ymd(n).y!==ymd(today).y?ymd(n).y+'년 ':'')),lab=rel?rel:ylab+md_(n),head=eun(lab);
    var names=[];if(h)names.push(h.kind==='sub'?h.name:(h.name));
    if(sp.term)names.push(sp.term.name+' 절기'+termTime(sp.term));
    sp.custom.forEach(function(x){names.push(x)});
    var mem=sp.memorial;mem.forEach(function(x){names.push(x)});
    var title;
    if(h)title=head+' '+ida(h.name);
    else if(sp.term)title=head+' '+ida(sp.term.name);
    else if(sp.custom.length)title=head+' '+ida(sp.custom[0]);
    else if(mem.length)title=head+' '+ida(mem[0]);
    else title=head+' '+(rel?md_(n)+' ':'')+WD[wd(n)]+'요일이에요';
    R.b=title;R.ic=h?'calendar':'moon';
    var sm=[];if(rel)sm.push(md_(n)+'('+WD[wd(n)]+')');else sm.push(WD[wd(n)]+'요일');
    if(L)sm.push(C.lunarLabel(L)+(L.leap?' (윤달)':''));
    R.s=sm.join(' · ');
    if(h){R.rows.push({t:'공휴일',s:h.name+(h.src==='calc'?' · 규정 기준':h.src==='kasi-prelim'?' · 월력요항 발표 전':'')});var rn=runTxt(C,n);if(rn)R.rows.push({t:'연휴',s:md_(rn.a)+'('+WD[wd(rn.a)]+')~'+md_(rn.b)+'('+WD[wd(rn.b)]+') · '+rn.txt})}
    else if(!h){R.rows.push({t:'공휴일',s:wd(n)===0?'일요일이라 쉬는 날이에요':'아니에요'})}
    if(sp.term)R.rows.push({t:'절기',s:sp.term.name+termTime(sp.term)});
    if(sp.custom.length)R.rows.push({t:'명절·풍속',s:sp.custom.join(' · ')});
    if(mem.length)R.rows.push({t:'기념일',s:mem.slice(0,3).join(' · ')+(mem.length>3?' 외 '+(mem.length-3)+'개':'')});
    var nx=nextHolRow(C,n);if(nx&&!h)R.rows.push({t:'다음 공휴일',s:holLine(C,nx.n,today)+' · '+afterTxt(nx.n,today)});
    var say;
    say=eun(lab)+' '+WD[wd(n)]+'요일이고, 음력으로는 '+(L?(L.leap?'윤 ':'')+L.m+'월 '+L.d+'일':'표에 없는 날')+'이에요.';
    if(h)say+=' '+josa(spk(h.name),['이라','라'])+' 공휴일이에요.';else if(wd(n)===0)say+=' 일요일이에요.';else say+=' 공휴일은 아니에요.';
    if(sp.term)say+=' 절기로는 '+sp.term.name+'이에요.';
    if(sp.custom.length)say+=' '+sp.custom.join(', ')+'이에요.';
    if(mem.length)say+=' '+mem.slice(0,2).join(', ')+'이에요.';
    if(!h&&nx)say+=' 다음 공휴일은 '+md_(nx.n)+' '+ida(spk(nx.h.name))+'.';
    R.say=say;return R}
  if(it.t==='lunar'){var n2=refToN(it.ref,today);var L2=n2==null?null:C.solarToLunar(n2);
    if(!L2){R.b=OUT;R.s='음력 표는 '+fmt(C.lunarRange().from,today,{noWd:true,year:true})+'부터 '+fmt(C.lunarRange().to,today,{noWd:true,year:true})+'까지만 있어요';R.say=R.b;R.ic='moon';return R}
    var rel2=relWord(n2,today),lab2=rel2||md_(n2);
    R.ic='moon';
    R.b=eun(lab2)+' 음력 '+(L2.leap?'윤':'')+L2.m+'월 '+L2.d+'일이에요';
    R.s=md_(n2)+'('+WD[wd(n2)]+') · '+(L2.leap?'윤달 · ':'')+(L2.len===30?'큰달 30일':'작은달 29일')+'까지';
    var tm=C.termOn(n2);if(tm)R.rows.push({t:'절기',s:tm.name+termTime(tm)});
    var h2=C.holiday(n2);if(h2)R.rows.push({t:'공휴일',s:h2.name});
    var cu=C.customDays(n2);if(cu.length)R.rows.push({t:'명절·풍속',s:cu.join(' · ')});
    R.say=eun(lab2)+' 음력으로 '+(L2.leap?'윤 ':'')+L2.m+'월 '+L2.d+'일이에요'+(L2.leap?'. 이 달은 윤달이에요':'')+'.';return R}
  if(it.t==='term'){var t0=C.termOn(today),pv=C.prevTerm(today),nx2=C.nextTerm(today,false);R.ic='sun';
    if(!pv&&!nx2){R.b=OUT;R.say=R.b;return R}
    if(t0){R.b='오늘은 '+ida(t0.name);R.s=md_(t0.n)+'('+WD[wd(t0.n)]+')'+termTime(t0)}
    else if(pv){R.b='지금은 '+pv.name+' 절기예요';R.s=md_(pv.n)+'부터 · '+afterTxt(pv.n,today).replace('일 전','일째').replace('어제','1일째')}
    if(nx2){R.rows.push({t:'다음 절기',s:nx2.name+' · '+fmt(nx2.n,today)+termTime(nx2)+' · '+afterTxt(nx2.n,today)});
      var nx3=C.nextTerm(nx2.n,false);if(nx3)R.rows.push({t:'그다음',s:nx3.name+' · '+fmt(nx3.n,today)+' · '+afterTxt(nx3.n,today)})}
    R.say=(t0?'오늘은 '+t0.name+'이에요.':'지금은 '+(pv?pv.name+' 절기예요.':''))+(nx2?' 다음 절기는 '+afterTxt(nx2.n,today)+' '+md_(nx2.n)+' '+nx2.name+'이에요.':'');return R}
  if(it.t==='sub'){R.ic='calendar';var sb=[];for(var n8=today;n8<=rg.to&&sb.length<4;n8++){var h8=C.holiday(n8);if(h8&&h8.kind==='sub')sb.push({n:n8,h:h8})}
    if(!sb.length){R.b='앞으로 대체공휴일이 없어요';R.s='표에 있는 2030년 말까지 없어요';R.say=R.b+'.';return R}
    var f8=sb[0],src8=String(f8.h.name).replace(/^대체공휴일\(|\)$/g,'');R.b='다음 대체공휴일은 '+fmt(f8.n,today);R.s=src8+' 대신 쉬어요 · '+afterTxt(f8.n,today);
    sb.slice(1).forEach(function(x){R.rows.push({t:fmt(x.n,today),s:x.h.name})});
    R.say='다음 대체공휴일은 '+afterTxt(f8.n,today)+', '+yw(f8.n,today)+md_(f8.n)+' '+WD[wd(f8.n)]+'요일이에요. '+src8+' 대신 쉬어요.';return R}
  if(it.t==='temp'){R.ic='calendar';var el8=[];for(var n9=today;n9<=rg.to&&el8.length<3;n9++){var h9=C.holiday(n9);if(h9&&h9.kind==='elec')el8.push({n:n9,h:h9})}
    R.b='정해진 임시공휴일은 없어요';R.s='정부가 새로 발표하면 앱을 업데이트해서 넣어요 · 미리 알 수 없어요';
    el8.forEach(function(x){R.rows.push({t:fmt(x.n,today),s:x.h.name+' (법으로 정해진 날)'})});
    R.say='지금 정해진 임시공휴일은 없어요. 정부가 발표하면 앱을 업데이트해서 넣을게요.'+(el8[0]?' 법으로 정해진 쉬는 날로는 '+md_(el8[0].n)+' '+josa(el8[0].h.name,['이','가'])+' 있어요.':'');return R}
  if(it.t==='next'){var nx4=nextHolRow(C,today);R.ic='calendar';
    if(!nx4){R.b='표에 더 이상 공휴일이 없어요';R.s=OUT;R.say=R.b+'. '+OUT;return R}
    var nm4=nx4.h.kind==='sub'?nx4.h.name:nx4.h.group==='seol'?'설날 연휴':nx4.h.group==='chuseok'?'추석 연휴':nx4.h.name;
    R.b='다음 공휴일은 '+ida(nm4);
    var rn4=nx4.run;R.s=fmt(nx4.n,today)+' · '+afterTxt(nx4.n,today)+(rn4&&rn4.days>1?' · '+rn4.days+'일 연속 쉬어요':'');
    var th=C.holiday(today);if(th)R.s='오늘도 '+th.name+' · '+R.s;
    // 이어서 두 번 더
    var cur=nx4.run?nx4.run.b:nx4.n,cnt=0;
    while(cnt<3){var h5=C.nextHoliday(cur,false);if(!h5)break;var r5=C.run(h5.n);var nm5=h5.h.group==='seol'?'설날 연휴':h5.h.group==='chuseok'?'추석 연휴':h5.h.name;
      R.rows.push({t:fmt(h5.n,today).replace(/^\d{4}년 /,function(x){return x}),s:nm5+(r5&&r5.days>1?' · '+r5.days+'일 연속':'')});cur=r5?r5.b:h5.n;cnt++}
    R.say='다음 공휴일은 '+afterTxt(nx4.n,today)+', '+yw(nx4.n,today)+md_(nx4.n)+' '+ida(spk(nm4))+'.'+(rn4&&rn4.days>1?' 주말과 이어져 '+rn4.days+'일을 쉬어요.':'');
    if(R.rows.length)R.say+=' 그다음은 '+R.rows[0].t.replace(/\(.\)/,'')+' '+R.rows[0].s.split(' · ')[0]+'이에요.';
    if(nx4.h.src==='calc')R.meta='월력요항 발표 전 · 관공서의 공휴일에 관한 규정으로 계산한 날이에요';
    return R}
  if(it.t==='list'){R.ic='calendar';var a,b,title2;var td=ymd(today);
    if(it.yl!==undefined){var yy=it.yl&&it.yl.abs?it.yl.abs:td.y+(it.yl||0);a=dn(yy,1,1);b=dn(yy,12,31);title2=yy+'년 공휴일'}
    else if(it.ml!==undefined){var mo=it.ml&&it.ml.abs?it.ml.abs:ymd(dn(td.y,td.m+(it.ml||0),1)).m;var yy2=it.ml&&it.ml.abs?td.y:ymd(dn(td.y,td.m+(it.ml||0),1)).y;if(it.ml&&it.ml.abs&&dn(td.y,mo,28)<today-200)yy2=td.y+1;a=dn(yy2,mo,1);b=dn(yy2,mo+1,1)-1;title2=(yy2!==td.y?yy2+'년 ':'')+mo+'월 공휴일'}
    else{a=today;b=today+365;title2='앞으로 공휴일'}
    if(a<rg.from||b>rg.to+0&&a>rg.to){R.b=OUT;R.say=R.b;return R}
    var seen={},ents=[];for(var x=a;x<=Math.min(b,rg.to);x++){var hx=C.holiday(x);if(!hx||seen[x])continue;
      if((hx.group==='seol'||hx.group==='chuseok')&&hx.kind!=='sub'){var gs=C.groupSpan(x);for(var y=gs.a;y<=gs.b;y++)seen[y]=1;ents.push({a:gs.a,b:gs.b,name:hx.group==='seol'?'설날 연휴':'추석 연휴'})}
      else{seen[x]=1;ents.push({a:x,b:x,name:hx.name})}}
    R.b=ents.length?title2+' '+ents.length+'개':title2.replace(' 공휴일','')+'에는 공휴일이 없어요';R.s='';if(!ents.length){var nxl=C.nextHoliday(Math.max(a,today)-1,false);if(nxl)R.s='다음 공휴일은 '+fmt(nxl.n,today)+' '+nxl.h.name}
    var lim=it.yl!==undefined?9:7;
    ents.slice(0,lim).forEach(function(e){R.rows.push({t:md_(e.a)+(e.b!==e.a?'~'+(ymd(e.b).m!==ymd(e.a).m?md_(e.b):ymd(e.b).d+'일'):'')+'('+WD[wd(e.a)]+')',s:e.name})});
    if(ents.length>lim)R.rows.push({t:'외 '+(ents.length-lim)+'개',s:''});
    R.say=ents.length?title2+'은 '+ents.length+'개예요.'+(ents[0]?' 가장 가까운 날은 '+md_(ents[0].a)+' '+ida(spk(ents[0].name))+'.':''):R.b+'.'+(R.s?' '+R.s+'이에요.':'');return R}
  if(it.t==='leap'){R.ic='moon';var ls=[];C.lunarMonthsAll().forEach(function(e){if(e.leap&&e.s+e.len>today)ls.push(e)});
    if(!ls.length){R.b='표에 앞으로 윤달이 없어요';R.s='음력 표는 2031년 2월까지만 있어요';R.say=R.b+'. '+R.s;return R}
    var e0=ls[0];R.b='다음 윤달은 '+e0.y+'년 윤'+e0.m+'월이에요';R.s=fmt(e0.s,today)+'~'+fmt(e0.s+e0.len-1,today)+' · '+e0.len+'일';ls.slice(1).forEach(function(e){R.rows.push({t:e.y+'년',s:'윤'+e.m+'월 · '+fmt(e.s,today)+'부터'})});
    R.say='다음 윤달은 '+e0.y+'년 윤 '+e0.m+'월이에요. '+md_(e0.s)+'부터 '+md_(e0.s+e0.len-1)+'까지예요.';return R}
  if(it.t==='when'){var nm=it.name,yearBase=today,from,found=null,label=nm.label;
    var ty=ymd(today).y,wantY=nm.year!=null?nm.year:ty+(nm.yoff||0);
    from=nm.year!=null||nm.yoff?dn(wantY,1,1):today;
    if(nm.year!=null||nm.yoff){if(wantY<ymd(rg.from).y||wantY>ymd(rg.to).y){R.b=wantY+'년은 표에 없어요';R.s=OUT;R.say=R.b+'. '+OUT;return R}}
    if(nm.k==='hol'){found=C.holidayByName(nm.key,from);if(found!=null&&(nm.year!=null||nm.yoff)&&ymd(found).y!==wantY)found=null}
    else if(nm.k==='term'){var tt=C.termByName(nm.key,from);if(tt&&(nm.year==null&&!nm.yoff||ymd(tt.n).y===wantY))found=tt.n}
    else if(nm.k==='cus'){if(nm.key==='복날'){var best=null;['초복','중복','말복'].forEach(function(k){var x=C.customByName(k,from);if(x!=null&&(best==null||x<best.n))best={n:x,k:k}});if(best){found=best.n;label=best.k}}
      else found=C.customByName(nm.key,from);if(found!=null&&(nm.year!=null||nm.yoff)&&ymd(found).y!==wantY)found=null}
    else if(nm.k==='mem'){found=C.memorialByName(nm.key,from);if(found!=null&&(nm.year!=null||nm.yoff)&&ymd(found).y!==wantY)found=null}
    if(found==null&&nm.k==='term'&&(C.data.skipTerms||[]).some(function(x){return x[0]===nm.key&&(wantY===x[1])})){R.ic='sun';R.b=wantY+'년 '+eun(label)+' 날짜를 정하지 못했어요';R.s='태양이 그 자리에 오는 때가 자정 1분 앞뒤라, 한국천문연구원이 발표하면 반영할게요';R.say=R.b+'. '+R.s;return R}
    if(found==null){R.b=label+' 날짜는 표에 없어요';R.s=nm.k==='cus'&&(nm.key==='한식'||/복$/.test(nm.key))?'한식·삼복은 2026~2028년 달력자료에만 있어요':OUT;R.say=R.b+'. '+R.s;R.ic='calendar';return R}
    var n5=found,h6=C.holiday(n5),L6=C.solarToLunar(n5),rel6=relWord(n5,today);
    var shown=nm.k==='hol'&&h6&&(h6.group==='seol'||h6.group==='chuseok')?label:label;
    R.ic=nm.k==='term'?'sun':nm.k==='hol'?'calendar':'moon';
    R.b=eun(label)+' '+fmt(n5,today)+(nm.k==='term'?termTime(C.termOn(n5)):'')+'이에요';
    var sm2=[afterTxt(n5,today)];if(L6)sm2.push(C.lunarLabel(L6));R.s=sm2.join(' · ');
    if(h6){var spn=spanTxt(C,n5,today);if(spn)R.rows.push({t:'연휴',s:spn});
      var subs=C.subFor(n5);if(subs&&subs.length)R.rows.push({t:'대체공휴일',s:subs.map(function(x){return fmt(x,today)}).join(', ')});
      var rn6=runTxt(C,n5),gsp=C.groupSpan(n5),glen=gsp?gsp.b-gsp.a+1:1;if(rn6&&rn6.days>glen)R.rows.push({t:'쉬는 날',s:md_(rn6.a)+'('+WD[wd(rn6.a)]+')~'+md_(rn6.b)+'('+WD[wd(rn6.b)]+') · '+rn6.days+'일 연속'});
      if(glen===1&&(wd(n5)===6||wd(n5)===0)&&!(rn6&&rn6.days>1))R.rows.push({t:'요일',s:WD[wd(n5)]+'요일이라 주말과 겹쳐요'});
      if(h6.src==='calc')R.meta='월력요항 발표 전 · 관공서의 공휴일에 관한 규정으로 계산한 날이에요';else if(h6.src==='kasi-prelim')R.meta='한국천문연구원 달력자료 · 월력요항 발표 전 자료';}
    else if(nm.k==='mem'||nm.k==='cus')R.rows.push({t:'공휴일',s:'공휴일은 아니에요'});
    // 다음 해 같은 날
    var nxt=null;if(nm.k==='hol')nxt=C.holidayByName(nm.key,n5+60);else if(nm.k==='term'){var t9=C.termByName(nm.key,n5+60);nxt=t9&&t9.n}else if(nm.k==='cus')nxt=C.customByName(nm.key,n5+60);else if(nm.k==='mem')nxt=C.memorialByName(nm.key,n5+60);
    if(nxt!=null&&nxt-n5<430)R.rows.push({t:'다음 해',s:fmt(nxt,today,{year:true})});
    R.say=eun(label)+' '+(rel6?rel6+', ':yw(n5,today))+md_(n5)+' '+WD[wd(n5)]+'요일이에요.'+(L6&&nm.k!=='term'?' 음력으로 '+(L6.leap?'윤 ':'')+L6.m+'월 '+L6.d+'일이에요.':'')+(h6&&C.run(n5)&&C.run(n5).days>1?' 주말과 이어져 '+C.run(n5).days+'일 연속 쉬어요.':'');
    return R}
  if(it.t==='l2s'){R.ic='moon';var lm=it.lm,ld=it.ld;
    if(lm<1||lm>12||ld<1||ld>30){R.b='음력 날짜가 맞지 않아요';R.s='달은 1~12, 날은 1~30 사이예요';R.say=R.b;return R}
    var ev={lm:lm,ld:ld,leap:it.leap},oc=C.nextOccurrence(ev,today),lab7='음력 '+(it.leap?'윤':'')+lm+'월 '+ld+'일';
    if(!oc){R.b=lab7+' 날짜는 표에 없어요';R.s=OUT;R.say=R.b;return R}
    var rel7=relWord(oc.n,today);
    R.b=lab7+'은 '+fmt(oc.n,today)+'이에요';R.s=afterTxt(oc.n,today)+(oc.adj?' · 그 달이 29일까지라 29일로 맞췄어요':'')+(oc.leapAdj?' · 그해엔 윤'+lm+'월이 없어 평달로 계산했어요':'');
    var oc2=C.occurrences(ev,oc.n+1,oc.n+800)[0];if(oc2)R.rows.push({t:'그다음 해',s:fmt(oc2.n,today,{year:true})});
    R.say=lab7+'은 '+(rel7?rel7+', ':yw(oc.n,today))+md_(oc.n)+' '+WD[wd(oc.n)]+'요일이에요.';return R}
  return null}

/* ---------- 내 음력 생일·기념일 ---------- */
function evLabel(ev){var l=String(ev.label||'').trim();if(!l)l='음력 기념일';else if(!/(생일|생신|기일|제사|기념일|결혼|환갑|칠순|팔순|돌|잔치)/.test(l))l+=' 음력 기념일';return l}
function evLunar(ev){return '음력 '+(ev.leap?'윤':'')+ev.lm+'월 '+ev.ld+'일'}
// 알림 시각 만들기: 전날·당일 오전 9시 (밀리초는 쓰는 쪽이 지역 시간으로 만듦)
function evAlerts(C,events,today,horizonDays){var out=[];(events||[]).forEach(function(ev){C.occurrences(ev,today,today+(horizonDays||400)).forEach(function(o){
  var lab=evLabel(ev);
  out.push({id:ev.id+':'+iso(o.n)+':pre',n:o.n-1,hour:9,text:'내일은 '+ida(lab)+' · '+evLunar(ev)});
  out.push({id:ev.id+':'+iso(o.n)+':day',n:o.n,hour:9,text:'오늘은 '+ida(lab)+' · '+evLunar(ev)})})});
  out.sort(function(a,b){return a.n-b.n||a.id.localeCompare(b.id)});return out.filter(function(a){return a.n>=today})}
// 전날·당일 홈 카드(한 번만): 음력 기념일 → 공휴일 전날
function eveCards(C,events,today){var out=[];
  (events||[]).forEach(function(ev){var o=C.nextOccurrence(ev,today);if(!o)return;
    if(o.n===today+1||o.n===today){var lab=evLabel(ev);out.push({k:'cal:ev:'+ev.id+':'+iso(o.n)+':'+(o.n===today?'d':'p'),kind:'ev',p:1.5,b:(o.n===today?'오늘은 ':'내일은 ')+ida(lab),s:evLunar(ev)+' · '+md_(o.n)+'('+WD[wd(o.n)]+')',say:(o.n===today?'오늘은 ':'내일은 ')+ida(lab)+'. '+evLunar(ev)+'이에요.'})}});
  var h=C.holiday(today+1);
  if(h&&!C.holiday(today)&&C.inRange(today+1)){var r=C.run(today+1),nm=h.group==='seol'?'설날 연휴':h.group==='chuseok'?'추석 연휴':h.name;
    if(h.kind==='sub'){var s0=today;nm=h.name}
    var rs=r&&r.days>1?md_(r.a)+'('+WD[wd(r.a)]+')~'+md_(r.b)+'('+WD[wd(r.b)]+') · '+r.days+'일 연속 쉬어요':md_(today+1)+'('+WD[wd(today+1)]+')';
    out.push({k:'cal:hol:'+iso(today+1),kind:'hol',p:2.5,b:'내일은 '+ida(nm),s:rs,say:'내일은 '+ida(spk(nm))+'.'+(r&&r.days>1?' 주말과 이어져 '+r.days+'일을 쉬어요.':'')})}
  return out}

return {make:make,ruleHolidays:ruleHolidays,parseQ:parseQ,answer:answer,normQ:normQ,koDates:koDates,nameLookup:nameLookup,evAlerts:evAlerts,eveCards:eveCards,evLabel:evLabel,evLunar:evLunar,fmt:fmt,md:md_,ida:ida,refToN:refToN,dn:dn,ymd:ymd,wd:wd,iso:iso,parseIso:parseIso,josa:josa,josaRo:josaRo,hasBatchim:hasBatchim,WD:WD,TERM_NAMES:TERM_NAMES,FIXED:FIXED,ELECTIONS:ELECTIONS,DAY:DAY}
});
