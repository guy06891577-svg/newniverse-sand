package kr.newniverse.sms;
import java.util.*;

/** v9: 사람끼리의 약속 문자(상대 날짜·요일·구어 시각·장소) — 지금은 2026-11-27(금) 15:00 */
public class CalV9Test{
  static int ok=0,bad=0;
  static void t(String n,boolean c){ if(c) ok++; else { bad++; System.out.println("FAIL "+n);} }
  static long ms(int y,int mo,int d,int h,int mi){ return CalV5Test.ms(y,mo,d,h,mi); }
  static final long NOW=ms(2026,11,27,15,0);
  static final String ADDR="01098765432";

  /** 시각 있는 일정 */
  static void at(String text,long now,long want,String place){
    ApptParser.R r=ApptParser.parse(text,now,null,ADDR);
    boolean good=r!=null&&r.when==want&&r.hasTime&&"casual".equals(r.src)&&r.ask&&(place==null||place.equals(r.location));
    t("‘"+text+"’ → "+Casual.label(want,true)+(place==null?"":" @"+place),good);
    if(!good) System.out.println("   got "+(r==null?"null · "+ApptParser.stage(text,now,null,ADDR):Casual.label(r.when,r.hasTime)+" ht="+r.hasTime+" src="+r.src+" @"+r.location));
  }
  static void at(String text,long want){ at(text,NOW,want,null); }
  /** 하루 종일 일정 */
  static void day(String text,long now,int y,int mo,int d,String place){
    ApptParser.R r=ApptParser.parse(text,now,null,ADDR);
    boolean good=r!=null&&!r.hasTime&&ApptParser.dayOf(r.when)==ApptParser.dayOf(ms(y,mo,d,12,0))&&"casual".equals(r.src)&&(place==null||place.equals(r.location));
    t("‘"+text+"’ → "+mo+"/"+d+" 하루 종일"+(place==null?"":" @"+place),good);
    if(!good) System.out.println("   got "+(r==null?"null · "+ApptParser.stage(text,now,null,ADDR):Casual.label(r.when,r.hasTime)+" ht="+r.hasTime+" @"+r.location));
  }
  static void none(String text,long now,String stage){
    ApptParser.R r=ApptParser.parse(text,now,null,ADDR);
    String st=ApptParser.stage(text,now,null,ADDR);
    boolean good=r==null&&(stage==null||stage.equals(st));
    t("‘"+text+"’ → 넣지 않음"+(stage==null?"":" ("+stage+")"),good);
    if(!good) System.out.println("   got "+(r==null?st:Casual.label(r.when,r.hasTime)+" src="+r.src));
  }
  static void none(String text,String stage){ none(text,NOW,stage); }

  public static void main(String[] x){
    t("NOW is Friday",ApptParser.cal(NOW).get(Calendar.DAY_OF_WEEK)==Calendar.FRIDAY);
    // ---- 사용자 문장 그대로 ----
    String S="다음주 목요일 2시에 안동대학교서 봐";
    at(S,NOW,ms(2026,12,3,14,0),"안동대학교");
    ApptParser.R r=ApptParser.parse(S,NOW,"김철수",ADDR);
    t("title with contact name",r!=null&&r.title.equals("약속 · 김철수"));
    r=ApptParser.parse(S,NOW,null,ADDR);
    t("title with last 4 digits",r!=null&&r.title.equals("약속 · 5432"));
    t("ask line spelled out",r!=null&&Casual.askLine(r).equals("12월 3일(목) 오후 2시 · 안동대학교 — 달력에 넣을까요?"));
    t("stage parse:ok",ApptParser.stage(S,NOW,null,ADDR).equals("parse:ok"));
    // 언제나 묻기(자동으로 넣지 않음) — 주소록에 있어도, 없어도
    CalDecide.D d=CalDecide.decide(true,true,S,NOW,"김철수",ADDR); t("contact → ask (never auto)",d.act.equals("ask"));
    d=CalDecide.decide(true,true,S,NOW,null,ADDR); t("unknown number → ask",d.act.equals("ask"));
    d=CalDecide.decide(false,true,S,NOW,"김철수",ADDR); t("auto off → skip off",d.act.equals("skip")&&d.reason.equals("off"));
    // ---- 주 단위 (월~일 주) ----
    at("담주 목요일 2시 반에 만나",ms(2026,12,3,14,30));
    at("다음 주 목 오후 3시 미팅",ms(2026,12,3,15,0));
    at("다음주목요일 4시에 보자",ms(2026,12,3,16,0));
    at("차주 월요일 10시 미팅",ms(2026,11,30,10,0));
    at("다다음주 화요일 저녁 7시 모임",ms(2026,12,8,19,0));
    at("이번주 일요일 12시에 보자",ms(2026,11,29,12,0));
    at("이번 주 토요일 6시에 만나자",ms(2026,11,28,18,0));
    none("이번주 수요일에 봐","parse:casual-past");
    none("이번주 금요일 8시에 봐","parse:casual-past"); // 앞말 없는 8시 = 오전 8시, 이미 지남
    at("이번주 금요일 밤 8시에 봐",ms(2026,11,27,20,0));
    // ---- 요일만: 다가오는 그 요일 (오늘은 시각이 남았을 때만) ----
    day("목요일에 봐",NOW,2026,12,3,null);
    at("금요일 5시에 보자",ms(2026,11,27,17,0));
    at("금요일 2시에 보자",ms(2026,12,4,14,0));
    day("금요일에 보자",NOW,2026,12,4,null);
    day("목요일날 만나",NOW,2026,12,3,null);
    at("목요 2시 봐",ms(2026,12,3,14,0));
    at("목욕 2시에 보자",ms(2026,12,3,14,0));
    at("월요일 오전 10시 미팅 있어요",ms(2026,11,30,10,0));
    day("일요일에 약속 있어",NOW,2026,11,29,null);
    // ---- 오늘·내일·낼·모레·글피 ----
    at("내일 2시에 강남역에서 보자",NOW,ms(2026,11,28,14,0),"강남역");
    at("낼 저녁 7시 약속",ms(2026,11,28,19,0));
    at("모레 아침 9시에 만나",ms(2026,11,29,9,0));
    at("글피 점심 12시 반에 보자",ms(2026,11,30,12,30));
    at("오늘 3시 반에 봐",ms(2026,11,27,15,30));
    none("오늘 2시에 봐","parse:casual-past");
    at("오늘 밤 12시에 봐",ms(2026,11,28,0,0));
    at("오늘 밤 1시에 봐",ms(2026,11,28,1,0));
    // ---- 시각 규칙 ----
    at("내일 11시에 봐",ms(2026,11,28,11,0));
    at("내일 12시에 봐",ms(2026,11,28,12,0));
    at("내일 1시에 봐",ms(2026,11,28,13,0));
    at("내일 7시에 봐",ms(2026,11,28,19,0));
    at("내일 8시에 봐",ms(2026,11,28,8,0));
    at("내일 오전 7시에 봐",ms(2026,11,28,7,0));
    at("내일 오후 9시 15분에 봐",ms(2026,11,28,21,15));
    at("내일 14:30에 봐",ms(2026,11,28,14,30));
    at("내일 두시에 봐",ms(2026,11,28,14,0));
    at("내일 저녁 여섯시 반에 만나자",ms(2026,11,28,18,30));
    at("내일 저녁여섯시 만나자",ms(2026,11,28,18,0));
    at("내일 새벽 5시에 보자",ms(2026,11,28,5,0));
    at("내일 밤 11시 봐",ms(2026,11,28,23,0));
    at("내일 점심 1시 보자",ms(2026,11,28,13,0));
    at("내일 아침 12시 반 미팅",ms(2026,11,28,0,30));
    day("내일 학교에서 보자",NOW,2026,11,28,"학교");
    day("내일 3시간 동안 모임",NOW,2026,11,28,null);    // ‘3시간’은 시각이 아님
    day("내일 네 시청에서 보자",NOW,2026,11,28,"시청"); // ‘네 시청’은 4시가 아님
    // ---- 장소 ----
    at("다음주 월요일 3시 강남역 앞에서 보자",NOW,ms(2026,11,30,15,0),"강남역");
    at("내일 2시에 스타벅스 안동점에서 만나",NOW,ms(2026,11,28,14,0),"스타벅스 안동점");
    at("내일 3시에 거기서 봐",NOW,ms(2026,11,28,15,0),"");
    at("내일 3시에 봐",NOW,ms(2026,11,28,15,0),"");
    at("내일 3시에 삼성병원 앞에서 보자",NOW,ms(2026,11,28,15,0),"삼성병원");
    day("내일 목욕탕에서 보자",NOW,2026,11,28,"목욕탕");
    at("12월 5일 3시에 카페에서 만나",NOW,ms(2026,12,5,15,0),"카페"); // 숫자 날짜도 약속 말이 있으면
    // ---- 약속 말이 있어야 ----
    none("다음주 목요일 2시에 안동대학교","parse:casual-no-cue");
    none("내일 비 온대",null);
    none("내일 2시 회의자료 보내줘",null);
    // ---- 지난 일·부정 ----
    none("목요일에 봤어","parse:casual-negated");
    none("목요일에 만났어",null);
    none("다음주 목요일 못 봐","parse:casual-negated");
    none("내일은 안 돼 다음에 보자",null);
    none("내일 약속 취소하자",null);
    none("내일 2시에 만나기로 한 거 미루자",null);
    // ---- 정해지지 않은 날 ----
    none("주말에 보자","parse:casual-vague");
    none("이번 주말에 만나","parse:casual-vague");
    none("다음달에 만나","parse:casual-vague");
    none("조만간 보자","parse:casual-vague");
    // ---- 기관 문자는 그대로 ----
    none("[Web발신] 내일 2시에 보자",null);
    none("[OO마트] 내일 2시 할인 행사 만나보세요",null);
    ApptParser.R s=ApptParser.parse(CalV5Test.LMS,ms(2026,11,26,18,45),null,"01055556666");
    t("school LMS unchanged (event, not casual)",s!=null&&"school".equals(s.src)&&s.when==ms(2026,11,27,16,0));
    ApptParser.R cl=ApptParser.parse(CalV8Test.clinic("2026-12-03 09시 20분"),ms(2026,11,26,18,45),null,"0533334444");
    t("clinic unchanged (hosp, auto)",cl!=null&&"hosp".equals(cl.src)&&!cl.ask);
    // ---- 연말·일요일 경계 ----
    at("다음주 금요일 3시에 봐",ms(2026,12,28,9,0),ms(2027,1,8,15,0),null);
    day("다음주 월요일 보자",ms(2026,11,29,10,0),2026,11,30,null);    // 일요일에 말한 ‘다음주 월요일’ = 내일
    none("이번주 월요일에 보자",ms(2026,11,29,10,0),"parse:casual-past");
    day("월요일에 보자",ms(2026,11,29,10,0),2026,11,30,null);
    // ---- 줄바꿈·특수 공백 ----
    at("다음주\u00A0목요일\r\n2시에 안동대학교서 봐",NOW,ms(2026,12,3,14,0),"안동대학교");
    // ---- 보여 주는 날짜 ----
    t("label 2:30",Casual.label(ms(2026,12,3,14,30),true).equals("12월 3일(목) 오후 2시 30분"));
    t("label all-day",Casual.label(ms(2026,12,3,0,0),false).equals("12월 3일(목)"));
    t("label noon",Casual.label(ms(2026,12,3,12,0),true).equals("12월 3일(목) 오후 12시"));
    t("label midnight",Casual.label(ms(2026,12,3,0,0),true).equals("12월 3일(목) 오전 12시"));
    System.out.println("Cal v9 java tests: "+ok+" passed, "+bad+" failed");
    if(bad>0) System.exit(1);
  }
}
