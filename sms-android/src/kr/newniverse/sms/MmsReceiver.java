package kr.newniverse.sms;

import android.content.*;
import android.provider.Telephony;

/** v4: 사진·장문(MMS/LMS) 도착 신호(WAP_PUSH_DELIVER, 기본 문자 앱일 때만 옴) → 내려받기 시작 */
public class MmsReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context c,Intent i){
    if(!Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION.equals(i.getAction())) return;
    final PendingResult pr=goAsync(); final Context app=c.getApplicationContext();
    new Thread(()->{
      try{ MmsInbox.onPush(app,i); }
      catch(Exception e){ try{ SmsDiag.mms(app,System.currentTimeMillis(),"fail",103,0,false,InboxLogic.errClass(e),null); MmsInbox.notifyFail(app,103); }catch(Exception x){} }
      finally{ try{ pr.finish(); }catch(Exception e){} }
    },"nv-mms-push").start();
  }
}
