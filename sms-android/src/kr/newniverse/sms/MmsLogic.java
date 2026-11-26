package kr.newniverse.sms;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v4: 사진·장문(MMS/LMS) 받기의 순수 논리 (안드로이드 없음).
 * 오류 번호 → 쉬운 말, 다시 시도 규칙, 알림 문구, 진단 문구, 런처·달력에 넘길 글, 저장소 칸 값.
 * 진단·실패 알림 문구에는 본문·번호를 넣지 않는다.
 */
final class MmsLogic {
  static final int MAX_AUTO_RETRY=1;
  static final int TYPE_FROM=137, TYPE_TO=151, TYPE_CC=130;
  static final int M_TYPE_RETRIEVE_CONF=132, MSG_BOX_INBOX=1;

  /** 결과 코드(-1 = RESULT_OK), SmsManager.MMS_ERROR_* (1~12), 앱 안 오류(100~) → 쉬운 말 */
  static String errLabel(int code){
    switch(code){
      case -1: return "받음";
      case 1: return "알 수 없는 오류";
      case 2: return "MMS 접속 설정(APN) 문제";
      case 3: return "통신사 MMS 서버에 연결 못 함";
      case 4: return "통신사 서버 응답 오류(HTTP)";
      case 5: return "내려받는 중 끊김(입출력)";
      case 6: return "잠시 뒤 다시 시도 필요";
      case 7: return "통신사 설정 오류";
      case 8: return "모바일 데이터 연결 없음";
      case 9: return "유심(가입자) 번호 오류";
      case 10: return "유심이 꺼져 있음";
      case 11: return "모바일 데이터가 꺼져 있음";
      case 12: return "통신사가 MMS를 막아 둠";
      case 100: return "받은 파일이 비어 있음";
      case 101: return "받은 내용을 읽지 못함";
      case 102: return "폰 저장소에 넣지 못함";
      case 103: return "내려받기를 시작하지 못함";
      default: return "오류 "+code;
    }
  }
  /** 다시 해 볼 만한 오류인가 (연결·일시적) */
  static boolean isTransient(int code){ return code==1||code==3||code==4||code==5||code==6||code==8||code==103; }
  static boolean autoRetry(int code,int attempts){ return isTransient(code)&&attempts<=MAX_AUTO_RETRY; }

  static String failTitle(){ return "장문을 받지 못했어요"; }
  static String failText(int code){
    String why=errLabel(code);
    String hint=(code==11||code==8)?"모바일 데이터를 켜고 ":(code==2||code==7)?"통신사 MMS 설정을 확인하고 ":"신호가 잡히는 곳에서 ";
    return "사진·장문 문자가 와 있지만 통신사에서 내려받지 못했어요 ("+why+"). "+hint+"‘다시 받기’를 눌러 주세요.";
  }

  /** 알림 미리보기: 글이 있으면 글, 없으면 사진 개수 */
  static String preview(String subject,String text,int images){
    String t=feedText(subject,text);
    if(!t.isEmpty()) return images>0?"[사진 "+images+"장] "+t:t;
    if(images>0) return "사진 "+images+"장";
    return "(멀티미디어 문자)";
  }
  /** 런처 카드·달력용 글: 제목이 본문에 없으면 앞에 붙임 */
  static String feedText(String subject,String text){
    String s=subject==null?"":subject.trim(), t=text==null?"":text.trim();
    if(isNoSubject(s)) return t;
    if(t.isEmpty()) return s;
    if(t.contains(s)) return t;
    return s+"\n"+t;
  }
  static boolean isNoSubject(String s){ String x=s==null?"":s.trim(); return x.isEmpty()||x.equals("제목없음")||x.equals("제목 없음")||x.equalsIgnoreCase("no subject")||x.equals("<제목없음>"); }

  /** MMS 저장소 date 는 초 단위. 혹시 밀리초로 들어 있어도 밀리초로 맞춤 */
  static long msFromMmsDate(long v){ return v>100_000_000_000L?v:v*1000L; }

  /** AOSP 식: 제목은 원래 바이트를 ISO-8859-1 문자열로 저장하고 sub_cs 에 글자셋 */
  static String toIso(byte[] raw){ return raw==null?"":new String(raw,StandardCharsets.ISO_8859_1); }
  static String fromIso(String stored,int cs){
    if(stored==null||stored.isEmpty()) return "";
    if(cs<=0) return stored;
    for(int i=0;i<stored.length();i++) if(stored.charAt(i)>0xff) return stored; // 이미 풀린 글
    return MmsPdu.decode(stored.getBytes(StandardCharsets.ISO_8859_1),cs);
  }

  /** content://mms/inbox 에 넣을 칸 (date 는 초 단위, 받은 시각) */
  static Map<String,Object> pduValues(MmsPdu.Pdu d,long receivedMs,long threadId,long subId,boolean textOnly,String contentLocation){
    Map<String,Object> v=new LinkedHashMap<>();
    v.put("msg_box",MSG_BOX_INBOX);
    v.put("m_type",M_TYPE_RETRIEVE_CONF);
    v.put("date",receivedMs/1000L);
    if(d.date>0) v.put("date_sent",d.date);
    v.put("read",0); v.put("seen",0);
    if(threadId>=0) v.put("thread_id",threadId);
    if(subId>=0) v.put("sub_id",subId);
    if(d.subject!=null&&d.subject.raw.length>0){ v.put("sub",toIso(d.subject.raw)); v.put("sub_cs",d.subject.charset==0?MmsPdu.CS_UTF8:d.subject.charset); }
    v.put("ct_t",d.contentType==null||d.contentType.isEmpty()?"application/vnd.wap.multipart.related":d.contentType);
    if(!d.messageId.isEmpty()) v.put("m_id",d.messageId);
    if(!d.transactionId.isEmpty()) v.put("tr_id",d.transactionId);
    if(contentLocation!=null&&!contentLocation.isEmpty()) v.put("ct_l",contentLocation);
    v.put("m_cls",d.messageClass.isEmpty()?"personal":d.messageClass);
    v.put("v",d.version>0?d.version:0x12);
    if(d.size>0) v.put("m_size",d.size);
    if(d.priority>0) v.put("pri",d.priority);
    v.put("text_only",textOnly?1:0);
    return v;
  }
  static boolean textOnly(MmsPdu.Pdu d){ for(MmsPdu.Part p:d.parts) if(!p.isText()&&!p.isSmil()) return false; return true; }

  /** 진단: 내려받기 상태 문구 */
  static String dlLabel(String st){
    if("ok".equals(st)) return "받음";
    if("pending".equals(st)) return "받는 중";
    if("retry".equals(st)) return "다시 받는 중";
    if("fail".equals(st)) return "받지 못함";
    if("dup".equals(st)) return "이미 저장되어 있음";
    if("ignored".equals(st)) return "받을 내용 아님(보고 신호)";
    if("parse".equals(st)) return "알림을 읽지 못함";
    return "아직 없음";
  }
  /** 임시 PDU 파일 이름: 경로 문자 없이 짧게 */
  static boolean safeFileName(String n){ return n!=null&&n.matches("[A-Za-z0-9_\\-]{1,64}\\.pdu"); }

  /** v8 진단: 받은 PDU 의 조각 구성(종류:글자 수 또는 바이트)과 통신사가 알린 크기 대비 받은 크기. 본문 없음 */
  static String pduSummary(MmsPdu.Pdu d,int downloaded,long announced){
    StringBuilder sb=new StringBuilder(); int n=0;
    for(MmsPdu.Part p:d.parts){ if(n++>0) sb.append(", "); if(n>8){ sb.append("…"); break; } sb.append(p.ct).append(':').append(p.isText()?p.text().length()+"자":p.data.length+"B"); }
    return d.parts.size()+"개 · "+sb+" · 제목 "+d.subjectStr().length()+"자 · 받은 "+downloaded+"B"+(announced>0?" / 알린 크기 "+announced+"B":"");
  }
}
