package kr.newniverse.sms;
import java.util.*;
public class CalTest{
  static int ok=0,bad=0;
  static void t(String n,boolean c){ if(c) ok++; else { bad++; System.out.println("FAIL "+n);} }
  static long ms(int y,int mo,int d,int h,int mi){ Calendar c=Calendar.getInstance(ApptParser.TZ); c.clear(); c.set(y,mo-1,d,h,mi,0); return c.getTimeInMillis(); }
  static final long AT=ms(2026,11,26,12,40);
  static class Fake implements CalLogic.Store{ List<CalLogic.Entry> l=new ArrayList<>(); Map<Long,CalLogic.Entry> cal=new LinkedHashMap<>(); long seq=100; int ins=0,upd=0,del=0;
    public List<CalLogic.Entry> load(){ List<CalLogic.Entry> r=new ArrayList<>(); for(CalLogic.Entry e:l) r.add(e.copy()); return r; }
    public void save(List<CalLogic.Entry> x){ l=new ArrayList<>(); for(CalLogic.Entry e:x) l.add(e.copy()); }
    public long insert(CalLogic.Entry e){ ins++; long id=++seq; cal.put(id,e.copy()); return id; }
    public void update(CalLogic.Entry e){ upd++; CalLogic.Entry x=cal.get(e.id); if(x!=null){ x.start=e.start; x.allDay=e.allDay; } }
    public void delete(CalLogic.Entry e){ del++; cal.remove(e.id); } }
  static CalLogic.Out run(Fake f,String body,String sender,String contact,boolean manual){
    if(SmsSafety.isAd(body)||SmsSafety.suspicious(body)) return new CalLogic.Out();
    ApptParser.R r=ApptParser.parse(body,AT,contact); if(r==null||(!r.trusted&&!manual)) return new CalLogic.Out();
    return CalLogic.apply(f,r,sender,manual); }
  public static void main(String[] a){
    // ---- 파싱 ----
    ApptParser.R r=ApptParser.parse("[Web발신]\n[안동병원] 박준혁님 진료예약 확인: 2026.12.03(목) 14:30 소아청소년과 변경은 054-123-4567",AT,null);
    t("new hosp",r!=null&&r.kind==ApptParser.NEW&&r.org.equals("안동병원")&&r.when==ms(2026,12,3,14,30)&&r.hasTime&&r.who.equals("박준혁")&&r.trusted&&r.src.equals("hosp"));
    r=ApptParser.parse("[예약안내] 서울치과의원 12/3(목) 오후 2:30 예약되었습니다. 문의 02-123-4567",AT,null);
    t("new dental pm",r!=null&&r.kind==ApptParser.NEW&&r.org.equals("서울치과의원")&&r.when==ms(2026,12,3,14,30));
    r=ApptParser.parse("[하나이비인후과] 내일(11/27) 오전 10시 예약 안내드립니다.",AT,null);
    t("rel tomorrow",r!=null&&r.when==ms(2026,11,27,10,0)&&r.org.equals("하나이비인후과"));
    r=ApptParser.parse("(안동의료원) 홍길동님 12월 5일 9시 30분 내시경 검진 예약이 접수되었습니다",AT,null);
    t("korean date",r!=null&&r.when==ms(2026,12,5,9,30)&&r.org.equals("안동의료원"));
    r=ApptParser.parse("[안동시보건소] 독감 예방접종 예약 확인 12/8 10:00 방문해 주세요",AT,null);
    t("agency",r!=null&&r.src.equals("agency")&&r.trusted&&r.when==ms(2026,12,8,10,0));
    r=ApptParser.parse("[안동병원] 1/5 14:00 예약되었습니다",AT,null);
    t("next year",r!=null&&r.when==ms(2027,1,5,14,0));
    r=ApptParser.parse("내일 3시 병원 예약했어 같이 가자",AT,"엄마");
    t("contact no confirm word",r==null);
    r=ApptParser.parse("12/3 오후 3시 예약 확인했어. 그때 보자",AT,"김철수");
    t("contact confirm",r!=null&&r.src.equals("contact")&&r.trusted&&r.org.equals("김철수")&&r.when==ms(2026,12,3,15,0));
    r=ApptParser.parse("12/3 오후 3시 예약 확인했어. 그때 보자",AT,null);
    t("stranger untrusted",r!=null&&!r.trusted);
    r=ApptParser.parse("[CJ대한통운] 고객님의 택배가 내일(11/27) 도착 예정입니다. 운송장 6891 2345 6789",AT,null);
    t("parcel future",r!=null&&r.kind==ApptParser.PARCEL&&r.org.equals("CJ대한통운")&&!r.hasTime&&ApptParser.dayOf(r.when)==ApptParser.dayOf(ms(2026,11,27,0,0)));
    r=ApptParser.parse("[CJ대한통운] 고객님의 택배가 오늘(11/26) 배송 예정입니다.",AT,null);
    t("parcel today skipped",r==null);
    r=ApptParser.parse("[한진택배] 택배가 배송완료 되었습니다. 11/27 일 수령",AT,null);
    t("parcel delivered skipped",r==null);
    r=ApptParser.parse("[안동병원] 11/20 14:00 예약되었습니다",AT,null);
    t("past skipped",r==null);
    r=ApptParser.parse("[안동병원] 12/3 14:30 예약이 12/5 10:00로 변경되었습니다",AT,null);
    t("change two dates",r!=null&&r.kind==ApptParser.CHANGE&&r.oldWhen==ms(2026,12,3,14,30)&&r.when==ms(2026,12,5,10,0));
    r=ApptParser.parse("[안동병원] 12/3 14:30 예약이 취소되었습니다",AT,null);
    t("cancel",r!=null&&r.kind==ApptParser.CANCEL&&r.when==ms(2026,12,3,14,30));
    t("no date",ApptParser.parse("[안동병원] 진료 예약 안내드립니다. 전화 주세요",AT,null)==null);
    t("phone not date",ApptParser.parse("[안동병원] 예약 확인 054-123-4567 로 연락주세요",AT,null)==null);
    // ---- 광고·스미싱 ----
    String[] ads={"(광고)[안동치과] 12/3 예약 시 임플란트 할인 무료수신거부 080-123-4567","[광고] 안동병원 건강검진 예약 이벤트 12/10까지","[Web발신](광고) 피부과 예약 확정 12/5 14:00 수신거부 080 1234 5678","안동병원 예약일 12/3 14:00 무료거부 0801234567"};
    for(String s:ads) t("ad:"+s,SmsSafety.isAd(s));
    String[] sm={"[안동병원] 예약 확인 12/3 14:30 변경 http://bit.ly/3xYz9","[국민건강보험] 건강검진 예약 확인 12/3 http://nhis-check.xyz 미납 확인 필요","[안동병원] 예약 12/3 14:30 http://192.168.0.5/a","[택배] 내일 도착 예정 앱설치 하세요 http://x.kr/a.apk"};
    for(String s:sm) t("smish:"+s,SmsSafety.suspicious(s));
    t("clean link ok",!SmsSafety.suspicious("[안동병원] 예약 확인 12/3 14:30 변경 https://booking.naver.com/booking/13/bizes/123"));
    t("plain ok",!SmsSafety.isAd("[안동병원] 박준혁님 12/3 14:30 예약되었습니다")&&!SmsSafety.suspicious("[안동병원] 박준혁님 12/3 14:30 예약되었습니다"));
    Fake f=new Fake();
    for(String s:ads) run(f,s,"1588-1234",null,false);
    for(String s:sm) run(f,s,"1588-1234",null,false);
    t("ads+smish insert nothing",f.ins==0&&f.l.isEmpty());
    run(f,"12/3 오후 3시 예약 확인했어",   "01099998888",null,false);
    t("unknown sender skipped",f.ins==0);
    // ---- 넣기 · 중복 ----
    String B="[Web발신] [안동병원] 박준혁님 12/3 14:30 예약되었습니다";
    CalLogic.Out o=run(f,B,"1588-1234",null,false);
    t("add",o.op.equals("add")&&f.ins==1&&f.cal.size()==1&&o.e.title.equals("안동병원 예약 · 박준혁"));
    o=run(f,B,"1588-1234",null,false); t("dedupe same sms",o.op.equals("dup")&&f.ins==1);
    o=run(f,"[안동병원] 박준혁님 내일 모레 12/3 14:30 진료예정 안내드립니다","1588-9999",null,false); t("dedupe reminder other sender",o.op.equals("dup")&&f.ins==1);
    o=run(f,"[안동병원] 박준혁님 12/3 14:30 예약되었습니다","1588-1234",null,true); t("manual dup",o.op.equals("dup"));
    // ---- 변경 ----
    o=run(f,"[안동병원] 12/3 14:30 예약이 12/5 10:00로 변경되었습니다","1588-1234",null,false);
    t("change updates",o.op.equals("update")&&f.upd==1&&f.ins==1&&f.cal.values().iterator().next().start==ms(2026,12,5,10,0)&&f.l.get(0).start==ms(2026,12,5,10,0));
    CalLogic.Entry after=o.e.copy(), prev=o.prev.copy();
    CalLogic.undo(f,"update",after,prev); t("undo change restores",f.cal.values().iterator().next().start==ms(2026,12,3,14,30));
    run(f,"[안동병원] 12/3 14:30 예약이 12/5 10:00로 변경되었습니다","1588-1234",null,false);
    // change other sender -> no match -> added as new
    int before=f.ins; o=run(f,"[안동병원] 12/9 11:00 예약이 12/11 11:00로 변경되었습니다","1588-7777",null,false);
    t("change without match adds new",o.op.equals("add")&&f.ins==before+1&&f.cal.size()==2);
    // ---- 취소 ----
    o=run(f,"[안동병원] 12/5 10:00 예약이 취소되었습니다","1588-0000",null,false); t("cancel other sender no match",o.op.equals("none")&&f.cal.size()==2);
    o=run(f,"[안동병원] 12/5 10:00 예약이 취소되었습니다","1588-1234",null,false); t("cancel deletes",o.op.equals("delete")&&f.cal.size()==1&&f.del>=1);
    t("cancel removes from list",f.l.stream().noneMatch(e->e.id>0&&e.start==ms(2026,12,5,10,0)));
    CalLogic.undo(f,"delete",null,o.prev); t("undo cancel reinserts",f.cal.size()==2);
    o=run(f,"[서울치과의원] 12/20 15:00 예약이 취소되었습니다","1588-1234",null,false); t("cancel nothing to cancel",o.op.equals("none"));
    // ---- 취소(undo) 후 재안내는 다시 넣지 않음 ----
    Fake g=new Fake(); o=run(g,"[하나이비인후과] 12/4 10:00 예약되었습니다","02-111-2222",null,false); CalLogic.undo(g,"add",o.e.copy(),null);
    t("undo add deletes",g.cal.isEmpty());
    o=run(g,"[하나이비인후과] 12/4 10:00 예약되었습니다 리마인드","02-111-2222",null,false); t("after undo auto doesn't re-add",o.op.equals("dup")&&g.ins==1);
    o=run(g,"[하나이비인후과] 12/4 10:00 예약되었습니다","02-111-2222",null,true); t("manual after undo re-adds",o.op.equals("add")&&g.ins==2);
    // ---- 택배 하루 ----
    Fake p=new Fake(); o=run(p,"[롯데택배] 택배가 모레(11/28) 도착 예정입니다. 송장번호 241234567890","1566-2222",null,false);
    t("parcel allday add",o.op.equals("add")&&o.e.allDay&&o.e.title.equals("택배 도착 예정 · 롯데택배"));
    o=run(p,"[롯데택배] 택배가 모레(11/28) 도착 예정입니다. 송장번호 241234567890","1566-2222",null,false); t("parcel dedupe",o.op.equals("dup"));
    // ---- 연락처 ----
    Fake c=new Fake(); o=run(c,"12/3 오후 3시 예약 확인했어. 그때 보자","010-1234-5678","김철수",false); t("contact auto add",o.op.equals("add")&&o.e.title.equals("예약 · 김철수"));
    System.out.println("Calendar/safety Java tests: "+ok+" passed, "+bad+" failed"); if(bad>0) System.exit(1);
  }}
