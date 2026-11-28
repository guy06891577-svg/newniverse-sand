import datetime as dt
from astro import *
import ephem
def near(t,m=15):
    mid=dt.datetime(t.year,t.month,t.day); s=(t-mid).total_seconds()/60
    return min(s,1440-s)<m
for y in (2029,2030):
    for n,t in terms(y)+terms(y-1):
        if t.year in (2029,2030) and near(t): print('TERM edge',n,t)
d=ephem.Date(dt.datetime(2028,12,1))
while True:
    n=ephem.next_new_moon(d); k=to_kst(n)
    if k.year>2031: break
    if near(k): print('NM edge',k)
    d=ephem.Date(n+1)
print('done')
