package kr.newniverse.sms;
import java.util.*;

/** v7: 읽기 단계 표시 · 긴 글에서 멈추지 않음 · 학교 문자(주소록에 없는 번호)도 ‘넣을까요?’ · 권한 없음 표시 */
public class CalV7Test{
  static int ok=0,bad=0;
  static void t(String n,boolean c){ if(c) ok++; else { bad++; System.out.println("FAIL "+n);} }
  static final long AT=CalV5Test.AT; static final String LMS=CalV5Test.LMS;
  static long time(Runnable r){ long a=System.nanoTime(); r.run(); return (System.nanoTime()-a)/1000000; }

  public static void main(String[] x){
    // 단계 이름
    t("stage ok",ApptParser.stage(LMS,AT,null,"01012345678").equals("parse:ok"));
    t("stage no-date",ApptParser.stage("안녕 오늘 뭐해?",AT,null,"010").equals("parse:no-date"));
    t("stage deadline-only",ApptParser.stage("[길주중학교]\n학부모님 안녕하세요. 신청 기간: 2026. 11. 20. ~ 11. 25.(수)까지 신청해 주세요.",AT,null,"010").equals("parse:deadline-only"));
    t("stage no-datetime-line",ApptParser.stage("12/3 3시에 보자 ㅎㅎ",AT,"엄마","01011112222").equals("parse:no-datetime-line"));
    t("stage null body no throw",ApptParser.stage(null,AT,null,null).startsWith("parse:"));

    // 학교 문자는 주소록에 없는 번호여도 ask (자동 X)
    CalDecide.D d=CalDecide.decide(true,true,LMS,AT,null,"01055556666");
    t("school unknown mobile → ask",d.act.equals("ask")&&d.r!=null&&d.r.trusted);
    d=CalDecide.decide(true,true,LMS,AT,null,"01055556666",true);
    t("force flag harmless for trusted",d.act.equals("ask")&&!"forced".equals(d.reason));
    String nos=LMS.replace("[길주중학교]","");
    d=CalDecide.decide(true,true,nos,AT,null,"01055556666");
    t("no tag unknown → skip untrusted",d.act.equals("skip")&&d.reason.equals("untrusted"));
    d=CalDecide.decide(true,true,nos,AT,null,"01055556666",true);
    t("forced (reread) → ask with reason forced",d.act.equals("ask")&&d.reason.equals("forced"));
    t("forced label says test",CalDecide.stepsLabel("ask","forced").contains("시험"));
    d=CalDecide.decide(true,true,nos,AT,null,"01055556666",true);
    d=CalDecide.decide(false,true,LMS,AT,"내 번호","010"); t("off with perm → off",d.reason.equals("off"));
    d=CalDecide.decide(false,false,LMS,AT,"내 번호","010"); t("no perm (on unset) → noperm, not off",d.reason.equals("noperm"));
    d=CalDecide.decide(true,false,LMS,AT,"내 번호","010"); t("no perm → noperm",d.reason.equals("noperm"));

    // 긴 글: 5000자까지만 읽고, 공백은 하나로 → 오래 걸리지 않음
    String sp=" ".repeat(20000)+"일시: 2026. 11. 27.(금) 16:00";
    long ms=time(()->ApptParser.parse(sp,AT,"내 번호","010"));
    t("20k spaces fast ("+ms+"ms)",ms<500);
    String sl="1/2/3/4/5/6/7/8/9 ".repeat(2000);
    ms=time(()->{ ApptParser.parse(sl,AT,null,"010"); ApptParser.hasDate(sl,AT); ApptParser.stage(sl,AT,null,"010"); });
    t("36k slash text fast ("+ms+"ms)",ms<1500);
    String lg=("가나다 "+"2026. 11. 27.(금) 오후 4시 ").repeat(1500);
    ms=time(()->{ ApptParser.parse(lg,AT,null,"010"); ApptParser.hasDate(lg,AT); });
    t("40k dated text fast ("+ms+"ms)",ms<1500);
    ms=time(()->{ for(int i=0;i<60;i++){ ApptParser.hasDate(lg,AT); CalDecide.decide(true,true,lg,AT,null,"010",true); } });
    t("60 long bodies under 10s ("+ms+"ms)",ms<10000);
    t("norm cap",ApptParser.norm("가".repeat(9000)).length()<=ApptParser.MAXLEN);
    t("norm collapses space runs",ApptParser.norm("a    b\u00A0\u00A0 c").equals("a b c"));
    t("norm still idempotent",ApptParser.norm(ApptParser.norm(LMS)).equals(ApptParser.norm(LMS)));
    // 긴 글 앞에 일정이 있어도 읽음
    t("event within first 5000 chars of a long text",ApptParser.parse(LMS+"\n"+"가나다 ".repeat(1500),AT,"내 번호","010")!=null);

    System.out.println("Cal v7 java tests: "+ok+" passed, "+bad+" failed");
    if(bad>0) System.exit(1);
  }
}
