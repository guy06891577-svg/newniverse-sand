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
  /** v6(시험): 마지막 문자로 달력 단계를 다시 돌려 보기 → {ok, steps, label} */
  @JavascriptInterface public String calReread(){ try{ return AutoCal.reread(a.getApplicationContext()); }catch(Exception e){ return "{\"ok\":false,\"label\":\"다시 읽지 못했어요 ("+InboxLogic.errClass(e)+")\"}"; } }
  /** v4: 못 받은 장문 다시 받기 → 다시 시도한 개수 */
  @JavascriptInterface public int mmsRetry(){ try{ return MmsInbox.retryAll(a.getApplicationContext()); }catch(Exception e){ return 0; } }

    @JavascriptInterface public void requestPerms(){ a.runOnUiThread(()->{ String[] m=missing(a); if(m.length>0) a.requestPermissions(m,MainActivity.RQ_PERM); else ((MainActivity)a).js("window.NV_resume&&NV_resume('perm')"); }); }
  @JavascriptInterface public void requestRole(){ a.runOnUiThread(()->{ Intent i=Nv.roleIntent(a); if(i==null){ openDefaultApps(); return; } try{ a.startActivityForResult(i,MainActivity.RQ_ROLE); }catch(Exception e){ openDefaultApps(); } }); }
  @JavascriptInterface public void openDefaultApps(){ a.runOnUiThread(()->{ try{ a.startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)); }catch(Exception e){ try{ a.startActivity(new Intent(Settings.ACTION_SETTINGS)); }catch(Exception x){} } }); }
  @JavascriptInterface public void openAppSettings(){ a.runOnUiThread(()->{ try{ a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+a.getPackageName()))); }catch(Exception e){} }); }

  /** 대화 목록. {ok, err, list} — 읽기 실패는 빈 목록이 아니라 err 로 구분. v4: SMS+MMS 를 MsgMerge 로 합침 */
  @JavascriptInterface public String getThreads(){
    JSONObject o=new JSONObject(); JSONArray arr=new JSONArray();
    try{
      o.put("ok",true); o.put("err",""); o.put("list",arr);
      if(!canRead()){ o.put("needPerm",true); return o.toString(); }
      ContentResolver cr=a.getContentResolver();
      List<MsgMerge.Item> sms=new ArrayList<>(), mms=new ArrayList<>();
      Map<Long,String> addr=new HashMap<>();
      try(Cursor q=cr.query(SMS,new String[]{"_id","thread_id","address","body","date","type","read"},"type!=3",null,"date DESC")){
        int n=0;
        while(q!=null&&q.moveToNext()&&n++<40000){
          long th=q.getLong(1); int ty=q.getInt(5);
          MsgMerge.Item it=new MsgMerge.Item(false,q.getLong(0),th,q.getLong(4),ty==1,q.getInt(6)==0).with(q.getString(2),n<=4000?q.getString(3):"");
          it.box=ty; sms.add(it); if(!addr.containsKey(th)&&q.getString(2)!=null) addr.put(th,q.getString(2));
        }
      }
      try(Cursor q=cr.query(MMS,new String[]{"_id","thread_id","date","msg_box","read"},null,null,"date DESC")){
        int n=0;
        while(q!=null&&q.moveToNext()&&n++<5000){
          int box=q.getInt(3);
          MsgMerge.Item it=new MsgMerge.Item(true,q.getLong(0),q.getLong(1),MmsLogic.msFromMmsDate(q.getLong(2)),box==1,q.getInt(4)==0);
          it.box=box; mms.add(it);
        }
      }
      for(MsgMerge.Conv t:MsgMerge.threads(sms,mms)){
        MsgMerge.Item L=t.last; JSONObject j=new JSONObject();
        String ad=addr.get(t.id);
        if(L.mms){
          String[] tx=MmsInbox.text(cr,L.id); String sub=mmsSubject(cr,L.id);
          String txt=MmsLogic.feedText(sub,tx[0]);
          j.put("snip",txt.isEmpty()?(tx[1].isEmpty()?"(멀티미디어 문자)":"사진"):txt); j.put("mms",true);
          j.put("out",L.box!=1); j.put("fail",L.box==5);
          if(ad==null) ad=MmsInbox.from(cr,L.id,L.box);
        } else { j.put("snip",L.text.isEmpty()?snipOf(cr,L.id):L.text); j.put("out",L.box!=1); j.put("fail",L.box==5); if(ad==null) ad=L.addr; }
        j.put("id",t.id); j.put("addr",nz(ad)); j.put("date",L.date); j.put("unread",t.unread); j.put("n",t.n); j.put("lastIn",t.lastIn);
        arr.put(j);
      }
      o.put("list",arr);
    }catch(Exception e){
      try{ o.put("ok",false); o.put("err",InboxLogic.errClass(e)); o.put("list",new JSONArray()); }catch(Exception x){}
    }
    return o.toString();
  }
  String snipOf(ContentResolver cr,long id){
    try(Cursor q=cr.query(SMS,new String[]{"body"},"_id=?",new String[]{String.valueOf(id)},null)){ if(q!=null&&q.moveToFirst()) return nz(q.getString(0)); }catch(Exception e){}
    return "";
  }
  /** MMS 제목 (AOSP 식 ISO 저장 + sub_cs 풀기) */
  static String mmsSubject(ContentResolver cr,long id){
    try(Cursor q=cr.query(Uri.parse("content://mms/"+id),new String[]{"sub","sub_cs"},null,null,null)){
      if(q!=null&&q.moveToFirst()) return MmsLogic.fromIso(q.getString(0),q.isNull(1)?0:q.getInt(1));
    }catch(Exception e){}
    return "";
  }

  /** 한 대화의 문자들 (오래된 → 최근). v4: SMS+MMS 를 날짜 순으로 합침, MMS 는 글·제목·사진 */
  @JavascriptInterface public String getMessages(long thread,int limit){
    JSONArray arr=new JSONArray(); if(!canRead()||thread<=0) return arr.toString();
    int lim=Math.max(20,Math.min(limit,2000));
    ContentResolver cr=a.getContentResolver();
    List<MsgMerge.Item> sms=new ArrayList<>(), mms=new ArrayList<>();
    try(Cursor q=cr.query(SMS,new String[]{"_id","body","date","type"},"thread_id=? AND type!=3",new String[]{String.valueOf(thread)},"date DESC")){
      while(q!=null&&q.moveToNext()&&sms.size()<lim){ int ty=q.getInt(3);
        MsgMerge.Item it=new MsgMerge.Item(false,q.getLong(0),thread,q.getLong(2),ty==1,false).with("",nz(q.getString(1))); it.box=ty; sms.add(it); }
    }catch(Exception e){}
    try(Cursor q=cr.query(MMS,new String[]{"_id","date","msg_box"},"thread_id=?",new String[]{String.valueOf(thread)},"date DESC")){
      while(q!=null&&q.moveToNext()&&mms.size()<Math.min(lim,300)){ int box=q.getInt(2);
        MsgMerge.Item it=new MsgMerge.Item(true,q.getLong(0),thread,MmsLogic.msFromMmsDate(q.getLong(1)),box==1,false); it.box=box; mms.add(it); }
    }catch(Exception e){}
    for(MsgMerge.Item it:MsgMerge.conversation(sms,mms,lim)){
      try{
        JSONObject j=new JSONObject();
        j.put("id",it.key()); j.put("date",it.date); j.put("dir",it.in?"in":"out");
        if(!it.mms){ j.put("body",it.text); j.put("st",it.box==5?"fail":(it.box==4||it.box==6)?"sending":"ok"); }
        else {
          j.put("st",it.box==5?"fail":it.box==4?"sending":"ok"); j.put("mms",true);
          StringBuilder sb=new StringBuilder(); JSONArray imgs=new JSONArray();
          try(Cursor p=cr.query(PART,new String[]{"_id","ct","text"},"mid=?",new String[]{String.valueOf(it.id)},"seq ASC")){
            while(p!=null&&p.moveToNext()){ String ct=nz(p.getString(1)).toLowerCase();
              if(ct.equals("text/plain")){ String t=p.getString(2); if(t!=null&&!t.isEmpty()){ if(sb.length()>0) sb.append('\n'); sb.append(t);} }
              else if(ct.startsWith("image/")) imgs.put(String.valueOf(p.getLong(0))); }
          }catch(Exception e){}
          String sub=mmsSubject(cr,it.id);
          if(!sub.isEmpty()&&!MmsLogic.isNoSubject(sub)&&!sb.toString().contains(sub.trim())) j.put("sub",sub.trim());
          j.put("body",sb.toString()); j.put("imgs",imgs);
        }
        arr.put(j);
      }catch(Exception e){}
    }
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
    // v4: 장문(MMS) 글도 찾기
    try(Cursor q=a.getContentResolver().query(PART,new String[]{"mid","text"},"ct='text/plain' AND text LIKE ? ESCAPE '\\'",new String[]{like},"_id DESC")){
      while(q!=null&&q.moveToNext()&&arr.length()<60){ long mid=q.getLong(0);
        try(Cursor m=a.getContentResolver().query(Uri.parse("content://mms/"+mid),new String[]{"thread_id","date","msg_box"},null,null,null)){
          if(m==null||!m.moveToFirst()) continue;
          JSONObject j=new JSONObject(); j.put("thread",m.getLong(0)); j.put("addr",MmsInbox.from(a.getContentResolver(),mid,m.getInt(2))); j.put("body",nz(q.getString(1))); j.put("date",MmsLogic.msFromMmsDate(m.getLong(1))); arr.put(j); }
      }
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
