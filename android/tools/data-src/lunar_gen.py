import ephem, datetime as dt, json
from astro import *
from korean_lunar_calendar import KoreanLunarCalendar
def lib_conv(d):
    c=KoreanLunarCalendar(); assert c.setSolarDate(d.year,d.month,d.day)
    return (c.lunarYear,c.lunarMonth,c.lunarDay,bool(c.isIntercalation))
def newmoons():
    out=[]; d=ephem.Date(dt.datetime(2023,1,1))
    while True:
        n=ephem.next_new_moon(d); k=to_kst(n)
        if k.year>2032: break
        out.append(k); d=ephem.Date(n+1)
    return out
NM=[k.date() for k in newmoons()]
LONG2M={330:1,0:2,30:3,60:4,90:5,120:6,150:7,180:8,210:9,240:10,270:11,300:12}
zq=[]
for y in range(2022,2032):
    for k in range(1,24,2): zq.append((find_term(y,k),(315+15*k)%360))
zq=sorted(set(zq),key=lambda x:x[0])
months=[]
for i in range(len(NM)-1):
    a,b=NM[i],NM[i+1]
    months.append({'a':a,'b':b,'z':[(t,l) for t,l in zq if a<=t.date()<b]})
# anchor: month containing 동지 of 2024 = 11
i0=next(i for i,m in enumerate(months) if any(l==270 and t.year==2024 for t,l in m['z']))
prev=None
for j in range(i0,len(months)):
    m=months[j]
    if not m['z']:
        m['n']=prev['n']; m['leap']=True
    else:
        assert len(m['z'])==1,(m['a'],m['z'])
        m['n']=LONG2M[m['z'][0][1]]; m['leap']=False
    prev=m
months=months[i0:]
# lunar year: month 1 starts new year; 11/12 month of ... year label
y=2024
for m in months:
    if m['n']==1 and not m['leap']: y+=1 if m is not months[0] else 0
    m['y']=y
# fix: year increments at month n==1 (non-leap). months[0] is 11/2024
def conv_astro(d):
    for m in months:
        if m['a']<=d<m['b']: return (m['y'],m['n'],(d-m['a']).days+1,m['leap'])
bad=0; d=dt.date(2025,11,1)
while d<=dt.date(2031,2,28):
    a=conv_astro(d); b=lib_conv(d)
    if a!=b:
        bad+=1
        if bad<15: print('DIFF',d,a,b)
    d+=dt.timedelta(1)
print('diffs',bad)
if __name__=='__main__':
    for m in months:
        if dt.date(2025,11,1)<=m['a']<=dt.date(2031,2,28): print(m['a'],m['y'],m['n'],'윤' if m['leap'] else '',(m['b']-m['a']).days)
