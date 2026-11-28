import re, json
def seg(t, a, b):
    i=t.find(a); j=t.find(b,i+len(a)); return t[i+len(a):j]
def nums(s): return [int(x) for x in re.findall(r'\d+',s)]
out={}
for y in (2026,2027,2028):
    t=open(f'kasi_{y}.txt').read()
    i=t.find('음력(%d'%y)
    lun=seg(t[i:],'일| ','(음력 :')   # table
    # rows: m| d| 대소| 월건| 일진| Y| M| D
    rows=re.findall(r'(윤\|\s*)?(\d{1,2})\| (\d{1,2})\| (대|소)\| (\S+)\| (\S+)\| (\d{4})\| (\d{1,2})\| (\d{1,2})\|',lun+'|')
    months=[]
    for leap,m,d,ds,mg,ij,Y,M,D in rows: months.append(dict(leap=bool(leap),m=int(m),big=(ds=='대'),solar=f'{Y}-{int(M):02d}-{int(D):02d}'))
    terms_txt=seg(t[i:],'24절기| 명칭| 날짜| 월| 일| 시| 분| ','기타 명절')
    terms=re.findall(r'([가-힣]{2,3})\| (\d+)\| (\d+)\| (\d+)\| (\d+)\|',terms_txt)
    misc_txt=seg(t[i:],'기타 명절과 잡절(삼복)| 명칭| 월| 일| ','국경일과 공휴일')
    misc=re.findall(r'([가-힣]+)\| (\d+)\| (\d+)\|',misc_txt)
    hol_txt=seg(t[i:],'국경일과 공휴일| 명칭| 날짜(요일)| 명칭| 날짜(요일)| ','* | 국경일') if '* | 국경일' in t[i:] else seg(t[i:],'국경일과 공휴일| 명칭| 날짜(요일)| 명칭| 날짜(요일)| ','일요일| 월| 일요일')
    out[y]=dict(months=months,terms=terms,misc=misc,hol=hol_txt)
    print(y,len(months),len(terms),misc); print(hol_txt[:900])
json.dump(out,open('kasi_parsed.json','w'),ensure_ascii=False,indent=1)
