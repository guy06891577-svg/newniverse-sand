package kr.newniverse.sms;

import java.util.*;
import java.util.regex.*;

/**
 * v9: 사람끼리 주고받는 짧은 약속 문자(순수 자바). 예: ‘다음주 목요일 2시에 안동대학교서 봐’ → 12월 3일(목) 오후 2시 · 안동대학교.
 * 규칙(사용자 승인):
 *  날짜  오늘=0 · 내일/낼=+1 · 모레=+2 · 글피=+3.
 *        요일만(목요일·목요·목요일날·목요일에·목욕) = 다가오는 그 요일. 오늘은 시각이 아직 남았을 때만(시각이 없으면 다음 주).
 *        이번주 X = 이번 주(월~일)의 X · 다음주/담주/다음 주 X = 다음 주(월~일)의 X · 다다음주 X = 그다음 주의 X.
 *        주말·다음달·조만간처럼 날짜가 딱 정해지지 않으면 넣지 않음.
 *  시각  N시 · N시 반 · N시 M분 · N:MM. 오전/오후/아침/점심/저녁/밤/새벽/낮 을 따름. 앞말이 없으면 1~7시=오후, 8~11시=오전, 12시=낮 12시.
 *        시각이 없으면 하루 종일 일정.
 *  장소  ‘서/에서/앞에서’ 바로 앞의 이름(예: 안동대학교서 → 안동대학교).
 *  약속 말  봐 · 보자 · 만나 · 만나자 · 약속 · 모임 · 미팅 중 하나가 있어야 함.
 *  제외  지난 일(봤어·만났어 …)과 부정(못 봐·안 돼 …).
 *  사람 문자는 언제나 ‘달력에 넣을까요?’로 물어봄(자동으로 넣지 않음).
 */
final class Casual {
  static final int MAXLEN=400; // 사람끼리 짧은 문자만
  static final String WD="(월|화|수|목|금|토|일)";
  // 요일: 목요일 · 목요일날 · 목요 · 목욕(구어, 목욕탕·목욕하다 제외) — 뒤에 ‘에/날/에는’ 은 따라와도 됨
  static final String WDAY=WD+"(?:요일날|요일|요(?:(?=[에날])|(?![가-힣]))|욕(?![탕하재용물]))";
  static final Pattern WEEK=Pattern.compile("(이번\\s*주|금주|다다음\\s*주|담담\\s*주|다음\\s*주|담\\s*주|차주)\\s*(?:"+WDAY+"|"+WD+"(?![가-힣]))");
  static final Pattern BARE=Pattern.compile("(?<![가-힣])"+WDAY);
  static final Pattern REL=Pattern.compile("(?<![가-힣])(오늘|내일|낼|모레|글피)(?:(?=[에은도밤저아점오])|(?![가-힣]))");
  static final Pattern VAGUE=Pattern.compile("주말|다음\\s*달|담\\s*달|조만간|언제\\s*한번|나중에|다음에\\s*(?:봐|보자|만나)");
  static final String[] KNUM={"열두","열한","열","아홉","여덟","일곱","여섯","다섯","네","세","두","한"};
  static final int[] KVAL={12,11,10,9,8,7,6,5,4,3,2,1};
  static final Pattern TIME=Pattern.compile("(?:(오전|오후|아침|점심|저녁|밤|새벽|낮)\\s*)?(?:(?<!\\d)(\\d{1,2})\\s*:\\s*(\\d{2})|((?<!\\d)\\d{1,2}|(?:(?<=오전|오후|아침|점심|저녁|새벽|밤|낮)|(?<![가-힣]))(?:열두|열한|열|아홉|여덟|일곱|여섯|다섯|네|세|두|한))\\s*시(?![간청장작험계골])\\s*(?:(\\d{1,2})\\s*분|(반))?)");
  static final Pattern CUE=Pattern.compile("봐|보자|만나|약속|모임|미팅");
  // 지난 일 · 부정
  static final Pattern NEG=Pattern.compile("봤|만났|모였|못\\s*(?:봐|보|만나|가|와|갈|올)|안\\s*(?:돼|되|봐|보|만나|가|와)|않|취소|말자|미루자|미뤄|연기|불가");
  // 장소: 이름 + (앞에서|앞|에서|서) + (약속 말). 이름은 띄어쓴 두 낱말까지
  static final Pattern PLACE=Pattern.compile("((?:[가-힣A-Za-z0-9]{1,15}\\s)?[가-힣A-Za-z0-9]{1,15}?)\\s*(?:앞에서|에서|서)\\s*(?:\\d{1,2}\\s*시[^가-힣]*)?(?:봐|보자|만나|모임|미팅|약속)");
  static final Pattern NOT_PLACE=Pattern.compile("(?:\\d|시$|분$|반$|요일|요$|주$|오늘|내일|낼|모레|글피|오전|오후|아침|점심|저녁|밤|새벽|낮|^거기|^여기|^저기|^그때|^이따|^있다|^(?:네|세|두|한|그|이|저|좀|꼭)$)");

  static final class Res { long when; boolean hasTime; String place=""; String why=""; }

  /** 사람 약속 문자면 Res, 아니면 null (why 에 이유를 적고 싶으면 whyNot 사용) */
  static Res find(String n,long at){ Res r=read(n,at); return r!=null&&r.why.isEmpty()?r:null; }
  /** 못 읽은 이유: "" (읽음) · casual-no-day · casual-vague · casual-no-cue · casual-negated · casual-past · casual-long */
  static String whyNot(String n,long at){ Res r=read(n,at); return r==null?"casual-no-day":r.why; }

  static Res read(String n,long at){
    if(n==null) return null;
    Res out=new Res();
    String s=n.replace('\n',' ');
    Calendar now=ApptParser.cal(at);
    Calendar day=null; int dayEnd=-1;
    Matcher w=WEEK.matcher(s);
    if(w.find()){
      String k=w.group(1).replaceAll("\\s+","");
      int add=k.startsWith("이번")||k.equals("금주")?0:(k.startsWith("다다음")||k.startsWith("담담"))?2:1;
      String wd=w.group(2)!=null?w.group(2):w.group(3);
      day=weekDay(now,add,idx(wd)); dayEnd=w.end();
    }
    Matcher rel=REL.matcher(s);
    boolean bare=false; ApptParser.DT numeric=null;
    if(day==null&&rel.find()){ String k=rel.group(1); int add=k.equals("오늘")?0:(k.equals("내일")||k.equals("낼"))?1:k.equals("모레")?2:3;
      day=(Calendar)now.clone(); day.add(Calendar.DATE,add); dayEnd=rel.end(); }
    Matcher b=BARE.matcher(s);
    if(day==null&&b.find()){ bare=true; day=weekDay(now,0,idx(b.group(1))); dayEnd=b.end(); }
    DT0: if(day==null){ java.util.List<ApptParser.DT> L=ApptParser.dts(s,at); // 숫자 날짜(12월 3일 …)도 약속 말이 있으면
      for(ApptParser.DT x:L){ day=ApptParser.cal(x.ms); dayEnd=-1; numeric=x; break DT0; } }
    if(day==null){ if(VAGUE.matcher(s).find()){ out.why="casual-vague"; return out; } return null; }
    if(s.length()>MAXLEN){ out.why="casual-long"; return out; }
    if(NEG.matcher(s).find()){ out.why="casual-negated"; return out; }
    if(!CUE.matcher(s).find()){ out.why=TIME.matcher(s).find()?"casual-no-cue":"casual-no-day"; return out; } // 시각도 없으면 평범한 대화(‘오늘 뭐해?’)로 봄
    // 시각: 날짜 말 뒤에서 먼저, 없으면 앞에서
    int mins=-1; Matcher t=TIME.matcher(s);
    if(numeric!=null){ if(numeric.hasTime){ Calendar q=ApptParser.cal(numeric.ms); mins=q.get(Calendar.HOUR_OF_DAY)*60+q.get(Calendar.MINUTE); } }
    else
    if(t.find(Math.max(0,dayEnd))) mins=minutes(t); else { t=TIME.matcher(s); if(t.find()) mins=minutes(t); }
    boolean ht=mins>=0;
    Calendar c=(Calendar)day.clone(); c.set(Calendar.HOUR_OF_DAY,0); c.set(Calendar.MINUTE,0); c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0);
    if(ht){ if(mins>=24*60){ c.add(Calendar.DATE,1); mins-=24*60; } c.set(Calendar.HOUR_OF_DAY,mins/60); c.set(Calendar.MINUTE,mins%60); }
    if(bare){ // 요일만: 다가오는 그 요일. 오늘이면 시각이 아직 남았을 때만
      boolean today=ApptParser.dayOf(c.getTimeInMillis())==ApptParser.dayOf(at);
      if(ApptParser.dayOf(c.getTimeInMillis())<ApptParser.dayOf(at)||(today&&(!ht||c.getTimeInMillis()<=at))) c.add(Calendar.DATE,7);
    }
    out.when=c.getTimeInMillis(); out.hasTime=ht;
    if(ht?out.when<=at:ApptParser.dayOf(out.when)<ApptParser.dayOf(at)){ out.why="casual-past"; return out; }
    out.place=place(s);
    return out;
  }
  static int idx(String wd){ return "월화수목금토일".indexOf(wd); } // 0=월
  /** 이번 주(월~일) 기준 add 주 뒤의 요일 */
  static Calendar weekDay(Calendar now,int addWeeks,int wd){
    Calendar c=(Calendar)now.clone();
    int today=(c.get(Calendar.DAY_OF_WEEK)+5)%7; // 0=월 … 6=일
    c.add(Calendar.DATE,-today+addWeeks*7+wd);
    return c;
  }
  static int minutes(Matcher t){
    String ap=t.group(1); int h,mi=0;
    if(t.group(2)!=null){ h=ApptParser.i(t.group(2)); mi=ApptParser.i(t.group(3)); }
    else { String hs=t.group(4); h=-1; for(int k=0;k<KNUM.length;k++) if(KNUM[k].equals(hs)) h=KVAL[k]; if(h<0) h=ApptParser.i(hs);
      if(t.group(5)!=null) mi=ApptParser.i(t.group(5)); else if(t.group(6)!=null) mi=30; }
    if(h<0||h>24||mi>59) return -1;
    if(ap==null){ if(h>=1&&h<=7) h+=12; } // 앞말 없음: 1~7시 오후 · 8~11시 오전 · 12시 낮
    else switch(ap){
      case "오후": case "저녁": if(h<12) h+=12; break;
      case "밤": if(h==12) h=24; else if(h>=6&&h<12) h+=12; else if(h<6) h+=24; break; // 밤 12시 = 그날 자정, 밤 1시 = 다음 날 새벽 1시
      case "점심": case "낮": if(h<=5) h+=12; break;
      case "오전": case "아침": case "새벽": if(h==12) h=0; break;
    }
    return h*60+mi;
  }
  static String place(String s){
    Matcher m=PLACE.matcher(s); String best="";
    while(m.find()){
      String[] ws=m.group(1).trim().split("\\s+"); StringBuilder sb=new StringBuilder();
      for(String x:ws){ String y=x.replaceAll("(에|에서)$",""); if(y.equals("앞")) continue; if(y.isEmpty()||NOT_PLACE.matcher(y).find()) { sb.setLength(0); continue; } if(sb.length()>0) sb.append(' '); sb.append(y); }
      if(sb.length()>=1) best=sb.toString();
    }
    return best;
  }
  /** 사람이 읽는 때: ‘12월 3일(목) 오후 2시’ · ‘12월 3일(목) 오후 2시 30분’ · ‘12월 3일(목)’ */
  static String label(long ms,boolean hasTime){
    Calendar c=ApptParser.cal(ms); String[] w={"일","월","화","수","목","금","토"};
    String s=(c.get(Calendar.MONTH)+1)+"월 "+c.get(Calendar.DATE)+"일("+w[c.get(Calendar.DAY_OF_WEEK)-1]+")";
    if(hasTime){ int h=c.get(Calendar.HOUR_OF_DAY), m=c.get(Calendar.MINUTE);
      s+=" "+(h<12?"오전 ":"오후 ")+(h%12==0?12:h%12)+"시"+(m==0?"":" "+m+"분"); }
    return s;
  }
  /** 묻는 알림·카드의 한 줄: ‘12월 3일(목) 오후 2시 · 안동대학교 — 달력에 넣을까요?’ */
  static String askLine(ApptParser.R r){ return label(r.when,r.hasTime)+(r.location==null||r.location.isEmpty()?"":" · "+r.location)+" — 달력에 넣을까요?"; }
  static String last4(String addr){ String d=addr==null?"":addr.replaceAll("[^0-9]",""); return d.length()>=4?d.substring(d.length()-4):d; }
}
