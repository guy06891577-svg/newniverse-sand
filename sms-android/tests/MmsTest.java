package kr.newniverse.sms;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** v4: 장문·사진(MMS/LMS) PDU 읽기, 합치기 순서, 런처 넘김 시험. 실제 통신사 PDU 와 같은 WSP 인코딩으로 픽스처를 만듦. */
public class MmsTest {
  static int ok=0,bad=0;
  static void t(String n,boolean c){ if(c) ok++; else { bad++; System.out.println("FAIL "+n);} }

  // ---- 픽스처 만들기 (OMA MMS / WSP 인코딩) ----
  static final class W {
    final ByteArrayOutputStream o=new ByteArrayOutputStream();
    W b(int... v){ for(int x:v) o.write(x); return this; }
    W bytes(byte[] x){ o.write(x,0,x.length); return this; }
    W text(String s){ byte[] x=s.getBytes(StandardCharsets.ISO_8859_1); if(x.length>0&&(x[0]&0xff)>=0x80) o.write(0x7f); bytes(x); o.write(0); return this; }
    W textRaw(byte[] x){ if(x.length>0&&(x[0]&0xff)>=0x80) o.write(0x7f); bytes(x); o.write(0); return this; }
    W uintvar(long v){ int[] t=new int[5]; int n=0; do{ t[n++]=(int)(v&0x7f); v>>=7; }while(v>0); for(int i=n-1;i>=0;i--) o.write(t[i]|(i>0?0x80:0)); return this; }
    W vlen(int n){ if(n<31) o.write(n); else { o.write(31); uintvar(n); } return this; }
    W longInt(long v){ int n=0; long x=v; byte[] t=new byte[8]; do{ t[n++]=(byte)(x&0xff); x>>>=8; }while(x>0); o.write(n); for(int i=n-1;i>=0;i--) o.write(t[i]); return this; }
    /** Encoded-string-value with charset (short-integer if <128) */
    W enc(int cs,byte[] raw){ W v=new W(); if(cs<128) v.b(0x80|cs); else v.longInt(cs); v.textRaw(raw); byte[] x=v.get(); vlen(x.length); bytes(x); return this; }
    W from(String addr){ W v=new W(); v.b(0x80).text(addr); byte[] x=v.get(); vlen(x.length); bytes(x); return this; }
    byte[] get(){ return o.toByteArray(); }
  }
  static Charset KR=Charset.forName("x-windows-949");

  static byte[] part(byte[] ctValue,byte[] headers,byte[] data){
    W h=new W().bytes(ctValue).bytes(headers); byte[] hb=h.get();
    return new W().uintvar(hb.length).uintvar(data.length).bytes(hb).bytes(data).get();
  }
  static byte[] ctGeneral(int wkType,int charset){ W v=new W().b(0x80|wkType); if(charset>0){ v.b(0x81); if(charset<128) v.b(0x80|charset); else v.longInt(charset); } byte[] x=v.get(); return new W().vlen(x.length).bytes(x).get(); }
  static byte[] ctText(String type,int charset){ W v=new W().text(type); if(charset>0){ v.b(0x81); if(charset<128) v.b(0x80|charset); else v.longInt(charset); } byte[] x=v.get(); return new W().vlen(x.length).bytes(x).get(); }
  static byte[] hdrLoc(String name){ return new W().b(0x8E).text(name).get(); }
  static byte[] hdrCid(String cid){ return new W().b(0xC0).b(0x22).text("<"+cid+">").get(); }

  static byte[] retrieveConf(String tr,String mid,String from,int subCs,byte[] subj,long date,List<byte[]> parts){
    W w=new W();
    w.b(0x8C,0x84).b(0x98).text(tr).b(0x8D,0x92);
    w.b(0x8B).text(mid);
    w.b(0x89).from(from);
    w.b(0x97).enc(106,"01099998888/TYPE=PLMN".getBytes(StandardCharsets.US_ASCII));
    if(subj!=null) w.b(0x96).enc(subCs,subj);
    w.b(0x85).longInt(date);
    w.b(0x8A,0x80).b(0x8F,0x81);
    // Content-Type: multipart.related; type="application/smil"; start="<smil>"
    W ct=new W().b(0x80|0x33).b(0x89).text("application/smil").b(0x8A).text("<smil>"); byte[] cb=ct.get();
    w.b(0x84).vlen(cb.length).bytes(cb);
    w.uintvar(parts.size()); for(byte[] p:parts) w.bytes(p);
    return w.get();
  }
  static byte[] smilPart(){ String smil="<smil><head><layout><root-layout/></layout></head><body><par dur=\"5000ms\"><text src=\"text_0.txt\" region=\"Text\"/></par></body></smil>";
    return part(ctText("application/smil",106),concat(hdrCid("smil"),hdrLoc("smil.xml")),smil.getBytes(StandardCharsets.UTF_8)); }
  static byte[] concat(byte[]... a){ W w=new W(); for(byte[] x:a) w.bytes(x); return w.get(); }

  static final String LMS_KO="[Web발신]\n[안동병원] 장준혁님 12월 3일(수) 오후 2시 정형외과 진료 예약되었습니다. 변경·취소는 054-840-1114로 연락 주세요. 진료 30분 전까지 1층 원무과에서 접수해 주시기 바랍니다. 주차는 지하 2층을 이용해 주세요.";

  public static void main(String[] a){
    // 1. M-Notification.ind (WAP 푸시)
    W n=new W();
    n.b(0x8C,0x82).b(0x98).text("SKT-tr-0001").b(0x8D,0x92);
    n.b(0x89).from("01012345678/TYPE=PLMN");
    n.b(0x96).enc(38,"[안동병원] 예약 안내".getBytes(KR));
    n.b(0x8A,0x80).b(0x8E).longInt(2345);
    W ex=new W().b(0x81).longInt(604800); byte[] eb=ex.get(); n.b(0x88).vlen(eb.length).bytes(eb);
    n.b(0x83).text("http://omms.nate.com:9082/oma_b?k=AbC123");
    MmsPdu.Pdu ni=MmsPdu.parse(n.get());
    t("notif parsed",ni!=null);
    t("notif type",ni.type==MmsPdu.NOTIFICATION_IND);
    t("notif tr id",ni.transactionId.equals("SKT-tr-0001"));
    t("notif location",ni.contentLocation.equals("http://omms.nate.com:9082/oma_b?k=AbC123"));
    t("notif from (TYPE=PLMN stripped)",ni.fromStr().equals("01012345678"));
    t("notif subject EUC-KR",ni.subjectStr().equals("[안동병원] 예약 안내"));
    t("notif size",ni.size==2345);
    t("notif expiry relative",ni.expiryRelative&&ni.expiry==604800);
    t("notif class",ni.messageClass.equals("personal"));
    t("notif version 1.2",ni.version==0x12);

    // 2. EUC-KR LMS (SKT 형식: smil + text/plain;charset=EUC-KR), 300바이트 넘는 글 → uintvar 여러 바이트
    byte[] krBody=LMS_KO.getBytes(KR);
    t("fixture body > 127 bytes",krBody.length>127);
    List<byte[]> ps=new ArrayList<>(); ps.add(smilPart()); ps.add(part(ctGeneral(0x03,38),concat(hdrCid("text_0"),hdrLoc("text_0.txt")),krBody));
    byte[] lms=retrieveConf("SKT-tr-0001","MID-77@skt","01012345678/TYPE=PLMN",38,"[안동병원]".getBytes(KR),1764133200L,ps);
    MmsPdu.Pdu r=MmsPdu.parse(lms);
    t("lms parsed",r!=null&&r.type==MmsPdu.RETRIEVE_CONF);
    t("lms content type",r.contentType.equals("application/vnd.wap.multipart.related"));
    t("lms parts 2",r.parts.size()==2);
    t("lms smil part",r.parts.get(0).isSmil()&&r.parts.get(0).cid.equals("smil")&&r.parts.get(0).cl.equals("smil.xml"));
    t("lms text part ct+charset",r.parts.get(1).isText()&&r.parts.get(1).charset==38);
    t("lms text EUC-KR decoded",r.text().equals(LMS_KO));
    t("lms text excludes smil",!r.text().contains("<smil>"));
    t("lms subject",r.subjectStr().equals("[안동병원]"));
    t("lms from",r.fromStr().equals("01012345678"));
    t("lms to",r.to.size()==1&&MmsPdu.cleanAddr(r.to.get(0).str()).equals("01099998888"));
    t("lms date",r.date==1764133200L);
    t("lms message id",r.messageId.equals("MID-77@skt"));
    t("lms tr id",r.transactionId.equals("SKT-tr-0001"));
    t("lms no images",r.imageCount()==0&&MmsLogic.textOnly(r));
    t("lms appt parsable",ApptParser.parse(MmsLogic.feedText(r.subjectStr(),r.text()),1764000000000L,null)!=null);

    // 3. KS_C_5601 (36) 문자셋 번호, CP949 확장 글자(똠, 햏)
    String ext="똠방각하 햏님 택배 배송 예정";
    List<byte[]> p3=new ArrayList<>(); p3.add(part(ctGeneral(0x03,36),hdrLoc("t.txt"),ext.getBytes(KR)));
    MmsPdu.Pdu r3=MmsPdu.parse(retrieveConf("t3","m3","15881255",0,null,1L,p3));
    t("ksc5601 cp949 ext decoded",r3!=null&&r3.text().equals(ext));
    t("no subject → empty",r3.subjectStr().isEmpty());

    // 4. UTF-8 LMS (텍스트 Content-Type 이름, charset=106) + 사진 여러 장 multipart.mixed
    String u8="[CJ대한통운] 고객님의 택배가 오늘 도착 예정입니다 😀 운송장 123456789012";
    byte[] jpg=new byte[600]; jpg[0]=(byte)0xFF; jpg[1]=(byte)0xD8; for(int i=2;i<jpg.length;i++) jpg[i]=(byte)(i*7); jpg[598]=(byte)0xFF; jpg[599]=(byte)0xD9;
    byte[] png=new byte[]{(byte)0x89,'P','N','G',13,10,26,10,0,0,0,13};
    W mixed=new W();
    mixed.b(0x8C,0x84).b(0x98).text("tr4").b(0x8D,0x93).b(0x89).from("+821055556666/TYPE=PLMN").b(0x96).enc(106,"택배 사진".getBytes(StandardCharsets.UTF_8)).b(0x85).longInt(1764200000L);
    mixed.b(0x84).b(0x80|0x23); // multipart.mixed (short form)
    mixed.uintvar(3);
    mixed.bytes(part(new byte[]{(byte)0x9E},hdrLoc("photo1.jpg"),jpg));            // image/jpeg 짧은 형태
    mixed.bytes(part(ctText("text/plain",106),concat(hdrCid("t1"),hdrLoc("text.txt")),u8.getBytes(StandardCharsets.UTF_8)));
    W pngCt=new W().b(0x80|0x20).b(0x85).text("cat.png"); byte[] pc=pngCt.get();
    mixed.bytes(part(new W().vlen(pc.length).bytes(pc).get(),new byte[0],png));
    MmsPdu.Pdu r4=MmsPdu.parse(mixed.get());
    t("mixed parsed",r4!=null&&r4.parts.size()==3);
    t("mixed jpeg part",r4.parts.get(0).ct.equals("image/jpeg")&&r4.parts.get(0).isImage()&&Arrays.equals(r4.parts.get(0).data,jpg));
    t("mixed jpeg cl",r4.parts.get(0).cl.equals("photo1.jpg"));
    t("mixed utf8 text with emoji",r4.text().equals(u8));
    t("mixed png name param",r4.parts.get(2).ct.equals("image/png")&&r4.parts.get(2).name.equals("cat.png")&&Arrays.equals(r4.parts.get(2).data,png));
    t("mixed image count",r4.imageCount()==2&&!MmsLogic.textOnly(r4));
    t("mixed subject utf8",r4.subjectStr().equals("택배 사진"));
    t("mixed from +82",r4.fromStr().equals("+821055556666"));
    t("mixed version 1.3",r4.version==0x13);
    t("preview with photos",MmsLogic.preview(r4.subjectStr(),r4.text(),r4.imageCount()).startsWith("[사진 2장] 택배 사진\n[CJ대한통운]"));

    // 5. 글자셋 없음: UTF-8 이면 UTF-8, 아니면 CP949 로 추측
    t("unknown cs utf8",MmsPdu.decode("병원 예약".getBytes(StandardCharsets.UTF_8),0).equals("병원 예약"));
    t("unknown cs euc-kr",MmsPdu.decode("병원 예약".getBytes(KR),0).equals("병원 예약"));
    t("ucs2 (1000)",MmsPdu.decode("카드 승인".getBytes(StandardCharsets.UTF_16BE),1000).equals("카드 승인"));
    List<byte[]> p5=new ArrayList<>(); p5.add(part(ctGeneral(0x03,1000),new byte[0],"인증번호 482913".getBytes(StandardCharsets.UTF_16BE)));
    t("ucs2 long-integer charset param",MmsPdu.parse(retrieveConf("t5","m5","1",0,null,1L,p5)).text().equals("인증번호 482913"));
    t("trailing NUL stripped",MmsPdu.decode(new byte[]{'a','b',0,0},106).equals("ab"));

    // 6. 망가진 PDU: 예외 없이 null 또는 부분 결과
    t("null pdu",MmsPdu.parse(null)==null);
    t("tiny pdu",MmsPdu.parse(new byte[]{0x01})==null);
    byte[] cut=Arrays.copyOf(lms,lms.length-40);
    MmsPdu.Pdu rc=null; boolean threw=false; try{ rc=MmsPdu.parse(cut); }catch(Throwable e){ threw=true; }
    t("truncated no throw",!threw&&rc!=null&&rc.type==MmsPdu.RETRIEVE_CONF);
    boolean threw2=false; Random rnd=new Random(7);
    for(int k=0;k<300;k++){ byte[] g=lms.clone(); for(int j=0;j<6;j++) g[rnd.nextInt(g.length)]=(byte)rnd.nextInt(256); try{ MmsPdu.parse(g); }catch(Throwable e){ threw2=true; } }
    t("fuzzed no throw",!threw2);

    // 7. M-NotifyResp.ind 만들기 → 다시 읽기
    byte[] nr=MmsPdu.notifyResp("SKT-tr-0001",MmsPdu.STATUS_RETRIEVED);
    MmsPdu.Pdu nrp=MmsPdu.parse(nr);
    t("notifyresp bytes start",(nr[0]&0xff)==0x8C&&(nr[1]&0xff)==0x83);
    t("notifyresp roundtrip",nrp!=null&&nrp.type==MmsPdu.NOTIFYRESP_IND&&nrp.transactionId.equals("SKT-tr-0001")&&nrp.status==0x81&&nrp.version==0x12);

    // 8. 저장소 칸 (AOSP 관례)
    Map<String,Object> v=MmsLogic.pduValues(r,1764133260123L,42,1,true,"http://x");
    t("values date seconds",((Long)v.get("date"))==1764133260L);
    t("values msg_box inbox",((Integer)v.get("msg_box"))==1&&((Integer)v.get("m_type"))==132);
    t("values thread",((Long)v.get("thread_id"))==42L);
    t("values sub iso+cs",((Integer)v.get("sub_cs"))==38&&MmsLogic.fromIso((String)v.get("sub"),38).equals("[안동병원]"));
    t("values m_id tr_id",v.get("m_id").equals("MID-77@skt")&&v.get("tr_id").equals("SKT-tr-0001"));
    t("values text_only",((Integer)v.get("text_only"))==1);
    t("fromIso already decoded",MmsLogic.fromIso("병원",106).equals("병원"));
    t("fromIso no cs",MmsLogic.fromIso("abc",0).equals("abc"));

    // 9. 쉬운 말·다시 시도·알림 (본문·번호 없음)
    t("err 8",MmsLogic.errLabel(8).contains("모바일 데이터"));
    t("err 2 apn",MmsLogic.errLabel(2).contains("APN"));
    t("auto retry once on transient",MmsLogic.autoRetry(5,1)&&!MmsLogic.autoRetry(5,2));
    t("no auto retry on disabled data",!MmsLogic.autoRetry(11,1));
    t("fail title",MmsLogic.failTitle().equals("장문을 받지 못했어요"));
    t("fail text has retry, no v1",MmsLogic.failText(3).contains("다시 받기")&&!MmsLogic.failText(3).contains("v1"));
    t("fail text no digits of numbers",!MmsLogic.failText(4).matches(".*\\d{3,}.*"));
    t("safe file name",MmsLogic.safeFileName("k1a2b-1.pdu")&&!MmsLogic.safeFileName("../x.pdu")&&!MmsLogic.safeFileName("a/b.pdu")&&!MmsLogic.safeFileName(null));
    t("dl label",MmsLogic.dlLabel("fail").equals("받지 못함")&&MmsLogic.dlLabel("").equals("아직 없음"));
    t("feedText subject dup",MmsLogic.feedText("[안동병원]",LMS_KO).equals(LMS_KO.trim()));
    t("feedText subject prepend",MmsLogic.feedText("카드 승인","35,000원 일시불").equals("카드 승인\n35,000원 일시불"));
    t("feedText no subject",MmsLogic.feedText("제목없음","본문").equals("본문"));
    t("mms date s→ms",MmsLogic.msFromMmsDate(1764133200L)==1764133200000L&&MmsLogic.msFromMmsDate(1764133200000L)==1764133200000L);

    // 10. SMS + MMS 합치기 순서
    List<MsgMerge.Item> sms=new ArrayList<>(), mms=new ArrayList<>();
    sms.add(new MsgMerge.Item(false,1,7,1000_000L,true,false).with("010","a"));
    sms.add(new MsgMerge.Item(false,2,7,3000_000L,false,false).with("010","c"));
    mms.add(new MsgMerge.Item(true,1,7,MmsLogic.msFromMmsDate(2000),true,true).with("010","b-lms"));
    mms.add(new MsgMerge.Item(true,2,7,MmsLogic.msFromMmsDate(3000),true,false).with("010","d-same-time"));
    List<MsgMerge.Item> conv=MsgMerge.conversation(sms,mms,100);
    StringBuilder ord=new StringBuilder(); for(MsgMerge.Item it:conv) ord.append(it.text).append(',');
    t("conversation ordered by date, sms first on tie",ord.toString().equals("a,b-lms,c,d-same-time,"));
    t("conversation keys",conv.get(1).key().equals("m1")&&conv.get(0).key().equals("s1"));
    List<MsgMerge.Item> lim=MsgMerge.conversation(sms,mms,2);
    t("conversation keeps newest lim",lim.size()==2&&lim.get(0).text.equals("c")&&lim.get(1).text.equals("d-same-time"));
    sms.add(new MsgMerge.Item(false,3,8,5000_000L,true,true).with("020","other thread newest"));
    List<MsgMerge.Conv> th=MsgMerge.threads(sms,mms);
    t("threads count",th.size()==2);
    t("threads newest first",th.get(0).id==8&&th.get(1).id==7);
    t("thread 7 last is mms",th.get(1).last.mms&&th.get(1).last.text.equals("d-same-time"));
    t("thread 7 unread counts mms",th.get(1).unread==1&&th.get(1).n==4);
    t("thread lastIn",th.get(1).lastIn==3000_000L);

    // 11. 런처 넘김: LMS 글도 포함, 사적인 글·보낸 글 제외, 최근 순
    List<MsgMerge.Item> fs=new ArrayList<>(), fm=new ArrayList<>();
    fs.add(new MsgMerge.Item(false,11,0,10_000L,true,false).with("1588","[국민은행] 인증번호 [482913]를 입력해 주세요"));
    fs.add(new MsgMerge.Item(false,12,0,30_000L,true,false).with("010","엄마 오늘 저녁 뭐 먹어?"));
    fm.add(new MsgMerge.Item(true,21,0,20_000L,true,false).with("01012345678",MmsLogic.feedText(r.subjectStr(),r.text())));
    fm.add(new MsgMerge.Item(true,22,0,40_000L,true,false).with("1588",MmsLogic.feedText(r4.subjectStr(),r4.text())));
    fm.add(new MsgMerge.Item(true,23,0,50_000L,false,false).with("010","내가 보낸 택배 사진"));
    List<MsgMerge.Item> feed=MsgMerge.feed(fs,fm,300);
    t("feed size",feed.size()==3);
    t("feed newest first",feed.get(0).id==22&&feed.get(1).id==21&&feed.get(2).id==11);
    t("feed includes LMS 병원",feed.get(1).mms&&feed.get(1).text.contains("진료 예약"));
    t("feed excludes private + outgoing",feed.stream().noneMatch(x->x.text.contains("엄마")||x.text.contains("내가 보낸")));
    t("feed max",MsgMerge.feed(fs,fm,1).size()==1);

    System.out.println("MMS java tests: "+ok+" passed, "+bad+" failed");
    if(bad>0) System.exit(1);
  }
}
