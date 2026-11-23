package kr.newniverse.sms;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;

/** 전화를 거절하면서 '문자로 답하기'를 고르면 시스템이 부르는 곳 */
public class RespondService extends Service {
  @Override public int onStartCommand(Intent i,int f,int id){
    try{
      if(i!=null&&"android.intent.action.RESPOND_VIA_MESSAGE".equals(i.getAction())){
        Uri u=i.getData(); String to=u==null?"":u.getSchemeSpecificPart(); if(to==null) to="";
        int q=to.indexOf('?'); if(q>=0) to=to.substring(0,q);
        String text=i.getStringExtra(Intent.EXTRA_TEXT);
        if(text!=null&&!text.trim().isEmpty()) Nv.send(this,to,text);
      }
    }catch(Exception e){}
    stopSelf(id); return START_NOT_STICKY;
  }
  @Override public IBinder onBind(Intent i){ return null; }
}
