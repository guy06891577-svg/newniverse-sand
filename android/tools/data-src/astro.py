import ephem, math, datetime as dt
KST=dt.timedelta(hours=9)
def to_kst(d): return ephem.Date(d).datetime()+KST
def sun_lon(d):
    s=ephem.Sun(); s.compute(d, epoch=d)
    return math.degrees(ephem.Ecliptic(s, epoch=d).lon)%360
def find_term(year,k):  # k: 0..23 term index, longitude 315 + 15k -> (k=0 입춘 315)
    target=(315+15*k)%360
    # approx date
    base=ephem.Date(dt.datetime(year,2,4)); d0=base+ k*15.2184
    lo,hi=d0-10,d0+10
    for _ in range(60):
        mid=(lo+hi)/2
        diff=(sun_lon(mid)-target+180)%360-180
        if diff<0: lo=mid
        else: hi=mid
    return to_kst((lo+hi)/2)
TERMS=['입춘','우수','경칩','춘분','청명','곡우','입하','소만','망종','하지','소서','대서','입추','처서','백로','추분','한로','상강','입동','소설','대설','동지','소한','대한']
def terms(year):
    out=[]
    for k in range(24):
        y=year if k<23 else year+1  # 소한 대한 in year+1 when base year starts at 입춘
        out.append((TERMS[k],find_term(year,k)))
    return out
if __name__=='__main__':
    for y in range(2026,2031):
        for n,t in terms(y): print(y,n,t.strftime('%Y-%m-%d %H:%M'))
