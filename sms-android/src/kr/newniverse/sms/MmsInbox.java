package kr.newniverse.sms;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsManager;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * v4: 기본 문자 앱으로서 사진·장문(MMS/LMS) 받기.
 *  1) WAP_PUSH_DELIVER 의 M-Notification.ind 읽기 → 받을 주소·트랜잭션 ID 를 앱 안에 기억(다시 받기용)
 *  2) SmsManager.downloadMultimediaMessage → 우리 파일 통로(MmsFileProvider)에 M-Retrieve.conf 가 써짐
 *  3) 결과가 오면 PDU 읽기 → content://mms (pdu·part·addr, msg_box=받은 편지함, thread_id) 에 저장
 *  4) M-NotifyResp.ind(받았어요) 보내기 → 알림 · 런처 신호 · 달력
 *  실패: 일시적 오류면 한 번 더 자동으로, 그래도 안 되면 ‘장문을 받지 못했어요’ 알림 + 다시 받기.
 *  진단에는 시각·결과·오류 번호만 남기고 본문·번호는 남기지 않음.
 */
final class MmsInbox {
  static final String PREF="nv_mms_pending";
  static final String ACT_DL="kr.newniverse.sms.MMS_DOWNLOADED", ACT_RETRY="kr.newniverse.sms.MMS_RETRY", ACT_NR="kr.newniverse.sms.MMS_NOTIFYRESP_SENT";
  static final Uri MMS=Uri.parse("content://mms"), MMS_INBOX=Uri.parse("content://mms/inbox");
  static final String[] MMS_PKGS={"com.android.phone","com.android.mms.service"};
  static final int FAIL_NOTIF=2999;

  static SharedPreferences pend(Context c){ return c.getSharedPreferences(PREF,Context.MODE_PRIVATE); }
  static String keyOf(String tr,String loc){ String s=(tr==null||tr.isEmpty())?loc:tr; return "k"+Integer.toHexString((s==null?"":s).hashCode()); }

  /** 1) 도착 신호 */
  static void onPush(Context c,Intent i){
    byte[] data=i.getByteArrayExtra("data");
    long now=System.currentTimeMillis();
    MmsPdu.Pdu n=MmsPdu.parse(data);
    if(n==null){ SmsDiag.mms(c,now,"parse",0,0,false,"",null); return; }
    if(n.type!=MmsPdu.NOTIFICATION_IND||n.contentLocation.isEmpty()){ SmsDiag.mms(c,now,"ignored",0,0,false,"",null); return; }
    long sub=SmsInbox.subIdFrom(i);
    String key=keyOf(n.transactionId,n.contentLocation);
    try{
      JSONObject o=new JSONObject(); o.put("loc",n.contentLocation); o.put("tr",n.transactionId); o.put("sub",sub); o.put("at",now); o.put("tries",0); o.put("from",n.fromStr()); o.put("size",n.size);
      pend(c).edit().putString(key,o.toString()).apply();
    }catch(Exception e){}
    SmsDiag.mms(c,now,"pending",0,0,false,"",null);
    download(c,key);
  }

  static SmsManager mgr(Context c,long sub){
    SmsManager m=Nv.smsManager(c);
    if(sub>=0){ try{ m=m.createForSubscriptionId((int)sub); }catch(Throwable e){} }
    return m;
  }

  static int pendingFlags(){ return PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0); }

  /** 2) 내려받기 시작(처음·다시) */
  static void download(Context c,String key){
    JSONObject o=load(c,key); if(o==null) return;
    int tries=o.optInt("tries",0)+1;
    try{ o.put("tries",tries); pend(c).edit().putString(key,o.toString()).apply(); }catch(Exception e){}
    String name=key+"-"+tries+".pdu";
    File f=MmsFileProvider.file(c,name);
    try{
      if(f!=null&&f.exists()) f.delete();
      Uri u=MmsFileProvider.uri(name);
      for(String p:MMS_PKGS){ try{ c.grantUriPermission(p,u,Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION); }catch(Exception e){} }
      Intent r=new Intent(c,MmsResultReceiver.class).setAction(ACT_DL+"."+key+"."+tries).putExtra("key",key).putExtra("file",name);
      PendingIntent pi=PendingIntent.getBroadcast(c,(key+tries).hashCode(),r,pendingFlags());
      mgr(c,o.optLong("sub",-1)).downloadMultimediaMessage(c,o.optString("loc"),u,null,pi);
      SmsDiag.mms(c,0,tries>1?"retry":"pending",0,0,false,"",null);
    }catch(Exception e){
      failed(c,key,103,0,InboxLogic.errClass(e));
    }
  }
  static JSONObject load(Context c,String key){ try{ String s=pend(c).getString(key,null); return s==null?null:new JSONObject(s); }catch(Exception e){ return null; } }

  /** 3) 내려받기 결과 */
  static void onDownloaded(Context c,Intent i,int resultCode){
    String key=i.getStringExtra("key"), name=i.getStringExtra("file");
    int http=i.getIntExtra("android.telephony.extra.MMS_HTTP_STATUS",0);
    File f=MmsFileProvider.file(c,name);
    JSONObject o=load(c,key);
    if(o==null){ if(f!=null) f.delete(); return; }
    if(resultCode!=Activity.RESULT_OK){ if(f!=null) f.delete(); failed(c,key,resultCode,http,""); return; }
    byte[] data=null;
    try{ if(f!=null&&f.exists()&&f.length()>0&&f.length()<8*1024*1024){ data=new byte[(int)f.length()]; try(FileInputStream in=new FileInputStream(f)){ int off=0; while(off<data.length){ int r=in.read(data,off,data.length-off); if(r<0) break; off+=r; } } } }catch(Exception e){ data=null; }
    if(f!=null) f.delete();
    if(data==null){ failed(c,key,100,http,""); return; }
    MmsPdu.Pdu d=MmsPdu.parse(data);
    if(d==null||d.type!=MmsPdu.RETRIEVE_CONF){ failed(c,key,101,http,""); return; }
    if(d.retrieveStatus>=0xC0&&d.parts.isEmpty()){ failed(c,key,4,http,"retr:"+Integer.toHexString(d.retrieveStatus)); return; }
    long now=System.currentTimeMillis();
    String from=d.fromStr(); if(from.isEmpty()) from=o.optString("from","");
    String[] res={""};
    Uri row=persist(c,d,o.optLong("at",now),o.optLong("sub",-1),o.optString("loc"),from,res);
    if(row==null){ failed(c,key,102,http,res[0]); return; }
    boolean dup="dup".equals(res[0]);
    pend(c).edit().remove(key).apply();
    SmsDiag.mms(c,0,dup?"dup":"ok",-1,http,true,"",null);
    cancelFail(c);
    sendNotifyResp(c,o.optString("tr"),o.optLong("sub",-1));
    if(dup){ MainActivity.refresh(); return; }
    String text=MmsLogic.feedText(d.subjectStr(),d.text());
    SmsInbox.notify(c,from,MmsLogic.preview(d.subjectStr(),d.text(),d.imageCount()),true);
    FeedProvider.ping(c);
    if(!text.isEmpty()) AutoCal.onSms(c,from,text,now);
    MainActivity.refresh();
  }

  static void failed(Context c,String key,int code,int http,String err){
    JSONObject o=load(c,key);
    int tries=o==null?99:o.optInt("tries",1);
    if(o!=null&&MmsLogic.autoRetry(code,tries)){ SmsDiag.mms(c,0,"retry",code,http,false,err,null); download(c,key); return; }
    SmsDiag.mms(c,0,"fail",code,http,false,err,null);
    notifyFail(c,code);
    MainActivity.refresh();
  }

  static void notifyFail(Context c,int code){
    NotificationManager nm=c.getSystemService(NotificationManager.class); if(nm==null) return;
    Nv.channels(c);
    Intent open=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    PendingIntent pi=PendingIntent.getActivity(c,FAIL_NOTIF,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    Intent rt=new Intent(c,MmsResultReceiver.class).setAction(ACT_RETRY);
    PendingIntent rpi=PendingIntent.getBroadcast(c,FAIL_NOTIF,rt,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    String text=MmsLogic.failText(code);
    Notification.Builder b=new Notification.Builder(c,Nv.CH_MSG).setSmallIcon(R.drawable.ic_msg).setColor(0xFF8F6BFF)
      .setContentTitle(MmsLogic.failTitle()).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
      .setCategory(Notification.CATEGORY_ERROR).setContentIntent(pi).setAutoCancel(true)
      .addAction(new Notification.Action.Builder(null,"다시 받기",rpi).build());
    try{ nm.notify(FAIL_NOTIF,b.build()); }catch(Exception e){}
  }
  static void cancelFail(Context c){ try{ NotificationManager nm=c.getSystemService(NotificationManager.class); if(nm!=null) nm.cancel(FAIL_NOTIF); }catch(Exception e){} }

  /** 아직 못 받은 장문 모두 다시 받기 (알림 단추·설정 화면) */
  static int retryAll(Context c){
    cancelFail(c);
    Map<String,?> all=pend(c).getAll(); int n=0;
    for(String k:all.keySet()){
      JSONObject o=load(c,k); if(o==null) continue;
      try{ o.put("tries",0); pend(c).edit().putString(k,o.toString()).apply(); }catch(Exception e){}
      download(c,k); n++;
    }
    return n;
  }
  static int pendingCount(Context c){ try{ return pend(c).getAll().size(); }catch(Exception e){ return 0; } }

  /** 같은 장문이 이미 저장돼 있으면(시스템이 대신 저장했거나 두 번 받음) 그 행 */
  static Uri existing(ContentResolver cr,MmsPdu.Pdu d){
    String sel; String[] args;
    if(!d.messageId.isEmpty()){ sel="m_id=?"; args=new String[]{d.messageId}; }
    else if(!d.transactionId.isEmpty()){ sel="tr_id=?"; args=new String[]{d.transactionId}; }
    else return null;
    try(Cursor q=cr.query(MMS,new String[]{"_id"},sel+" AND msg_box=1",args,null)){
      if(q!=null&&q.moveToFirst()) return Uri.withAppendedPath(MMS,String.valueOf(q.getLong(0)));
    }catch(Exception e){}
    return null;
  }

  /** 3') 저장. res[0] = ""(새로) | "dup" | 예외 종류 */
  static Uri persist(Context c,MmsPdu.Pdu d,long receivedMs,long sub,String loc,String from,String[] res){
    ContentResolver cr=c.getContentResolver();
    Uri ex=existing(cr,d); if(ex!=null){ res[0]="dup"; return ex; }
    long tid=-1;
    try{ Set<String> rc=new HashSet<>(); rc.add(from.isEmpty()?"Unknown":from); tid=Telephony.Threads.getOrCreateThreadId(c,rc); }catch(Throwable e){}
    Uri row;
    try{
      row=cr.insert(MMS_INBOX,SmsInbox.cv(MmsLogic.pduValues(d,receivedMs,tid,sub,MmsLogic.textOnly(d),loc)));
    }catch(Exception e){ res[0]=InboxLogic.errClass(e); return null; }
    if(row==null){ res[0]="null"; return null; }
    String id=row.getLastPathSegment();
    int okParts=0, seq=0;
    for(MmsPdu.Part p:d.parts){
      try{
        ContentValues v=new ContentValues();
        v.put("seq",p.isSmil()?-1:seq++);
        v.put("ct",p.ct);
        if(!p.name.isEmpty()) v.put("name",p.name);
        if(!p.filename.isEmpty()) v.put("fn",p.filename);
        if(!p.cid.isEmpty()) v.put("cid","<"+p.cid+">");
        if(!p.cl.isEmpty()) v.put("cl",p.cl);
        Uri pu;
        if(p.isText()||p.isSmil()||"text/html".equalsIgnoreCase(p.ct)){
          v.put("chset",MmsPdu.CS_UTF8); v.put("text",p.text());
          pu=cr.insert(Uri.parse("content://mms/"+id+"/part"),v);
        } else {
          if(p.charset>0) v.put("chset",p.charset);
          pu=cr.insert(Uri.parse("content://mms/"+id+"/part"),v);
          if(pu!=null) try(OutputStream os=cr.openOutputStream(pu)){ if(os!=null) os.write(p.data); }
        }
        if(pu!=null) okParts++;
      }catch(Exception e){ if(res[0].isEmpty()) res[0]=InboxLogic.errClass(e); }
    }
    if(okParts==0&&!d.parts.isEmpty()){ try{ cr.delete(row,null,null); }catch(Exception e){} if(res[0].isEmpty()) res[0]="parts"; return null; }
    addr(cr,id,from,MmsLogic.TYPE_FROM,MmsPdu.CS_UTF8);
    for(MmsPdu.Enc t:d.to) addr(cr,id,MmsPdu.cleanAddr(t.str()),MmsLogic.TYPE_TO,MmsPdu.CS_UTF8);
    for(MmsPdu.Enc t:d.cc) addr(cr,id,MmsPdu.cleanAddr(t.str()),MmsLogic.TYPE_CC,MmsPdu.CS_UTF8);
    res[0]="";
    return row;
  }
  static void addr(ContentResolver cr,String id,String a,int type,int cs){
    if(a==null||a.isEmpty()) return;
    try{ ContentValues v=new ContentValues(); v.put("address",a); v.put("type",type); v.put("charset",cs); v.put("msg_id",id);
      cr.insert(Uri.parse("content://mms/"+id+"/addr"),v); }catch(Exception e){}
  }

  /** 4) 통신사에 ‘받았어요’(M-NotifyResp.ind). 실패해도 문자는 이미 저장됨 */
  static void sendNotifyResp(Context c,String tr,long sub){
    if(tr==null||tr.isEmpty()) return;
    String name="nr"+Integer.toHexString(tr.hashCode())+".pdu";
    try{
      File f=MmsFileProvider.file(c,name); if(f==null) return;
      try(FileOutputStream os=new FileOutputStream(f)){ os.write(MmsPdu.notifyResp(tr,MmsPdu.STATUS_RETRIEVED)); }
      Uri u=MmsFileProvider.uri(name);
      for(String p:MMS_PKGS){ try{ c.grantUriPermission(p,u,Intent.FLAG_GRANT_READ_URI_PERMISSION); }catch(Exception e){} }
      Intent r=new Intent(c,MmsResultReceiver.class).setAction(ACT_NR+"."+name).putExtra("file",name);
      PendingIntent pi=PendingIntent.getBroadcast(c,name.hashCode(),r,pendingFlags());
      mgr(c,sub).sendMultimediaMessage(c,u,null,null,pi);
      SmsDiag.mmsAck(c,"sending");
    }catch(Exception e){ SmsDiag.mmsAck(c,"err:"+InboxLogic.errClass(e)); }
  }

  // ---------- 읽기 도우미 (목록·대화·런처) ----------
  static String[] text(ContentResolver cr,long id){
    StringBuilder sb=new StringBuilder(); String img=""; int imgs=0;
    try(Cursor q=cr.query(Uri.parse("content://mms/part"),new String[]{"_id","ct","text"},"mid=?",new String[]{String.valueOf(id)},"seq ASC")){
      while(q!=null&&q.moveToNext()){ String ct=q.getString(1)==null?"":q.getString(1).toLowerCase();
        if(ct.equals("text/plain")){ String t=q.getString(2);
          if(t==null||t.isEmpty()) t=partFile(cr,q.getLong(0)); // v7: 글이 text 칸이 아니라 파일(_data)에 있는 문자(삼성이 저장한 장문 등)
          if(t!=null&&!t.isEmpty()){ if(sb.length()>0) sb.append('\n'); sb.append(t);} }
        else if(ct.startsWith("image/")){ imgs++; if(img.isEmpty()) img=String.valueOf(q.getLong(0)); } }
    }catch(Exception e){}
    return new String[]{sb.toString(),img,String.valueOf(imgs)};
  }
  /** 글 조각이 파일로 저장된 경우: content://mms/part/ID 를 열어 읽음(64KB까지, UTF-8; 깨지면 EUC-KR) */
  static String partFile(ContentResolver cr,long partId){
    try(java.io.InputStream in=cr.openInputStream(Uri.parse("content://mms/part/"+partId))){
      if(in==null) return "";
      java.io.ByteArrayOutputStream bo=new java.io.ByteArrayOutputStream(); byte[] buf=new byte[4096]; int n; while((n=in.read(buf))>0&&bo.size()<65536) bo.write(buf,0,n);
      byte[] b=bo.toByteArray(); String u=new String(b,java.nio.charset.StandardCharsets.UTF_8);
      if(u.indexOf('\uFFFD')>=0){ try{ return new String(b,java.nio.charset.Charset.forName("x-windows-949")); }catch(Exception e){} }
      return u;
    }catch(Exception e){ return ""; }
  }
  static String from(ContentResolver cr,long id,int box){
    try(Cursor q=cr.query(Uri.parse("content://mms/"+id+"/addr"),new String[]{"address","type"},null,null,null)){
      String any="";
      while(q!=null&&q.moveToNext()){ String ad=q.getString(0)==null?"":q.getString(0); int ty=q.getInt(1); if(ad.equals("insert-address-token")) continue;
        if((box==1&&ty==MmsLogic.TYPE_FROM)||(box!=1&&ty==MmsLogic.TYPE_TO)) return ad; if(any.isEmpty()) any=ad; }
      return any;
    }catch(Exception e){ return ""; }
  }
}
