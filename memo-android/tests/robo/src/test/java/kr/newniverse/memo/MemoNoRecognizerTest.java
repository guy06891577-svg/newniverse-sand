package kr.newniverse.memo;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.provider.Settings;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.speech.SpeechRecognizer;
import java.time.Duration;
import java.util.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSpeechRecognizer;

/** 이 폰에 음성 인식기가 아예 없을 때: 소리 파일로 저장 · 아무 말도 없으면 저장 안 함 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MemoNoRecognizerTest {
  Application app; ActivityController<MainActivity> ctl; MainActivity act;
  @Before public void setUp(){
    app=RuntimeEnvironment.getApplication();
    shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO,Manifest.permission.POST_NOTIFICATIONS);
    ShadowSpeechRecognizer.reset(); ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(false); AudioCapture.lenient=true; AudioCapture.testAmp=0;
    Settings.Global.putInt(app.getContentResolver(),Settings.Global.AIRPLANE_MODE_ON,0);
    shadowOf((KeyguardManager)app.getSystemService(Context.KEYGUARD_SERVICE)).setKeyguardLocked(false);
    app.getSharedPreferences("memo",0).edit().clear().commit();
    MainActivity.gate=new MainActivity.Gate(){ @Override public boolean unlocked(Activity a){ return true; } @Override public void requestUnlock(Activity a,Runnable ok,Runnable no){ ok.run(); } };
  }
  @After public void tearDown(){ AudioCapture.testAmp=-1; AudioCapture.lenient=false; }
  void idle(long ms){ shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)); }
  List<Note> notes(){ return new NoteStore(app.getFilesDir()).all(); }
  JSONObject last(String t){ for(int i=act.log.size()-1;i>=0;i--) if(t.equals(act.log.get(i).optString("t"))) return act.log.get(i); return null; }
  void launch(){ ctl=Robolectric.buildActivity(MainActivity.class,new Intent(app,MainActivity.class)).setup(); act=ctl.get(); }

  @Test public void recognizerMissingFallsBackToAudio(){
    assertFalse(Dictation.recognizerAvailable(app));
    launch();
    assertNull("인식기를 만들지 않아요",ShadowSpeechRecognizer.getLatestSpeechRecognizer());
    assertTrue(act.dict.audioMode); assertEquals("audio",last("status").optString("state")); assertEquals("unavailable",last("status").optString("detail"));
    AudioCapture.testAmp=6000; idle(600); AudioCapture.testAmp=0; idle(2800); idle(400);
    assertFalse(act.dict.busy());
    List<Note> l=notes(); assertEquals(1,l.size()); assertEquals(Note.AUDIO,l.get(0).kind); assertEquals("",l.get(0).text); assertFalse(l.get(0).audio.isEmpty());
    assertEquals("audio",last("saved").optString("kind"));
    assertTrue("나중에 글로 옮기기: 소리 파일이 남아 있어요",new NoteStore(app.getFilesDir()).audioFile(l.get(0).audio).exists());
  }
  @Test public void silenceOnlyInAudioModeSavesNothing(){
    launch(); idle(7500); idle(400);
    assertEquals(0,notes().size()); assertNotNull(last("none"));
  }
  @Test public void tapStopsAudioRecordingAndKeepsIt(){
    launch(); AudioCapture.testAmp=5000; idle(800);
    act.br.stop(); idle(100);
    assertEquals(1,notes().size()); assertEquals(Note.AUDIO,notes().get(0).kind);
  }
  @Test public void audioNoteCanBeEditedIntoText() throws Exception {
    launch(); AudioCapture.testAmp=5000; idle(800); act.br.stop(); idle(100);
    Note n=notes().get(0);
    assertTrue(new JSONObject(act.br.edit(String.valueOf(n.id),"  나중에 옮겨 적은 글  ")).getBoolean("ok"));
    Note m=notes().get(0); assertEquals("나중에 옮겨 적은 글",m.text); assertFalse("소리 파일은 그대로",m.audio.isEmpty());
  }
}
