package kr.newniverse.sms;

import java.util.*;
import java.util.regex.*;

/**
 * v2: 문자에서 일정(병원·공공기관 예약, 택배 도착 예정)을 뽑는 규칙(순수 자바, 폰 안에서만).
 * kind: NEW 예약 확정/안내 · CHANGE 예약 변경 · CANCEL 예약 취소 · PARCEL 택배 도착 예정(내일 이후)
 */
final class ApptParser {
  static final int NEW=1, CHANGE=2, CANCEL=3, PARCEL=4;
  static final TimeZone TZ=TimeZone.getTimeZone("Asia/Seoul");
  static final class R { int kind; String org="", who="", title="", src=""; long when, oldWhen; boolean hasTime, oldHasTime, trusted; }
  static final class DT { long ms; boolean hasTime; }

  static final String HOSP="병원|의원|치과|한의원|의료원|클리닉|내과|외과|안과|피부과|이비인후과|정형외과|소아과|산부인과|비뇨기과|검진센터";
  static final String AGEN="보건소|주민센터|행정복지센터|시청|구청|군청|도청|건강보험공단|공단|세무서|법원|등기소|면허시험장|교육청|경찰서|출입국[가-힣]*사무소";
  static final Pattern HOSP_W=Pattern.compile("[가-힣A-Za-z0-9]{2,12}(?:"+HOSP+")");
  static final Pattern AGEN_W=Pattern.compile("[가-힣A-Za-z0-9]{2,12}(?:"+AGEN+")");
  static final Pattern BRACKET=Pattern.compile("[\\[【]([^\\]】]{2,20})[\\]】]");
  static final Pattern COMPANY=Pattern.compile("CJ\\s*대한통운|대한통운|롯데택배|롯데글로벌로지스|한진택배|로젠택배|우체국택배|우체국|쿠팡|경동택배|대신택배|GS25\\s*택배|CU\\s*택배|마켓컬리|컬리");
  static final Pattern CONFIRM=Pattern.compile("예약\\s*(?:되었|됐|확인|확정|안내|완료|접수|일시|내역|시간|일정|알림)|예약일|예약하신|진료\\s*(?:일시|예정|안내|예약)|내원|방문\\s*(?:예정|일시|예약)|접종\\s*예약|검진\\s*(?:예약|일정)|리마인드|진료\\s*시간");
  static final Pattern CANCEL_RX=Pattern.compile("취소\\s*(?:되었|됐|완료|처리|되어|접수|됨)|예약[^.\\n]{0,25}취소\\s*(?:되|됨|완료)");
  static final Pattern CHANGE_RX=Pattern.compile("(?:변경|조정|연기)\\s*(?:되었|됐|완료|안내|처리|되어|됨)|(?:으)?로\\s*변경");
  static final Pattern WHO=Pattern.compile("([가-힣]{2,4})\\s*(?:님|환자)");
  static final Pattern PARCEL_RX=Pattern.compile("택배|배송|배달|도착");
  static final Pattern DELIVERED=Pattern.compile("배송\\s*완료|배달\\s*완료|전달\\s*완료|수령\\s*완료|배송\\s*출발|배송\\s*중|오늘\\s*(?:\\([^)]*\\))?\\s*(?:도착|배송|배달)");
  // 날짜: [1..3] 2026.12.03  [4..5] 12/3 · 12월 5일  [6..8] 2026년 12월 5일  [9] 오늘·내일·모레
  static final String DATE="(?<![\\d])(?:(\\d{4})[.\\-/]\\s?(\\d{1,2})[.\\-/]\\s?(\\d{1,2})|(\\d{4})\\s?년\\s?(\\d{1,2})\\s?월\\s?(\\d{1,2})\\s?일?|(\\d{1,2})\\s?[./월]\\s?(\\d{1,2})\\s?일?|(오늘|내일|모레|글피)(?:\\s*[\\(\\[]\\d{1,2}\\s*/\\s*\\d{1,2}[\\)\\]])?)(?!\\d)";
  static final String TIME="(?:[\\s,·]*(?:[\\(\\[][월화수목금토일][\\)\\]])?[\\s,·]*(?:(오전|오후|저녁|낮|아침)\\s*)?(\\d{1,2})\\s*(?::\\s*(\\d{2})|시\\s*(?:(\\d{1,2})\\s*분|(반))?))?";
  static final Pattern DT_RX=Pattern.compile(DATE+"\\s*(?:[\\(\\[][월화수목금토일][\\)\\]])?"+TIME);

  static Calendar cal(long ms){ Calendar c=Calendar.getInstance(TZ); c.setTimeInMillis(ms); return c; }

  static List<DT> dts(String b,long at){
    List<DT> out=new ArrayList<>(); Matcher m=DT_RX.matcher(b);
    while(m.find()){
      int y,mo,d; Calendar now=cal(at); boolean noYear=false;
      if(m.group(1)!=null){ y=i(m.group(1)); mo=i(m.group(2)); d=i(m.group(3)); }
      else if(m.group(4)!=null){ y=i(m.group(4)); mo=i(m.group(5)); d=i(m.group(6)); }
      else if(m.group(7)!=null){ y=now.get(Calendar.YEAR); mo=i(m.group(7)); d=i(m.group(8)); noYear=true; }
      else { Calendar c=cal(at); String r=m.group(9); int add=r.equals("오늘")?0:r.equals("내일")?1:r.equals("모레")?2:3; c.add(Calendar.DATE,add); y=c.get(Calendar.YEAR); mo=c.get(Calendar.MONTH)+1; d=c.get(Calendar.DATE); }
      if(mo<1||mo>12||d<1||d>31) continue;
      boolean ht=m.group(11)!=null; int h=0,mi=0;
      if(ht){ h=i(m.group(11)); if(m.group(12)!=null) mi=i(m.group(12)); else if(m.group(13)!=null) mi=i(m.group(13)); else if(m.group(14)!=null) mi=30;
        String ap=m.group(10);
        if("오후".equals(ap)||"저녁".equals(ap)){ if(h<12) h+=12; } else if("오전".equals(ap)||"아침".equals(ap)){ if(h==12) h=0; } else if(h>=1&&h<=6) h+=12;
        if(h>23||mi>59){ ht=false; h=0; mi=0; } }
      Calendar c=Calendar.getInstance(TZ); c.clear(); c.set(y,mo-1,d,h,mi,0);
      if(noYear&&c.getTimeInMillis()<at-45L*24*3600*1000) c.set(Calendar.YEAR,y+1);
      DT t=new DT(); t.ms=c.getTimeInMillis(); t.hasTime=ht; out.add(t);
    }
    return out;
  }
  static int i(String s){ try{ return Integer.parseInt(s.trim()); }catch(Exception e){ return 0; } }

  static String org(String b){
    Matcher m=BRACKET.matcher(b);
    while(m.find()){ String t=m.group(1).trim(); if(t.matches(".*(Web발신|국제발신|발신|광고|MMS|알림톡).*")&&!HOSP_W.matcher(t).find()) continue;
      if(HOSP_W.matcher(t).find()||AGEN_W.matcher(t).find()) return t; }
    Matcher h=HOSP_W.matcher(b); if(h.find()) return h.group();
    Matcher a=AGEN_W.matcher(b); if(a.find()) return a.group();
    return "";
  }
  static long dayOf(long ms){ return Math.floorDiv(ms+9L*3600*1000,24L*3600*1000); }

  /** 일정으로 볼 문자면 R, 아니면 null. contact: 보낸 사람이 주소록에 있으면 그 이름(없으면 null) */
  static R parse(String b,long at,String contact){
    if(b==null||b.trim().length()<8) return null;
    List<DT> L=dts(b,at); if(L.isEmpty()) return null;
    R r=new R(); Matcher wm=WHO.matcher(b); if(wm.find()) r.who=wm.group(1);
    String org=org(b);
    // 택배 도착 예정 (내일 이후)
    Matcher cm=COMPANY.matcher(b);
    if(org.isEmpty()&&cm.find()&&PARCEL_RX.matcher(b).find()&&b.contains("예정")&&!DELIVERED.matcher(b).find()){
      DT t=L.get(0); if(dayOf(t.ms)<=dayOf(at)) return null;
      r.kind=PARCEL; r.org=cm.group().replaceAll("\\s+",""); r.when=t.ms; r.hasTime=false; r.src="parcel"; r.trusted=true; r.title="택배 도착 예정 · "+r.org; return r; }
    boolean cancel=CANCEL_RX.matcher(b).find(), change=!cancel&&CHANGE_RX.matcher(b).find(), conf=CONFIRM.matcher(b).find();
    if(!(cancel||change||conf)) return null;
    if(!org.isEmpty()){ r.org=org; r.src=AGEN_W.matcher(org).find()&&!HOSP_W.matcher(org).find()?"agency":"hosp"; r.trusted=true; }
    else if(contact!=null&&!contact.isEmpty()){ r.org=contact; r.src="contact"; r.trusted=true; }
    else { r.org=""; r.src=""; r.trusted=false; }
    if(cancel){ r.kind=CANCEL; DT t=L.get(0); r.when=t.ms; r.hasTime=t.hasTime; }
    else if(change){ r.kind=CHANGE; DT n=L.get(L.size()-1); r.when=n.ms; r.hasTime=n.hasTime; if(L.size()>=2){ DT o=L.get(0); r.oldWhen=o.ms; r.oldHasTime=o.hasTime; } }
    else { r.kind=NEW; DT p=L.get(0); for(DT t:L) if(t.hasTime){ p=t; break; } r.when=p.ms; r.hasTime=p.hasTime; }
    if(r.kind!=CANCEL&&r.when<at-3600L*1000) return null;
    String base=r.org.isEmpty()?"예약":("contact".equals(r.src)?"예약 · "+r.org:r.org+" 예약");
    r.title=base+(r.who.isEmpty()||"contact".equals(r.src)?"":" · "+r.who);
    return r;
  }
}
