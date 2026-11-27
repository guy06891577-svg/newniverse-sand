package kr.newniverse.memo;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** 설정(폰 안에만 저장). 소리 보관은 처음에 꺼져 있어요. */
final class Prefs {
  final SharedPreferences sp;
  Prefs(Context c){ sp=c.getSharedPreferences("memo",Context.MODE_PRIVATE); }
  boolean offline(){ return sp.getBoolean("offline",true); }      // 폰 안 인식(오프라인) 먼저
  boolean keepAudio(){ return sp.getBoolean("keepAudio",false); } // 말소리 파일(m4a)도 함께 보관
  boolean autoClose(){ return sp.getBoolean("autoClose",true); }  // 저장 뒤 잠깐 있다가 자동으로 닫기
  boolean vibrate(){ return sp.getBoolean("vibrate",true); }
  boolean quiet(){ return sp.getBoolean("quiet",true); }          // 인식 시작·끝 소리 줄이기
  long silenceMs(){ long v=sp.getLong("silenceMs",ListenPolicy.SILENCE_MS); return v==1500||v==2500||v==4000?v:ListenPolicy.SILENCE_MS; }
  boolean muted(){ return sp.getBoolean("muted",false); }
  void setMuted(boolean v){ sp.edit().putBoolean("muted",v).apply(); }
  boolean askedMic(){ return sp.getBoolean("askedMic",false); }
  void setAskedMic(){ sp.edit().putBoolean("askedMic",true).apply(); }
  boolean askedNotif(){ return sp.getBoolean("askedNotif",false); }
  void setAskedNotif(){ sp.edit().putBoolean("askedNotif",true).apply(); }
  boolean askedCal(){ return sp.getBoolean("askedCal",false); }
  void setAskedCal(){ sp.edit().putBoolean("askedCal",true).apply(); }
  /** 키: offline keepAudio autoClose vibrate quiet (true/false) · silenceMs (1500/2500/4000). 모르는 키는 false */
  boolean set(String k,String v){
    switch(k){
      case "offline": case "keepAudio": case "autoClose": case "vibrate": case "quiet":
        sp.edit().putBoolean(k,"true".equals(v)).apply(); return true;
      case "silenceMs": try{ long l=Long.parseLong(v); if(l==1500||l==2500||l==4000){ sp.edit().putLong(k,l).apply(); return true; } }catch(Exception e){} return false;
      default: return false;
    }
  }
  JSONObject json(){
    try{ return new JSONObject().put("offline",offline()).put("keepAudio",keepAudio()).put("autoClose",autoClose())
        .put("vibrate",vibrate()).put("quiet",quiet()).put("silenceMs",silenceMs()); }catch(Exception e){ return new JSONObject(); }
  }
}
