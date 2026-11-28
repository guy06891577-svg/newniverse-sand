package kr.newniverse.sms;

import java.time.*;
import java.util.*;
import java.util.regex.*;

/**
 * v10: 공과금·자동이체·카드 대금 ‘출금 예정’ 문자 읽기 (순수 자바, 폰 안에서만). 「빠른 확인용」.
 * 확실한 것만: 기관 이름(대괄호/알려진 기관) + 출금·결제·납기 말 + 금액 하나 + 날짜 하나가 모두 있어야 함.
 * 이미 결제된 카드 사용 문자(승인·일시불·할부)·결제/출금 완료·광고·링크 사칭·연체 독촉·인증번호는 제외.
 */
final class BillParser {
  static final ZoneId KST=DepositParser.KST;
  static final class Bill { String org="", cat="", kind=""; long amt; String due=""; /* yyyy-MM-dd (KST) */ }
  static final class Res { boolean ok; String reason=""; Bill bill; }

  static final String[][] CATS={
    {"전기","한전|한국전력|KEPCO|전기\\s*요금|전기료"},
    {"가스","도시가스|가스공사|가스\\s*요금|가스"},
    {"수도","상하수도|수도\\s*요금|수도사업|수자원|워터|수도"},
    {"통신","SKT|SK\\s*텔레콤|SK\\s*브로드밴드|KT(?![A-Za-z])|LG\\s*U\\+|LGU\\+|LG\\s*유플러스|U\\+|알뜰폰|통신\\s*요금|인터넷\\s*요금|휴대폰\\s*요금|핸드폰\\s*요금|통신"},
    {"카드","[가-힣]{1,4}\\s*카드|카드\\s*대금|카드"},
        {"연금·건보","국민연금|건강보험|국민건강|장기요양|고용보험|산재보험"},
    {"보험","보험|생명|화재|손해|해상|라이프"},
    {"관리비","관리비|아파트|관리사무소"},
  };
  static final Pattern DEBIT_KW=Pattern.compile("출금\\s*예정|자동\\s*이체\\s*(?:예정|안내)|결제\\s*예정|납부\\s*예정|이체\\s*예정|출금\\s*일|결제\\s*일|납기\\s*일|납기|납부\\s*기한|납부\\s*기일|납부\\s*일|이체\\s*일|납입\\s*일|출금\\s*안내");
  static final Pattern PAID=Pattern.compile("승인|일시불|할부\\s*\\d|누적|사용\\s*금액|이용\\s*금액|사용하신|이용하신|결제\\s*(?:완료|되었|됐|처리)|납부\\s*(?:완료|되었|됐)|출금\\s*(?:완료|되었|됐)|출금되|정상\\s*처리|납부하셨|취소|인증\\s*번호|OTP|입금\\s*[\\d,]|잔액\\s*[\\d,]|체크카드");
  static final Pattern URGENT=Pattern.compile("미납|체납|연체|독촉|압류|정지|법적|긴급|해지\\s*예정|최고|추심|대출|한도\\s*(?:조회|상향|증액|초과|가능)|당첨|이벤트|혜택|쿠폰|무료|환급|링크|클릭|바로가기|접속|본인\\s*확인|앱\\s*설치");
  static final Pattern BAL=Pattern.compile("(?:잔액|잔고|한도|포인트|적립|할인|감면|면제)\\s*[:：]?\\s*-?[\\d,]+\\s*(?:원|P|점)?|[\\d,]+\\s*(?:원\\s*)?(?:할인|감면|적립)");
  static final Pattern AMT=Pattern.compile("(\\d{1,3}(?:,\\d{3})+|\\d{3,})\\s*원");
  static final Pattern AMT_LBL=Pattern.compile("(?:결제\\s*예정\\s*금액|출금\\s*예정\\s*금액|출금\\s*금액|청구\\s*금액|납부\\s*금액|납부\\s*하실\\s*금액|납입\\s*금액|이체\\s*금액|결제\\s*금액|요금|보험료|관리비|금액)\\s*[:：]?\\s*(\\d{1,3}(?:,\\d{3})+|\\d{3,})\\s*원?");
  static final Pattern D_FULL=Pattern.compile("(20\\d{2})\\s*[./\\-년]\\s*(\\d{1,2})\\s*[./\\-월]\\s*(\\d{1,2})\\s*일?");
  static final Pattern D_KOR=Pattern.compile("(?<!\\d)(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");
  static final Pattern D_SLASH=Pattern.compile("(?<![\\d/.])(\\d{1,2})/(\\d{1,2})(?![\\d/:])");
  static final Pattern D_DAY=Pattern.compile("(?:매월\\s*|출금일\\s*[:：]?\\s*|결제일\\s*[:：]?\\s*|납기일\\s*[:：]?\\s*)(\\d{1,2})\\s*일");
  static final Pattern DLBL=Pattern.compile("(?:출금\\s*예정\\s*일|결제\\s*예정\\s*일|납부\\s*예정\\s*일|출금\\s*일|결제\\s*일|납기\\s*일|납기|납부\\s*기한|납부\\s*기일|납부\\s*일|이체\\s*일|납입\\s*일|자동\\s*이체\\s*예정|출금\\s*예정|결제\\s*예정)\\s*(?:은|는|:|：)?\\s*");

  static Res no(String why){ Res r=new Res(); r.reason=why; return r; }

  static Res parse(String addr,String body,long recvMs){
    String t=DepositParser.norm(body); if(t.isEmpty()) return no("empty");
    if(t.length()>500) return no("long");
    String f=DepositParser.flat(t);
    if(!DEBIT_KW.matcher(f).find()) return no("no-kw");
    if(SmsSafety.isAd(f)) return no("ad");
    if(SmsSafety.URL.matcher(f).find()||SmsSafety.IPURL.matcher(f).find()||f.toLowerCase(Locale.ROOT).contains(".apk")) return no("url");
    if(URGENT.matcher(f).find()||SmsSafety.suspicious(f)) return no("urgent");
    if(PAID.matcher(f).find()) return no("paid");

    String org=org(f); String cat=cat(org+" "+f);
    if(org.isEmpty()) return no("no-org");
    if(cat.isEmpty()) return no("no-cat");

    String w=BAL.matcher(f).replaceAll(" ");
    long amt=-1; Matcher m=AMT_LBL.matcher(w);
    if(m.find()) amt=DepositParser.toL(m.group(1));
    else{ Set<Long> all=new LinkedHashSet<>(); m=AMT.matcher(w); while(m.find()){ long v=DepositParser.toL(m.group(1)); if(v>0) all.add(v); }
      if(all.size()==1) amt=all.iterator().next(); else if(all.isEmpty()) return no("no-amt"); else return no("ambig-amt"); }
    if(amt<=0) return no("no-amt"); if(amt>10_000_000_000L) return no("range");

    String due=dueDate(f,recvMs); if(due==null) return no("no-date");
    Bill b=new Bill(); b.org=org; b.cat=cat; b.amt=amt; b.due=due; b.kind=kind(f);
    Res r=new Res(); r.ok=true; r.bill=b; r.reason="ok"; return r;
  }

  static String kind(String f){
    if(Pattern.compile("결제\\s*예정|카드").matcher(f).find()&&Pattern.compile("결제\\s*예정|결제\\s*일").matcher(f).find()) return "card";
    if(Pattern.compile("자동\\s*이체|출금\\s*예정|출금\\s*일").matcher(f).find()) return "auto";
    return "due";
  }

  static String org(String f){
    Matcher m=Pattern.compile("\\[([^\\]]{1,16})\\]").matcher(f);
    while(m.find()){ String g=m.group(1).trim().replaceAll("(?:안내|알림|발신|공지)$","").trim(); if(!g.isEmpty()&&!g.matches(".*(?:Web|광고).*")) return clip(g); }
    for(String[] c:CATS){ Matcher k=Pattern.compile(c[1]).matcher(f); if(k.find()&&!c[0].equals("카드")&&!c[0].equals("관리비")) return clip(k.group().trim()); }
    Matcher k=Pattern.compile("[가-힣]{1,4}\\s*카드").matcher(f); if(k.find()) return clip(k.group().replaceAll("\\s",""));
    return "";
  }
  static String clip(String s){ return s.length()>12?s.substring(0,12):s; }
  static String cat(String s){ for(String[] c:CATS) if(Pattern.compile(c[1]).matcher(s).find()) return c[0]; return ""; }

  /** 납기·출금 날짜 (yyyy-MM-dd). 라벨 뒤 날짜 우선, 없으면 문자 속 날짜가 하나뿐일 때만. 못 정하면 null */
  static String dueDate(String f,long recv){
    ZonedDateTime rz=Instant.ofEpochMilli(recv).atZone(KST); LocalDate rd=rz.toLocalDate();
    Matcher l=DLBL.matcher(f);
    while(l.find()){ String rest=f.substring(l.end(), Math.min(f.length(), l.end()+24)); LocalDate d=firstDate(rest,rd,true); if(d!=null) return d.toString(); }
    Matcher dd=D_DAY.matcher(f); if(dd.find()){ LocalDate d=dayOfMonth(Integer.parseInt(dd.group(1)),rd); if(d!=null) return d.toString(); }
    // 라벨 없이: 문자 속 날짜가 하나일 때만
    List<LocalDate> ds=allDates(f,rd); Set<LocalDate> u=new LinkedHashSet<>(ds);
    if(u.size()==1) return u.iterator().next().toString();
    return null;
  }
  static LocalDate firstDate(String s,LocalDate rd,boolean mustStart){
    int best=Integer.MAX_VALUE; LocalDate res=null; Matcher m;
    m=D_FULL.matcher(s); if(m.find()&&m.start()<=(mustStart?3:99)&&m.start()<best){ try{ res=LocalDate.of(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3))); best=m.start(); }catch(Exception e){} }
    m=D_KOR.matcher(s); if(m.find()&&m.start()<=(mustStart?3:99)&&m.start()<best){ LocalDate d=md(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),rd); if(d!=null){ res=d; best=m.start(); } }
    m=D_SLASH.matcher(s); if(m.find()&&m.start()<=(mustStart?3:99)&&m.start()<best){ LocalDate d=md(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),rd); if(d!=null){ res=d; best=m.start(); } }
    if(res==null){ m=Pattern.compile("^\\s*(\\d{1,2})\\s*일").matcher(s); if(m.find()) res=dayOfMonth(Integer.parseInt(m.group(1)),rd); }
    return res;
  }
  static List<LocalDate> allDates(String f,LocalDate rd){
    List<LocalDate> a=new ArrayList<>(); Matcher m=D_FULL.matcher(f);
    while(m.find()){ try{ a.add(LocalDate.of(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)))); }catch(Exception e){} }
    String g=D_FULL.matcher(f).replaceAll(" ");
    m=D_KOR.matcher(g); while(m.find()){ LocalDate d=md(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),rd); if(d!=null) a.add(d); }
    g=D_KOR.matcher(g).replaceAll(" ");
    m=D_SLASH.matcher(g); while(m.find()){ LocalDate d=md(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),rd); if(d!=null) a.add(d); }
    return a;
  }
  /** 연도 없는 월/일 → 받은 날 기준 앞으로 가장 가까운 날(받은 날 30일 전까지는 지난 날짜로 둠) */
  static LocalDate md(int mo,int da,LocalDate rd){
    LocalDate best=null;
    for(int y=rd.getYear()-1;y<=rd.getYear()+1;y++){ try{ LocalDate d=LocalDate.of(y,mo,da);
      long diff=java.time.temporal.ChronoUnit.DAYS.between(rd,d); if(diff>=-30&&diff<=366){ if(best==null||d.isBefore(best)) best=d; } }catch(Exception e){} }
    return best;
  }
  static LocalDate dayOfMonth(int day,LocalDate rd){
    if(day<1||day>31) return null;
    for(int k=0;k<3;k++){ LocalDate base=rd.withDayOfMonth(1).plusMonths(k); try{ LocalDate d=base.withDayOfMonth(Math.min(day,base.lengthOfMonth())); if(!d.isBefore(rd)) return d; }catch(Exception e){} }
    return null;
  }
}
