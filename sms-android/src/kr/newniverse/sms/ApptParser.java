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
  static final class DT { long ms; boolean hasTime; String line=""; }

  static final String HOSP="병원|의원|치과|한의원|의료원|클리닉|내과|외과|안과|피부과|이비인후과|정형외과|소아과|산부인과|비뇨기과|검진센터|의학과|신경과|신경외과|성형외과|메디컬|요양원";
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
  // ---------- v6: 실제 폰에서 온 글자 모양 정리 ----------
  static final String LABELS="(?:행사\\s*)?(?:일\\s*시|장\\s*소|주\\s*제|대\\s*상|신청\\s*기간|신청\\s*방법|문\\s*의|준비물|참가비|제\\s*목)";
  static final Pattern BULLET_SPLIT=Pattern.compile("[ \\t]*([○●■□▪▫▶▷◆◇☞※•])");
  static final Pattern LABEL_SPLIT=Pattern.compile("[ \\t]+(?="+LABELS+"\\s*:)");
  static final Pattern KBUL=Pattern.compile("(?m)(^|[ \\t])ㅇ\\s*(?="+LABELS+")");
  /**
   * 줄바꿈(CRLF·CR·U+2028)·모든 유니코드 공백(NBSP 등)·0폭 글자·전각 글자(：～．（）０-９)·물결표(〜～∼)·여러 동그라미(⚬◯◦●◎ㅇ)를 한 모양으로.
   * 동그라미나 ‘일시:’ ‘장소:’ 앞에서 줄을 나눠, 줄바꿈이 사라진 한 줄짜리 문자도 줄 단위로 읽게 함. 여러 번 해도 같은 결과.
   */
  static final int MAXLEN=5000;
  static String norm(String s){
    if(s==null) return "";
    if(s.length()>MAXLEN) s=s.substring(0,MAXLEN); // v7: 아주 긴 글에서 정규식이 오래 걸리지 않게
    String t=s.replace("\r\n","\n").replace('\r','\n').replace('\u2028','\n').replace('\u2029','\n').replace('\u0085','\n').replace('\u000B','\n').replace('\u000C','\n');
    StringBuilder sb=new StringBuilder(t.length());
    for(int k=0;k<t.length();k++){
      char ch=t.charAt(k);
      if(ch=='\n'){ sb.append(ch); continue; }
      if((ch>=0x200B&&ch<=0x200F)||ch==0x2060||ch==0xFEFF||ch==0x00AD||(ch>=0x202A&&ch<=0x202E)||ch==0x180E) continue;
      if(ch=='\t'||Character.getType(ch)==Character.SPACE_SEPARATOR){ if(sb.length()>0&&sb.charAt(sb.length()-1)==' ') continue; sb.append(' '); continue; } // 공백은 하나로
      if(ch>=0xFF01&&ch<=0xFF5E){ ch=(char)(ch-0xFEE0); }
      if(ch=='\u301C'||ch=='\u223C'||ch=='\u02DC'||ch=='\u223E') ch='~';
      else if(ch=='\u2013'||ch=='\u2014'||ch=='\u2012'||ch=='\u2212'||ch=='\u2015') ch='-';
      else if(ch=='\u25E6'||ch=='\u26AC'||ch=='\u25EF'||ch=='\u2B58'||ch=='\u25CF'||ch=='\u25CE') ch='○';
      sb.append(ch);
    }
    t=sb.toString();
    t=KBUL.matcher(t).replaceAll("$1○ ");
    t=BULLET_SPLIT.matcher(t).replaceAll("\n$1");
    t=LABEL_SPLIT.matcher(t).replaceAll("\n");
    return t.replaceAll("\n{3,}","\n\n");
  }
  static final Pattern SEG_LABEL=Pattern.compile("^[ \\t○●■□▪▫▶▷◆◇☞※•\\-\\*◦ㆍ·▲▼]*");
  /** 줄 앞의 동그라미·공백을 뗀 줄 */
  static List<String> lines(String n){
    List<String> out=new ArrayList<>();
    for(String ln:n.split("\n")){ String x=SEG_LABEL.matcher(ln).replaceFirst("").trim(); if(!x.isEmpty()) out.add(x); }
    return out;
  }
  /** 이름표로 시작하는 줄의 값. 값에 date 가 있어야 하면 mustDate */
  static String labelValue(List<String> ls,String labelRx,boolean colonRequired,boolean mustDate,long at){
    Pattern p=Pattern.compile("^(?:"+labelRx+")\\s*"+(colonRequired?"[:\\-]":"[:\\-]?")+"\\s*(.*)$");
    for(int k=0;k<ls.size();k++){
      Matcher m=p.matcher(ls.get(k)); if(!m.find()) continue;
      String v=m.group(1).trim();
      if(mustDate){ if(!dts(v,at).isEmpty()) return v; if(v.isEmpty()&&k+1<ls.size()&&!dts(ls.get(k+1),at).isEmpty()) return ls.get(k+1); continue; }
      if(!v.isEmpty()) return v;
    }
    return "";
  }
  static final Pattern TIME_ONLY=Pattern.compile("(?:(오전|오후|저녁|낮|아침)\\s*)?(\\d{1,2})\\s*(?::\\s*(\\d{2})|시\\s*(?:(\\d{1,2})\\s*분|(반))?)");
  /** ‘시간:’ 줄 같은 데서 시각만 뽑기 → 분(0~1439) 또는 -1 */
  static int timeOf(String v){
    if(v==null) return -1; Matcher m=TIME_ONLY.matcher(v); if(!m.find()) return -1;
    int h=i(m.group(2)), mi=m.group(3)!=null?i(m.group(3)):m.group(4)!=null?i(m.group(4)):m.group(5)!=null?30:0; String ap=m.group(1);
    if("오후".equals(ap)||"저녁".equals(ap)){ if(h<12) h+=12; } else if("오전".equals(ap)||"아침".equals(ap)){ if(h==12) h=0; } else if(h>=1&&h<=6) h+=12;
    return h>23||mi>59?-1:h*60+mi;
  }
  /** 일시 줄 찾기 + 날짜·시각 읽기. 없으면 null. why[0] 에 실패 단계 */
  static DT whenOf(List<String> ls,String n,long at){
    String v=labelValue(ls,"(?:행사\\s*)?일\\s*시",false,true,at);
    if(v.isEmpty()) v=labelValue(ls,"(?:행사\\s*)?(?:일\\s*정|일\\s*자|날\\s*짜|개최\\s*일|진행\\s*일|시\\s*간)",false,true,at);
    if(v.isEmpty()){ // 줄 맨 앞이 아니어도 ‘일시’ 바로 뒤에 날짜가 있으면
      Matcher m=Pattern.compile("(?<![가-힣])(?:일\\s*시|일\\s*정)\\s*[:\\-]?\\s*").matcher(n);
      while(m.find()){ int e=n.indexOf('\n',m.end()); String rest=n.substring(m.end(),e<0?n.length():e); if(!dts(rest,at).isEmpty()){ v=rest; break; } }
    }
    if(v.isEmpty()) return null;
    List<DT> L=dts(v,at); if(L.isEmpty()) return null;
    DT t=L.get(0); t.line=v;
    if(!t.hasTime){ // 시각이 다른 줄(시간:)에 있을 수 있음. 없으면 하루 종일
      int mi=timeOf(labelValue(ls,"(?:행사\\s*)?시\\s*간",false,false,at));
      if(mi>=0){ Calendar c=cal(t.ms); c.set(Calendar.HOUR_OF_DAY,mi/60); c.set(Calendar.MINUTE,mi%60); t.ms=c.getTimeInMillis(); t.hasTime=true; }
    }
    return t;
  }
  
  /** 일시: 줄에서 시작(+끝) 시각 */
  static R event(String b,long at,String contact,String addr){
    String n=norm(b); List<String> ls=lines(n);
    DT t=whenOf(ls,n,at); if(t==null) return null;
    String when=t.line;
    R r=new R(); r.kind=NEW; r.when=t.ms; r.hasTime=t.hasTime; r.src="event";
    if(t.hasTime){ Matcher e=Pattern.compile("^\\s*[~\\-]\\s*(\\d{1,2})\\s*:\\s*(\\d{2})").matcher(when.substring(Math.min(when.length(),endOfFirst(when)))); // 끝 시각
      if(e.find()){ Calendar c=cal(t.ms); c.set(Calendar.HOUR_OF_DAY,i(e.group(1))); c.set(Calendar.MINUTE,i(e.group(2))); if(c.getTimeInMillis()>t.ms&&c.getTimeInMillis()-t.ms<12L*3600*1000) r.endMs=c.getTimeInMillis(); } }
    if(r.when<at-3600L*1000) return null;
    r.location=clean(labelValue(ls,"(?:행사\\s*)?(?:장\\s*소|위\\s*치)",true,false,at));
    String tg=tag(n), topic=clean(labelValue(ls,"(?:주\\s*제|제\\s*목|행사명|프로그램|행사\\s*내용)",true,false,at));
    if(topic.length()>60) topic=topic.substring(0,60);
    boolean school=!tg.isEmpty()&&SCH_W.matcher(tg).find(), hosp=!tg.isEmpty()&&HOSP_W.matcher(tg).find(), agen=!tg.isEmpty()&&AGEN_W.matcher(tg).find();
    r.org=tg;
    boolean inContacts=contact!=null&&!contact.isEmpty(); boolean schoolAsk=false;
    if(hosp||agen){ r.trusted=true; r.src=agen&&!hosp?"agency":"hosp"; }
    else if(school){ r.trusted=true; r.src="school"; schoolAsk=!(inContacts||isLandline(addr)); } // v7: 학교 문자는 번호가 주소록에 없어도 ‘달력에 넣을까요?’로 물어봄(자동으로는 안 넣음)
    else if(inContacts){ r.trusted=true; r.src="contact"; if(r.org.isEmpty()) r.org=contact; }
    else r.trusted=false;
    String base=r.org.isEmpty()?"일정":r.org;
    r.title=topic.isEmpty()?(school||!hosp&&!agen?base+" 일정":base+" 예약"):base+" · "+topic;
    r.ask=(APPLY.matcher(n).find()&&!CONFIRM.matcher(n).find())||schoolAsk;
    return r;
  }
  static int endOfFirst(String when){ Matcher m=DT_RX.matcher(when); return m.find()?m.end():when.length(); }
  static int rawDates(String b){ Matcher m=DT_RX.matcher(norm(b)); int c=0; while(m.find()) if(m.group(9)==null) c++; return c; }
  /** 숫자로 쓴 날짜가 있는지(오늘·내일 같은 말만 있는 건 제외) — 진단 기록을 일상 문자로 어지럽히지 않으려고 */
  static boolean hasDate(String b,long at){ return rawDates(b)>0; }
  /**
   * v6: parse()가 null 을 돌려줬을 때 어느 단계에서 막혔는지 (진단용, 본문·번호 없음)
   *  parse:no-date · parse:deadline-only(마감·신청기간 날짜뿐) · parse:no-datetime-line(일시 줄도 예약 확정 말도 없음) · parse:past(이미 지난 일정) · parse:other
   */
  /** v7: 진단용 ‘읽기 단계’ — 읽었으면 parse:ok, 못 읽었으면 why() */
  static String stage(String b,long at,String contact,String addr){
    try{ return parse(b,at,contact,addr)!=null?"parse:ok":why(b,at); }catch(RuntimeException e){ return "parse:error"; }
  }
  /**
   * 못 읽었을 때 막힌 단계 (진단용, 본문·번호 없음):
   *  parse:no-date · parse:deadline-only(마감·신청기간 날짜뿐) · parse:past(날짜는 있는데 모두 이미 지남)
   *  · parse:label-found-no-date(‘일시’ 줄은 있는데 날짜를 못 읽음) · parse:no-datetime-line(‘일시’ 줄도 예약 말도 없음) · parse:other
   */
  static String why(String b,long at){
    String n=norm(b);
    if(rawDates(n)==0) return "parse:no-date";
    List<DT> L=dts(n,at);
    if(L.isEmpty()) return "parse:deadline-only";
    boolean future=false; for(DT t:L) if(t.ms>=at-3600L*1000) future=true;
    if(!future&&!CANCEL_RX.matcher(n).find()) return "parse:past";
    List<String> ls=lines(n);
    DT w=whenOf(ls,n,at);
    if(w!=null) return w.ms<at-3600L*1000?"parse:past":"parse:other";
    if(!labelValue(ls,"(?:행사\\s*)?일\\s*시",false,false,at).isEmpty()) return "parse:label-found-no-date";
    if(!CANCEL_RX.matcher(n).find()&&!CHANGE_RX.matcher(n).find()&&!CONFIRM.matcher(n).find()) return "parse:no-datetime-line";
    return "parse:other";
  }
  /** v8: ‘일시’ · ‘장소’ 이름표 줄이 있는지 (진단용 예/아니요) */
  static String labels(String b,long at){
    try{ String n=norm(b); List<String> ls=lines(n);
      boolean il=!labelValue(ls,"(?:행사\\s*)?일\\s*시",false,false,at).isEmpty();
      boolean jl=!labelValue(ls,"(?:행사\\s*)?(?:장\\s*소|위\\s*치)",true,false,at).isEmpty();
      return "일시 "+(il?"예":"아니요")+" · 장소 "+(jl?"예":"아니요");
    }catch(RuntimeException e){ return "?"; }
  }

  static long dayOf(long ms){ return Math.floorDiv(ms+9L*3600*1000,24L*3600*1000); }

  /** 일정으로 볼 문자면 R, 아니면 null. contact: 보낸 사람이 주소록에 있으면 그 이름(없으면 null) */
  static R parse(String b,long at,String contact){ return parse(b,at,contact,null); }
  /** addr: 보낸 번호(학교가 일반 전화(0xx)로 보낸 건지 확인용) */
  static R parse(String b,long at,String contact,String addr){
    if(b==null) return null;
    b=norm(b);
    if(b.trim().length()<8) return null;
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
