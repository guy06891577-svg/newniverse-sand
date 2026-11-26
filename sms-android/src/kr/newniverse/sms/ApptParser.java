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
  static final class R { int kind; String org="", who="", title="", src="", location=""; long when, oldWhen, endMs; boolean hasTime, oldHasTime, trusted, ask; }
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
  // v5: '2026. 11. 27.(금) 16:00' 처럼 날짜 끝 점(.) 뒤에 요일이 오는 모양도 읽음
  static final Pattern DT_RX=Pattern.compile(DATE+"\\.?\\s*(?:[\\(\\[][월화수목금토일][\\)\\]])?"+TIME);
  // v5: ○ 일시: / ○ 장소: / ○ 주제: 처럼 이름표가 붙은 줄
  static final String BUL="^[\\s○●◦◎ㆍ·•■□▶▷▪▫☞※\\-\\*◆◇▲▼]*";
  static final Pattern LBL_WHEN=Pattern.compile("(?m)"+BUL+"(?:행사\\s*)?(?:일\\s*시|일\\s*정|일\\s*자|날\\s*짜|시\\s*간)\\s*[:：]\\s*(.+)$");
  static final Pattern LBL_PLACE=Pattern.compile("(?m)"+BUL+"(?:행사\\s*)?(?:장\\s*소|위\\s*치)\\s*[:：]\\s*(.+)$");
  static final Pattern LBL_TOPIC=Pattern.compile("(?m)"+BUL+"(?:주\\s*제|제\\s*목|행사명|프로그램|행사\\s*내용)\\s*[:：]\\s*(.+)$");
  /** 신청 기간·접수 기간·마감 같은 줄의 날짜는 일정이 아님 */
  static final Pattern DEADLINE_LINE=Pattern.compile("(?:신청|접수|모집|제출|등록|지원)\\s*(?:기간|기한|마감)|마감\\s*(?:일|기한)?\\s*[:：]");
  static final Pattern AFTER_DEADLINE=Pattern.compile("^\\s*(?:까지|이내|전까지|마감)");
  // v5: 학교·교육기관
  static final String SCH="학교|유치원|어린이집|학원|교육지원청|교육청|교육원|도서관|대학교?|평생학습관|복지관|문화원|센터";
  static final Pattern SCH_W=Pattern.compile("[가-힣A-Za-z0-9]{2,12}(?:"+SCH+")");
  // 신청·모집 안내(자동으로 넣지 않고 ‘넣을까요?’ 하고 물음)
  static final Pattern APPLY=Pattern.compile("신청|모집|선착순|네이버\\s*폼|구글\\s*폼|폼\\s*(?:으로|을|링크)|접수|참가\\s*희망|참여\\s*희망|희망하시는|응모|수강\\s*(?:신청|생)|지원\\s*바랍");

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
      if(AFTER_DEADLINE.matcher(b.substring(m.end())).find()) continue; // '11월 26일(목)까지' = 마감일
      { int ls=b.lastIndexOf('\n',m.start())+1; int le=b.indexOf('\n',m.end()); if(le<0) le=b.length();
        if(DEADLINE_LINE.matcher(b.substring(ls,le)).find()) continue; }
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
  static boolean isLandline(String addr){
    String d=addr==null?"":addr.replaceAll("[^0-9+]","");
    if(d.startsWith("+82")) d="0"+d.substring(3); else if(d.startsWith("82")&&d.length()>=10) d="0"+d.substring(2);
    return d.matches("0(?:2|3[1-3]|4[1-4]|5[1-5]|6[1-4]|70)\\d{7,8}");
  }
  static String clean(String x){ return x==null?"":x.replaceAll("\\s+"," ").trim(); }
  static String first(Pattern p,String b){ Matcher m=p.matcher(b); return m.find()?clean(m.group(1)):""; }
  /** 문자 맨 앞 [기관] 이름표 (Web발신 같은 건 건너뜀) */
  static String tag(String b){
    Matcher m=BRACKET.matcher(b);
    while(m.find()){ String t=m.group(1).trim(); if(t.matches(".*(Web발신|국제발신|발신|광고|MMS|알림톡).*")) continue; return t; }
    return "";
  }
  /** 일시: 줄에서 시작(+끝) 시각 */
  static R event(String b,long at,String contact,String addr){
    String when=first(LBL_WHEN,b); if(when.isEmpty()) return null;
    List<DT> L=dts(when,at); if(L.isEmpty()) return null;
    DT t=L.get(0);
    R r=new R(); r.kind=NEW; r.when=t.ms; r.hasTime=t.hasTime; r.src="event";
    if(t.hasTime){ Matcher e=Pattern.compile("^\\s*[~～\\-–]\\s*(\\d{1,2})\\s*:\\s*(\\d{2})").matcher(when.substring(Math.min(when.length(),endOfFirst(when)))); // 끝 시각
      if(e.find()){ Calendar c=cal(t.ms); c.set(Calendar.HOUR_OF_DAY,i(e.group(1))); c.set(Calendar.MINUTE,i(e.group(2))); if(c.getTimeInMillis()>t.ms&&c.getTimeInMillis()-t.ms<12L*3600*1000) r.endMs=c.getTimeInMillis(); } }
    if(r.when<at-3600L*1000) return null;
    r.location=first(LBL_PLACE,b);
    String tg=tag(b), topic=first(LBL_TOPIC,b);
    if(topic.length()>60) topic=topic.substring(0,60);
    boolean school=!tg.isEmpty()&&SCH_W.matcher(tg).find(), hosp=!tg.isEmpty()&&HOSP_W.matcher(tg).find(), agen=!tg.isEmpty()&&AGEN_W.matcher(tg).find();
    r.org=tg;
    boolean inContacts=contact!=null&&!contact.isEmpty();
    if(hosp||agen){ r.trusted=true; r.src=agen&&!hosp?"agency":"hosp"; }
    else if(school){ r.trusted=inContacts||isLandline(addr); r.src="school"; }
    else if(inContacts){ r.trusted=true; r.src="contact"; if(r.org.isEmpty()) r.org=contact; }
    else r.trusted=false;
    String base=r.org.isEmpty()?"일정":r.org;
    r.title=topic.isEmpty()?(school||!hosp&&!agen?base+" 일정":base+" 예약"):base+" · "+topic;
    r.ask=APPLY.matcher(b).find()&&!CONFIRM.matcher(b).find();
    return r;
  }
  static int endOfFirst(String when){ Matcher m=DT_RX.matcher(when); return m.find()?m.end():when.length(); }
  /** 숫자로 쓴 날짜가 있는지(오늘·내일 같은 말만 있는 건 제외) — 진단 기록을 일상 문자로 어지럽히지 않으려고 */
  static boolean hasDate(String b,long at){ if(b==null) return false; Matcher m=DT_RX.matcher(b); while(m.find()) if(m.group(9)==null) return true; return false; }

  static long dayOf(long ms){ return Math.floorDiv(ms+9L*3600*1000,24L*3600*1000); }

  /** 일정으로 볼 문자면 R, 아니면 null. contact: 보낸 사람이 주소록에 있으면 그 이름(없으면 null) */
  static R parse(String b,long at,String contact){ return parse(b,at,contact,null); }
  /** addr: 보낸 번호(학교가 일반 전화(0xx)로 보낸 건지 확인용) */
  static R parse(String b,long at,String contact,String addr){
    if(b==null||b.trim().length()<8) return null;
    boolean cn=CANCEL_RX.matcher(b).find(), ch=!cn&&CHANGE_RX.matcher(b).find();
    if(!cn&&!ch&&!CONFIRM.matcher(b).find()){ R ev=event(b,at,contact,addr); if(ev!=null) return ev; }
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
    r.location=first(LBL_PLACE,b);
    return r;
  }
}
