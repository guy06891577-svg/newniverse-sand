package kr.newniverse.phone;

/** 상황 모드 종류와 기본값 (순수 자바). 새 모드를 넣을 때는 여기에 한 줄만 보태면 같은 틀이 그대로 쓰여요. */
final class ModeDefs {
  private ModeDefs(){}
  static final String DRIVE="drive", MEETING="meeting";
  static final String[] IDS={DRIVE,MEETING};
  static final int[] TIMERS={30,60,120};
  static final int MAX_TEXT=140;
  static final String ALLOW_NONE="none", ALLOW_STARRED="starred", ALLOW_CONTACTS="contacts";

  static boolean valid(String id){ for(String x:IDS) if(x.equals(id)) return true; return false; }
  static String name(String id){ return MEETING.equals(id)?"회의 중":"운전 중"; }
  /** 음성·바로가기에서 온 말을 모드 이름으로 */
  static String fromSpeech(String s){
    if(s==null) return null;
    String t=s.replaceAll("\\s+","");
    if(t.matches(".*(운전중|운전모드|운전할게|운전한다|운전합니다|운전해).*")) return DRIVE;
    if(t.matches(".*(회의중|회의모드|회의할게|회의한다|회의들어|회의시작).*")) return MEETING;
    return null;
  }
  static boolean timers(String id){ return MEETING.equals(id); }
  static String defaultText(String id){
    return MEETING.equals(id)?"지금 회의 중이라 전화를 못 받아요. 끝나면 연락드릴게요.":"지금 운전 중이라 전화를 못 받아요. 도착하면 연락드릴게요.";
  }
  static String allow(String s){ return ALLOW_STARRED.equals(s)||ALLOW_CONTACTS.equals(s)?s:ALLOW_NONE; }
  static int timer(int m){ for(int t:TIMERS) if(t==m) return m; return 0; }
  /** 답장 문구 다듬기: 앞뒤 공백·줄바꿈 정리, 길이 제한 */
  static String cleanText(String t){
    if(t==null) return "";
    String s=t.replace('\r',' ').replace('\n',' ').replaceAll("\\s{2,}"," ").trim();
    if(s.length()>MAX_TEXT) s=s.substring(0,MAX_TEXT).trim();
    return s;
  }
  static String fmtDur(long ms){
    long m=Math.max(0,ms/60000);
    if(m<1) return "1분 안";
    if(m<60) return m+"분";
    long h=m/60, r=m%60;
    return r==0?h+"시간":h+"시간 "+r+"분";
  }
  /** 「오후 3:05」 */
  static String hm(long t){
    java.util.Calendar c=java.util.Calendar.getInstance(); c.setTimeInMillis(t);
    int h=c.get(java.util.Calendar.HOUR_OF_DAY), m=c.get(java.util.Calendar.MINUTE);
    return (h<12?"오전 ":"오후 ")+((h%12)==0?12:(h%12))+":"+(m<10?"0":"")+m;
  }
  static String clip(String s,int n){ if(s==null) return ""; String t=s.replace('\n',' ').trim(); return t.length()<=n?t:t.substring(0,n)+"…"; }
}
