package kr.newniverse.sms;
import java.util.*;
public class InboxLogicTest{
  static int ok=0,bad=0;
  static void t(String n,boolean c){ if(c) ok++; else { bad++; System.out.println("FAIL "+n);} }
  public static void main(String[] a){
    t("concat null",InboxLogic.concatParts(null).equals(""));
    t("concat empty",InboxLogic.concatParts(new String[]{}).equals(""));
    t("concat multipart Korean",InboxLogic.concatParts(new String[]{"[안동병원] 장준혁님 ","12월 3일 오후 2시 ","진료 예약되었습니다"}).equals("[안동병원] 장준혁님 12월 3일 오후 2시 진료 예약되었습니다"));
    t("concat skips null parts",InboxLogic.concatParts(new String[]{"가",null,"나"}).equals("가나"));

    t("subId long",InboxLogic.subId(Long.valueOf(7))==7);
    t("subId int",InboxLogic.subId(Integer.valueOf(3))==3);
    t("subId string",InboxLogic.subId("11")==11);
    t("subId null",InboxLogic.subId(null)==-1);
    t("subId junk",InboxLogic.subId("no")==-1);

    t("digits kr +82",InboxLogic.digits("+82 10-1234-5678").equals("01012345678"));
    t("sameAddr plus and local",InboxLogic.sameAddr("+821012345678","010-1234-5678"));
    t("sameAddr different",!InboxLogic.sameAddr("01011111111","01022222222"));

    long now=1_700_000_000_000L;
    List<InboxLogic.Row> rows=InboxLogic.rows(new String[][]{{"01012345678","안녕",String.valueOf(now-1000)}});
    t("dup same within window",InboxLogic.isDup("010-1234-5678","안녕",now,rows));
    t("not dup different body",!InboxLogic.isDup("01012345678","다른",now,rows));
    t("not dup outside window",!InboxLogic.isDup("01012345678","안녕",now+InboxLogic.DEDUP_MS+1,rows));
    t("not dup empty list",!InboxLogic.isDup("010","안녕",now,new ArrayList<>()));

    Map<String,Object> f=InboxLogic.valuesFull("010","본문",now,now-5,42,7);
    t("full type inbox",Integer.valueOf(1).equals(f.get("type")));
    t("full thread",Long.valueOf(42).equals(f.get("thread_id"))||Integer.valueOf(42).equals(f.get("thread_id")));
    t("full sub",Long.valueOf(7).equals(f.get("sub_id"))||Integer.valueOf(7).equals(f.get("sub_id")));
    t("full no sub when -1",!InboxLogic.valuesFull("010","b",now,now,-1,-1).containsKey("sub_id"));
    Map<String,Object> m=InboxLogic.valuesMinimal("010","본문",now,9);
    t("min type inbox",Integer.valueOf(1).equals(m.get("type")));
    t("min no date_sent",!m.containsKey("date_sent"));
    t("min no sub",!m.containsKey("sub_id"));
    t("min thread",Long.valueOf(9).equals(m.get("thread_id"))||Integer.valueOf(9).equals(m.get("thread_id")));

    t("notify saved who",InboxLogic.notifyWho(true,"엄마").equals("엄마"));
    t("notify saved empty","새 문자".equals(InboxLogic.notifyWho(true,"")));
    t("notify fail title",InboxLogic.notifyWho(false,"엄마").equals("문자를 저장하지 못했어요"));
    t("notify fail has no body",!InboxLogic.notifyFailText().contains("엄마")&&InboxLogic.notifyFailText().contains("저장 기록"));
    t("public saved","새 문자".equals(InboxLogic.notifyPublic(true)));
    t("public fail",InboxLogic.notifyPublic(false).equals("문자를 저장하지 못했어요"));
    t("label ok",InboxLogic.insertLabel("ok").equals("저장됨"));
    t("label retry",InboxLogic.insertLabel("retry-ok").equals("저장됨(다시 시도)"));
    t("label fail",InboxLogic.insertLabel("fail").equals("저장 실패"));
    t("label empty",InboxLogic.insertLabel("").equals("아직 없음"));
    t("source deliver",InboxLogic.sourceLabel("deliver").equals("SMS_DELIVER"));
    t("source received",InboxLogic.sourceLabel("received").contains("안전망"));
    t("err class",InboxLogic.errClass(new IllegalStateException("010-1234 secret")).equals("IllegalStateException"));

    System.out.println("InboxLogic java tests: "+ok+" passed, "+bad+" failed");
    if(bad>0) System.exit(1);
  }
}
