package kr.newniverse.sms;
import java.nio.charset.*;
import java.util.*;

/** v8: 지난 일정은 ‘past’ · 정신건강의학과 같은 병원 이름 · 단계 이름 · 여러 조각 장문(조각마다 글자셋·종류 표기) */
public class CalV8Test{
  static int ok=0,bad=0;
  static void t(String n,boolean c){ if(c) ok++; else { bad++; System.out.println("FAIL "+n);} }
  static final long AT=CalV5Test.ms(2026,11,26,18,45);
  static final String SCHOOL=CalV5Test.LMS;
  /** 실제 폰에서 18:45 에 온 ‘전달’ 문자 모양 (제목없음 · Web발신 · 병원 · 날짜는 지난 10월 31일) */
  static String clinic(String date){ return "전달: 제목없음\n[Web발신]\n장준혁님 더브레인정신건강의학과입니다.\n"+date+" 예약되어있습니다.\n변경이 필요하시면 병원으로 연락 주세요.\n** 대구광역시 수성구 달구벌대로 2360 메트로빌딩 6층으로 내원 바랍니다"; }

  static void past(String name,String text){
    t(name+" → parse null",ApptParser.parse(text,AT,null,"01011112222")==null);
    t(name+" → stage past",ApptParser.stage(text,AT,null,"01011112222").equals("parse:past"));
    CalDecide.D d=CalDecide.decide(true,true,text,AT,null,"01011112222");
    t(name+" → decide skip noparse:parse:past",d.act.equals("skip")&&d.reason.equals("noparse:parse:past"));
    if(!d.reason.equals("noparse:parse:past")) System.out.println("   got "+d.reason+" / "+ApptParser.stage(text,AT,null,"01011112222"));
  }

  static MmsPdu.Pdu pdu(List<byte[]> parts){ return MmsPdu.parse(MmsTest.retrieveConf("tr","mid","01012345678/TYPE=PLMN",106,null,1764133200L,parts)); }

  public static void main(String[] x){
    // 지난 일정 → past
    past("clinic 2026-10-31 09시 20분",clinic("2026-10-31 09시 20분"));
    past("clinic 2026.10.31 09:20",clinic("2026.10.31 09:20"));
    past("clinic 10월 31일 오전 9시 20분",clinic("10월 31일 오전 9시 20분").replace("2026","2026"));
    past("clinic one line",clinic("2026-10-31 09시 20분").replace("\n"," "));
    past("clinic NBSP+CRLF",clinic("2026-10-31 09시 20분").replace(" ","\u00A0").replace("\n","\r\n"));
    ApptParser.R pr=ApptParser.parse(clinic("2026-10-31 09시 20분"),AT,null,"01011112222");
    t("clinic dts reads 'YYYY-MM-DD HH시 MM분'",ApptParser.dts(ApptParser.norm(clinic("2026-10-31 09시 20분")),AT).size()==1&&ApptParser.dts(ApptParser.norm(clinic("2026-10-31 09시 20분")),AT).get(0).ms==CalV5Test.ms(2026,10,31,9,20));
    t("past stage listed with labels none",ApptParser.labels(clinic("2026-10-31 09시 20분"),AT).equals("일시 아니요 · 장소 아니요"));

    // 같은 병원 문자가 미래 날짜면 일정으로 읽고(병원 이름 인식) 자동 등록
    String fut=clinic("2026-12-31 09시 20분");
    ApptParser.R r=ApptParser.parse(fut,AT,null,"01011112222");
    t("future clinic parsed",r!=null&&r.when==CalV5Test.ms(2026,12,31,9,20)&&r.hasTime);
    t("clinic org found (정신건강의학과)",r!=null&&r.org.contains("정신건강의학과")&&r.trusted&&"hosp".equals(r.src));
    CalDecide.D d=CalDecide.decide(true,true,fut,AT,null,"01011112222");
    t("future clinic → auto",d.act.equals("auto"));
    t("future clinic stage ok",ApptParser.stage(fut,AT,null,"01011112222").equals("parse:ok"));

    // 학교 문자(점 찍힌 일시, 미래)
    r=ApptParser.parse(SCHOOL,AT,null,"01055556666");
    t("school future dotted 일시 → 2026-11-27 16:00",r!=null&&r.when==CalV5Test.ms(2026,11,27,16,0)&&r.location.equals("안동시 중앙로 1-2 솜씨쟁이"));
    t("school labels yes/yes",ApptParser.labels(SCHOOL,AT).equals("일시 예 · 장소 예"));
    t("school asks",CalDecide.decide(true,true,SCHOOL,AT,null,"01055556666").act.equals("ask"));
    // 학교 일정이 지난 뒤에 읽으면 past
    long late=CalV5Test.ms(2026,11,28,9,0);
    t("school after the event → past",ApptParser.stage(SCHOOL,late,null,"01055556666").equals("parse:past"));

    // 단계 이름
    t("stage no-date",ApptParser.stage("안녕하세요 반갑습니다 오늘도 좋은 하루",AT,null,"010").equals("parse:no-date"));
    String noval="[길주중학교]\n학부모 안내문입니다. 2026. 12. 5. 발송\n ○ 일시: 추후 공지\n ○ 장소: 강당\n 참가를 희망하시는 분은 신청해 주세요.";
    t("stage label-found-no-date",ApptParser.stage(noval,AT,null,"010").equals("parse:label-found-no-date"));
    t("stage deadline-only still",ApptParser.stage("[길주중학교]\n신청 기간: 2026. 11. 28. ~ 11. 30.(월)까지 신청해 주세요. 참가 희망자",AT,null,"010").equals("parse:deadline-only"));
    t("stage no-datetime-line still",ApptParser.stage("12/30 3시 회의록 잘 받았어",AT,"엄마","01011112222").equals("parse:no-datetime-line"));
    t("v9: 12/30 3시에 보자 → casual ok",ApptParser.stage("12/30 3시에 보자 ㅎㅎ 잘 지내",AT,"엄마","01011112222").equals("parse:ok"));
    t("stage no body no throw",ApptParser.stage("",AT,null,null).startsWith("parse:"));

    // ---- 여러 조각 장문 ----
    String[] ln=SCHOOL.split("\n");
    String p1=String.join("\n",Arrays.copyOfRange(ln,0,4)), p2=String.join("\n",Arrays.copyOfRange(ln,4,8)), p3=String.join("\n",Arrays.copyOfRange(ln,8,ln.length));
    Charset KR=Charset.forName("x-windows-949");
    List<byte[]> ps=new ArrayList<>();
    ps.add(MmsTest.smilPart());
    ps.add(MmsTest.part(MmsTest.ctGeneral(0x03,106),MmsTest.concat(MmsTest.hdrCid("t0"),MmsTest.hdrLoc("text_0.txt")),p1.getBytes(StandardCharsets.UTF_8)));            // 짧은 정수 종류 + UTF-8
    ps.add(MmsTest.part(new MmsTest.W().text("text/plain; charset=euc-kr").get(),MmsTest.concat(MmsTest.hdrCid("t1"),MmsTest.hdrLoc("text_1.txt")),p2.getBytes(KR)));   // 글자로 쓴 종류 + 매개변수 + EUC-KR
    ps.add(MmsTest.part(MmsTest.ctText("text/plain",106),MmsTest.concat(MmsTest.hdrCid("t2"),MmsTest.hdrLoc("text_2.txt")),p3.getBytes(StandardCharsets.UTF_8)));         // 글자 종류 + charset 매개
    MmsPdu.Pdu d3=pdu(ps);
    t("multipart parsed 4 parts",d3!=null&&d3.parts.size()==4);
    t("part ct params stripped → text/plain",d3.parts.get(2).ct.equals("text/plain")&&d3.parts.get(2).isText());
    t("part charset from ct text param",d3.parts.get(2).charset==38);
    t("all 3 text parts concatenated in order",d3.text().equals(p1+"\n"+p2+"\n"+p3));
    String viaMms=MmsLogic.feedText(d3.subjectStr(),d3.text());
    t("multipart school text → same event",ApptParser.parse(viaMms,AT,null,"01055556666")!=null&&ApptParser.stage(viaMms,AT,null,"01055556666").equals("parse:ok"));
    String sum=MmsLogic.pduSummary(d3,1234,1300);
    t("pdu summary parts+lens",sum.startsWith("4개")&&sum.contains("text/plain:"+p1.length()+"자")&&sum.contains("text/plain:"+p2.length()+"자")&&sum.contains("1234B")&&sum.contains("1300B"));
    t("pdu summary has no body",!sum.contains("길주")&&!sum.contains("솜씨")&&!sum.contains("010"));
    // quoted charset param
    List<byte[]> q=new ArrayList<>(); q.add(MmsTest.part(new MmsTest.W().text("text/plain;charset=\"utf-8\"").get(),new byte[0],"안녕하세요 2026. 12. 3. 일시".getBytes(StandardCharsets.UTF_8)));
    MmsPdu.Pdu dq=pdu(q); t("quoted charset param",dq!=null&&dq.parts.size()==1&&dq.parts.get(0).isText()&&dq.parts.get(0).charset==106&&dq.text().startsWith("안녕하세요"));
    // 조각 순서: 같은 PDU 의 조각 순서를 그대로 이어 붙임(뒤섞지 않음)
    List<byte[]> o=new ArrayList<>(); for(int i=1;i<=4;i++) o.add(MmsTest.part(MmsTest.ctGeneral(0x03,106),new byte[0],("조각"+i).getBytes(StandardCharsets.UTF_8)));
    t("order kept",pdu(o).text().equals("조각1\n조각2\n조각3\n조각4"));
    // 널 바이트가 끝에 붙은 조각
    List<byte[]> z=new ArrayList<>(); z.add(MmsTest.part(MmsTest.ctGeneral(0x03,106),new byte[0],("끝에 널\0\0").getBytes(StandardCharsets.UTF_8)));
    t("trailing NULs dropped",pdu(z).text().equals("끝에 널"));
    // 알림 상태 라벨: ‘성공’은 알림이 실제로 떴을 때만
    t("label ask posted",CalDecide.stepsLabel("ask","").contains("떠 있는 것 확인"));
    for(String nr:new String[]{"notif-perm","notif-off","notif-channel","notif-notshown"}){
      String l=CalDecide.stepsLabel("ask>blocked",nr); t("label blocked "+nr,l.contains("알림을 띄우지 못함")&&l.contains("앱 안 카드")&&!l.contains("성공"));
    }
    t("blocked perm label text",CalDecide.stepsLabel("ask>blocked","notif-perm").contains("알림 권한이 없음"));
    t("blocked is not good",!CalDecide.isGood("ask>blocked")&&CalDecide.isGood("ask"));
    System.out.println("Cal v8 java tests: "+ok+" passed, "+bad+" failed");
    if(bad>0) System.exit(1);
  }
}
