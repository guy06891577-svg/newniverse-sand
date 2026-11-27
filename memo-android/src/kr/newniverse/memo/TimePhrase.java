package kr.newniverse.memo;

import java.util.*;
import java.util.regex.*;

/**
 * 말로 한 메모 속 ‘때’를 찾는 규칙(순수 자바). 문자 앱(Casual)과 같은 날짜·시각 규칙을 쓰고, ‘30분 뒤’ 같은 말도 읽어요.
 *  날짜  오늘=0 · 내일/낼=+1 · 모레=+2 · 글피=+3 (내일모레=모레).
 *        요일만(목요일·목요·목욜·목요일날) = 다가오는 그 요일(오늘이면 시각이 아직 남았을 때만).
 *        이번주 X = 이번 주(월~일)의 X · 다음주/담주/차주 X = 다음 주의 X · 다다음주 X = 그다음 주의 X.
 *        ‘12월 3일’ 같은 숫자 날짜 · ‘3일 뒤’ ‘2주 뒤’ 도 읽음.
 *  시각  N시 · N시 반 · N시 M분 · N:MM · 오전/오후/아침/점심/저녁/밤/새벽/낮. 앞말이 없으면 1~7시=오후, 8~11시=오전, 12시=낮 12시.
 *  뒤    ‘30분 뒤’ ‘한 시간 반 후’ ‘1시간 30분 뒤’ = 지금부터 그만큼 뒤(초 단위까지 그대로).
 *  날짜만 말하면 오전 9시(defaulted)로 제안하고, 시각만 말했는데 이미 지났으면 내일 그 시각(rolled)으로 제안해요.
 *  지난 일(어제·했어 …)은 때로 치지 않아요. 이미 지난 때는 Hit.past=true 로 돌려 주고, 알림은 맞추지 않아요.
 *  날짜만 말하고 시각이 없으면 오전 9시로 맞춰요. 확인 문구는 confirmSet()/confirmPast() 를 써요.
 */
final class TimePhrase {
  static final int DEFAULT_HOUR=9;
  static final class Hit {
    long when; boolean explicitTime, defaulted, rolled, relative, past; String phrase="";
    String label(){ return TimePhrase.label(when,true); }
    /** 화면에 보일 한 줄: ‘12월 3일(목) 오후 2시’ (+ 설명) */
    String line(){
      String s=TimePhrase.label(when,true);
      if(defaulted) s+=" · 시각을 말씀하지 않아 오전 9시로";
      else if(rolled) s+=" · 오늘은 지나서 내일로";
      return s;
    }
    /** 확인 카드: ‘12월 3일 (목) 오전 9:00 알림 맞춤’ */
    String confirmSet(){
      String s=TimePhrase.confirmWhen(when)+" 알림 맞춤";
      if(defaulted) s+=" · 시각을 말씀하지 않아 오전 9시로 정했어요";
      else if(rolled) s+=" · 오늘은 지나서 내일로 정했어요";
      return s;
    }
    String confirmPast(){ return TimePhrase.confirmWhen(when)+"은 이미 지난 때라 알림을 맞추지 않았어요"; }
  }

  static final String WD="(월|화|수|목|금|토|일)";
  static final String WDAY=WD+"(?:요일날|요일|요(?:(?=[에날])|(?![가-힣]))|욜(?![가-힣])|욕(?![탕하재용물]))";
  static final Pattern WEEK=Pattern.compile("(이번\\s*주|금주|다다음\\s*주|담담\\s*주|다음\\s*주|담\\s*주|차주)\\s*(?:"+WDAY+"|"+WD+"(?![가-힣]))");
  static final Pattern BARE=Pattern.compile("(?<![가-힣])"+WDAY);
  static final Pattern REL=Pattern.compile("(?<![가-힣])(오늘|내일|낼|모레|글피)(?:(?=[에은도밤저아점오])|(?![가-힣]))");
  static final Pattern MD=Pattern.compile("(?<![\\d])(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");
  static final String[] KNUM={"열두","열한","열","아홉","여덟","일곱","여섯","다섯","네","세","두","한"};
  static final int[] KVAL={12,11,10,9,8,7,6,5,4,3,2,1};
  static final Pattern TIME=Pattern.compile("(?:(오전|오후|아침|점심|저녁|밤|새벽|낮)\\s*)?(?:(?<!\\d)(\\d{1,2})\\s*:\\s*(\\d{2})|((?<!\\d)\\d{1,2}|(?:(?<=오전|오후|아침|점심|저녁|새벽|밤|낮)|(?<![가-힣]))(?:열두|열한|열|아홉|여덟|일곱|여섯|다섯|네|세|두|한))\\s*시(?![간청장작험계골])\\s*(?:(\\d{1,2})\\s*분|(반))?)");
  // ‘뒤’ 말: 앞에 시간·분·일·주 말이 붙어 있어야 함
  static final String K_H="(?:\\d{1,2}|열두|열한|열|아홉|여덟|일곱|여섯|다섯|네|세|두|한)";
  static final String SINO="(?:[이삼사오]?십[일이삼사오육칠팔구]?|[일이삼사오육칠팔구])";
  static final String AMT="(?:"+K_H+"\\s*시간(?:\\s*반)?|반\\s*시간|(?:\\d{1,3}|"+SINO+")\\s*분|\\d{1,2}\\s*일|하루|이틀|사흘|나흘|\\d\\s*주일?|일주일)";
  static final Pattern AFTER=Pattern.compile("(?<![가-힣\\d])((?:"+AMT+"\\s*)+)(?:뒤|후|이따가?|있다가|지나서)");
  static final Pattern A_H=Pattern.compile("("+K_H+")\\s*시간(\\s*반)?");
  static final Pattern A_HALFH=Pattern.compile("반\\s*시간");
  static final Pattern A_M=Pattern.compile("(\\d{1,3}|"+SINO+")\\s*분");
  static final Pattern A_D=Pattern.compile("(\\d{1,2})\\s*일|하루|이틀|사흘|나흘");
  static final Pattern A_W=Pattern.compile("(\\d)\\s*주일?|일주일");
  static final Pattern PAST_WORD=Pattern.compile("어제|그제|그저께|엊그제|지난\\s*(?:주|달|번|해)|작년|며칠\\s*전|(?:\\d|분|시간|일|주)\\s*전(?:에)?");
  static final Pattern PAST_TENSE=Pattern.compile("(?:했|았|었|였|됐|봤|갔|왔|났|끝났|먹었|만났)(?:어|다|지|는데|어요|습니다|음)");
  static final Pattern REMIND_CUE=Pattern.compile("알려|알림|깨워|리마인드|잊지|까먹지|잊어버리지|상기");

  static TimeZone tz(){ return TimeZone.getDefault(); }
  static Calendar cal(long ms){ Calendar c=Calendar.getInstance(tz(),Locale.KOREA); c.setTimeInMillis(ms); return c; }
  static long dayOf(long ms){ Calendar c=cal(ms); c.set(Calendar.HOUR_OF_DAY,12); c.set(Calendar.MINUTE,0); c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0); return c.getTimeInMillis()/3600000L/24L; }
  static int i(String s){ try{ return Integer.parseInt(s.trim()); }catch(Exception e){ return -1; } }

  /** 메모 속 때를 찾아요. 없거나 제안하기 곤란하면 null. now = 말을 마친 시각 */
  static Hit find(String text,long now){
    if(text==null) return null;
    String s=text.replace('\n',' ').replace("내일모레","모레").trim();
    if(s.isEmpty()||s.length()>2000) return null;
    boolean cue=REMIND_CUE.matcher(s).find();
    if(!cue&&PAST_TENSE.matcher(s).find()) return null;

    Calendar nowC=cal(now);
    // 1) 지금부터 ‘N분 뒤’
    Matcher am=AFTER.matcher(s);
    if(am.find()){
      String chunk=am.group(1);
      long addMs=0; int addDays=0;
      Matcher m=A_H.matcher(chunk); if(m.find()){ addMs+=hours(m.group(1))*3600000L+(m.group(2)!=null?1800000L:0); }
      else if(A_HALFH.matcher(chunk).find()) addMs+=1800000L;
      m=A_M.matcher(chunk); if(m.find()){ int v=minutes(m.group(1)); if(v<0) return null; addMs+=v*60000L; }
      m=A_D.matcher(chunk);
      if(m.find()){ String g=m.group(0); addDays+=m.group(1)!=null?i(m.group(1)):g.equals("하루")?1:g.equals("이틀")?2:g.equals("사흘")?3:4; }
      m=A_W.matcher(chunk); if(m.find()) addDays+=m.group(1)!=null?7*i(m.group(1)):7;
      if(addMs<=0&&addDays<=0) return null;
      if(addDays>366||addMs>366L*86400000L) return null;
      Hit h=new Hit(); h.relative=true; h.phrase=am.group().trim();
      if(addDays>0&&addMs==0){ // 며칠 뒤: 그날 시각이 있으면 그 시각, 없으면 오전 9시
        Calendar c=(Calendar)nowC.clone(); c.add(Calendar.DATE,addDays);
        Matcher t=TIME.matcher(s); int mins=-1; if(t.find(am.end())) mins=minutes(t);
        if(mins>=0){ midnight(c); c.add(Calendar.MINUTE,mins); h.explicitTime=true; }
        else { midnight(c); c.set(Calendar.HOUR_OF_DAY,DEFAULT_HOUR); h.defaulted=true; }
        h.when=c.getTimeInMillis();
      } else {
        h.when=now+addMs+(long)addDays*86400000L; h.explicitTime=true;
      }
      if(h.when<=now) h.past=true; return h;
    }

    // 2) 날짜 말
    Calendar day=null; int dayEnd=-1; boolean bare=false; boolean explicitDay=false; boolean todayOnly=false;
    Matcher w=WEEK.matcher(s);
    if(w.find()){
      String k=w.group(1).replaceAll("\\s+","");
      int add=k.startsWith("이번")||k.equals("금주")?0:(k.startsWith("다다음")||k.startsWith("담담"))?2:1;
      String wd=w.group(2)!=null?w.group(2):w.group(3);
      day=weekDay(nowC,add,"월화수목금토일".indexOf(wd)); dayEnd=w.end(); explicitDay=true;
    }
    if(day==null){
      Matcher rel=REL.matcher(s);
      if(rel.find()){ String k=rel.group(1); int add=k.equals("오늘")?0:(k.equals("내일")||k.equals("낼"))?1:k.equals("모레")?2:3;
        day=(Calendar)nowC.clone(); day.add(Calendar.DATE,add); dayEnd=rel.end(); explicitDay=true; todayOnly=k.equals("오늘"); }
    }
    if(day==null){
      Matcher b=BARE.matcher(s);
      if(b.find()){ bare=true; day=weekDay(nowC,0,"월화수목금토일".indexOf(b.group(1))); dayEnd=b.end(); explicitDay=true; }
    }
    if(day==null){
      Matcher md=MD.matcher(s);
      if(md.find()){ int mo=i(md.group(1)), d=i(md.group(2));
        if(mo>=1&&mo<=12&&d>=1&&d<=31){ Calendar c=(Calendar)nowC.clone(); c.set(Calendar.MONTH,mo-1); c.set(Calendar.DATE,d);
          if(c.get(Calendar.MONTH)==mo-1){ day=c; dayEnd=md.end(); explicitDay=true; } } }
    }
    if(day==null&&PAST_WORD.matcher(s).find()) return null; // 어제 3시에 … 는 제안하지 않음

    // 3) 시각 말
    Matcher t=TIME.matcher(s); int mins=-1;
    if(dayEnd>=0&&t.find(dayEnd)) mins=minutes(t); else { t=TIME.matcher(s); if(t.find()) mins=minutes(t); }
    boolean ht=mins>=0;
    if(todayOnly&&!ht&&!cue) return null; // ‘오늘 날씨 좋네’처럼 오늘만 있고 시각·알림 말도 없으면 알림으로 치지 않음
    if(day==null&&!ht) return null;

    Hit h=new Hit(); h.explicitTime=ht;
    Calendar c;
    if(day==null){ // 시각만: 오늘 그 시각, 지났으면 내일
      c=(Calendar)nowC.clone(); midnight(c); c.add(Calendar.MINUTE,mins);
      if(c.getTimeInMillis()<=now){ c.add(Calendar.DATE,1); h.rolled=true; }
    } else {
      c=(Calendar)day.clone(); midnight(c);
      if(ht) c.add(Calendar.MINUTE,mins); else { c.set(Calendar.HOUR_OF_DAY,DEFAULT_HOUR); h.defaulted=true; }
      if(bare){ // 요일만: 다가오는 그 요일. 오늘이면 시각이 아직 남았을 때만
        boolean today=dayOf(c.getTimeInMillis())==dayOf(now);
        if(dayOf(c.getTimeInMillis())<dayOf(now)||(today&&(!ht||c.getTimeInMillis()<=now))) c.add(Calendar.DATE,7);
      }
    }
    h.when=c.getTimeInMillis();
    if(h.when<=now) h.past=true; // 이미 지난 때(알림은 맞추지 않음)
    return h;
  }

  static void midnight(Calendar c){ c.set(Calendar.HOUR_OF_DAY,0); c.set(Calendar.MINUTE,0); c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0); }
  /** 이번 주(월~일) 기준 addWeeks 주 뒤의 요일(wd 0=월) */
  static Calendar weekDay(Calendar now,int addWeeks,int wd){
    Calendar c=(Calendar)now.clone();
    int today=(c.get(Calendar.DAY_OF_WEEK)+5)%7;
    c.add(Calendar.DATE,-today+addWeeks*7+wd);
    return c;
  }
  static int hours(String g){ for(int k=0;k<KNUM.length;k++) if(KNUM[k].equals(g)) return KVAL[k]; int v=i(g); return v<0?0:v; }
  static int minutes(String g){
    int v=i(g); if(v>=0) return v;
    String d="일이삼사오육칠팔구"; int ten=g.indexOf('십');
    if(ten<0){ int k=d.indexOf(g); return k>=0?k+1:-1; }
    int a=ten==0?1:d.indexOf(g.charAt(0))+1; int b=ten+1<g.length()?d.indexOf(g.charAt(ten+1))+1:0;
    return a*10+b;
  }
  static int minutes(Matcher t){
    String ap=t.group(1); int h,mi=0;
    if(t.group(2)!=null){ h=i(t.group(2)); mi=i(t.group(3)); }
    else { String hs=t.group(4); h=-1; for(int k=0;k<KNUM.length;k++) if(KNUM[k].equals(hs)) h=KVAL[k]; if(h<0) h=i(hs);
      if(t.group(5)!=null) mi=i(t.group(5)); else if(t.group(6)!=null) mi=30; }
    if(h<0||h>24||mi<0||mi>59) return -1;
    if(ap==null){ if(h>=1&&h<=7) h+=12; } // 앞말 없음: 1~7시 오후 · 8~11시 오전 · 12시 낮
    else switch(ap){
      case "오후": case "저녁": if(h<12) h+=12; break;
      case "밤": if(h==12) h=24; else if(h>=6&&h<12) h+=12; else if(h<6) h+=24; break;
      case "점심": case "낮": if(h<=5) h+=12; break;
      case "오전": case "아침": case "새벽": if(h==12) h=0; break;
    }
    return h*60+mi;
  }
  /** 확인·목록용: ‘12월 3일 (목) 오전 9:00’ */
  static String confirmWhen(long ms){
    Calendar c=cal(ms); String[] w={"일","월","화","수","목","금","토"};
    int h=c.get(Calendar.HOUR_OF_DAY), m=c.get(Calendar.MINUTE);
    String ap=h<12?"오전":"오후"; int h12=h%12==0?12:h%12;
    return (c.get(Calendar.MONTH)+1)+"월 "+c.get(Calendar.DATE)+"일 ("+w[c.get(Calendar.DAY_OF_WEEK)-1]+") "+ap+" "+h12+":"+(m<10?"0":"")+m;
  }
  /** 사람이 읽는 때: ‘12월 3일(목) 오후 2시’ · ‘12월 3일(목) 오후 2시 30분’ */
  static String label(long ms,boolean hasTime){
    Calendar c=cal(ms); String[] w={"일","월","화","수","목","금","토"};
    String s=(c.get(Calendar.MONTH)+1)+"월 "+c.get(Calendar.DATE)+"일("+w[c.get(Calendar.DAY_OF_WEEK)-1]+")";
    if(hasTime){ int h=c.get(Calendar.HOUR_OF_DAY), m=c.get(Calendar.MINUTE);
      s+=" "+(h<12?"오전 ":"오후 ")+(h%12==0?12:h%12)+"시"+(m==0?"":" "+m+"분"); }
    return s;
  }
}
