package kr.newniverse.sms;

import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.provider.Telephony;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * v3: 뉴니버스 런처에 「문자 속 할 일 정리」용 문자를 넘기는 통로.
 *  - 같은 서명 앱(런처)만 쓸 수 있음: signature 수준 권한 kr.newniverse.permission.SMS_FEED + 호출자 서명 재확인.
 *  - 이 앱이 기본 문자 앱이고 문자 읽기 권한이 있을 때만 응답. 인터넷 권한이 없어 밖으로 나가지 않음.
 *  - 택배·병원/예약·카드 결제·인증번호와 관련된 말이 있는 문자만 골라서 넘김(FeedFilter). 최근 N일(최대 30일).
 *  call("status") → {def, perm, ver}   call("recent", "7") → {json:[{id,addr,at,x}]}
 *  조회가 올 때마다 설정 화면 진단(시각·메서드·행 수·오류 종류)만 남김. 본문은 남기지 않음.
 */
public class FeedProvider extends ContentProvider {
  static final String PERM="kr.newniverse.permission.SMS_FEED";
  static final String ACTION_NEW="kr.newniverse.sms.action.NEW";
  @Override public boolean onCreate(){ return true; }
  boolean callerOk(Context c){
    try{ return c.getPackageManager().checkSignatures(Binder.getCallingUid(),Process.myUid())==PackageManager.SIGNATURE_MATCH; }catch(Exception e){ return false; } }
  @Override public Bundle call(String method,String arg,Bundle extras){
    Context c=getContext(); Bundle b=new Bundle();
    if(c==null||!callerOk(c)){ b.putBoolean("ok",false); b.putString("json","[]"); try{ if(c!=null) SmsDiag.feed(c,method==null?"":method,false,0,"caller"); }catch(Exception e){} return b; }
    boolean def=Nv.isDefault(c), perm=c.checkSelfPermission(android.Manifest.permission.READ_SMS)==PackageManager.PERMISSION_GRANTED;
    b.putBoolean("ok",true); b.putBoolean("def",def); b.putBoolean("perm",perm); b.putInt("ver",3);
    if("cal_status".equals(method)){ b.putBoolean("calOn",AutoCal.on(c)); b.putBoolean("calPerm",AutoCal.perm(c)); try{ SmsDiag.feed(c,"cal_status",true,0,""); }catch(Exception e){} return b; }
    if("cal_add".equals(method)){ String r="none";
      try{ JSONObject o=new JSONObject(arg==null?"{}":arg); r=AutoCal.manual(c,o.optString("addr"),o.optString("x"),o.optLong("at",System.currentTimeMillis())); }catch(Exception e){}
      b.putString("r",r); try{ SmsDiag.feed(c,"cal_add",true,0,""); }catch(Exception e){} return b; }
    if("recent".equals(method)){
      JSONArray a=new JSONArray(); String err="";
      if(def&&perm){
        long since=System.currentTimeMillis()-FeedFilter.days(arg)*24L*3600*1000;
        String[] proj=new String[]{Telephony.Sms._ID,Telephony.Sms.ADDRESS,Telephony.Sms.DATE,Telephony.Sms.BODY};
        Cursor cur=null;
        try{
          try{
            Bundle q=new Bundle(); q.putString(ContentResolver.QUERY_ARG_SQL_SELECTION,Telephony.Sms.DATE+">?"); q.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,new String[]{String.valueOf(since)});
            q.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER,Telephony.Sms.DATE+" DESC"); q.putInt(ContentResolver.QUERY_ARG_LIMIT,FeedFilter.MAX_ROWS);
            cur=c.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,proj,q,null);
          }catch(Exception qe){
            err=InboxLogic.errClass(qe);
            cur=c.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,proj,Telephony.Sms.DATE+">?",new String[]{String.valueOf(since)},Telephony.Sms.DATE+" DESC");
          }
          int n=0;
          if(cur!=null) while(cur.moveToNext()&&n++<FeedFilter.MAX_ROWS){ String body=cur.getString(3); if(!FeedFilter.relevant(body)) continue;
            JSONObject o=new JSONObject(); o.put("id",cur.getLong(0)); o.put("addr",cur.getString(1)==null?"":cur.getString(1)); o.put("at",cur.getLong(2)); o.put("x",FeedFilter.clip(body)); try{ o.put("cal",AutoCal.has(c,cur.getString(1),body,cur.getLong(2))); }catch(Exception e){} a.put(o); }
        }catch(Exception e){ err=InboxLogic.errClass(e); } finally{ if(cur!=null) try{ cur.close(); }catch(Exception e){} }
      }
      b.putString("json",a.toString());
      try{ SmsDiag.feed(c,"recent",err.isEmpty(),a.length(),err); }catch(Exception e){}
      return b;
    }
    try{ SmsDiag.feed(c,method==null?"status":method,true,0,""); }catch(Exception e){}
    return b;
  }
  /** 새 문자가 왔다는 신호만(내용 없음). 같은 서명 런처만 받음 */
  static void ping(Context c){ try{ Intent i=new Intent(ACTION_NEW).setPackage("kr.newniverse.launcher"); c.sendBroadcast(i,PERM); }catch(Exception e){} }
  @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){ return null; }
  @Override public String getType(Uri u){ return null; }
  @Override public Uri insert(Uri u,ContentValues v){ return null; }
  @Override public int delete(Uri u,String s,String[] a){ return 0; }
  @Override public int update(Uri u,ContentValues v,String s,String[] a){ return 0; }
}
