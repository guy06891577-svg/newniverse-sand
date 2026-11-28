package kr.newniverse.phone;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.os.*;
import android.speech.*;
import org.json.*;
import java.util.ArrayList;

/** v6 통화 메모 받아쓰기 (한국어). 기기 안 인식기를 먼저 쓰고, 잠금 화면에서는 인터넷(클라우드) 인식기를 절대 쓰지 않아요.
 *  결과는 화면(JS)의 NV_voice({st,text,mode,why})로 보냄. 녹음 파일은 만들지 않아요. */
final class VoiceNote {
  interface Out { void send(String json); }
  private static VoiceNote cur;
  final Activity a; final Out out; SpeechRecognizer sr; String mode=""; String partial=""; final StringBuilder done=new StringBuilder(); boolean stopping;

  private VoiceNote(Activity a,Out out){ this.a=a; this.out=out; }

  static boolean locked(Context c){ try{ KeyguardManager km=c.getSystemService(KeyguardManager.class); return km!=null&&km.isKeyguardLocked(); }catch(Throwable e){ return false; } }
  static boolean onDevice(Context c){ if(Build.VERSION.SDK_INT<33) return false; try{ return SpeechRecognizer.isOnDeviceRecognitionAvailable(c); }catch(Throwable e){ return false; } }
  static boolean any(Context c){ try{ return SpeechRecognizer.isRecognitionAvailable(c); }catch(Throwable e){ return false; } }
  static String plan(Context c){ return MemoRules.voiceMode(Build.VERSION.SDK_INT,locked(c),onDevice(c),any(c)); }

  /** 반환: ok · mic(마이크 허용 필요) · mic-locked(잠금 중이라 허용 창을 못 띄움) · locked(잠금 중 + 기기 안 인식기 없음) · none */
  static String start(Activity a,Out out){
    cancel();
    if(!Nv.has(a,Manifest.permission.RECORD_AUDIO)){
      if(locked(a)) return "mic-locked";
      a.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MainActivity.RQ_MIC); return "mic";
    }
    String m=plan(a);
    if("locked".equals(m)||"none".equals(m)) return m;
    VoiceNote v=new VoiceNote(a,out);
    return v.begin(m)?"ok":"none";
  }
  boolean begin(String m){
    release(); mode=m;
    try{
      sr="device".equals(m)?SpeechRecognizer.createOnDeviceSpeechRecognizer(a):SpeechRecognizer.createSpeechRecognizer(a);
      sr.setRecognitionListener(listener);
      Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
      i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
      i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR"); i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"ko-KR");
      i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true); i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
      // 기기 안 인식기는 오프라인 우선. 일반 인식기(잠금 풀렸을 때만)는 한국어 팩이 없을 때를 위한 두 번째 길이라 오프라인을 강제하지 않음
      if("device".equals(m)) i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
      i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,a.getPackageName());
      i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,2500L);
      i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,2500L);
      sr.startListening(i); cur=this; return true;
    }catch(Throwable e){ release(); return false; }
  }
  /** 기기 안 인식기에 한국어가 없을 때: 잠금이 풀려 있고 일반 인식기가 있으면 그쪽으로 한 번만 다시 시도 */
  boolean fellBack;
  boolean tryCloud(int code){
    if(fellBack||stopping||!"device".equals(mode)||done.length()>0||!partial.isEmpty()) return false;
    if(!MemoRules.cloudFallback(code,locked(a),any(a))) return false;
    fellBack=true; release(); new Handler(Looper.getMainLooper()).post(()->{ if(cur==this&&!begin("cloud")) finish("lang"); }); return true;
  }
  static void stop(){ VoiceNote v=cur; if(v==null) return; v.stopping=true; try{ v.sr.stopListening(); }catch(Throwable e){ v.finish(""); } }
  static void cancel(){ VoiceNote v=cur; cur=null; if(v!=null) v.release(); }
  void release(){ try{ if(sr!=null){ sr.cancel(); sr.destroy(); } }catch(Throwable e){} sr=null; }

  void emit(String st,String why){
    try{ JSONObject o=new JSONObject(); o.put("st",st); o.put("text",(done.toString()+(partial.isEmpty()?"":(done.length()>0?" ":"")+partial)).trim()); o.put("mode",mode); if(why!=null) o.put("why",why); out.send(o.toString()); }catch(Exception e){}
  }
  void finish(String why){ if(cur==this) cur=null; if(!partial.isEmpty()){ if(done.length()>0) done.append(' '); done.append(partial); partial=""; } emit(why.isEmpty()?"done":"error",why.isEmpty()?null:why); release(); }

  final RecognitionListener listener=new RecognitionListener(){
    @Override public void onReadyForSpeech(Bundle b){ emit("listening",null); }
    @Override public void onBeginningOfSpeech(){}
    @Override public void onRmsChanged(float r){}
    @Override public void onBufferReceived(byte[] b){}
    @Override public void onEndOfSpeech(){}
    @Override public void onPartialResults(Bundle b){ String t=first(b); if(!t.isEmpty()&&!t.equals(partial)){ partial=t; emit("partial",null); } }
    @Override public void onResults(Bundle b){ String t=first(b); if(!t.isEmpty()) partial=t; finish(""); }
    @Override public void onError(int code){
      if(code==SpeechRecognizer.ERROR_NO_MATCH||code==SpeechRecognizer.ERROR_SPEECH_TIMEOUT){ finish(partial.isEmpty()&&done.length()==0?"nomatch":""); return; }
      if(tryCloud(code)) return;
      finish(code==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS?"perm":code==12||code==13?"lang":code==SpeechRecognizer.ERROR_NETWORK||code==SpeechRecognizer.ERROR_NETWORK_TIMEOUT||code==SpeechRecognizer.ERROR_SERVER?"net":"fail");
    }
    @Override public void onEvent(int t,Bundle b){}
  };
  static String first(Bundle b){ if(b==null) return ""; ArrayList<String> l=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); return l==null||l.isEmpty()||l.get(0)==null?"":l.get(0).trim(); }
}
