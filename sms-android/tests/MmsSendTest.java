package kr.newniverse.sms;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** v9: 장문(LMS) 보내기 — M-Send.req 만들기 → 우리 MmsPdu 읽기로 되읽기, 나눔 기준, 통신사 응답(M-Send.conf) */
public class MmsSendTest{
  static int ok=0,bad=0;
  static void t(String n,boolean c){ if(c) ok++; else { bad++; System.out.println("FAIL "+n);} }
  static String hex(byte[] b,int n){ StringBuilder s=new StringBuilder(); for(int i=0;i<Math.min(n,b.length);i++) s.append(String.format("%02X ",b[i]&0xff)); return s.toString().trim(); }
  static byte[] uv(long v){ ByteArrayOutputStream o=new ByteArrayOutputStream(); MmsPdu.writeUintvar(o,v); return o.toByteArray(); }
  static int indexOf(byte[] a,byte[] b){ outer: for(int i=0;i+b.length<=a.length;i++){ for(int j=0;j<b.length;j++) if(a[i+j]!=b[j]) continue outer; return i; } return -1; }

  public static void main(String[] x){
    String body="안녕하세요 뉴니버스 장문 시험입니다.\n둘째 줄 😀 이모지와 ‘따옴표’, 숫자 0123456789, 영어 ABC.\n"; while(body.length()<320) body+="한글 장문 문자가 한 통으로 가는지 확인해요. ";
    long date=1764223200L; // 임의 시각(초)
    byte[] p=MmsPdu.sendReq("T19ab",Collections.singletonList("010-9876-5432"),null,body,date,true);
    // 머리말 순서: Message-Type, Transaction-ID, MMS-Version 이 맨 앞
    t("starts 8C 80 98 'T19ab' 00 8D 92",hex(p,10).equals("8C 80 98 54 31 39 61 62 00 8D")&&(p[10]&0xff)==0x92);
    t("From = insert-address-token (89 01 81)",indexOf(p,new byte[]{(byte)0x89,0x01,(byte)0x81})>0);
    t("Date long-integer",indexOf(p,new byte[]{(byte)0x85,0x04,(byte)0x69,(byte)0x27,(byte)0xF8,(byte)0x60})>0||indexOf(p,new byte[]{(byte)0x85,0x04})>0);
    t("Expiry relative 7 days (88 05 81 03 09 3A 80)",indexOf(p,new byte[]{(byte)0x88,0x05,(byte)0x81,0x03,0x09,0x3A,(byte)0x80})>0);
    t("no delivery/read report (86 81, 90 81)",indexOf(p,new byte[]{(byte)0x86,(byte)0x81})>0&&indexOf(p,new byte[]{(byte)0x90,(byte)0x81})>0);
    MmsPdu.Pdu d=MmsPdu.parse(p);
    t("parses",d!=null);
    t("type send-req",d.type==MmsPdu.SEND_REQ);
    t("transaction id",d.transactionId.equals("T19ab"));
    t("version 1.2",d.version==0x12);
    t("from insert token, no address",d.fromInsertToken&&d.from==null);
    t("one recipient /TYPE=PLMN",d.to.size()==1&&d.to.get(0).str().equals("01098765432/TYPE=PLMN"));
    t("no subject header",d.subject==null&&d.subjectStr().isEmpty());
    t("date",d.date==date);
    t("expiry relative 604800",d.expiryRelative&&d.expiry==604800);
    t("priority normal",d.priority==0x81);
    t("message class personal",d.messageClass.equals("personal"));
    t("content-type multipart.related",d.contentType.equals("application/vnd.wap.multipart.related"));
    t("2 parts: smil + text",d.parts.size()==2&&d.parts.get(0).isSmil()&&d.parts.get(1).isText());
    MmsPdu.Part tp=d.parts.get(1);
    t("text charset utf-8",tp.charset==MmsPdu.CS_UTF8);
    t("text cid/cl",tp.cid.equals("text_0")&&tp.cl.equals("text_0.txt"));
    t("text round trip (한글·이모지·줄바꿈)",d.text().equals(body));
    t("smil cid + refers to text_0.txt",d.parts.get(0).cid.equals("smil")&&d.parts.get(0).text().contains("text_0.txt"));
    t("smil charset utf-8",d.parts.get(0).charset==MmsPdu.CS_UTF8);
    t("feedText = body (제목 없음)",MmsLogic.feedText(d.subjectStr(),d.text()).equals(body.trim()));
    // SMIL 없이(multipart/mixed)
    MmsPdu.Pdu m=MmsPdu.parse(MmsPdu.sendReq("T2",Collections.singletonList("01011112222"),null,"짧은 글",0,false));
    t("no smil → multipart.mixed, 1 text part",m!=null&&m.contentType.equals("application/vnd.wap.multipart.mixed")&&m.parts.size()==1&&m.text().equals("짧은 글")&&m.date==0);
    // 제목(쓰지 않지만 인코딩은 맞아야 함)
    MmsPdu.Pdu s=MmsPdu.parse(MmsPdu.sendReq("T3",Collections.singletonList("01011112222"),"안녕 제목",body,date,true));
    t("subject utf-8 round trip",s!=null&&s.subjectStr().equals("안녕 제목")&&s.text().equals(body));
    String longSub="가".repeat(40); MmsPdu.Pdu s2=MmsPdu.parse(MmsPdu.sendReq("T4",Collections.singletonList("01011112222"),longSub,"x",date,true));
    t("long subject (value-length 31+uintvar)",s2!=null&&s2.subjectStr().equals(longSub)&&s2.text().equals("x"));
    // 아주 긴 글 (uintvar 3바이트)
    String big="장문".repeat(4000);
    MmsPdu.Pdu b=MmsPdu.parse(MmsPdu.sendReq("T5",Collections.singletonList("01011112222"),null,big,date,true));
    t("24KB text round trip",b!=null&&b.text().equals(big));
    // 여러 받는 사람 · 주소 모양
    MmsPdu.Pdu g=MmsPdu.parse(MmsPdu.sendReq("T6",Arrays.asList("+82 10-1234-5678","a@b.kr"),null,"x",date,true));
    t("two recipients",g!=null&&g.to.size()==2&&g.to.get(0).str().equals("+821012345678/TYPE=PLMN")&&g.to.get(1).str().equals("a@b.kr"));
    // 받기 쪽에서 우리 장문을 받으면(M-Retrieve.conf 와 같은 본문) 그대로 읽힘 — 같은 multipart 읽기
    t("receive path text reading consistent",MmsLogic.preview(d.subjectStr(),d.text(),d.imageCount()).equals(body.trim()));
    // uintvar · long-integer
    t("uintvar 0x7f",hex(uv(0x7f),9).equals("7F"));
    t("uintvar 0x80",hex(uv(0x80),9).equals("81 00"));
    t("uintvar 16383",hex(uv(16383),9).equals("FF 7F"));
    t("uintvar 16384",hex(uv(16384),9).equals("81 80 00"));
    t("uintvar 0",hex(uv(0),9).equals("00"));
    { ByteArrayOutputStream o=new ByteArrayOutputStream(); MmsPdu.writeLong(o,604800); t("long 604800",hex(o.toByteArray(),9).equals("03 09 3A 80")); }
    { ByteArrayOutputStream o=new ByteArrayOutputStream(); MmsPdu.writeVlen(o,30); MmsPdu.writeVlen(o,31); t("vlen 30 / 31",hex(o.toByteArray(),9).equals("1E 1F 1F")); }
    // M-Send.conf 읽기
    byte[] conf=new byte[]{(byte)0x8C,(byte)0x81,(byte)0x98,'T','1',0,(byte)0x8D,(byte)0x92,(byte)0x92,(byte)0x80,(byte)0x8B,'m','i','d','7',0};
    MmsPdu.Pdu c=MmsPdu.parse(conf);
    t("send-conf parsed",c!=null&&c.type==MmsPdu.SEND_CONF&&c.responseStatus==0x80&&c.messageId.equals("mid7"));
    t("sendOk OK+0x80",MmsLogic.sendOk(-1,c));
    t("sendOk OK + no conf (some phones)",MmsLogic.sendOk(-1,null));
    byte[] rej=conf.clone(); rej[9]=(byte)0xE3; MmsPdu.Pdu cr=MmsPdu.parse(rej);
    t("sendOk OK but carrier rejected 0xE3 → fail",!MmsLogic.sendOk(-1,cr)&&MmsLogic.respLabel(0xE5).contains("받지 않음")&&MmsLogic.respLabel(0xE3).contains("보낸 사람"));
    t("sendOk error code 3 → fail",!MmsLogic.sendOk(3,c));
    t("resp labels",MmsLogic.respLabel(0x80).equals("통신사가 받음")&&MmsLogic.respLabel(0xC1).contains("일시")&&MmsLogic.respLabel(0x84).contains("보낸 사람 번호")&&MmsLogic.respLabel(0xEF).contains("0xef"));
    t("send err labels",MmsLogic.sendErrLabel(-1).equals("보냄")&&MmsLogic.sendErrLabel(8).equals("모바일 데이터 연결 없음")&&MmsLogic.sendErrLabel(106).contains("거절"));
    t("stage labels",MmsLogic.outStageLabel("handed").contains("넘김")&&MmsLogic.outStageLabel("saved").contains("저장"));
    // 나눔 기준: 한글 70자 · 영어 160자
    t("70 한글 → 1",MmsLogic.smsSegments("가".repeat(70))==1);
    t("71 한글 → 2 → LMS",MmsLogic.smsSegments("가".repeat(71))==2&&MmsLogic.useLms(MmsLogic.smsSegments("가".repeat(71))));
    t("160 ascii → 1",MmsLogic.smsSegments("a".repeat(160))==1&&!MmsLogic.useLms(1));
    t("161 ascii → 2",MmsLogic.smsSegments("a".repeat(161))==2);
    t("80 € (ext, 2칸) → 1; 81 → 2",MmsLogic.smsSegments("€".repeat(80))==1&&MmsLogic.smsSegments("€".repeat(81))==2);
    t("ascii + 1 한글 → UCS-2 rule",MmsLogic.smsSegments("a".repeat(70)+"가")==2&&MmsLogic.smsSegments("a".repeat(69)+"가")==1);
    t("empty → 1",MmsLogic.smsSegments("")==1);
    // 보낸 편지함 칸
    Map<String,Object> v=MmsLogic.outValues("T19ab",date*1000L,7,1,1234);
    t("outbox values",v.get("msg_box").equals(4)&&v.get("m_type").equals(128)&&v.get("date").equals(date)&&v.get("thread_id").equals(7L)&&v.get("tr_id").equals("T19ab")&&v.get("m_size").equals(1234)&&v.get("read").equals(1));
    t("outbox values hold no body/number",!v.toString().contains("010")&&!v.toString().contains("안녕"));
    t("core columns ⊂ outbox values",v.keySet().containsAll(MmsLogic.OUT_CORE));
    System.out.println("MMS send java tests: "+ok+" passed, "+bad+" failed");
    if(bad>0) System.exit(1);
  }
}
