package kr.newniverse.sms;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.telephony.SmsMessage;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import org.json.*;
import java.io.*;
import java.util.*;

/** 화면(JS) ↔ 안드로이드 다리. JS에서는 window.NVSms.이름(). 문자 내용은 이 폰 안에서만 오가고 밖으로 보내지 않음 */
class Bridge {
  final Activity a;
  Bridge(Activity a){ this.a=a; }
  static final Uri SMS=Uri.parse("content://sms"), MMS=Uri.parse("content://mms"), PART=Uri.parse("content://mms/part");
  static final String[] PERMS={Manifest.permission.READ_SMS,Manifest.permission.SEND_SMS,Manifest.permission.RECEIVE_SMS,Manifest.permission.READ_CONTACTS};

  static String[] missing(Context c){
    List<String> m=new ArrayList<>(); for(String p:PERMS) if(!Nv.has(c,p)) m.add(p);
    if(Build.VERSION.SDK_INT>=33&&!Nv.has(c,Manifest.permission.POST_NOTIFICATIONS)) m.add(Manifest.permission.POST_NOTIFICATIONS);
    return m.toArray(new String[0]);
  }
  boolean canRead(){ return Nv.has(a,Manifest.permission.READ_SMS); }

  @JavascriptInterface public String status(){
    JSONObject o=new JSONObject();
    try{
      o.put("def",Nv.isDefault(a)); o.put("canRole",Nv.roleIntent(a)!=null);
      JSONObject p=new JSONObject();
      p.put("read",canRead()); p.put("send",Nv.has(a,Manifest.permission.SEND_SMS)); p.put("recv",Nv.has(a,Manifest.permission.RECEIVE_SMS));
      p.put("contacts",Nv.has(a,Manifest.permission.READ_CONTACTS)); p.put("notif",Build.VERSION.SDK_INT<33||Nv.has(a,Manifest.permission.POST_NOTIFICATIONS));
      o.put("perms",p); o.put("sdk",Build.VERSION.SDK_INT); o.put("ver",Nv.VER); o.put("now",System.currentTimeMillis());
    }catch(Exception e){}
    return o.toString();
  }
  // v2 달력: 문자 속 일정 자동으로 달력에(「뉴니버스」 전용 달력)
  @JavascriptInterface public String calStatus(){ JSONObject o=new JSONObject(); try{ o.put("on",AutoCal.on(a)); o.put("perm",AutoCal.perm(a)); o.put("asked",AutoCal.asked(a)); }catch(Exception e){} return o.toString(); }
  @JavascriptInterface public void calSet(boolean on){ a.runOnUiThread(()->{ if(!on){ AutoCal.setOn(a,false); ((MainActivity)a).js("window.NV_cal&&NV_cal()"); return; }
      if(AutoCal.perm(a)){ AutoCal.setOn(a,true); ((MainActivity)a).js("window.NV_cal&&NV_cal()"); return; }
      ((MainActivity)a).reqCal(true); }); }
  @JavascriptInterface public void calSkip(){ a.runOnUiThread(()->{ AutoCal.setOn(a,false); ((MainActivity)a).js("window.NV_cal&&NV_cal()"); }); }
  @JavascriptInterface public void calDelete(){ CalStore.deleteAll(a); }
  @JavascriptInterface public String diag(){ return SmsDiag.json(a).toString(); }

    @JavascriptInterface public void requestPerms(){ a.runOnUiThread(()->{ String[] m=missing(a); if(m.length>0) a.requestPermissions(m,MainActivity.RQ_PERM); else ((MainActivity)a).js("window.NV_resume&&NV_resume('perm')"); }); }
  @JavascriptInterface public void requestRole(){ a.runOnUiThread(()->{ Intent i=Nv.roleIntent(a); if(i==null){ openDefaultApps(); return; } try{ a.startActivityForResult(i,MainActivity.RQ_ROLE); }catch(Exception e){ openDefaultApps(); } }); }
  @JavascriptInterface public void openDefaultApps(){ a.runOnUiThread(()->{ try{ a.startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)); }catch(Exception e){ try{ a.startActivity(new Intent(Settings.ACTION_SETTINGS)); }catch(Exception x){} } }); }
  @JavascriptInterface public void openAppSettings(){ a.runOnUiThread(()->{ try{ a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+a.getPackageName()))); }catch(Exception e){} }); }

  /** 대화 목록. {ok, err, list} — 읽기 실패는 빈 목록이 아니라 err 로 구분 */
  @JavascriptInterface public String getThreads(){
    JSONObject o=new JSONObject(); JSONArray arr=new JSONArray();
    try{
      o.put("ok",true); o.put("err",""); o.put("list",arr);
      if(!canRead()){ o.put("needPerm",true); return o.toString(); }
      LinkedHashMap<Long,JSONObject> map=new LinkedHashMap<>();
      ContentResolver cr=a.getContentResolver();
      try(Cursor q=cr.query(SMS,new String[]{"thread_id","address","body","date","type","read"},"type!=3",null,"date DESC")){
        int n=0;
        while(q!=null&&q.moveToNext()&&n++<40000){
          long th=q.getLong(0); JSONObject j=map.get(th);
          if(j==null){ j=new JSONObject(); j.put("id",th); j.put("addr",nz(q.getString(1))); j.put("snip",nz(q.getString(2))); j.put("date",q.getLong(3));
            int ty=q.getInt(4); j.put("out",ty!=1); j.put("fail",ty==5); j.put("unread",0); j.put("n",0); j.put("lastIn",0L); map.put(th,j); }
          if(q.getInt(4)==1){ if(j.optLong("lastIn")==0) j.put("lastIn",q.getLong(3)); if(q.getInt(5)==0) j.put("unread",j.getInt("unread")+1); }
          j.put("n",j.getInt("n")+1);
        }
      }
      try(Cursor q=cr.query(MMS,new String[]{"_id","thread_id","date","msg_box","read"},null,null,"date DESC")){
        int n=0; Set<Long> done=new HashSet<>();
        while(q!=null&&q.moveToNext()&&n++<5000){
          long id=q.getLong(0),th=q.getLong(1),d=q.getLong(2)*1000L; int box=q.getInt(3); boolean unread=box==1&&q.getInt(4)==0;
          JSONObject j=map.get(th);
          if(j==null){ j=new JSONObject(); j.put("id",th); j.put("addr",mmsAddr(id,box)); j.put("date",0L); j.put("unread",0); j.put("n",0); j.put("lastIn",0L); map.put(th,j); }
          if(unread) j.put("unread",j.getInt("unread")+1);
          if(box==1&&d>j.optLong("lastIn")) j.put("lastIn",d);
          j.put("n",j.getInt("n")+1);
          if(!done.contains(th)&&d>j.optLong("date")){ String[] tx=mmsText(id); j.put("snip",tx[0].isEmpty()?(tx[1].isEmpty()?"(멀티미디어 문자)":"사진"):tx[0]); j.put("date",d); j.put("out",box!=1); j.put("fail",false); j.put("mms",true); }
          done.add(th);
        }
      }
      List<JSONObject> l=new ArrayList<>(map.values());
      l.sort((x,y)->Long.compare(y.optLong("date"),x.optLong("date")));
      for(JSONObject j:l) arr.put(j);
      o.put("list",arr);
    }catch(Exception e){
      try{ o.put("ok",false); o.put("err",InboxLogic.errClass(e)); o.put("list",new JSONArray()); }catch(Exception x){}
    }
    return o.toString();
  }
  String mmsAddr(long id,int box){
    // 받은 MMS: 보낸 사람(137), 보낸 MMS: 받는 사람(151)
    try(Cursor q=a.getContentResolver().query(Uri.parse("content://mms/"+id+"/addr"),new String[]{"address","type"},null,null,null)){
      String any="";
      while(q!=null&&q.moveToNext()){ String ad=nz(q.getString(0)); int ty=q.getInt(1); if(ad.equals("insert-address-token")) continue;
        if((box==1&&ty==137)||(box!=1&&ty==151)) return ad; if(any.isEmpty()) any=ad; }
      return any;
    }catch(Exception e){ return ""; }
  }
  /** [글, 첫 사진 part id] */
  String[] mmsText(long id){
    StringBuilder sb=new StringBuilder(); String img="";
    try(Cursor q=a.getContentResolver().query(PART,new String[]{"_id","ct","text"},"mid=?",new String[]{String.valueOf(id)},"seq ASC")){
      while(q!=null&&q.moveToNext()){ String ct=nz(q.getString(1));
        if(ct.equals("text/plain")){ String t=q.getString(2); if(t!=null){ if(sb.length()>0) sb.append('\n'); sb.append(t);} }
        else if(ct.startsWith("image/")&&img.isEmpty()) img=String.valueOf(q.getLong(0)); }
    }catch(Exception e){}
    return new String[]{sb.toString(),img};
  }

  /** 한 대화의 문자들 (오래된 → 최근) */
  @JavascriptInterface public String getMessages(long thread,int limit){
    JSONArray arr=new JSONArray(); if(!canRead()||thread<=0) return arr.toString();
    int lim=Math.max(20,Math.min(limit,2000)); List<JSONObject> l=new ArrayList<>();
    ContentResolver cr=a.getContentResolver();
    try(Cursor q=cr.query(SMS,new String[]{"_id","body","date","type"},"thread_id=? AND type!=3",new String[]{String.valueOf(thread)},"date DESC")){
      while(q!=null&&q.moveToNext()&&l.size()<lim){
        JSONObject j=new JSONObject(); int ty=q.getInt(3);
        j.put("id","s"+q.getLong(0)); j.put("body",nz(q.getString(1))); j.put("date",q.getLong(2)); j.put("dir",ty==1?"in":"out");
        j.put("st",ty==5?"fail":(ty==4||ty==6)?"sending":"ok"); l.add(j);
      }
    }catch(Exception e){}
    try(Cursor q=cr.query(MMS,new String[]{"_id","date","msg_box"},"thread_id=?",new String[]{String.valueOf(thread)},"date DESC")){
      int n=0;
      while(q!=null&&q.moveToNext()&&n++<Math.min(lim,300)){
        long id=q.getLong(0); JSONObject j=new JSONObject(); int box=q.getInt(2);
        j.put("id","m"+id); j.put("date",q.getLong(1)*1000L); j.put("dir",box==1?"in":"out"); j.put("st",box==5?"fail":box==4?"sending":"ok"); j.put("mms",true);
        StringBuilder sb=new StringBuilder(); JSONArray imgs=new JSONArray();
        try(Cursor p=cr.query(PART,new String[]{"_id","ct","text"},"mid=?",new String[]{String.valueOf(id)},"seq ASC")){
          while(p!=null&&p.moveToNext()){ String ct=nz(p.getString(1));
            if(ct.equals("text/plain")){ String t=p.getString(2); if(t!=null){ if(sb.length()>0) sb.append('\n'); sb.append(t);} }
            else if(ct.startsWith("image/")) imgs.put(String.valueOf(p.getLong(0))); }
        }catch(Exception e){}
        j.put("body",sb.toString()); j.put("imgs",imgs); l.add(j);
      }
    }catch(Exception e){}
    l.sort((x,y)->Long.compare(x.optLong("date"),y.optLong("date")));
    int from=Math.max(0,l.size()-lim);
    for(int i=from;i<l.size();i++) arr.put(l.get(i));
    return arr.toString();
  }

  /** MMS 사진 한 장 → 작게 줄인 data URL (화면에서만 씀) */
  @JavascriptInterface public String getPart(String partId){
    if(!canRead()||partId==null||!partId.matches("\\d+")) return "";
    Uri u=Uri.parse("content://mms/part/"+partId);
    try{
      BitmapFactory.Options o=new BitmapFactory.Options(); o.inJustDecodeBounds=true;
      try(InputStream in=a.getContentResolver().openInputStream(u)){ BitmapFactory.decodeStream(in,null,o); }
      int s=1; while(Math.max(o.outWidth,o.outHeight)/s>1280) s*=2;
      BitmapFactory.Options o2=new BitmapFactory.Options(); o2.inSampleSize=s;
      Bitmap b; try(InputStream in=a.getContentResolver().openInputStream(u)){ b=BitmapFactory.decodeStream(in,null,o2); }
      if(b==null) return "";
      ByteArrayOutputStream bo=new ByteArrayOutputStream(); b.compress(Bitmap.CompressFormat.JPEG,82,bo); b.recycle();
      return "data:image/jpeg;base64,"+Base64.encodeToString(bo.toByteArray(),Base64.NO_WRAP);
    }catch(Exception e){ return ""; }
  }

  /** 문자 내용 찾기 (최근 60개) */
  @JavascriptInterface public String search(String qs){
    JSONArray arr=new JSONArray(); if(!canRead()||qs==null||qs.trim().length()<2) return arr.toString();
    String like="%"+qs.trim().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
    try(Cursor q=a.getContentResolver().query(SMS,new String[]{"thread_id","address","body","date"},"body LIKE ? ESCAPE '\\' AND type!=3",new String[]{like},"date DESC")){
      while(q!=null&&q.moveToNext()&&arr.length()<60){ JSONObject j=new JSONObject(); j.put("thread",q.getLong(0)); j.put("addr",nz(q.getString(1))); j.put("body",nz(q.getString(2))); j.put("date",q.getLong(3)); arr.put(j); }
    }catch(Exception e){}
    return arr.toString();
  }

  /** 주소록 (이름·번호) */
  @JavascriptInterface public String getContacts(){
    JSONArray arr=new JSONArray(); if(!Nv.has(a,Manifest.permission.READ_CONTACTS)) return arr.toString();
    String[] pr={ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER};
    Set<String> seen=new HashSet<>();
    try(Cursor q=a.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,pr,null,null,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" ASC")){
      while(q!=null&&q.moveToNext()&&arr.length()<8000){
        String n=q.getString(0),p=q.getString(1); if(n==null||p==null) continue;
        String k=n+"|"+p.replaceAll("[^0-9+]",""); if(!seen.add(k)) continue;
        JSONObject j=new JSONObject(); j.put("name",n); j.put("num",p); arr.put(j);
      }
    }catch(Exception e){}
    return arr.toString();
  }

  @JavascriptInterface public String send(String addr,String text){ String r=Nv.send(a,addr,text); if("perm".equals(r)) requestPerms(); return r; }
  /** "나눠지는 개수,쓴 글자,남은 글자" */
  @JavascriptInterface public String segments(String text){
    try{ int[] r=SmsMessage.calculateLength(text==null?"":text,false); return r[0]+","+r[1]+","+r[2]; }catch(Exception e){ return "1,0,0"; }
  }
  /** 읽음 처리 (폰 저장소에 쓰는 건 기본 문자 앱일 때만 가능) + 그 사람 알림 지우기 */
  @JavascriptInterface public void markRead(long thread,String addr){
    NotificationManager nm=a.getSystemService(NotificationManager.class); if(nm!=null&&addr!=null) nm.cancel(Nv.notifId(addr));
    if(thread<=0||!Nv.isDefault(a)) return;
    try{ ContentValues v=new ContentValues(); v.put("read",1); v.put("seen",1);
      a.getContentResolver().update(Uri.parse("content://sms/inbox"),v,"thread_id=? AND read=0",new String[]{String.valueOf(thread)});
      ContentValues m=new ContentValues(); m.put("read",1); m.put("seen",1);
      a.getContentResolver().update(MMS,m,"thread_id=? AND read=0",new String[]{String.valueOf(thread)});
    }catch(Exception e){}
  }
  @JavascriptInterface public void viewing(String addr){ MainActivity.viewing=Nv.digits(addr==null?"":addr); }
  @JavascriptInterface public String takeOpen(){
    if(!(a instanceof MainActivity)) return "";
    MainActivity m=(MainActivity)a; if(m.pendingAddr==null) return "";
    JSONObject o=new JSONObject(); try{ o.put("addr",m.pendingAddr); o.put("body",m.pendingBody==null?"":m.pendingBody); }catch(Exception e){}
    m.pendingAddr=null; m.pendingBody=null; return o.toString();
  }
  @JavascriptInterface public void dial(String num){ a.runOnUiThread(()->{ try{ a.startActivity(new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",Nv.digits(num),null))); }catch(Exception e){} }); }
  @JavascriptInterface public void copy(String text){ a.runOnUiThread(()->{ try{ ClipboardManager cm=a.getSystemService(ClipboardManager.class); cm.setPrimaryClip(ClipData.newPlainText("문자",text)); }catch(Exception e){} }); }
  @JavascriptInterface public void vibrate(int ms){ try{ Vibrator v=a.getSystemService(Vibrator.class); if(v!=null) v.vibrate(VibrationEffect.createOneShot(Math.max(5,Math.min(ms,60)),VibrationEffect.DEFAULT_AMPLITUDE)); }catch(Exception e){} }
  static String nz(String s){ return s==null?"":s; }
}
