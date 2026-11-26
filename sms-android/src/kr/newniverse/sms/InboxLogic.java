package kr.newniverse.sms;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v3: 받은 문자 저장·중복 확인·알림 문구의 순수 논리(안드로이드 없음).
 * 여러 조각(PDU)을 한 통으로 이어 붙이고, 구독 번호는 Long/Integer 모두 받는다.
 * 진단·알림 문구에는 문자 본문·번호를 넣지 않는다.
 */
final class InboxLogic {
  static final long DEDUP_MS=2L*60*1000;
  static final int TYPE_INBOX=1;

  static String concatParts(String[] parts){
    if(parts==null) return "";
    StringBuilder sb=new StringBuilder();
    for(String p:parts) if(p!=null) sb.append(p);
    return sb.toString();
  }

  /** SMS_DELIVER extra "subscription" 은 long 또는 int 로 올 수 있음 */
  static long subId(Object extra){
    if(extra instanceof Number) return ((Number)extra).longValue();
    if(extra instanceof String){
      try{ return Long.parseLong(((String)extra).trim()); }catch(Exception e){}
    }
    return -1L;
  }

  static String digits(String n){
    if(n==null) return "";
    String d=n.replaceAll("[^0-9+]","");
    if(d.startsWith("+82")&&d.length()>3) d="0"+d.substring(3);
    else if(d.startsWith("82")&&d.length()>=11) d="0"+d.substring(2);
    return d.replace("+","");
  }

  static boolean sameAddr(String a,String b){
    String x=digits(a),y=digits(b);
    if(x.isEmpty()||y.isEmpty()) return a!=null&&a.equals(b);
    return x.equals(y)||(x.length()>=8&&y.length()>=8&&(x.endsWith(y)||y.endsWith(x)));
  }

  static final class Row{
    final String addr,body; final long date;
    Row(String a,String b,long d){ addr=a; body=b; date=d; }
  }

  static boolean isDup(String addr,String body,long now,List<Row> existing){
    String b=body==null?"":body;
    if(existing==null) return false;
    for(Row r:existing){
      if(!sameAddr(addr,r.addr)) continue;
      if(!b.equals(r.body==null?"":r.body)) continue;
      if(Math.abs(now-r.date)<=DEDUP_MS) return true;
    }
    return false;
  }

  static List<Row> rows(String[][] pairsAndDate){
    List<Row> l=new ArrayList<>();
    if(pairsAndDate==null) return l;
    for(String[] p:pairsAndDate) l.add(new Row(p[0],p[1],Long.parseLong(p[2])));
    return l;
  }

  static Map<String,Object> valuesFull(String addr,String body,long date,long dateSent,long threadId,long subId){
    Map<String,Object> v=new LinkedHashMap<>();
    v.put("address",addr==null?"":addr);
    v.put("body",body==null?"":body);
    v.put("date",date);
    v.put("date_sent",dateSent);
    v.put("read",0);
    v.put("seen",0);
    v.put("type",TYPE_INBOX);
    if(threadId>=0) v.put("thread_id",threadId);
    if(subId>=0) v.put("sub_id",subId);
    return v;
  }

  static Map<String,Object> valuesMinimal(String addr,String body,long date,long threadId){
    Map<String,Object> v=new LinkedHashMap<>();
    v.put("address",addr==null?"":addr);
    v.put("body",body==null?"":body);
    v.put("date",date);
    v.put("type",TYPE_INBOX);
    v.put("read",0);
    if(threadId>=0) v.put("thread_id",threadId);
    return v;
  }

  static String notifyWho(boolean saved,String who){
    if(!saved) return "문자를 저장하지 못했어요";
    return who==null||who.isEmpty()?"새 문자":who;
  }
  static String notifyPublic(boolean saved){ return saved?"새 문자":"문자를 저장하지 못했어요"; }
  static String notifyFailText(){ return "받은 문자를 폰에 넣지 못했어요. 뉴니버스 문자 설정에서 ‘받은 문자 저장 기록’을 확인해 주세요."; }

  static String insertLabel(String insert){
    if("ok".equals(insert)) return "저장됨";
    if("retry-ok".equals(insert)) return "저장됨(다시 시도)";
    if("dup".equals(insert)) return "이미 저장되어 있음";
    if("fail".equals(insert)) return "저장 실패";
    if("parse".equals(insert)) return "문자를 읽지 못함";
    return "아직 없음";
  }
  static String sourceLabel(String src){
    if("deliver".equals(src)) return "SMS_DELIVER";
    if("received".equals(src)) return "안전망(SMS_RECEIVED)";
    return "아직 없음";
  }
  static String errClass(Throwable e){ return e==null?"":e.getClass().getSimpleName(); }
}
