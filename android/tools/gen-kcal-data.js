#!/usr/bin/env node
/* kcal-data.js 만들기: node android/tools/gen-kcal-data.js
 * 입력: data-src/export.json (음력 달 표·24절기·한식/삼복·고정 기념일) + data-src/kasi_holidays_official.json (한국천문연구원 달력자료 공휴일 표 2026~2028)
 * 2029~2030 공휴일은 「관공서의 공휴일에 관한 규정」 규칙으로 계산(월력요항 발표 전이라 src=calc). 2026~2028 은 한국천문연구원 표를 그대로 쓰고, 규칙 계산이 그 표와 똑같은지 시험(android/tests/unit_kcal.js)이 따로 확인해요. */
const fs=require('fs'),path=require('path');
const K=require('../../kcal.js');
const src=JSON.parse(fs.readFileSync(path.join(__dirname,'data-src/export.json'),'utf8'));
const off=JSON.parse(fs.readFileSync(path.join(__dirname,'data-src/kasi_holidays_official.json'),'utf8'));
const base={months:src.months,hol:{},terms:{},misc:{},days:[],range:['2026-01-01','2030-12-31']};
const C0=K.make(base);
const hol={};
for(const y of ['2026','2027','2028']){
  for(const [name,a,b,kind] of off[y]){
    const A=K.parseIso(a),B=K.parseIso(b);
    for(let n=A;n<=B;n++){
      let nm=name,grp='';
      if(name==='설날'){nm=n===A+1?'설날':'설날 연휴';grp='seol'}
      else if(name==='추석'){nm=n===A+1?'추석':'추석 연휴';grp='chuseok'}
      else if(kind==='sub'){grp='sub'}
      else{const f=K.FIXED.find(x=>x[1]===name);grp=f?f[2]:name==='부처님오신날'?'buddha':kind==='elec'?'election':''}
      hol[K.iso(n)]=[nm,kind,grp,(y==='2026'&&(name==='노동절'||name==='제헌절'))?'law':(y==='2028'?'kasi-prelim':'kasi')];
    }
  }
}
// 대체공휴일의 group: 규칙 계산 쪽 이름과 맞춤 (어린이날 등). 2026~2028 은 표에 있는 대로 두되 group 은 규칙 쪽에서 가져옴
for(const y of [2026,2027,2028]){const R=K.ruleHolidays(C0,y);for(const n of Object.keys(R)){const k=K.iso(+n);if(hol[k]&&R[n].kind==='sub'){hol[k][0]=R[n].name;hol[k][2]=R[n].group}}}
for(const y of [2029,2030]){const R=K.ruleHolidays(C0,y);for(const n of Object.keys(R)){const r=R[n];hol[K.iso(+n)]=[r.name,r.kind,r.group,'calc']}}
const out={v:1,built:'2026-11-28',months:src.months,terms:src.terms,skipTerms:(src.skipped||[]).map(x=>[x[0],+x[1].slice(0,4)]),hol:Object.fromEntries(Object.entries(hol).sort()),misc:src.misc,days:src.days,range:base.range,
  note:'음력 달 표: 한국천문연구원 달력자료 2026~2028과 하루도 안 다름 · 2029~2031.2 는 천문 계산과 korean-lunar-calendar 표가 일치. 공휴일: 2026~2028 한국천문연구원(2028은 월력요항 발표 전), 2029~2030 규정 계산. 임시공휴일은 지정될 때 갱신.'};
const js='/* 뉴니버스 음력·공휴일 표 (만든 날 2026-11-28) — android/tools/gen-kcal-data.js 로 만든 파일이에요. 직접 고치지 마세요. */\nvar NVCAL_DATA='+JSON.stringify(out)+';\nif(typeof module==="object"&&module.exports)module.exports=NVCAL_DATA;\n';
fs.writeFileSync(path.join(__dirname,'../../kcal-data.js'),js);
console.log('hol days',Object.keys(hol).length,'bytes',js.length);
