package kr.newniverse.sms;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * v9: 긴 문자를 여러 통의 SMS 로 나누지 않고 장문(LMS) 한 통으로 보내기.
 *  1) M-Send.req PDU 만들기(MmsPdu.sendReq) → 앱 캐시의 파일(MmsFileProvider, 밖으로 안 열림)에 쓰기
 *  2) 기본 문자 앱이면 content://mms/outbox 에 ‘보내는 중’으로 저장(글 조각·SMIL·주소) → 대화에 바로 보임
 *  3) 전화 앱(MmsService)에 파일 읽기만 잠깐 허락하고 SmsManager.sendMultimediaMessage(문자용 기본 유심)
 *  4) 결과(MmsResultReceiver → onSent): 통신사 응답 M-Send.conf 를 읽어 ‘보냄(sent)’ 또는 ‘실패(failed)’로 바꿈. 실패한 말풍선을 누르면 다시 보내기.
 * 진단(‘MMS 보내기 기록’)에는 시각·글자 수·단계·결과 코드·HTTP 상태·통신사 응답 코드만. 본문·번호는 남기지 않음.
 */
final class MmsOutbox {
  static final String ACT_SENT="kr.newniverse.sms.MMS_SENT";

  static long smsSub(){ try{ int s=SubscriptionManager.getDefaultSmsSubscriptionId(); return s>=0&&s!=SubscriptionManager.INVALID_SUBSCRIPTION_ID?s:-1; }catch(Throwable e){ return -1; } }

  /** 반환 "ok"(넘김) | "err" */
  static String send(Context c,String to,String text){
    long now=System.currentTimeMillis();
    String tr="T"+Long.toHexString(now)+Integer.toHexString((int)(System.nanoTime()&0xffff));
    int len=text==null?0:text.length();
    SmsDiag.mmsOut(c,"build",len,0,0,0,"",now,false);
    byte[] pdu;
    try{ pdu=MmsPdu.sendReq(tr,Collections.singletonList(to),null,text,now/1000L,true); }
    catch(Throwable e){ SmsDiag.mmsOut(c,"error",len,105,0,0,InboxLogic.errClass(e),0,false); return "err"; }
    String name="s"+Long.toHexString(now)+".pdu";
    File f=MmsFileProvider.file(c,name);
    try(FileOutputStream os=new FileOutputStream(f)){ os.write(pdu); }
    catch(Throwable e){ SmsDiag.mmsOut(c,"error",len,105,0,0,InboxLogic.errClass(e),0,false); return "err"; }
    SmsDiag.mmsOutBytes(c,pdu.length);
    long sub=smsSub();
    Uri row=null;
    if(Nv.isDefault(c)){ try{ row=persist(c,to,text,tr,now,sub,pdu.length); }catch(Throwable e){ row=null; } }
    SmsDiag.mmsOut(c,"saved",len,0,0,0,row==null?(Nv.isDefault(c)?"nosave":"notdefault"):"",0,row!=null);
    try{
      Uri u=MmsFileProvider.uri(name);
      for(String p:MmsInbox.MMS_PKGS){ try{ c.grantUriPermission(p,u,Intent.FLAG_GRANT_READ_URI_PERMISSION); }catch(Exception e){} }
      Intent r=new Intent(c,MmsResultReceiver.class).setAction(ACT_SENT+"."+name).putExtra("file",name).putExtra("row",row==null?null:row.toString()).putExtra("len",len).putExtra("at",now);
      PendingIntent pi=PendingIntent.getBroadcast(c,name.hashCode(),r,MmsInbox.pendingFlags()); // 결과(통신사 응답·HTTP 상태)를 시스템이 채워 넣으므로 MUTABLE
      SmsManager m=MmsInbox.mgr(c,sub);
      m.sendMultimediaMessage(c,u,null,null,pi);
      SmsDiag.mmsOut(c,"handed",len,0,0,0,"",0,row!=null);
      return "ok";
    }catch(Throwable e){
      SmsDiag.mmsOut(c,"error",len,104,0,0,InboxLogic.errClass(e),0,row!=null);
      mark(c,row,false,null);
      if(f!=null) f.delete();
      return "err";
    }
  }

  /** ‘보내는 중’ 저장: pdu(outbox) + SMIL·글 조각 + 주소(보낸 사람 insert-address-token, 받는 사람) */
  static Uri persist(Context c,String to,String text,String tr,long now,long sub,int size){
    ContentResolver cr=c.getContentResolver();
    long tid=-1;
    try{ Set<String> rc=new HashSet<>(); rc.add(to); tid=Telephony.Threads.getOrCreateThreadId(c,rc); }catch(Throwable e){}
    java.util.Map<String,Object> v=MmsLogic.outValues(tr,now,tid,sub,size);
    Uri row=null;
    try{ row=cr.insert(Uri.parse("content://mms/outbox"),SmsInbox.cv(v)); }
    catch(IllegalArgumentException|android.database.sqlite.SQLiteException e){ // 제조사 저장소가 모르는 칸이 있으면 꼭 필요한 칸만으로 한 번 더
      v.keySet().retainAll(MmsLogic.OUT_CORE); row=cr.insert(Uri.parse("content://mms/outbox"),SmsInbox.cv(v)); }
    if(row==null) return null;
    String id=row.getLastPathSegment();
    Uri pu=Uri.parse("content://mms/"+id+"/part");
    ContentValues s=new ContentValues(); s.put("seq",-1); s.put("ct","application/smil"); s.put("cid","<smil>"); s.put("cl","smil.xml"); s.put("chset",MmsPdu.CS_UTF8); s.put("text",MmsPdu.SMIL);
    try{ cr.insert(pu,s); }catch(Exception e){}
    ContentValues t=new ContentValues(); t.put("seq",0); t.put("ct","text/plain"); t.put("cid","<text_0>"); t.put("cl","text_0.txt"); t.put("chset",MmsPdu.CS_UTF8); t.put("text",text==null?"":text);
    Uri tp=null; try{ tp=cr.insert(pu,t); }catch(Exception e){}
    if(tp==null){ try{ cr.delete(row,null,null); }catch(Exception e){} return null; }
    MmsInbox.addr(cr,id,"insert-address-token",MmsLogic.TYPE_FROM,MmsPdu.CS_UTF8);
    MmsInbox.addr(cr,id,to,MmsLogic.TYPE_TO,MmsPdu.CS_UTF8);
    return row;
  }

  /** 4) 결과 */
  static void onSent(Context c,Intent i,int code){
    String name=i.getStringExtra("file"); String row=i.getStringExtra("row"); int len=i.getIntExtra("len",0);
    File f=MmsFileProvider.file(c,name); if(f!=null) f.delete();
    int http=i.getIntExtra("android.telephony.extra.MMS_HTTP_STATUS",0);
    byte[] data=i.getByteArrayExtra("android.telephony.extra.MMS_DATA");
    MmsPdu.Pdu conf=data==null?null:MmsPdu.parse(data);
    if(conf!=null&&conf.type!=MmsPdu.SEND_CONF) conf=null;
    boolean ok=MmsLogic.sendOk(code,conf);
    int resp=conf==null?0:conf.responseStatus;
    int shown=code==Activity.RESULT_OK&&!ok?106:code;
    SmsDiag.mmsOut(c,"result",len,shown,http,resp,ok?"":(conf==null&&data!=null?"conf-unreadable":""),0,row!=null);
    SmsDiag.mmsOutDone(c,ok);
    mark(c,row==null?null:Uri.parse(row),ok,conf);
    MainActivity.refresh();
  }
  static void mark(Context c,Uri row,boolean ok,MmsPdu.Pdu conf){
    if(row==null) return;
    try{ ContentValues v=new ContentValues(); v.put("msg_box",ok?MmsLogic.MSG_BOX_SENT:MmsLogic.MSG_BOX_FAILED);
      if(conf!=null){ if(!conf.messageId.isEmpty()) v.put("m_id",conf.messageId); if(conf.responseStatus!=0) v.put("resp_st",conf.responseStatus); }
      if(ok) v.put("date_sent",System.currentTimeMillis()/1000L);
      c.getContentResolver().update(row,v,null,null); }catch(Exception e){}
  }
}
