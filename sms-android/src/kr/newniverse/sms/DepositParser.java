package kr.newniverse.sms;

import java.time.*;
import java.util.*;
import java.util.regex.*;

/**
 * v11: 은행 입금 문자 읽기 (순수 자바, 폰 안에서만). 「빠른 확인용」 — 은행이 문자 형식을 바꾸면 틀릴 수 있음.
 * 입금으로 세는 조건: ① 은행 이름(문자 앞부분·대괄호·끝, 또는 발신번호 힌트) ② ‘입금’ 말 + 금액 ③ 시각·가린 계좌·잔액 중 하나(문자 모양)
 * 세지 않는 것: 출금·카드 결제·잔액 조회·입금 취소·광고·링크가 든 문자·입금 부탁/예정/확인 요청·개인 번호에서 온 약한 문자.
 * 계좌번호는 읽어도 저장하지 않음(결과에 계좌 칸이 없음).
 */
final class DepositParser {
  static final ZoneId KST=ZoneId.of("Asia/Seoul");
  static final class Dep { String bank="", who="", kind="transfer"; long amount, at; boolean mobile; }
  static final class Res { boolean ok; String reason=""; Dep dep; boolean suspect; }

  // ---- 은행 표 -------------------------------------------------------------
  static final class Bank {
    final String name; final Pattern strong, weak; final String[] nums; final boolean confirmed;
    Bank(String n,String strong,String weak,String[] nums,boolean confirmed){ name=n; this.strong=strong==null?null:Pattern.compile(strong); this.weak=weak==null?null:Pattern.compile(weak); this.nums=nums; this.confirmed=confirmed; }
  }
  static final Bank[] BANKS={
    new Bank("카카오뱅크","카카오\\s*뱅크","카카오(?!\\s*(?:페이|톡|T\\b|택시|모빌))",new String[]{"15993333"},true),
    new Bank("케이뱅크","케이\\s*뱅크|K\\s*뱅크","케이",new String[]{"15221000"},false),
    new Bank("토스뱅크","토스\\s*뱅크","토스",new String[]{"16617654"},false),
    new Bank("KB국민","KB\\s*국민|국민은행|KB(?![A-Za-z가-힣])","국민",new String[]{"15889999","16449999"},true),
    new Bank("신한","신한\\s*은행|신한(?!\\s*카드)(?=[\\s,\\d\\[\\]])","신한",new String[]{"15778000"},true),
    new Bank("우리","우리\\s*은행","우리(?!\\s*카드)",new String[]{"15885000"},true),
    new Bank("하나","하나\\s*은행","하나(?!\\s*카드)",new String[]{"15991111"},true),
    new Bank("IBK기업","IBK|기업\\s*은행","기업",new String[]{"15662566"},true),
    new Bank("NH농협","NH\\s*농협|농협\\s*은행|농협(?!\\s*(?:카드|택배))|축협","농협|NH",new String[]{"15882100","15442100","16612100","15222100","16613000","15223000"},true),
    new Bank("iM뱅크","iM\\s*뱅크|대구\\s*은행|iM(?=[\\s,\\d\\[])","대구",new String[]{"15665050"},false),
    new Bank("부산","부산\\s*은행","부산",new String[]{"15886200"},false),
    new Bank("경남","경남\\s*은행","경남",new String[]{"16008585"},false),
    new Bank("광주","광주\\s*은행","광주",new String[]{"15883388"},false),
    new Bank("전북","전북\\s*은행","전북",new String[]{"15884477"},false),
    new Bank("제주","제주\\s*은행","제주",new String[]{"15880079"},false),
    new Bank("SC제일","SC\\s*제일|SC\\s*은행","제일",new String[]{"15881599"},false),
    new Bank("씨티","씨티\\s*은행|한국씨티|Citi\\s*bank","씨티",new String[]{"15887000"},false),
    new Bank("수협","수협\\s*은행|수협","수협",new String[]{"15881515"},false),
    new Bank("신협","신협","신협",new String[]{"15666000"},false),
    new Bank("새마을금고","새마을\\s*금고|MG\\s*새마을","새마을",new String[]{"15999000"},true),
    new Bank("우체국","우체국(?!\\s*택배)","우체국(?!\\s*택배)",new String[]{"15881900"},false),
    new Bank("산업","산업\\s*은행|KDB","산업",new String[]{"15881500"},false),
  };
  static final Pattern LOCAL_COOP=Pattern.compile("([가-힣]{2,6})(?:농협|축협)(?!\\s*(?:카드|택배|은행))");

  // ---- 패턴 ------------------------------------------------------------------
  static final Pattern TAGS=Pattern.compile("\\[\\s*(?:Web\\s*발신|국제\\s*발신|국외\\s*발신)\\s*\\]");
  static final Pattern DEP_KW=Pattern.compile("입금(?!\\s*(?:자|처|계좌|은행|통장|취소|예정|요청|확인|내역|기한|기간|마감|안내|부탁|바랍|하세요|하시|해\\s*주|해주|하여|하면|하고|하셔|하라|후|전))|입금되|입금\\s*되");
  static final Pattern REQUEST=Pattern.compile("입금\\s*(?:해\\s*주|해주|하세요|하시|바랍|부탁|요청|예정|기한|기간|마감|안내|확인|후|전)|입금하|계좌로\\s*입금|무통장|입금\\s*계좌");
  static final Pattern OUT=Pattern.compile("(?<!대)출금|인출|결제|승인|일시불|할부|체크카드|카드|사용|납부|취소|정정|반환|오입금|환불요청|해지");
  static final Pattern PHISH=Pattern.compile("클릭|링크|바로가기|접속|앱\\s*설치|본인\\s*확인|인증\\s*번호|대출\\s*(?:한도|금리|상담|가능|승인|신청|문의|안내)|한도\\s*(?:조회|상향|증액|초과|가능)|저금리|당첨|상담\\s*(?:원|전화|문의)|입금\\s*확인\\s*요|확인\\s*바랍|문의\\s*바랍|전화\\s*주|연락\\s*주|미수령|수령\\s*(?:하|신청)");
  static final Pattern MASK=Pattern.compile("\\d[\\d\\-]*[*＊xX●○]+[\\d\\-*＊●○]*|[*＊●○]+[\\d\\-]*\\d");
  static final Pattern ACCT=Pattern.compile("\\d{2,6}-\\d{2,6}-\\d{2,8}(?:-\\d{1,3})?");
  static final Pattern PAREN4=Pattern.compile("\\(\\s*\\d{3,6}\\s*\\)");
  static final Pattern BAL=Pattern.compile("(?:잔액|잔고)\\s*[:：]?\\s*-?\\s*[\\d,]+\\s*원?");
  static final Pattern FULLDT=Pattern.compile("(20\\d{2})[./\\-](\\d{1,2})[./\\-](\\d{1,2})(?:[ ,T]*(\\d{1,2}):(\\d{2})(?::\\d{2})?)?");
  static final Pattern MDT=Pattern.compile("(?<![\\d/.])(\\d{1,2})/(\\d{1,2})(?:[ ,]*(\\d{1,2}):(\\d{2}))?(?![\\d/])");
  static final Pattern TIMEONLY=Pattern.compile("(?<![\\d:])(\\d{1,2}):(\\d{2})(?![\\d:])");
  static final Pattern AMT_A=Pattern.compile("입금(?:액|금액)?\\s*[:：]?\\s*(?:KRW\\s*)?(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*(원)?");
  static final Pattern AMT_B=Pattern.compile("(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*(원)?\\s*(?:이\\s*|이체\\s*|전자금융\\s*)?입금");
  static final Pattern AMT_SENT=Pattern.compile("(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원을?\\s*(?:보냈|보내|입금|송금|받았|받으)");
  static final Pattern PHONE_MOBILE=Pattern.compile("^01[016789]\\d{7,8}$");

  static String norm(String b){
    if(b==null) return "";
    StringBuilder s=new StringBuilder(b.length());
    for(int i=0;i<b.length();i++){ char c=b.charAt(i);
      if(c=='\u00a0'||c=='\u3000'||c=='\u200b'||c=='\ufeff') c=' ';
      else if(c>=0xFF01&&c<=0xFF5E) c=(char)(c-0xFEE0);
      s.append(c); }
    String t=TAGS.matcher(s.toString().replace("\r\n","\n").replace('\r','\n')).replaceAll("");
    return t.trim();
  }
  static String flat(String t){ return t.replaceAll("\\s*\\n+\\s*"," ").replaceAll("[ \\t]{2,}"," ").trim(); }
  static String digits(String s){ return s==null?"":s.replaceAll("[^0-9]",""); }

  static Res no(String why){ Res r=new Res(); r.reason=why; return r; }
  static Res suspect(String why){ Res r=no(why); r.suspect=true; return r; }

  /** 입금 문자인지 읽음. recvMs = 폰이 받은 때(ms) */
  static Res parse(String addr,String body,long recvMs){
    String t=norm(body); if(t.isEmpty()) return no("empty");
    if(t.length()>400) return no("long");
    String f=flat(t);
    boolean hasDep=DEP_KW.matcher(f).find()||AMT_SENT.matcher(f).find();
    if(!hasDep) return no("no-kw");
    // 광고·링크·사칭: ‘입금’이라 쓰여 있어도 세지 않고 ‘뺀 문자’로만 셈
    if(SmsSafety.isAd(f)) return suspect("ad");
    if(SmsSafety.URL.matcher(f).find()||SmsSafety.IPURL.matcher(f).find()||f.toLowerCase(Locale.ROOT).contains(".apk")) return suspect("url");
    if(PHISH.matcher(f).find()) return suspect("phish");
    if(REQUEST.matcher(f).find()) return no("request");
    if(OUT.matcher(f).find()) return no("withdraw");

    // 은행
    String bankName=null; boolean confirmed=false; boolean strongTag=false;
    Object[] bk=findBank(f);
    if(bk!=null){ bankName=(String)bk[0]; confirmed=(Boolean)bk[1]; strongTag=(Boolean)bk[2]; }
    String an=digits(addr);
    if(bankName==null){ for(Bank b:BANKS) for(String n:b.nums) if(!an.isEmpty()&&an.endsWith(n)){ bankName=b.name; confirmed=false; break; } }
    if(bankName==null) return no("no-bank");

    // 문자 모양: 시각/가린 계좌/잔액 중 하나 (인터넷은행의 ‘님이 ○○원을 보냈어요’ 는 예외)
    boolean hasTime=FULLDT.matcher(f).find()||MDT.matcher(f).find()||TIMEONLY.matcher(f).find();
    boolean hasMask=MASK.matcher(f).find()||ACCT.matcher(f).find()||PAREN4.matcher(f).find();
    boolean hasBal=BAL.matcher(f).find();
    boolean sentStyle=AMT_SENT.matcher(f).find()&&strongTag&&(bankName.equals("토스뱅크")||bankName.equals("카카오뱅크")||bankName.equals("케이뱅크"));
    if(!(hasTime||hasMask||hasBal||sentStyle)) return no("no-struct");

    // 개인 휴대전화 번호(010…)에서 온 문자는 세지 않음 — ‘가짜 입금 문자’ 흉내를 막기 위해(은행 알림은 대표번호로 옴)
    boolean mobile=PHONE_MOBILE.matcher(an).matches();
    if(mobile) return suspect("mobile");

    // 금액 (잔액·계좌·날짜를 먼저 지운 글에서)
    String w=f;
    w=BAL.matcher(w).replaceAll(" ");
    w=FULLDT.matcher(w).replaceAll(" ");
    w=MDT.matcher(w).replaceAll(" ");
    w=TIMEONLY.matcher(w).replaceAll(" ");
    w=MASK.matcher(w).replaceAll(" ");
    w=ACCT.matcher(w).replaceAll(" ");
    w=PAREN4.matcher(w).replaceAll(" ");
    long amt=-1; Matcher m=AMT_A.matcher(w);
    while(m.find()){ if(okAmtToken(m.group(1),m.group(2)!=null)){ amt=toL(m.group(1)); break; } }
    if(amt<0){ m=AMT_B.matcher(w); while(m.find()){ if(okAmtToken(m.group(1),m.group(2)!=null)){ amt=toL(m.group(1)); break; } } }
    if(amt<0){ m=AMT_SENT.matcher(w); if(m.find()) amt=toL(m.group(1)); }
    if(amt<=0) return no("no-amt");
    if(amt>10_000_000_000L) return no("range");

    Dep d=new Dep(); d.bank=bankName; d.amount=amt; d.mobile=mobile;
    d.at=resolveTime(f,recvMs);
    String[] sk=source(f,w,bankName); d.who=sk[0]; d.kind=sk[1];
    Res r=new Res(); r.ok=true; r.dep=d; r.reason=confirmed?"ok":"ok-generic";
    return r;
  }
  static boolean okAmtToken(String tok,boolean hasWon){ return hasWon||tok.indexOf(',')>0; }
  static long toL(String s){ try{ String d=s.replace(",",""); if(d.length()>12) return -1; return Long.parseLong(d); }catch(Exception e){ return -1; } }

  /** [이름, 형식 확인 여부, 이름 표식이 확실한지] */
  static Object[] findBank(String f){
    int bestPos=Integer.MAX_VALUE; Object[] best=null;
    int lastTok=Math.max(f.lastIndexOf(' '),f.lastIndexOf('\n'))+1;
    for(Bank b:BANKS){
      if(b.strong!=null){ Matcher m=b.strong.matcher(f); if(m.find()&&m.start()<bestPos){ bestPos=m.start(); String nm=b.name;
          if(b.name.equals("NH농협")){ Matcher lc=LOCAL_COOP.matcher(f); if(lc.find()&&lc.start()<=m.start()&&lc.end()>=m.end()) nm=lc.group(1)+"농협"; }
          best=new Object[]{nm,b.confirmed,true}; } }
      if(b.weak!=null){
        Matcher m=b.weak.matcher(f);
        while(m.find()){ int s=m.start(); boolean head=s<=14&&(s==0||!Character.isLetterOrDigit(f.charAt(s-1))), br=insideBracket(f,s), tail=s>=lastTok&&m.end()==f.length();
          if((head||br||tail)&&s<bestPos){ bestPos=s; best=new Object[]{b.name,b.confirmed,false}; } break; }
      }
    }
    return best;
  }
  static boolean insideBracket(String f,int pos){ int o=f.lastIndexOf('[',pos); if(o<0) return false; int c=f.indexOf(']',o); return c>=pos; }

  // ---- 시각 ----------------------------------------------------------------
  static long resolveTime(String f,long recv){
    ZonedDateTime rz=Instant.ofEpochMilli(recv).atZone(KST);
    Matcher m=FULLDT.matcher(f);
    try{
      if(m.find()){
        int y=Integer.parseInt(m.group(1)),mo=Integer.parseInt(m.group(2)),da=Integer.parseInt(m.group(3));
        int h=m.group(4)==null?12:Integer.parseInt(m.group(4)),mi=m.group(5)==null?0:Integer.parseInt(m.group(5));
        long t=ZonedDateTime.of(y,mo,da,h,mi,0,0,KST).toInstant().toEpochMilli();
        return plausible(t,recv)?t:recv;
      }
      m=MDT.matcher(f);
      if(m.find()){
        int mo=Integer.parseInt(m.group(1)),da=Integer.parseInt(m.group(2));
        boolean hasT=m.group(3)!=null; int h=hasT?Integer.parseInt(m.group(3)):12,mi=hasT?Integer.parseInt(m.group(4)):0;
        long best=Long.MIN_VALUE;
        for(int y=rz.getYear()-1;y<=rz.getYear()+1;y++){
          try{ long t=ZonedDateTime.of(y,mo,da,h,mi,0,0,KST).toInstant().toEpochMilli();
            if(t<=recv+36L*3600_000L&&t>best) best=t; }catch(Exception e){} }
        if(best!=Long.MIN_VALUE&&plausible(best,recv)){
          if(!hasT&&best/86400000L==recv/86400000L) return recv; return best; }
        return recv;
      }
      m=TIMEONLY.matcher(f);
      if(m.find()){
        int h=Integer.parseInt(m.group(1)),mi=Integer.parseInt(m.group(2));
        if(h<24&&mi<60){ ZonedDateTime z=rz.withHour(h).withMinute(mi).withSecond(0).withNano(0);
          long t=z.toInstant().toEpochMilli(); if(t>recv+36L*3600_000L) t=z.minusDays(1).toInstant().toEpochMilli();
          return plausible(t,recv)?t:recv; }
      }
    }catch(Exception e){}
    return recv;
  }
  /** 문자 속 시각이 받은 때보다 7일보다 더 옛날이거나 36시간보다 미래면 믿지 않음 */
  static boolean plausible(long t,long recv){ return t<=recv+36L*3600_000L&&t>=recv-7L*86400_000L; }

  // ---- 보낸 사람 -------------------------------------------------------------
  static final Pattern LBL1=Pattern.compile("(?:입금자|보낸\\s*분|보낸\\s*사람|송금인)\\s*[:：]?\\s*([^\\s,]{1,16})");
  static final Pattern LBL2=Pattern.compile("([^\\s,\\[\\]]{1,12})님이\\s*(?:\\d|[\\d,]+원)");
  static final Pattern STOP=Pattern.compile("^(?:입금|전자금융입금|전자금융|타행|모바일|스마트|인터넷|폰뱅킹|CD|ATM|이체|계좌|님이|님|원|잔액|거래|입금액|금액|KRW|보냈어요|받았어요|내|입출금통장|통장|자동|자동입금|자동이체|자동이체입금|급여|[A-Za-z]{0,2})$");
  static String who(String f,String w,String bank){
    Matcher m=LBL1.matcher(f); if(m.find()) return clipName(m.group(1));
    m=LBL2.matcher(f); if(m.find()){ String n=m.group(1); n=n.replaceAll("^[^가-힣A-Za-z0-9*(㈜]+",""); if(!n.isEmpty()) return clipName(n); }
    String s=LOCAL_COOP.matcher(w.replace("(주)","㈜").replace("(유)","㈜")).replaceAll(" ");
    for(Bank b:BANKS){ if(b.strong!=null) s=b.strong.matcher(s).replaceAll(" "); s=dropWeak(s,b); }
    s=s.replaceAll("입금\\s*(?:액|금액)?\\s*[:：]?\\s*(?:\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원?"," ")
       .replaceAll("(?:\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원[을이]?"," ")
       .replaceAll("[\\[\\](){}<>/,·:：→]"," ");
    String[] tk=s.trim().split("\\s+");
    List<String> keep=new ArrayList<>();
    for(String x:tk){ if(x.isEmpty()||x.matches("[\\d,\\-.]+")||STOP.matcher(x).matches()) continue;
      x=x.replaceAll("(?:입금|전자금융입금|타행입금|이체입금)$","").replaceAll("님$",""); if(x.isEmpty()||STOP.matcher(x).matches()) continue; keep.add(x); }
    if(keep.isEmpty()) return "";
    String a=keep.get(0);
    if((a.startsWith("(주)")||a.startsWith("㈜")||a.equals("주식회사")||a.equals("(주)"))&&keep.size()>1&&a.length()<=4) a=a+keep.get(1);
    return clipName(a);
  }
  /** 이름 속 글자(이하나의 ‘하나’)를 건드리지 않게, 문자 앞부분·대괄호·끝에 있는 은행 약칭만 지움 */
  static String dropWeak(String s,Bank b){
    if(b.weak==null) return s; Matcher m=b.weak.matcher(s); int lastTok=Math.max(s.lastIndexOf(' '),s.lastIndexOf('\n'))+1;
    StringBuilder o=new StringBuilder(); int p=0; boolean done=false;
    while(!done&&m.find()){ int st=m.start(); boolean head=st<=14&&(st==0||!Character.isLetter(s.charAt(st-1))||s.charAt(st-1)==']'||s.charAt(st-1)==' '||s.charAt(st-1)==','), br=insideBracket(s,st), tail=st>=lastTok&&m.end()==s.length();
      if(head||br||tail){ o.append(s,p,st).append(' '); p=m.end(); done=true; } }
    o.append(s.substring(p)); return o.toString();
  }
  static String clipName(String n){ n=n==null?"":n.trim(); return n.length()>14?n.substring(0,14):n; }

  /** 자동입금17,696원(KB112150583) 처럼 금액 바로 뒤 괄호가 보낸 곳 */
  static final Pattern SRC_PAREN=Pattern.compile("(?:자동\\s*)?입금\\s*[:：]?\\s*(?:\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원?\\s*[\\(（]\\s*([^\\)）]{1,24})\\s*[\\)）]");
  static final Pattern CARD_ID=Pattern.compile("^([A-Za-z]{2,3})(\\d{5,})$");
  /** 735439339B 처럼 숫자 덩어리+끝 영문 1~2자 — 거래 코드로 보고 보낸 사람에서 뺌 */
  static final Pattern TXN=Pattern.compile("^\\d{6,}[A-Za-z]{1,2}$");

  /** [표시 이름, 종류 transfer/card/voucher] */
  static String[] source(String f,String w,String bank){
    Matcher m=SRC_PAREN.matcher(f);
    if(m.find()) return labelSrc(m.group(1).trim());
    return labelSrc(who(f,w,bank));
  }
  static String[] labelSrc(String raw){
    if(raw==null) raw="";
    raw=raw.trim();
    if(raw.isEmpty()) return new String[]{"","transfer"};
    // 대표님 확인: (안동정산) = 안동사랑상품권 정산 입금
    if(raw.equals("안동정산")||raw.startsWith("안동사랑")) return new String[]{"안동사랑상품권 정산","voucher"};
    Matcher m=CARD_ID.matcher(raw);
    if(m.matches()){
      String pf=m.group(1).toUpperCase(Locale.ROOT);
      String nm=cardName(pf);
      return new String[]{nm!=null?nm+" 정산":raw,"card"};
    }
    if(TXN.matcher(raw).matches()) return new String[]{"","transfer"};
    return new String[]{clipName(raw),"transfer"};
  }
  /** 공개 자료·실물 문자로 확인된 카드사 접두어만 쉬운 이름으로. 그 외는 null(원문 그대로). */
  static String cardName(String pf){
    switch(pf){
      case "KB": return "KB카드";   // 실물 문자(KB112150583)·Payple/PortOne 약칭
      case "SH": return "신한카드"; // 대표님 예시(Shinhan 관용 약칭). KCP 공개 코드는 LG
      case "LG": return "신한카드"; // NHN KCP 공개 카드코드 CCLG(구 LG카드)
      case "HN": return "하나카드"; // NHN KCP CCHN
      case "BC": return "BC카드";   // NHN KCP CCBC, Payple/PortOne
      case "SS": return "삼성카드"; // NHN KCP CCSS
      case "LO": return "롯데카드"; // NHN KCP CCLO
      case "HD": return "현대카드"; // 대표님 예시(Hyundai 관용 약칭). KCP 공개 코드는 DI
      case "DI": return "현대카드"; // NHN KCP CCDI
      case "NH": return "NH카드";   // NHN KCP CCNH, PortOne
      case "WR": return "우리카드"; // NHN KCP CCWR
      case "CT": return "씨티카드"; // NHN KCP CCCT
      default: return null;
    }
  }
}

