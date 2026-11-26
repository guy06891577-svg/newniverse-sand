package kr.newniverse.sms;
import java.util.*;

/** v5: 학교 장문(LMS) 안내문 → 일정. 실제 폰에서 달력에 안 들어갔던 문자(글 줄은 그대로, 줄임표 부분만 채움)로 시험 */
public class CalV5Test{
  static int ok=0,bad=0;
  static void t(String n,boolean c){ if(c) ok++; else { bad++; System.out.println("FAIL "+n);} }
  static long ms(int y,int mo,int d,int h,int mi){ Calendar c=Calendar.getInstance(ApptParser.TZ); c.clear(); c.set(y,mo-1,d,h,mi,0); return c.getTimeInMillis(); }
  static final long AT=ms(2026,11,26,9,30);
  static final String LMS="[길주중학교]\n학부모님, 안녕하십니까?\n학부모 동아리 활동으로 목공 체험 프로그램을 운영합니다. 참가를 희망하시는 학부모님께서는 11월 26일(목)까지 네이버 폼으로 신청하여 주시기 바랍니다.\n\n ○ 일시: 2026. 11. 27.(금) 16:00 ~\n ○ 장소: 안동시 중앙로 1-2 솜씨쟁이\n ○ 대상: 본교 학부모 12명\n○ 주제: 원목 트레이, 원목 수납장 만들기\n ○ 신청 기간: 2026. 11. 24.(화) 10:00 ~ 11. 26.(목)\n○ 신청 방법: 네이버 폼 신청 접수 (주소 https://form.naver.com/response/abcDEF123)\n감사합니다.";
  static class Fake implements CalLogic.Store{ List<CalLogic.Entry> l=new ArrayList<>(); long seq=100; int ins=0; boolean failInsert=false;
    public List<CalLogic.Entry> load(){ List<CalLogic.Entry> r=new ArrayList<>(); for(CalLogic.Entry e:l) r.add(e.copy()); return r; }
    public void save(List<CalLogic.Entry> x){ l=new ArrayList<>(); for(CalLogic.Entry e:x) l.add(e.copy()); }
    public long insert(CalLogic.Entry e){ ins++; return failInsert?-1:++seq; }
    public void update(CalLogic.Entry e){} public void delete(CalLogic.Entry e){} }
  public static void main(String[] a){
    // 1. 점 찍힌 날짜
    List<ApptParser.DT> d=ApptParser.dts("2026. 11. 27.(금) 16:00 ~",AT);
    t("dotted year date+time",d.size()==1&&d.get(0).ms==ms(2026,11,27,16,0)&&d.get(0).hasTime);
    d=ApptParser.dts("11. 27.(금) 16:00",AT); t("M. D.(요일) HH:MM",d.size()==1&&d.get(0).ms==ms(2026,11,27,16,0));
    d=ApptParser.dts("12. 3. 오후 2시",AT); t("M. D. 오후 2시",d.size()==1&&d.get(0).ms==ms(2026,12,3,14,0));
    d=ApptParser.dts("2026.12.03(목) 14:30",AT); t("old dotted still works",d.size()==1&&d.get(0).ms==ms(2026,12,3,14,30));
    d=ApptParser.dts("2026. 12. 3.",AT); t("dotted date only",d.size()==1&&!d.get(0).hasTime);
    d=ApptParser.dts("11월 26일(목)까지 신청",AT); t("까지 deadline ignored",d.isEmpty());
    d=ApptParser.dts("○ 신청 기간: 2026. 11. 24.(화) 10:00 ~ 11. 26.(목)",AT); t("신청 기간 line ignored",d.isEmpty());
    d=ApptParser.dts("주소 중앙로 1-2 12명",AT); t("address 1-2 not a date",d.isEmpty());

    // 2. 정확한 문자
    ApptParser.R r=ApptParser.parse(LMS,AT,"내 번호","01012345678");
    t("event found",r!=null);
    t("event start 2026-11-27 16:00",r.when==ms(2026,11,27,16,0)&&r.hasTime);
    t("event NOT the 신청 기간 date",r.when!=ms(2026,11,24,10,0)&&r.when!=ms(2026,11,26,0,0)&&r.when!=ms(2026,11,26,10,0));
    t("event location from 장소",r.location.equals("안동시 중앙로 1-2 솜씨쟁이"));
    t("event title tag + 주제",r.title.equals("길주중학교 · 원목 트레이, 원목 수납장 만들기"));
    t("event org",r.org.equals("길주중학교")&&r.src.equals("school"));
    t("trusted via contact",r.trusted);
    t("is application notice → ask",r.ask);
    t("not an ad / not suspicious (form link w/o urgent)",!SmsSafety.isAd(LMS)&&!SmsSafety.suspicious(LMS));

    // 3. 신뢰: 학교 + (주소록 | 일반전화)
    r=ApptParser.parse(LMS,AT,null,"01012345678"); t("v7: school mobile not in contacts → still asks (never auto)",r!=null&&r.trusted&&r.ask);
    r=ApptParser.parse(LMS,AT,null,"0548401114"); t("school landline 054 → trusted",r!=null&&r.trusted);
    r=ApptParser.parse(LMS,AT,null,"+82548401114"); t("school landline +82 normalized → trusted",r!=null&&r.trusted);
    r=ApptParser.parse(LMS,AT,null,"02-123-4567"); t("school landline 02 → trusted",r!=null&&r.trusted);
    t("isLandline",ApptParser.isLandline("0548401114")&&ApptParser.isLandline("02-123-4567")&&!ApptParser.isLandline("01012345678")&&!ApptParser.isLandline("1588-1234")&&!ApptParser.isLandline(null));
    r=ApptParser.parse(LMS.replace("길주중학교","경북교육청"),AT,null,"01012345678"); t("교육청 trusted without contact",r!=null&&r.trusted&&r.src.equals("agency"));
    r=ApptParser.parse(LMS.replace("길주중학교","한빛학원"),AT,"원장님","01012345678"); t("academy tag + contact trusted",r!=null&&r.trusted&&r.src.equals("school"));
    r=ApptParser.parse(LMS.replace("길주중학교","행복마트"),AT,null,"01012345678"); t("random shop tag untrusted",r!=null&&!r.trusted);

    // 4. 결정: 신청 안내는 묻기, 확정은 자동, 이유 기록
    CalDecide.D x=CalDecide.decide(true,true,LMS,AT,"내 번호","01012345678");
    t("application → ask",x.act.equals("ask")&&x.r!=null);
    x=CalDecide.decide(false,true,LMS,AT,"내 번호","01012345678"); t("off → skip(off)",x.act.equals("skip")&&x.reason.equals("off"));
    x=CalDecide.decide(true,false,LMS,AT,"내 번호","01012345678"); t("no perm → skip(noperm)",x.act.equals("skip")&&x.reason.equals("noperm"));
    x=CalDecide.decide(true,true,LMS.replace("[길주중학교]",""),AT,null,"01012345678"); t("stranger without school tag → skip(untrusted)",x.act.equals("skip")&&x.reason.equals("untrusted"));
    x=CalDecide.decide(true,true,LMS,AT,null,"01012345678"); t("v7: school LMS from unknown mobile → ask",x.act.equals("ask"));
    x=CalDecide.decide(true,true,"안녕 오늘 저녁 뭐 먹어?",AT,"엄마","01011112222"); t("chat no date → skip(nodate)",x.reason.equals("nodate")&&x.r==null);
    x=CalDecide.decide(true,true,"12/3 3시에 보자 ㅎㅎ",AT,"엄마","01011112222"); t("chat with date → skip(noparse:parse:no-datetime-line)",x.reason.equals("noparse:parse:no-datetime-line"));
    x=CalDecide.decide(true,true,"(광고) 12/3 오후 3시 세일 예약 무료수신거부 080-123-4567",AT,"x","010"); t("ad skip",x.reason.equals("ad"));
    String conf="[안동병원] 장준혁님 2026. 12. 3.(목) 14:00 진료 예약되었습니다.";
    x=CalDecide.decide(true,true,conf,AT,null,"0548401114"); t("confirmed appointment → auto",x.act.equals("auto")&&x.r.when==ms(2026,12,3,14,0)&&!x.r.ask);
    String sc="[길주중학교] 학부모 상담 예약이 확정되었습니다.\n○ 일시: 2026. 12. 2.(수) 15:30\n○ 장소: 3학년 2반 교실";
    x=CalDecide.decide(true,true,sc,AT,null,"0548401114"); t("school confirmed 예약 확정 → auto, with location",x.act.equals("auto")&&x.r.location.equals("3학년 2반 교실")&&x.r.when==ms(2026,12,2,15,30));
    String info="[길주중학교] 학부모 총회 안내\n○ 일시: 2026. 12. 5.(토) 10:00 ~ 12:00\n○ 장소: 강당";
    x=CalDecide.decide(true,true,info,AT,null,"0548401114"); t("plain school notice (no 신청) → auto",x.act.equals("auto")&&x.r.endMs==ms(2026,12,5,12,0));
    t("recruit words",ApptParser.parse("[길주중학교]\n일시: 2026. 12. 5.(토) 10:00 선착순 모집 폼 신청",AT,"x","0548401114").ask);
    t("past event ignored",ApptParser.parse("[길주중학교] 안내\n○ 일시: 2026. 11. 1.(일) 10:00",AT,"x","0548401114")==null);

    // 5. 달력 쓰기: 위치·제목·실패 구분
    Fake f=new Fake(); x=CalDecide.decide(true,true,info,AT,null,"0548401114");
    CalLogic.Out o=CalLogic.apply(f,x.r,"0548401114",false);
    t("apply add keeps location+end",o.op.equals("add")&&o.e.location.equals("강당")&&o.e.end==ms(2026,12,5,12,0)&&o.e.title.equals("길주중학교 일정"));
    o=CalLogic.apply(f,x.r,"0548401114",false); t("apply dup",o.op.equals("dup")&&f.ins==1);
    Fake bf=new Fake(); bf.failInsert=true; o=CalLogic.apply(bf,x.r,"0548401114",false); t("insert failure is reported as fail, not none",o.op.equals("fail"));
    f=new Fake(); r=ApptParser.parse(LMS,AT,"내 번호","01012345678"); o=CalLogic.apply(f,r,"01012345678",true);
    t("manual 넣기 adds the event 2026-11-27 16:00 at 솜씨쟁이",o.op.equals("add")&&o.e.start==ms(2026,11,27,16,0)&&o.e.location.contains("솜씨쟁이")&&!o.e.allDay);

    // 6. 진단 문구
    t("label skip",CalDecide.stepsLabel("skip","off").equals("건너뜀(자동 넣기가 꺼져 있음)"));
    t("label try ok",CalDecide.stepsLabel("try>ok","").equals("시도 → 성공"));
    t("label try fail",CalDecide.stepsLabel("try>fail","nocalapp").equals("시도 → 실패(달력 앱이 없음)"));
    t("label ask",CalDecide.stepsLabel("ask","").contains("넣을까요")&&CalDecide.stepsLabel("ask>no","declined").contains("아니요"));
    t("label empty",CalDecide.stepsLabel("","").equals("아직 없음"));
    t("diag has no body or number",!CalDecide.stepsLabel("skip","untrusted").matches(".*\\d{4}.*"));
    t("brand name only",!CalDecide.stepsLabel("skip","off").contains("유니")&&!CalDecide.reasonLabel("nocalapp").toLowerCase().contains("newniverse"));

    // 7. 예전 확정 문자 동작 유지 + 장소 줄
    r=ApptParser.parse("[안동병원] 박준혁님 진료예약 확인\n일시: 2026.12.03(목) 14:30\n장소: 본관 3층 정형외과",AT,null);
    t("hospital confirm keeps old path + location",r!=null&&r.src.equals("hosp")&&r.when==ms(2026,12,3,14,30)&&r.location.equals("본관 3층 정형외과")&&!r.ask);
    System.out.println("Cal v5 java tests: "+ok+" passed, "+bad+" failed"); if(bad>0) System.exit(1);
  }}
