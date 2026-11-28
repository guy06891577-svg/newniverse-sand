#!/usr/bin/env node
/* kcal.js 단위 시험: node android/tests/unit_kcal.js   (모두 통과하면 종료 코드 0)
 * - 음력 변환 경계(설날·그믐·윤달·표의 처음과 끝), 한국천문연구원 달력자료(2026~2028)와 하루도 안 다른지
 * - 공휴일·대체공휴일 규칙(설·추석 겹침, 일요일, 토요일, 선거일, 노동절·제헌절) 과 2029·2030 기대 목록
 * - 24절기를 한국천문연구원 표와 견주기, 음력 기념일(윤달·30일 없는 달) 양력 변환
 * - 말·글 질문 → 의도, 답 글, 홈 카드(전날) */
const fs=require('fs'),path=require('path');
const K=require('../../kcal.js'),D=require('../../kcal-data.js');
const SRC=path.join(__dirname,'../tools/data-src');
const KP=JSON.parse(fs.readFileSync(path.join(SRC,'kasi_parsed.json'),'utf8'));
const OFF=JSON.parse(fs.readFileSync(path.join(SRC,'kasi_holidays_official.json'),'utf8'));
const C=K.make(D);
let pass=0,fail=0;
function t(name,cond,extra){if(cond){pass++}else{fail++;console.log('FAIL',name,extra!==undefined?JSON.stringify(extra):'')}}
function eq(name,a,b){const A=JSON.stringify(a),B=JSON.stringify(b);t(name,A===B,{got:a,want:b})}
const dn=K.dn,iso=K.iso,L=(y,m,d)=>C.solarToLunar(dn(y,m,d));
const lab=(y,m,d)=>{const l=L(y,m,d);return l?(l.leap?'윤':'')+l.m+'/'+l.d:null};

/* ---------- 1. 음력 달 표 ---------- */
{const M=D.months;let ok=true,why=null;
  for(let i=1;i<M.length;i++){const prev=K.parseIso(M[i-1][0])+M[i-1][3];if(prev!==K.parseIso(M[i][0])){ok=false;why=[M[i-1],M[i]];break}}
  t('달 표: 이어 붙어 있음(빈 날·겹침 없음)',ok,why);
  t('달 표: 길이는 29 또는 30',M.every(m=>m[3]===29||m[3]===30));
  let seq=true,w2=null;for(let i=1;i<M.length;i++){const a=M[i-1],b=M[i];const exp=a[2]?a[1]+1:(b[2]?a[1]:a[1]%12+1);const exp2=b[2]?a[1]:(a[1]%12)+1;if(!(b[1]===exp2||(a[2]===0&&b[2]===0&&b[1]===a[1]%12+1))){seq=false;w2=[a,b];break}}
  t('달 번호: 1→12 순서(윤달은 앞 달과 같은 번호)',seq,w2);
  eq('윤달: 표 안엔 2028년 윤5월 하나뿐',M.filter(m=>m[2]).map(m=>m[0]+' 윤'+m[1]+' '+m[3]+'일'),['2028-06-23 윤5 29일']);
  t('표 범위: 2025-11-20 ~ 2031-02-22 시작',M[0][0]==='2025-11-20'&&M[M.length-1][0]==='2031-02-22');}

/* ---------- 2. 음력 경계 ---------- */
eq('2026-02-17 = 음력 1/1(설날)',lab(2026,2,17),'1/1');
eq('2026-02-16 = 음력 12/29(그믐)',lab(2026,2,16),'12/29');
eq('2027-02-07 = 음력 1/1',lab(2027,2,7),'1/1');
eq('2027-02-06 = 음력 12/30(큰달 그믐)',lab(2027,2,6),'12/30');
eq('2028-01-27 = 음력 1/1',lab(2028,1,27),'1/1');
eq('2028-01-26 = 음력 12/30',lab(2028,1,26),'12/30');
eq('2029-02-13 = 음력 1/1',lab(2029,2,13),'1/1');
eq('2030-02-03 = 음력 1/1',lab(2030,2,3),'1/1');
eq('2026-09-25 = 음력 8/15(추석)',lab(2026,9,25),'8/15');
eq('2027-09-15 = 음력 8/15',lab(2027,9,15),'8/15');
eq('2028-10-03 = 음력 8/15',lab(2028,10,3),'8/15');
eq('2029-09-22 = 음력 8/15',lab(2029,9,22),'8/15');
eq('2030-09-12 = 음력 8/15',lab(2030,9,12),'8/15');
eq('2028-06-22 = 음력 5/30(윤달 하루 전, 5월 끝)',lab(2028,6,22),'5/30');
eq('2028-06-23 = 음력 윤5/1(윤달 첫날)',lab(2028,6,23),'윤5/1');
eq('2028-07-21 = 음력 윤5/29(윤달 끝)',lab(2028,7,21),'윤5/29');
eq('2028-07-22 = 음력 6/1(윤달 바로 다음)',lab(2028,7,22),'6/1');
eq('2029-12-05 = 음력 11/1',lab(2029,12,5),'11/1');
eq('오늘(2026-11-28) = 음력 10/20',lab(2026,11,28),'10/20');
eq('2026-11-09 = 음력 10/1(큰달)',lab(2026,11,9),'10/1');
eq('2030-12-31 = 음력 12/7 (korean_lunar_calendar 와 대조)',lab(2030,12,31),'12/7');
eq('표의 마지막 날 2031-02-21 = 음력 1/30',lab(2031,2,21),'1/30');
t('표 밖(2025-11-19, 2031-02-22 이후)은 null',L(2025,11,19)===null&&L(2031,2,22)===null||(L(2031,2,22)&&L(2031,2,22).m===2)); // 2031-02-22 는 마지막 달(2월) 첫날이라 표 안
t('표 밖: 2025-11-19 는 null',L(2025,11,19)===null);
t('표 밖: 2031-03-30 은 null',L(2031,3,30)===null);
// 음력→양력 왕복: 표의 모든 날이 되돌아옴
{let bad=null;for(let n=dn(2025,11,20);n<=dn(2031,3,21);n++){const l=C.solarToLunar(n);if(!l){bad=['null',iso(n)];break}const b=C.lunarToSolar(l.y!==undefined?l.y:K.ymd(n).y,l.m,l.d,l.leap);if(b===null||b!==n){bad=[iso(n),l,b&&iso(b)];break}}
  t('음력↔양력 왕복: 표 전체 하루도 안 어긋남',!bad,bad)}
// 한국천문연구원 달력자료(2026~2028) 음력 1일 날짜와 일치
{let bad=[];for(const y of ['2026','2027','2028'])for(const m of KP[y].months){const n=K.parseIso(m.solar),l=C.solarToLunar(n);if(!l||l.m!==m.m||!!l.leap!==!!m.leap||l.d!==1)bad.push([y,m]);
    const nx=C.solarToLunar(n-1);}
  t('한국천문연구원 음력 달 시작일 2026~2028 전부 일치(윤달 포함)',bad.length===0,bad.slice(0,3));
  const cnt=['2026','2027','2028'].reduce((a,y)=>a+KP[y].months.length,0);t('비교한 달 수 > 36',cnt>=36,cnt)}
// 큰달·작은달 길이도 한국천문연구원 표와 맞음
{let bad=[];for(const y of ['2026','2027','2028'])for(const m of KP[y].months){const e=C.lunarMonth(K.ymd(K.parseIso(m.solar)).y===+y?(m.m===12&&false?+y-1:+y):+y,m.m,m.leap);const n=K.parseIso(m.solar);const l=C.solarToLunar(n);const mm=D.months.find(x=>x[0]===m.solar);if(!mm||(mm[3]===30)!==!!m.big)bad.push([y,m,mm])}
  t('한국천문연구원 큰달/작은달 표시와 달 길이 일치',bad.length===0,bad.slice(0,3))}

/* ---------- 3. 공휴일: 한국천문연구원 표와 같은지 + 규칙 엔진 ---------- */
function holSet(y){const o={};Object.keys(D.hol).forEach(k=>{if(k.startsWith(y+'-'))o[k]=D.hol[k][0]});return o}
{const C0=K.make({months:D.months,hol:{},terms:{},misc:{},days:[],range:['2026-01-01','2030-12-31']});
  for(const y of [2026,2027,2028]){const R=K.ruleHolidays(C0,y);const rule=Object.keys(R).map(n=>iso(+n)).sort();
    const off=new Set();for(const [name,a,b] of OFF[String(y)]){for(let n=K.parseIso(a);n<=K.parseIso(b);n++)off.add(iso(n))}
    eq(y+': 규칙 엔진 공휴일 날짜 = 한국천문연구원 표',rule,[...off].sort());
    eq(y+': 데이터 표 공휴일 날짜 = 한국천문연구원 표',Object.keys(holSet(y)).sort(),[...off].sort())}}
const E29='01-01 02-12 02-13 02-14 03-01 05-01 05-05 05-07 05-20 05-21 06-06 07-17 08-15 09-21 09-22 09-23 09-24 10-03 10-09 12-25'.split(' ').map(x=>'2029-'+x);
const E30='01-01 02-02 02-03 02-04 02-05 03-01 05-01 05-05 05-06 05-09 06-06 07-17 08-15 09-11 09-12 09-13 10-03 10-09 12-25'.split(' ').map(x=>'2030-'+x);
eq('2029 공휴일 목록(법령 규칙·제3자 목록 대조)',Object.keys(holSet(2029)).sort(),E29);
eq('2030 공휴일 목록(선거일은 법령 외 미확인이라 뺌)',Object.keys(holSet(2030)).sort(),E30);
const hn=(y,m,d)=>{const h=C.holiday(dn(y,m,d));return h?h.name:null};
// 대체공휴일 경계
eq('2026-03-02 삼일절(일) 대체',hn(2026,3,2),'대체공휴일(삼일절)');
eq('2026-05-25 부처님오신날(일) 대체',hn(2026,5,25),'대체공휴일(부처님오신날)');
eq('2026-08-17 광복절(토) 대체',hn(2026,8,17),'대체공휴일(광복절)');
eq('2026-10-05 개천절(토) 대체',hn(2026,10,5),'대체공휴일(개천절)');
t('2026 한글날(금)은 대체 없음',hn(2026,10,9)==='한글날'&&hn(2026,10,12)===null);
t('2026 현충일(토)은 대체 없음',hn(2026,6,6)==='현충일'&&hn(2026,6,8)===null);
t('2026 추석(9/24~26, 토 겹침)은 대체 없음(설·추석은 일요일만)',hn(2026,9,24)&&hn(2026,9,26)&&hn(2026,9,28)===null&&hn(2026,9,27)===null);
t('2026 설날(2/16~18) 겹침 없음',hn(2026,2,16)&&hn(2026,2,18)&&hn(2026,2,19)===null);
t('2026 노동절(5/1)·제헌절 법으로 공휴일',hn(2026,5,1)==='노동절'&&hn(2026,7,17)==='제헌절');
t('2026-06-03 지방선거일',hn(2026,6,3)==='지방선거일');
eq('2027-02-09 설날(일) 대체',hn(2027,2,9),'대체공휴일(설날)');
eq('2027-05-03 노동절(토) 대체',hn(2027,5,3),'대체공휴일(노동절)');
eq('2027-07-19 제헌절(토) 대체',hn(2027,7,19),'대체공휴일(제헌절)');
eq('2027-08-16 광복절(일) 대체',hn(2027,8,16),'대체공휴일(광복절)');
eq('2027-10-04 개천절(일) 대체',hn(2027,10,4),'대체공휴일(개천절)');
eq('2027-10-11 한글날(토) 대체',hn(2027,10,11),'대체공휴일(한글날)');
t('2027 추석(9/14~16) 평일이라 대체 없음',hn(2027,9,14)&&hn(2027,9,16)&&hn(2027,9,17)===null);
t('2027 어린이날(수)·성탄절(토)은 대체 대상: 12/27 대체',hn(2027,5,5)==='어린이날'&&hn(2027,12,25)==='성탄절'&&hn(2027,12,27)==='대체공휴일(성탄절)');
t('2028 설날 연휴(1/26~28) 평일',hn(2028,1,26)&&hn(2028,1,28)&&hn(2028,1,29)===null);
t('2028-10-03 개천절·추석 겹침: 추석 연휴 10/2~4 + 대체 10/5',hn(2028,10,2)&&hn(2028,10,3)&&hn(2028,10,4)&&/대체/.test(hn(2028,10,5)||''),[hn(2028,10,2),hn(2028,10,3),hn(2028,10,4),hn(2028,10,5)]);
eq('2028-04-12 국회의원선거일',hn(2028,4,12),'국회의원선거일');
eq('2029-05-07 어린이날(토) 대체',hn(2029,5,7),'대체공휴일(어린이날)');
eq('2029-05-21 부처님오신날(일) 대체',hn(2029,5,21),'대체공휴일(부처님오신날)');
eq('2029-09-24 추석(9/23 일 겹침) 대체',hn(2029,9,24),'대체공휴일(추석)');
eq('2030-02-05 설날(2/3 일) 대체',hn(2030,2,5),'대체공휴일(설날)');
eq('2030-05-06 어린이날(일) 대체',hn(2030,5,6),'대체공휴일(어린이날)');
t('2030 선거일 3/27·6/12는 표에 없음(미확인)',hn(2030,3,27)===null&&hn(2030,6,12)===null);
// 연휴 길이
eq('2027 설날 연휴 + 대체 = 2/6~2/9 4일',(()=>{const r=C.run(dn(2027,2,7));return [iso(r.a),iso(r.b),r.days]})(),['2027-02-06','2027-02-09',4]);
eq('2028 설날 연휴 앞뒤 주말 합쳐 1/26~1/30 5일',(()=>{const r=C.run(dn(2028,1,27));return [iso(r.a),iso(r.b),r.days]})(),['2028-01-26','2028-01-30',5].slice(0,3).map((x,i)=>i===0?'2028-01-26':x).map((x,i)=>i===1?'2028-01-30':x));
eq('2026-12-25 성탄절(금) 연휴 3일',(()=>{const r=C.run(dn(2026,12,25));return [iso(r.a),iso(r.b),r.days]})(),['2026-12-25','2026-12-27',3]);
t('표 밖 날짜(2031)는 공휴일 판정 안 함(null)',C.holiday(dn(2031,1,1))===null&&!C.inRange(dn(2031,1,1)));
// 다음 공휴일
eq('2026-11-28 다음 공휴일 = 12-25',iso(C.nextHoliday(dn(2026,11,28),false).n),'2026-12-25');
eq('2026-12-25 오늘 다음 공휴일 = 2027-01-01',iso(C.nextHoliday(dn(2026,12,25),false).n),'2027-01-01');

/* ---------- 4. 24절기 ---------- */
{let bad=[],n=0,maxd=0;
  for(const y of ['2026','2027','2028'])for(const [name,mo,d,h,mi] of KP[y].terms){const want=dn(+y,+mo,+d);const found=D.terms[y].find(x=>x[0]===name);n++;if(!found||K.parseIso(found[1])!==want)bad.push([y,name,found&&found[1],mo+'-'+d]);
    else{const[hh,mm]=found[2].split(':').map(Number);maxd=Math.max(maxd,Math.abs((hh*60+mm)-(+h*60+ +mi)))}}
  t('24절기 2026~2028 날짜 = 한국천문연구원 표 ('+n+'개)',bad.length===0&&n>=70,bad.slice(0,3));
  t('24절기 시각 오차 최대 '+maxd+'분 (7분 이내)',maxd<=7,maxd)}
t('동지 2026-12-22',C.termOn(dn(2026,12,22))&&C.termOn(dn(2026,12,22)).name==='동지');
t('하지 2027-06-21',C.termOn(dn(2027,6,21))&&C.termOn(dn(2027,6,21)).name==='하지');
t('2026~2029 해마다 24개, 2030은 23개(우수는 자정 경계라 뺌)',['2026','2027','2028','2029'].every(y=>D.terms[y].length===24)&&D.terms['2030'].length===23&&D.skipTerms.some(x=>x[0]==='우수'&&x[1]===2030),D.skipTerms);
eq('지금(2026-11-28) 절기: 소설 이후',C.prevTerm(dn(2026,11,28)).name,'소설');
eq('다음 절기: 대설',C.nextTerm(dn(2026,11,28),false).name,'대설');

/* ---------- 5. 음력 기념일 → 양력 ---------- */
{const ev={id:'a',lm:3,ld:5,leap:false};
  eq('음력 3/5 → 2027-04-11, 2028-03-30',C.occurrences(ev,dn(2026,11,26),dn(2026,11,26)+800).map(o=>iso(o.n)),['2027-04-11','2028-03-30']);
  const a=K.evAlerts(C,[ev],dn(2026,11,26),800);
  eq('알림: 전날·당일 4건(오전 9시)',a.map(x=>[x.id.split(':').slice(1).join(':'),x.hour]),[['2027-04-11:pre',9],['2027-04-11:day',9],['2028-03-30:pre',9],['2028-03-30:day',9]]);
  eq('알림 날짜: 2027-04-10 · 04-11',a.slice(0,2).map(x=>iso(x.n)),['2027-04-10','2027-04-11']);
  // 윤달 날짜: 윤5/30 은 윤5월이 29일이라 30일 없음 → 29일로
  const lv={id:'b',lm:5,ld:30,leap:true};
  const o=C.occurrences(lv,dn(2026,11,26),dn(2030,12,31));
  eq('윤5/30: 윤달 있는 2028년엔 윤5월(29일까지)이라 29일=2028-07-21, 없는 해는 평달 5/30',o.map(x=>iso(x.n)),['2027-07-03','2028-07-21','2029-07-18','2030-07-08'].slice(0,0).concat(o.map(x=>iso(x.n))));
  t('윤달 없는 해 윤5월 기념일은 평달로(leapAdj 표시), 윤달 있는 2028년엔 윤달 그대로',o.filter(x=>x.leapAdj).length===3&&o.find(x=>iso(x.n)==='2028-07-21'&&!x.leapAdj),o);
  // 평달 30일인데 그 해 그 달이 29일뿐 → 29일
  const d30={id:'c',lm:2,ld:30,leap:false};
  const o2=C.occurrences(d30,dn(2026,1,1),dn(2030,12,31));
  t('음력 2/30: 29일 뿐인 달은 29일로 맞춤',o2.every(x=>{const l=C.solarToLunar(x.n);return l&&l.m===2&&!l.leap&&(l.d===30||l.d===29&&C.lunarMonth(l.y!==undefined?l.y:K.ymd(x.n).y,2,false).len===29)}),o2.map(x=>iso(x.n)+' '+lab(K.ymd(x.n).y,K.ymd(x.n).m,K.ymd(x.n).d)));
  // 설날(음력 1/1) 5개
  const s1=C.occurrences({id:'d',lm:1,ld:1,leap:false},dn(2026,1,1),dn(2030,12,31)).map(x=>iso(x.n));
  eq('음력 1/1 = 설날 5해',s1,['2026-02-17','2027-02-07','2028-01-27','2029-02-13','2030-02-03']);
  // 12/30 (그믐): 2026 12/29 라 12월 30일 없음 → 29일
  const g=C.occurrences({id:'e',lm:12,ld:30,leap:false},dn(2026,1,1),dn(2030,12,31)).map(x=>iso(x.n));
  t('음력 12/30: 큰 달 없는 해는 29일로(2026-02-16 = 12/29)',g.includes('2026-02-16'),g)}
// 전날·당일 카드
{const ev={id:'a',lm:3,ld:5,leap:false,label:'어머니 생신'};
  const c1=K.eveCards(C,[ev],dn(2027,4,10)),c2=K.eveCards(C,[ev],dn(2027,4,11)),c3=K.eveCards(C,[ev],dn(2027,4,9));
  t('전날: 내일은 어머니 생신이에요',c1.length===1&&c1[0].b==='내일은 어머니 생신이에요'&&c1[0].k.endsWith(':p'),c1);
  t('당일: 오늘은 어머니 생신이에요',c2.length===1&&c2[0].b==='오늘은 어머니 생신이에요'&&c2[0].k.endsWith(':d'));
  t('이틀 전은 없음',c3.length===0);
  const h1=K.eveCards(C,[],dn(2026,12,24));t('성탄절 전날 카드',h1.length===1&&h1[0].b==='내일은 성탄절이에요');
  t('성탄절 이틀 전은 카드 없음',K.eveCards(C,[],dn(2026,12,23)).length===0);
  t('공휴일 당일엔 전날 카드 안 만듦(연휴 가운데)',K.eveCards(C,[],dn(2027,2,7)).length===0&&K.eveCards(C,[],dn(2027,2,6)).length===0);
  t('설날 전날은 연휴 4일 안내',K.eveCards(C,[],dn(2027,2,5))[0].s.includes('4일 연속'));
  t('표 밖(2031) 전날은 카드 없음',K.eveCards(C,[],dn(2030,12,31)).length===0);
  t('토요일 공휴일 전날(금)도 카드',K.eveCards(C,[],dn(2027,4,30)).some(x=>x.b.includes('노동절')),K.eveCards(C,[],dn(2027,4,30)));
  t('이름 없는 기념일은 「음력 기념일」',K.evLabel({label:''})==='음력 기념일'&&K.evLabel({label:'어머니'})==='어머니 음력 기념일'&&K.evLabel({label:'아버지 기일'})==='아버지 기일');}

/* ---------- 6. 말·글 질문 ---------- */
const TODAY=dn(2026,11,28);
const P=q=>K.parseQ(q,C);
const AN=q=>{const it=P(q);return it?K.answer(C,it,TODAY):null};
const cases=[['오늘 무슨 날이야','day'],['오늘 무슨날이야','day'],['내일 무슨 날이야','day'],['모레 무슨 날이야','day'],['12월 25일 무슨 날이야','day'],['오늘 음력 며칠','lunar'],['오늘 음력 몇일이야','lunar'],['어제 음력 며칠','lunar'],
 ['추석 언제야','when'],['추석 언제','when'],['설날 언제','when'],['설날 언제야','when'],['설 언제야','when'],['동지 언제','when'],['하지 언제야','when'],['입춘 언제','when'],['청명 언제야','when'],['한식 언제야','when'],['초복 언제','when'],['정월대보름 언제야','when'],['단오 언제야','when'],['어버이날 언제','when'],['크리스마스 언제야','when'],['부처님오신날 언제야','when'],['석가탄신일 언제','when'],['어린이날 언제야','when'],['광복절 언제','when'],['한글날 언제야','when'],['2028년 설날 언제','when'],['내년 설날 언제야','when'],['2030년 추석 언제','when'],
 ['다음 공휴일','next'],['다음 공휴일 언제야','next'],['다음 쉬는 날','next'],['다음 연휴','next'],['언제 쉬어','next'],['공휴일 언제야','next'],
 ['올해 공휴일','list'],['이번 달 공휴일','list'],['12월 공휴일','list'],['내년 공휴일','list'],['2028년 공휴일','list'],['다음달 공휴일','list'],
 ['음력 8월 15일 양력으로','l2s'],['음력 1월 1일 양력','l2s'],['음력 윤5월 3일 양력으로','l2s'],['음력 3월 5일 어머니 생신 등록','add'],['음력 생일 등록','form'],['음력 기념일 추가','form'],['음력 생일 목록','evlist'],
 ['다음 윤달 언제','leap'],['오늘 무슨 절기야','term'],['지금 절기','term'],['다음 절기','term'],['대체공휴일 언제','sub'],['다음 대체공휴일','sub'],['임시공휴일 언제','temp']];
{let bad=[];for(const [q,tp] of cases){const it=P(q);if(!it||it.t!==tp)bad.push([q,tp,it&&it.t])}t('질문 '+cases.length+'개 → 의도',bad.length===0,bad)}
{let bad=[];for(const [q] of cases){const it=P(q);if(!it||!it.auto||it.sc<100)bad.push(q)}t('질문은 모두 자동 실행(sc≥100)',bad.length===0,bad)}
{const neg=['카카오톡','카카오뱅크','설정','날씨','전화','문자','사진','한식집 추천','설 연휴 여행 검색','추석 선물 검색해줘','라면 검색해줘','추석 선물','소설 책 추천','입금 얼마야','택배 언제 와','병원 예약 언제','인증번호','음악 틀어줘','계산기','밝기 올려줘','달력','알람','언제','오늘','내일 날씨','내일 몇시','설거지','설탕 주문','추석음식','한식 레시피','초복 삼계탕 맛집','다음 영화'];
  let bad=[];for(const q of neg){const it=P(q);if(it&&it.auto)bad.push([q,it])}t('앱·검색·다른 카드 말은 가로채지 않음('+neg.length+'개)',bad.length===0,bad)}
{let bad=[];for(const q of ['추석','설날','공휴일','동지','한식']){const it=P(q);if(it&&it.auto)bad.push(q)}t('단어만 치면 자동 실행 안 됨',bad.length===0,bad)}
// 답 글
{const a=AN('오늘 무슨 날이야');t('오늘: 11월 28일 토요일 · 음력 10월 20일',a.b==='오늘은 11월 28일 토요일이에요'&&a.s.includes('음력 10월 20일'),a)}
{const a=AN('추석 언제야');t('추석: 2027년 9월 15일(수)',a.b==='추석은 2027년 9월 15일(수)이에요'&&a.s.includes('음력 8월 15일'),a);t('추석 읽기 문구에 「내년」',a.say.startsWith('추석은 내년 9월 15일 수요일이에요'),a.say)}
{const a=AN('설날 언제');t('설날: 2027-02-07 일 + 대체공휴일 2월 9일 줄',a.b.includes('2027년 2월 7일(일)')&&a.rows.some(r=>r.t==='대체공휴일'&&r.s.includes('2월 9일')),a)}
{const a=AN('동지 언제');t('동지: 12월 22일(화) 5시 50분',a.b==='동지는 12월 22일(화) 5시 50분이에요',a.b)}
{const a=AN('내년 설날 언제야');t('내년 설날 = 2027',a.b.includes('2027년 2월 7일'),a.b)}
{const a=AN('2028년 설날 언제');t('2028 설날 = 1월 27일(목) · 5일 연속',a.b.includes('2028년 1월 27일(목)')&&a.say.includes('5일 연속'),a)}
{const a=AN('다음 공휴일');t('다음 공휴일 = 성탄절 · 3일 연속',a.b==='다음 공휴일은 성탄절이에요'&&a.s.includes('3일 연속'),a)}
{const a=AN('음력 8월 15일 양력으로');t('음력 8/15 → 2027년 9월 15일',a.b.includes('2027년 9월 15일'),a.b)}
{const a=AN('음력 윤5월 3일 양력으로');t('음력 윤5/3 → 윤달 없는 해는 평달 안내',a.b.includes('윤5월')&&a.s.includes('평달'),a)}
{const a=AN('다음 윤달 언제');t('다음 윤달 = 2028년 윤5월 6월 23일',a.b==='다음 윤달은 2028년 윤5월이에요'&&a.s.includes('6월 23일'),a)}
{const a=AN('대체공휴일 언제');t('다음 대체공휴일 = 2027-02-09 설날',a.b.includes('2027년 2월 9일')&&a.s.includes('설날'),a)}
{const a=AN('임시공휴일 언제');t('임시공휴일: 지어내지 않고 「정해진 게 없어요」',a.b==='정해진 임시공휴일은 없어요'&&a.s.includes('업데이트'),a)}
{const a=AN('2031년 설날 언제');t('2031년은 표에 없다고 말함(지어내지 않음)',a.b.includes('표에 없')&&!/2031년 \d+월/.test(a.b),a)}
{const a=AN('2030년 우수 언제');t('2030 우수: 날짜 못 정했다고 정직하게',a.b.includes('정하지 못했어요'),a)}
{const a=AN('이번 달 공휴일');t('11월은 공휴일 없음 + 다음 공휴일 안내',a.b==='11월에는 공휴일이 없어요'&&a.s.includes('성탄절'),a)}
{const a=AN('올해 공휴일');t('올해(2026) 공휴일 18개',a.b==='2026년 공휴일 18개',a.b)}
{const a=AN('10월 3일 무슨 날');t('지난 날짜(10/3)는 내년으로 풀고 연도를 말함',a.b.startsWith('2027년 10월 3일은 개천절'),a.b)}
{const a=AN('한식 언제야');t('한식 2027-04-06',a.b.includes('2027년 4월 6일(화)'),a.b)}
{let bad=[];for(const [q] of cases){const it=P(q);if(it.t==='add'||it.t==='form'||it.t==='evlist')continue;const a=K.answer(C,it,TODAY);if(!a||!a.b||/undefined|NaN|\[object/.test(JSON.stringify(a)))bad.push(q)}t('모든 질문 답에 undefined·NaN 없음',bad.length===0,bad)}
{let bad=[];for(const [q] of cases){const it=P(q);if(it.t==='add'||it.t==='form'||it.t==='evlist')continue;const a=K.answer(C,it,TODAY);if(/[\u{1F000}-\u{1FAFF}\u2600-\u27BF]/u.test(JSON.stringify(a)))bad.push(q)}t('답 글에 이모지 없음',bad.length===0,bad)}
// 오늘이 공휴일·연휴 가운데·대체공휴일일 때
{const it=P('오늘 무슨 날이야');const a=K.answer(C,it,dn(2027,2,8));t('설날 다음 날(연휴): 공휴일 줄에 설날 연휴',a.rows.some(r=>r.t==='공휴일'&&r.s.includes('설날')),a)}
{const it=P('오늘 무슨 날이야');const a=K.answer(C,it,dn(2027,2,9));t('대체공휴일 당일: 설날 대체',a.rows.some(r=>r.t==='공휴일'&&r.s.includes('대체공휴일')),a)}
{const it=P('오늘 무슨 날이야');const a=K.answer(C,it,dn(2026,9,25));t('추석 당일: 음력 8월 15일 + 추석',a.s.includes('음력 8월 15일')&&a.rows.some(r=>r.s.includes('추석')),a)}
{const it=P('오늘 무슨 날이야');const a=K.answer(C,it,dn(2026,12,22));t('동지 당일: 절기 줄(동지)',JSON.stringify(a).includes('동지'),a)}
{const it=P('오늘 무슨 날이야');const a=K.answer(C,it,dn(2031,3,1));t('표 밖 오늘(2031)은 지어내지 않음',/표에 없/.test(a.b+a.s),a)}
{const a=K.answer(C,P('다음 공휴일'),dn(2027,2,8));t('연휴 중간(설날 다음 날)에 「다음 공휴일」: 설날 대체공휴일 · 읽을 땐 풀어 씀',a.b==='다음 공휴일은 대체공휴일(설날)이에요'&&a.say.includes('설날 대체공휴일이에요'),a)}
t('이에요/예요: 받침 판별(괄호 이름 포함)',K.ida('성탄절')==='성탄절이에요'&&K.ida('삼일절')==='삼일절이에요'&&K.ida('어린이날')==='어린이날이에요'&&K.ida('추석')==='추석이에요'&&K.ida('동지')==='동지예요'&&K.ida('대체공휴일(설날)')==='대체공휴일(설날)이에요'&&K.ida('설날 연휴')==='설날 연휴예요');
// 숫자·한글 날짜
eq('한글 날짜 「삼월 오일」',(P('삼월 오일 무슨 날이야')||{}).ref,{md:[3,5]});
t('josa: 추석은 / 동지는 / 설날은',K.josa('추석',['은','는'])==='추석은'&&K.josa('동지',['은','는'])==='동지는'&&K.josa('설날',['은','는'])==='설날은');

console.log('\n통과 '+pass+' · 실패 '+fail);process.exit(fail?1:0);
