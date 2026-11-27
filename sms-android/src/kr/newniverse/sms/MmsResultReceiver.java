package kr.newniverse.sms;

import android.app.Activity;
import android.content.*;
import java.io.File;

/** v4: 장문 내려받기 결과 · ‘다시 받기’ 단추 · 통신사에 보낸 ‘받았어요’ 결과 (앱 안에서만, exported=false) */
public class MmsResultReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context c,Intent i){
    final String a=i.getAction()==null?"":i.getAction();
    final int code=getResultCode();
    final PendingResult pr=goAsync(); final Context app=c.getApplicationContext();
    new Thread(()->{
      try{
        if(a.startsWith(MmsOutbox.ACT_SENT)) MmsOutbox.onSent(app,i,code); // v9: 장문 보내기 결과
        else if(a.startsWith(MmsInbox.ACT_DL)) MmsInbox.onDownloaded(app,i,code);
        else if(a.equals(MmsInbox.ACT_RETRY)) MmsInbox.retryAll(app);
        else if(a.startsWith(MmsInbox.ACT_NR)){
          File f=MmsFileProvider.file(app,i.getStringExtra("file")); if(f!=null) f.delete();
          SmsDiag.mmsAck(app,code==Activity.RESULT_OK?"ok":"fail:"+code);
        }
      }catch(Exception e){ if(a.startsWith(MmsOutbox.ACT_SENT)){ try{ SmsDiag.mmsOut(app,"error",0,101,0,0,InboxLogic.errClass(e),0,false); }catch(Exception x){} return; } try{ SmsDiag.mms(app,0,"fail",101,0,false,InboxLogic.errClass(e),null); if(a.startsWith(MmsInbox.ACT_DL)) MmsInbox.notifyFail(app,101); }catch(Exception x){} }
      finally{ try{ pr.finish(); }catch(Exception e){} }
    },"nv-mms-result").start();
  }
}
