import json, datetime as dt
import astro
import lunar_gen as L
K=json.load(open('kasi_parsed.json'))
months=[]
for m in L.months:
    if dt.date(2025,11,1)<=m['a']<=dt.date(2031,2,28):
        months.append([m['a'].isoformat(),m['n'],1 if m['leap'] else 0,(m['b']-m['a']).days])
T={}
for y in ('2026','2027','2028'):
    for n,mo,da,h,mi in K[y]['terms']:
        yy=int(y); T.setdefault(yy,[]).append([n,f'{yy}-{int(mo):02d}-{int(da):02d}',f'{int(h):02d}:{int(mi):02d}','kasi'])
def near(t,m=15):
    mid=dt.datetime(t.year,t.month,t.day); s=(t-mid).total_seconds()/60
    return min(s,1440-s)<m
skipped=[]
for y in (2029,2030):
    seen=set()
    for n,t in astro.terms(y-1)+astro.terms(y):
        if t.year!=y or (n,t.date()) in seen: continue
        seen.add((n,t.date()))
        if near(t): skipped.append((n,t.isoformat())); continue
        T.setdefault(y,[]).append([n,t.strftime('%Y-%m-%d'),t.strftime('%H:%M'),'calc'])
for y in T: T[y].sort(key=lambda x:x[1])
misc={}
for y in ('2026','2027','2028'):
    misc[int(y)]=[[n,f'{int(y)}-{int(m):02d}-{int(d):02d}'] for n,m,d in K[y]['misc']]
days=json.load(open('kasi_common_days.json'))
out=dict(months=months,terms=T,misc=misc,days=days,skipped=skipped)
json.dump(out,open('export.json','w'),ensure_ascii=False)
print({y:len(v) for y,v in T.items()}, skipped, len(months))
