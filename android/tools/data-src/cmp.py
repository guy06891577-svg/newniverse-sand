import json, datetime as dt
from astro import *
from lunar_gen import months, conv_astro
K=json.load(open('kasi_parsed.json'))
maxd=0; bad=[]
for y in ('2026','2027','2028'):
    d=K[y]
    # lunar months compare
    for m in d['months']:
        s=dt.date.fromisoformat(m['solar']); a=conv_astro(s)
        if a[1]!=m['m'] or a[2]!=1 or a[3]!=m['leap']: bad.append(('month',y,m,a))
    # terms
    for n,mo,da,h,mi in d['terms']:
        mo,da,h,mi=map(int,(mo,da,h,mi))
        # find computed term
        yy=int(y)
        for T in (terms(yy-1)+terms(yy)):
            if T[0]==n and T[1].year==yy and T[1].month==mo:
                diff=(T[1]-dt.datetime(yy,mo,da,h,mi)).total_seconds()/60
                maxd=max(maxd,abs(diff))
                if T[1].day!=da: bad.append(('termday',y,n,T[1],(mo,da,h,mi)))
print('max term diff (min):',maxd); print('bad',bad)
