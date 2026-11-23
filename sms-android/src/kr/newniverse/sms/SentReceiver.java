package kr.newniverse.sms;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.provider.Telephony;

/** 보낸 결과: 기본 앱이면 저장해 둔 문자를 '보냄' 또는 '실패'로 바꿈 */
public class SentReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context c,Intent i){
    boolean ok=getResultCode()==Activity.RESULT_OK;
    String row=i.getStringExtra("row");
    if(row!=null&&(i.getBooleanExtra("last",true)||!ok)){
      try{ ContentValues v=new ContentValues(); v.put(Telephony.Sms.TYPE,ok?Telephony.Sms.MESSAGE_TYPE_SENT:Telephony.Sms.MESSAGE_TYPE_FAILED);
        c.getContentResolver().update(Uri.parse(row),v,null,null); }catch(Exception e){}
    }
    MainActivity.refresh();
  }
}
