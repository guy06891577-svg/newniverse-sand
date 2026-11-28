package kr.newniverse.launcher;

import android.app.Application;
import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import org.robolectric.RuntimeEnvironment;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class NvTtsTest {
  Application app;
  Handler ui;
  List<String> ev;
  NvTts tts;

  @Before public void setUp(){
    app=RuntimeEnvironment.getApplication();
    ui=new Handler(Looper.getMainLooper());
    ev=new ArrayList<>();
    tts=new NvTts(new NvTts.Sink(){
      public Context ctx(){ return app; }
      public void post(Runnable r){ ui.post(r); }
      public void onTts(String type){ ev.add(type); }
    }, ui);
    AudioManager am=(AudioManager)app.getSystemService(Context.AUDIO_SERVICE);
    am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
  }

  void idle(){ ShadowLooper.shadowMainLooper().idle(); }

  static final class Fake implements NvTts.Eng {
    int lang=TextToSpeech.LANG_COUNTRY_AVAILABLE;
    int speakResult=TextToSpeech.SUCCESS;
    String spoken;
    UtteranceProgressListener lis;
    public void setAudioAttributes(android.media.AudioAttributes a){}
    public int setLanguage(Locale loc){ return lang; }
    public void setOnUtteranceProgressListener(UtteranceProgressListener l){ lis=l; }
    public int speak(String text, int queue, android.os.Bundle params, String id){
      spoken=text;
      if(speakResult==TextToSpeech.SUCCESS && lis!=null) lis.onStart(id);
      return speakResult;
    }
    public void stop(){}
    public void shutdown(){}
  }

  @Test public void successSpeaksPendingAfterInit(){
    AtomicReference<Fake> fake=new AtomicReference<>();
    tts.maker=(c,l)->{
      Fake f=new Fake(); fake.set(f);
      ui.post(()->l.onInit(TextToSpeech.SUCCESS));
      return f;
    };
    String r=tts.speak("추석은 내년 9월 15일이에요","0");
    assertEquals("wait", r);
    idle();
    assertTrue(tts.ready);
    assertEquals("추석은 내년 9월 15일이에요", fake.get().spoken);
    assertTrue(ev.contains("start"));
    assertEquals("ok", tts.speak("다시 읽어 주세요","1"));
    idle();
    assertEquals("다시 읽어 주세요", fake.get().spoken);
  }

  @Test public void initErrorRetriesOnceThenFail(){
    AtomicInteger n=new AtomicInteger();
    tts.maker=(c,l)->{
      n.incrementAndGet();
      ui.post(()->l.onInit(TextToSpeech.ERROR));
      return new Fake();
    };
    String r=tts.speak("안녕하세요","0");
    assertEquals("wait", r);
    idle();
    assertEquals(2, n.get());
    assertTrue(tts.failed);
    assertTrue(ev.contains("error"));
    assertEquals("fail", tts.speak("또","0"));
  }

  @Test public void langMissingRetriesOnceThenFail(){
    AtomicInteger n=new AtomicInteger();
    tts.maker=(c,l)->{
      n.incrementAndGet();
      Fake f=new Fake(); f.lang=TextToSpeech.LANG_MISSING_DATA;
      ui.post(()->l.onInit(TextToSpeech.SUCCESS));
      return f;
    };
    tts.speak("절기","0");
    idle();
    assertEquals(2, n.get());
    assertTrue(tts.failed);
    assertEquals("fail", tts.state());
  }

  @Test public void waitTimeoutRetriesThenFails(){
    AtomicInteger n=new AtomicInteger();
    tts.maker=(c,l)->{ n.incrementAndGet(); return new Fake(); };
    assertEquals("wait", tts.speak("날씨","0"));
    ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(3600));
    assertEquals(2, n.get());
    ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(3600));
    assertTrue(tts.failed);
    assertTrue(ev.contains("error"));
    assertEquals("fail", tts.speak("날씨","0"));
  }

  @Test public void mutedReturnsMuteAndForceStillSpeaks(){
    AudioManager am=(AudioManager)app.getSystemService(Context.AUDIO_SERVICE);
    am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
    AtomicReference<Fake> fake=new AtomicReference<>();
    tts.maker=(c,l)->{
      Fake f=new Fake(); fake.set(f);
      ui.post(()->l.onInit(TextToSpeech.SUCCESS));
      return f;
    };
    assertEquals("mute", tts.speak("추석 언제야","0"));
    idle();
    assertTrue(fake.get()==null || fake.get().spoken==null);
    tts.start(); idle();
    assertEquals("ok", tts.speak("추석 언제야","1"));
    idle();
    assertEquals("추석 언제야", fake.get().spoken);
  }
}
