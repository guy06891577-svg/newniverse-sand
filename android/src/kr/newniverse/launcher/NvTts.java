package kr.newniverse.launcher;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.Locale;

/** v47b: 한국어 TTS. 엔진 초기화·언어·오디오 포커스를 한곳에서 다루고, 실패 시 한 번만 다시 시도한다. */
final class NvTts {
  interface Sink {
    Context ctx();
    void post(Runnable r);
    void onTts(String type);
  }
  interface Eng {
    void setAudioAttributes(AudioAttributes a);
    int setLanguage(Locale loc);
    void setOnUtteranceProgressListener(UtteranceProgressListener l);
    int speak(String text, int queue, android.os.Bundle params, String id);
    void stop();
    void shutdown();
  }
  interface Maker { Eng make(Context c, TextToSpeech.OnInitListener l); }

  static final class AndroidEng implements Eng {
    final TextToSpeech t;
    AndroidEng(TextToSpeech t){ this.t=t; }
    public void setAudioAttributes(AudioAttributes a){ t.setAudioAttributes(a); }
    public int setLanguage(Locale loc){ return t.setLanguage(loc); }
    public void setOnUtteranceProgressListener(UtteranceProgressListener l){ t.setOnUtteranceProgressListener(l); }
    public int speak(String text, int queue, android.os.Bundle params, String id){ return t.speak(text, queue, params, id); }
    public void stop(){ t.stop(); }
    public void shutdown(){ t.shutdown(); }
  }

  static final int MAX_TRIES=2, WAIT_MS=3500;
  final Sink sink; final Handler ui;
  Maker maker=(c,l)->new AndroidEng(new TextToSpeech(c,l));
  Eng eng;
  boolean ready, talking, failed, starting, haveFocus;
  int tries;
  String pend;
  AudioFocusRequest focusReq;

  final Runnable waitTimeout=()->{
    if(ready||failed) return;
    starting=false;
    if(tries<MAX_TRIES) start();
    else finishFail();
  };

  NvTts(Sink sink, Handler ui){ this.sink=sink; this.ui=ui; }

  boolean muted(){
    try{
      AudioManager am=(AudioManager)sink.ctx().getSystemService(Context.AUDIO_SERVICE);
      if(am!=null && am.getRingerMode()!=AudioManager.RINGER_MODE_NORMAL) return true;
    }catch(Exception e){}
    try{
      android.app.NotificationManager nm=(android.app.NotificationManager)sink.ctx().getSystemService(Context.NOTIFICATION_SERVICE);
      if(nm!=null && nm.getCurrentInterruptionFilter()>android.app.NotificationManager.INTERRUPTION_FILTER_ALL) return true;
    }catch(Exception e){}
    return false;
  }

  String state(){
    if(muted()) return "mute";
    if(talking) return "talk";
    if(failed) return "fail";
    if(ready) return "ready";
    return "wait";
  }

  String speak(String text, String force){
    final String t=text==null?"":text.trim();
    if(t.isEmpty()) return "fail";
    if(!"1".equals(force) && muted()) return "mute";
    if(failed && tries>=MAX_TRIES) return "fail";
    if(ready && eng!=null){
      pend=null;
      ui.post(()->doSpeak(t));
      return "ok";
    }
    pend=t;
    start();
    if(failed) return "fail";
    if(ready && eng!=null){
      // start()가 동기로 준비된 경우(시험)
      pend=null;
      ui.post(()->doSpeak(t));
      return "ok";
    }
    return "wait";
  }

  void stop(){
    pend=null;
    ui.post(()->{
      try{ if(eng!=null) eng.stop(); }catch(Exception e){}
      talking=false;
      dropFocus();
      sink.onTts("done");
    });
  }

  void start(){
    if(ready && eng!=null) return;
    if(starting && eng!=null) return;
    if(failed && tries>=MAX_TRIES) return;
    starting=true;
    tries++;
    shutdownQuiet(false);
    ready=false;
    try{
      eng=maker.make(sink.ctx(), this::onInit);
    }catch(Exception e){
      eng=null; starting=false;
      if(tries<MAX_TRIES) ui.post(this::start);
      else finishFail();
      return;
    }
    ui.removeCallbacks(waitTimeout);
    ui.postDelayed(waitTimeout, WAIT_MS);
  }

  void onInit(int st){
    ui.post(()->{
      starting=false;
      if(st!=TextToSpeech.SUCCESS || eng==null){
        if(tries<MAX_TRIES) start();
        else finishFail();
        return;
      }
      try{
        AudioAttributes attrs=attrs();
        if(attrs!=null) eng.setAudioAttributes(attrs);
      }catch(Exception e){}
      int lang=TextToSpeech.LANG_NOT_SUPPORTED;
      try{ lang=eng.setLanguage(Locale.KOREAN); }catch(Exception e){}
      if(badLang(lang)){
        try{ lang=eng.setLanguage(Locale.KOREA); }catch(Exception e){}
      }
      if(badLang(lang)){
        if(tries<MAX_TRIES) start();
        else finishFail();
        return;
      }
      try{
        eng.setOnUtteranceProgressListener(new UtteranceProgressListener(){
          @Override public void onStart(String id){ talking=true; sink.onTts("start"); }
          @Override public void onDone(String id){ talking=false; dropFocus(); sink.onTts("done"); }
          @Override public void onError(String id){ talking=false; dropFocus(); sink.onTts("error"); }
        });
      }catch(Exception e){}
      ready=true; failed=false;
      ui.removeCallbacks(waitTimeout);
      if(pend!=null){ String p=pend; pend=null; doSpeak(p); }
    });
  }

  static boolean badLang(int lang){
    return lang==TextToSpeech.LANG_MISSING_DATA || lang==TextToSpeech.LANG_NOT_SUPPORTED;
  }

  static AudioAttributes attrs(){
    try{
      return new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build();
    }catch(Exception e){ return null; }
  }

  void finishFail(){
    ready=false; failed=true; starting=false;
    ui.removeCallbacks(waitTimeout);
    pend=null;
    shutdownQuiet(false);
    sink.onTts("error");
  }

  void doSpeak(String t){
    if(eng==null || !ready) return;
    takeFocus();
    try{
      int r=eng.speak(t, TextToSpeech.QUEUE_FLUSH, null, "nv");
      if(r!=TextToSpeech.SUCCESS){ dropFocus(); sink.onTts("error"); }
    }catch(Exception e){ dropFocus(); sink.onTts("error"); }
  }

  void takeFocus(){
    try{
      AudioManager am=(AudioManager)sink.ctx().getSystemService(Context.AUDIO_SERVICE);
      if(am==null) return;
      AudioAttributes a=attrs();
      if(Build.VERSION.SDK_INT>=26){
        AudioFocusRequest.Builder b=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
          .setAcceptsDelayedFocusGain(false)
          .setOnAudioFocusChangeListener(i->{});
        if(a!=null) b.setAudioAttributes(a);
        focusReq=b.build();
        am.requestAudioFocus(focusReq);
        haveFocus=true;
      }else{
        am.requestAudioFocus(i->{}, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        haveFocus=true;
      }
    }catch(Exception e){}
  }

  void dropFocus(){
    if(!haveFocus) return;
    try{
      AudioManager am=(AudioManager)sink.ctx().getSystemService(Context.AUDIO_SERVICE);
      if(am==null){ haveFocus=false; focusReq=null; return; }
      if(Build.VERSION.SDK_INT>=26 && focusReq!=null) am.abandonAudioFocusRequest(focusReq);
      else am.abandonAudioFocus(null);
    }catch(Exception e){}
    haveFocus=false; focusReq=null;
  }

  void shutdownQuiet(boolean keepTries){
    dropFocus();
    Eng old=eng; eng=null; ready=false; talking=false;
    if(old!=null) try{ old.stop(); old.shutdown(); }catch(Exception e){}
    if(!keepTries){ /* tries는 재시도 횟수라 여기서 건드리지 않음 */ }
  }

  void destroy(){
    pend=null;
    ui.removeCallbacks(waitTimeout);
    starting=false;
    shutdownQuiet(false);
  }
}
