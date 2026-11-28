package kr.newniverse.phone;

import java.util.Calendar;
import java.util.TimeZone;

/** v6 통화 메모 규칙 (순수 자바): 언제 「메모 남길까요?」를 물을지 · 「며칠 전」 글 · 음성 인식기 고르기 */
final class MemoRules {
  private MemoRules(){}
  static final int MIN_SEC=15, MAX_TEXT=1000;

  /** 통화가 실제로 이어졌고(받았거나 상대가 받음), 다른 통화가 남아 있지 않고, 번호가 있고, 15초 이상이거나 저장된 연락처일 때만 */
  static boolean shouldOffer(boolean connected,long durMs,boolean isContact,String num,int othersLeft){
    if(!connected||othersLeft>0) return false;
    if(ModeRules.key(num).isEmpty()) return false;
    return durMs>=MIN_SEC*1000L||isContact;
  }

  /** 달력 날짜로 센 날 수: 0 오늘, 1 어제 … */
  static int daysBetween(long now,long at,TimeZone tz){
    Calendar a=Calendar.getInstance(tz), b=Calendar.getInstance(tz);
    a.setTimeInMillis(at); b.setTimeInMillis(now);
    zero(a); zero(b);
    return (int)Math.round((b.getTimeInMillis()-a.getTimeInMillis())/86400000.0);
  }
  private static void zero(Calendar c){ c.set(Calendar.HOUR_OF_DAY,0); c.set(Calendar.MINUTE,0); c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0); }
  static String ago(long now,long at,TimeZone tz){
    if(at<=0) return "";
    int d=daysBetween(now,at,tz);
    if(d<=0) return "오늘";
    if(d==1) return "어제";
    if(d<365) return d+"일 전";
    return "1년 넘게 전";
  }

  /** 한 줄로: 줄바꿈·여러 칸 → 한 칸, 길면 … */
  static String snippet(String t,int max){
    if(t==null) return "";
    String s=t.replaceAll("\\s+"," ").trim();
    if(s.codePointCount(0,s.length())<=max) return s;
    int end=s.offsetByCodePoints(0,max);
    return s.substring(0,end).trim()+"…";
  }
  static String clean(String t){
    if(t==null) return "";
    String s=t.replace("\r","").trim();
    if(s.length()>MAX_TEXT) s=s.substring(0,MAX_TEXT).trim();
    return s;
  }

  /** 음성 인식기 고르기. 잠금 화면이면 클라우드(인터넷) 인식기는 절대 쓰지 않아요.
   *  반환: "device"(기기 안) · "cloud"(잠금 풀림 + 기기 안 없음) · "locked"(잠금인데 기기 안 없음) · "none"(인식기 없음) */
  static String voiceMode(int sdk,boolean locked,boolean onDeviceAvail,boolean anyAvail){
    if(sdk>=33&&onDeviceAvail) return "device";
    if(locked) return "locked";
    return anyAvail?"cloud":"none";
  }
  /** 기기 안 인식기가 한국어를 못 할 때(12 언어 미지원 · 13 언어 모델 없음 · 5 클라이언트 오류) 잠금이 풀려 있을 때만 일반 인식기로 */
  static boolean cloudFallback(int code,boolean locked,boolean anyAvail){
    if(locked||!anyAvail) return false;
    return code==12||code==13||code==5;
  }
}
