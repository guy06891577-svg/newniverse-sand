package kr.newniverse.phone;

import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;
import org.json.JSONObject;

/**
 * 화면 없이 모드를 켜고 끄는 문. 뉴니버스 런처의 「운전 중」 말 명령이나 바로가기가 부름.
 * 같은 뉴니버스 서명 앱(또는 이 앱 자신)이 부른 것만 따라요 — 다른 앱이 몰래 모드를 켤 수 없게.
 * 방해 금지 허용이 없으면 켜지 않고 모드 화면을 열어 허용을 안내해요.
 */
public class ModeShortcutActivity extends Activity {
  static final String ACTION_ON="kr.newniverse.phone.MODE_ON", ACTION_OFF="kr.newniverse.phone.MODE_OFF";

  @Override protected void onCreate(Bundle b){
    super.onCreate(b);
    try{ handle(getIntent()); }catch(Exception e){}
    finish();
  }
  @Override protected void onNewIntent(Intent i){ super.onNewIntent(i); try{ handle(i); }catch(Exception e){} finish(); }

  /**
   * 부른 앱이 같은 서명인지. 부른 앱 이름은 startActivityForResult 로 불렀을 때만 시스템이 믿을 수 있게 알려 줘요
   * (인텐트에 적힌 「referrer」는 아무 앱이나 속일 수 있어서 쓰지 않아요).
   */
  boolean trusted(){
    try{
      String pkg=getCallingPackage();
      if(pkg==null) return false;
      if(pkg.equals(getPackageName())) return true;
      return getPackageManager().checkSignatures(pkg,getPackageName())==PackageManager.SIGNATURE_MATCH;
    }catch(Exception e){ return false; }
  }

  void handle(Intent i){
    if(i==null||!trusted()){ setResult(RESULT_CANCELED); return; }
    setResult(RESULT_OK);
    String act=i.getAction(); String mode=i.getStringExtra("mode");
    if(mode==null) mode=ModeDefs.fromSpeech(i.getStringExtra("say"));
    Context ctx=getApplicationContext();
    if(ACTION_OFF.equals(act)){
      JSONObject r=ModeManager.stop(ctx,"manual");
      Toast.makeText(this,r==null?"켜져 있는 모드가 없어요":"「"+ModeDefs.name(r.optString("id"))+"」을 껐어요",Toast.LENGTH_SHORT).show();
      if(r!=null) startActivity(new Intent(this,MainActivity.class).setAction(MainActivity.ACTION_MODES).putExtra("view","summary").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      return;
    }
    if(!ACTION_ON.equals(act)||!ModeDefs.valid(mode)) return;
    if(!Dnd.granted(ctx)){   // 허용이 없으면 조용히 실패하지 않고 안내 화면으로
      startActivity(new Intent(this,MainActivity.class).setAction(MainActivity.ACTION_MODES).putExtra("view","modes").putExtra("need","dnd").putExtra("mode",mode).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      return;
    }
    JSONObject r=ModeManager.start(ctx,mode,ModeDefs.timers(mode)?ModeStore.cfg(ctx,mode).optInt("timer"):0);
    Toast.makeText(this,r.optBoolean("ok")?"「"+ModeDefs.name(mode)+"」을 켰어요":"켜지 못했어요",Toast.LENGTH_SHORT).show();
  }
}
