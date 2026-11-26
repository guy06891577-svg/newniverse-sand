package kr.newniverse.sms;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import org.json.JSONObject;

/**
 * v3: 설정 화면에 보여주는 짧은 저장 기록. 문자 본문·번호·비밀은 남기지 않음.
 * (시각, 저장 결과/예외 종류, 받은 편지함 개수, 기본 앱 여부, 권한, 런처의 마지막 조회)
 * v4: 장문·사진(MMS) — 마지막 도착 신호 시각, 내려받기 결과·오류 번호·HTTP 상태, 저장 여부, ‘받았어요’ 응답, 못 받은 장문 수
 */
final class SmsDiag {
  static final String PREF="nv_diag";

  static SharedPreferences p(Context c){ return c.getSharedPreferences(PREF,Context.MODE_PRIVATE); }

  static int inboxCount(Context c){
    if(!Nv.has(c,android.Manifest.permission.READ_SMS)) return -1;
    try(Cursor q=c.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,new String[]{"_id"},null,null,null)){
      return q==null?-1:q.getCount();
    }catch(Exception e){ return -1; }
  }

  static void snapshotPerms(SharedPreferences.Editor e,Context c){
    e.putBoolean("def",Nv.isDefault(c));
    e.putBoolean("read",Nv.has(c,android.Manifest.permission.READ_SMS));
    e.putBoolean("recv",Nv.has(c,android.Manifest.permission.RECEIVE_SMS));
    e.putBoolean("send",Nv.has(c,android.Manifest.permission.SEND_SMS));
    e.putInt("inbox",inboxCount(c));
  }

  /** 받은 문자 처리 결과. insert = ok|retry-ok|dup|fail|parse , err = 예외 클래스 이름만 */
  static void incoming(Context c,String source,String insert,String err){
    try{
      SharedPreferences.Editor e=p(c).edit();
      e.putLong("at",System.currentTimeMillis());
      e.putString("source",source==null?"":source);
      e.putString("insert",insert==null?"":insert);
      e.putString("err",err==null?"":err);
      snapshotPerms(e,c);
      e.apply();
    }catch(Exception x){}
  }

  /** 런처가 FeedProvider 를 호출했을 때. method/ok/rows/err 만 남김(본문 없음) */
  static void feed(Context c,String method,boolean ok,int rows,String err){
    try{
      SharedPreferences.Editor e=p(c).edit();
      e.putLong("feed_at",System.currentTimeMillis());
      e.putString("feed_method",method==null?"":method);
      e.putBoolean("feed_ok",ok);
      e.putInt("feed_rows",rows);
      e.putString("feed_err",err==null?"":err);
      e.apply();
    }catch(Exception x){}
  }

  /** v4: 장문 처리. at>0 이면 도착 신호 시각도 갱신. state = pending|retry|ok|dup|fail|parse|ignored */
  static void mms(Context c,long at,String state,int code,int http,boolean saved,String err,Object unused){
    try{
      SharedPreferences.Editor e=p(c).edit();
      if(at>0){ e.putLong("mms_at",at); e.putBoolean("mms_saved",false); e.putInt("mms_code",0); e.putInt("mms_http",0); e.putString("mms_err",""); e.putString("mms_ack",""); }
      e.putLong("mms_upd",System.currentTimeMillis());
      e.putString("mms_state",state==null?"":state);
      if(at<=0||code!=0) e.putInt("mms_code",code);
      if(http!=0) e.putInt("mms_http",http);
      if(saved) e.putBoolean("mms_saved",true);
      if(err!=null&&!err.isEmpty()) e.putString("mms_err",err);
      e.apply();
    }catch(Exception x){}
  }
  /** v5: 달력 등록 단계. steps = skip | try>ok | try>fail | ask | ask>ok | ask>no | ask>fail, reason = CalDecide 이유 코드 (본문·번호 없음) */
  static void cal(Context c,String steps,String reason){
    try{ p(c).edit().putLong("cal_at",System.currentTimeMillis()).putString("cal_steps",steps==null?"":steps).putString("cal_reason",reason==null?"":reason).apply(); }catch(Exception x){}
  }
  static void mmsAck(Context c,String st){ try{ p(c).edit().putString("mms_ack",st==null?"":st).apply(); }catch(Exception x){} }

  static JSONObject json(Context c){
    JSONObject o=new JSONObject();
    try{
      SharedPreferences p=p(c);
      o.put("at",p.getLong("at",0));
      o.put("source",p.getString("source",""));
      o.put("insert",p.getString("insert",""));
      o.put("err",p.getString("err",""));
      o.put("inbox",inboxCount(c));
      o.put("def",Nv.isDefault(c));
      JSONObject pe=new JSONObject();
      pe.put("read",Nv.has(c,android.Manifest.permission.READ_SMS));
      pe.put("recv",Nv.has(c,android.Manifest.permission.RECEIVE_SMS));
      pe.put("send",Nv.has(c,android.Manifest.permission.SEND_SMS));
      o.put("perms",pe);
      o.put("feedAt",p.getLong("feed_at",0));
      o.put("feedMethod",p.getString("feed_method",""));
      o.put("feedOk",p.contains("feed_at")&&p.getBoolean("feed_ok",false));
      o.put("feedSeen",p.contains("feed_at")||p.getLong("feed_at",0)>0);
      o.put("feedRows",p.getInt("feed_rows",0));
      o.put("feedErr",p.getString("feed_err",""));
      String cs=p.getString("cal_steps",""), cr=p.getString("cal_reason","");
      o.put("calAt",p.getLong("cal_at",0)); o.put("calSteps",cs); o.put("calReason",cr);
      o.put("calLabel",CalDecide.stepsLabel(cs,cr.contains(":")?cr.substring(0,cr.indexOf(':')):cr)+(cr.contains(":")?" · "+cr.substring(cr.indexOf(':')+1):"")); o.put("calGood",CalDecide.isGood(cs));
      o.put("calOn",AutoCal.on(c)); o.put("calPerm",AutoCal.perm(c));
      o.put("mmsAt",p.getLong("mms_at",0));
      o.put("mmsUpd",p.getLong("mms_upd",0));
      o.put("mmsState",p.getString("mms_state",""));
      o.put("mmsCode",p.getInt("mms_code",0));
      o.put("mmsCodeLabel",p.getInt("mms_code",0)==0?"":MmsLogic.errLabel(p.getInt("mms_code",0)));
      o.put("mmsHttp",p.getInt("mms_http",0));
      o.put("mmsSaved",p.getBoolean("mms_saved",false));
      o.put("mmsErr",p.getString("mms_err",""));
      o.put("mmsAck",p.getString("mms_ack",""));
      o.put("mmsPending",MmsInbox.pendingCount(c));
      o.put("ver",Nv.VER);
    }catch(Exception e){}
    return o;
  }

  @SuppressWarnings("unused")
  static Uri inboxUri(){ return Telephony.Sms.Inbox.CONTENT_URI; }
}
