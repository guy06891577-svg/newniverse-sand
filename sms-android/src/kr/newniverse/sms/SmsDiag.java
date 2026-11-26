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
      o.put("ver",Nv.VER);
    }catch(Exception e){}
    return o;
  }

  @SuppressWarnings("unused")
  static Uri inboxUri(){ return Telephony.Sms.Inbox.CONTENT_URI; }
}
