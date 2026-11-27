package kr.newniverse.memo;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Looper;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.io.File;
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
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowKeyguardManager;
import org.robolectric.shadows.ShadowNotificationManager;
import org.robolectric.shadows.ShadowSpeechRecognizer;

/** 잠금 화면 표시 · 열자마자 듣기 · 이어 듣기/끊김/60초 · 받아쓰기 실패 대안 · 잠금 가림 · 알림(저장 때 자동) */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MemoRoboTest {
  Application app; ActivityController<MainActivity> ctl; MainActivity act;
  boolean locked=false; int unlockAsked=0; boolean unlockResult=true;

  @Before public void setUp(){
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    app=RuntimeEnvironment.getApplication();
    ShadowSpeechRecognizer.reset(); ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(false); ShadowAlarmManager.reset(); ShadowNotificationManager.reset(); org.robolectric.shadows.ShadowVibrator.reset();
    Settings.Global.putInt(app.getContentResolver(),Settings.Global.AIRPLANE_MODE_ON,0);
    shadowOf((KeyguardManager)app.getSystemService(Context.KEYGUARD_SERVICE)).setKeyguardLocked(false);
    AudioCapture.testAmp=-1; AudioCapture.lenient=true;
    shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO,Manifest.permission.POST_NOTIFICATIONS);
    ShadowAlarmManager.setCanScheduleExactAlarms(true);
    registerRecognizer(true);
    app.getSharedPreferences("memo",0).edit().clear().commit();
    new NoteStore(app.getFilesDir()).all().forEach(n->new NoteStore(app.getFilesDir()).delete(n.id));
    MainActivity.gate=new MainActivity.Gate(){
      @Override public boolean unlocked(Activity a){ return !locked; }
      @Override public void requestUnlock(Activity a,Runnable ok,Runnable no){ unlockAsked++; if(unlockResult){ locked=false; ok.run(); } else no.run(); }
    };
  }
  @After public void tearDown(){ AudioCapture.testAmp=-1; AudioCapture.lenient=false; }

  void registerRecognizer(boolean on){
    ResolveInfo ri=new ResolveInfo(); ri.serviceInfo=new ServiceInfo(); ri.serviceInfo.packageName="com.google.android.googlequicksearchbox"; ri.serviceInfo.name="svc";
    if(on) shadowOf(app.getPackageManager()).addResolveInfoForIntent(new Intent(RecognitionService.SERVICE_INTERFACE),ri);
  }
  MainActivity launch(){ return launch(new Intent(app,MainActivity.class).setAction(Intent.ACTION_MAIN)); }
  MainActivity launch(Intent i){ ctl=Robolectric.buildActivity(MainActivity.class,i).setup(); act=ctl.get(); return act; }
  void idle(long ms){ shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)); }
  SpeechRecognizer sr(){ return ShadowSpeechRecognizer.getLatestSpeechRecognizer(); }
  ShadowSpeechRecognizer ssr(){ return shadowOf(sr()); }
  static Bundle res(String t){ Bundle b=new Bundle(); b.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,new ArrayList<>(Arrays.asList(t))); return b; }
  void say(String partial){ ssr().triggerOnPartialResults(res(partial)); }
  void finalText(String t){ ssr().triggerOnResults(res(t)); }
  /** 말하는 척: 소리 크기 신호를 계속 보내면서 시간만큼 흘려보냄 */
  void speakFor(long ms){ for(long t=0;t<ms;t+=250){ ssr().triggerOnRmsChanged(8f); idle(250); } }
  List<Note> notes(){ return new NoteStore(app.getFilesDir()).all(); }
  JSONObject last(String type){ for(int i=act.log.size()-1;i>=0;i--) if(type.equals(act.log.get(i).optString("t"))) return act.log.get(i); return null; }
  int count(String type){ int k=0; for(JSONObject o:act.log) if(type.equals(o.optString("t"))) k++; return k; }

  // ===================== 잠금 화면 =====================
  @Test public void launchShowsOverLockScreenAndTurnsScreenOn(){
    launch();
    assertTrue("setShowWhenLocked(true)",act.showWhenLockedSet); assertTrue(act.turnScreenOnSet);
    assertTrue("Robolectric 이 본 showWhenLocked",shadowOf(act).getShowWhenLocked());
  }
  @Test public void viewingOldNotesDropsLockScreenDisplayAndRestoresAfter(){
    launch(); assertTrue(shadowOf(act).getShowWhenLocked());
    act.openPrivate("list");
    assertEquals(1,unlockAsked); assertEquals("list",act.view); assertFalse("지난 메모를 보는 동안은 잠금 화면 위 표시를 꺼요",shadowOf(act).getShowWhenLocked());
    act.leavePrivate(); assertEquals("rec",act.view); assertTrue(shadowOf(act).getShowWhenLocked());
  }
  @Test public void unlockDeniedKeepsRecordingView(){
    launch(); unlockResult=false; locked=true;
    act.openPrivate("list");
    assertEquals("rec",act.view); assertNotNull(last("lockdenied")); assertTrue(shadowOf(act).getShowWhenLocked());
  }
  @Test public void screenLeavingHidesOldNotes(){
    launch(); act.openPrivate("list"); assertEquals("list",act.view);
    ctl.pause().stop(); assertEquals("rec",act.view); assertTrue(shadowOf(act).getShowWhenLocked());
  }
  @Test public void oldNotesRequireUnlockInsideJavaBridge() throws Exception {
    Note n=new NoteStore(app.getFilesDir()).add(note("비밀 메모",1000));
    launch(); locked=true;
    for(String r:new String[]{act.br.list(""),act.br.get(String.valueOf(n.id)),act.br.copy(String.valueOf(n.id)),act.br.share(String.valueOf(n.id)),
        act.br.del(String.valueOf(n.id)),act.br.edit(String.valueOf(n.id),"x"),act.br.settings(),act.br.status(),act.br.play(String.valueOf(n.id)),act.br.setSetting("keepAudio","true")}){
      JSONObject o=new JSONObject(r); assertTrue(r,o.optBoolean("locked")); assertFalse(r.contains("비밀"));
    }
    assertEquals("지워지면 안 돼요",1,notes().size()); assertEquals("비밀 메모",notes().get(0).text);
    assertFalse(act.prefs.keepAudio());
    locked=false;
    JSONObject ok=new JSONObject(act.br.list("")); assertTrue(ok.getBoolean("ok")); assertEquals(1,ok.getJSONArray("notes").length());
    assertTrue(ok.toString().contains("비밀"));
  }
  @Test public void lockScreenCanOnlyRemindJustSavedNote() throws Exception {
    Note old=new NoteStore(app.getFilesDir()).add(note("내일 9시에 알려줘",System.currentTimeMillis()));
    launch(); locked=true;
    JSONObject r=new JSONObject(act.br.remind(String.valueOf(old.id),false)); assertTrue(r.optBoolean("locked"));
    assertEquals(0,shadowOf((AlarmManager)app.getSystemService(Context.ALARM_SERVICE)).getScheduledAlarms().size());
  }
  static Note note(String t,long at){ Note n=new Note(); n.text=t; n.createdAt=at; n.durationMs=3000; return n; }

  // ===================== 열자마자 듣기 =====================
  @Test public void launchStartsListeningImmediatelyInKorean(){
    launch();
    assertNotNull("인식기를 만들어야 해요",sr());
    Intent i=ssr().getLastRecognizerIntent(); assertNotNull("startListening 이 불려야 해요",i);
    assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH,i.getAction());
    assertEquals("ko-KR",i.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE));
    assertTrue(i.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false));
    assertTrue("오프라인 우선",i.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,false));
    assertEquals(2500L,i.getLongExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,0));
    assertTrue(act.dict.listening()); assertEquals("starting",last("status").optString("state"));
  }
  @Test public void readyStatusAndVibrationCue(){
    launch(); ssr().triggerOnReadyForSpeech(new Bundle());
    assertEquals("listening",last("status").optString("state"));
    org.robolectric.shadows.ShadowVibrator sv=shadowOf((android.os.Vibrator)app.getSystemService(Context.VIBRATOR_SERVICE));
    assertTrue("시작할 때 짧은 진동",sv.isVibrating()||sv.getMilliseconds()>0);
  }
  @Test public void vibrationCanBeTurnedOff(){
    app.getSharedPreferences("memo",0).edit().putBoolean("vibrate",false).commit();
    launch(); ssr().triggerOnReadyForSpeech(new Bundle());
    org.robolectric.shadows.ShadowVibrator sv=shadowOf((android.os.Vibrator)app.getSystemService(Context.VIBRATOR_SERVICE));
    assertFalse(sv.isVibrating()); assertEquals(0,sv.getMilliseconds());
  }
  @Test public void noMicPermissionAsksFirstAndNeverListens(){
    shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO);
    launch();
    assertNull("권한 없이 인식기를 만들면 안 돼요",sr()); assertFalse(act.dict.busy());
    assertNotNull(last("needmic")); assertTrue(last("needmic").optBoolean("canAsk"));
    assertNotNull(shadowOf(act).getLastRequestedPermission());
    assertEquals(Manifest.permission.RECORD_AUDIO,shadowOf(act).getLastRequestedPermission().requestedPermissions[0]);
    // 허용하면 바로 듣기 시작
    shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO);
    act.onRequestPermissionsResult(MainActivity.REQ_MIC,new String[]{Manifest.permission.RECORD_AUDIO},new int[]{0});
    assertNotNull(sr()); assertTrue(act.dict.listening());
  }
  @Test public void micDeniedShowsBlockedHint(){
    shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO);
    launch();
    act.onRequestPermissionsResult(MainActivity.REQ_MIC,new String[]{Manifest.permission.RECORD_AUDIO},new int[]{-1});
    assertEquals("needmic",last("needmic").optString("t")); assertNull(sr()); assertFalse(act.dict.busy());
  }
  @Test public void relaunchWhileListeningStopsAndSavesButIgnoresDoublePulse(){
    launch(); ssr().triggerOnReadyForSpeech(new Bundle()); say("테스트 메모");
    ctl.newIntent(new Intent(app,MainActivity.class)); // 켜자마자 들어온 중복 신호 → 무시
    assertTrue(act.dict.listening()); assertEquals(0,notes().size());
    idle(2000);
    ctl.newIntent(new Intent(app,MainActivity.class)); // 다시 두 번 누름 → 끝내고 저장
    idle(1900); assertEquals(1,notes().size()); assertEquals("테스트 메모",notes().get(0).text);
  }
  @Test public void relaunchWhenIdleStartsAgain(){
    launch(); ssr().triggerOnReadyForSpeech(new Bundle()); act.dict.cancel();
    assertFalse(act.dict.busy()); SpeechRecognizer before=sr();
    ctl.newIntent(new Intent(app,MainActivity.class)); assertTrue(act.dict.listening());
  }

  // ===================== 받아 적기 =====================
  @Test public void speechIsSavedAfterSilence() throws Exception {
    launch(); ssr().triggerOnReadyForSpeech(new Bundle());
    say("내일"); say("내일 아침 9시에"); finalText("내일 아침 9시에 알려줘");
    assertEquals("한 마디 뒤에도 바로 다시 듣기",true,act.dict.listening());
    idle(100); // 인식기가 다음 듣기를 시작
    idle(2600); // 말이 끊김
    idle(1700); // 마무리 기다림
    assertFalse(act.dict.busy());
    List<Note> l=notes(); assertEquals(1,l.size()); assertEquals("내일 아침 9시에 알려줘",l.get(0).text); assertEquals(Note.SPEECH,l.get(0).kind);
    JSONObject s=last("saved"); assertNotNull(s); assertEquals("내일 아침 9시에 알려줘",s.optString("text")); assertFalse(s.optBoolean("cont"));
    assertTrue("때 말이 있으면 자동으로 알림",s.has("remind")); assertTrue(s.getJSONObject("remind").optBoolean("ok"));
    assertTrue(s.getJSONObject("remind").optString("line").contains("오전 9:00 알림 맞춤"));
    assertEquals(1,count("saved")); assertTrue(notes().get(0).remindAt>0);
  }
  @Test public void shortPausesKeepListeningAndTextIsJoined(){
    launch(); ssr().triggerOnReadyForSpeech(new Bundle());
    say("우유 사고"); finalText("우유 사고");
    idle(100);
    assertTrue(act.dict.listening());
    ssr().triggerOnError(SpeechRecognizer.ERROR_NO_MATCH); // 쉬는 동안 인식기가 한 번 끝나도
    idle(1000);
    assertTrue("2.5초 안 쉬었으니 계속 들어요",act.dict.listening()); assertEquals(0,notes().size());
    say("계란도"); finalText("계란도 사기");
    idle(100); idle(2600); idle(1700);
    assertEquals(1,notes().size()); assertEquals("우유 사고 계란도 사기",notes().get(0).text);
  }
  @Test public void stopByTapSavesRightAwayWithFinalResult(){
    launch(); ssr().triggerOnReadyForSpeech(new Bundle()); say("지금 저장해줘");
    act.br.stop(); idle(10);
    assertEquals(Dictation.STOPPING,act.dict.state);
    finalText("지금 저장해줘 제발"); // stopListening 뒤 마지막 결과
    assertFalse(act.dict.busy()); assertEquals("지금 저장해줘 제발",notes().get(0).text);
  }
  @Test public void stopByTapWithoutFinalResultFallsBackToPartialAfterDeadline(){
    launch(); ssr().triggerOnReadyForSpeech(new Bundle()); say("부분 글만 있어요");
    act.br.stop(); idle(1000); assertTrue(act.dict.busy()); idle(800);
    assertFalse(act.dict.busy()); assertEquals("부분 글만 있어요",notes().get(0).text);
  }
  @Test public void nothingSaidSavesNothing(){
    launch(); ssr().triggerOnReadyForSpeech(new Bundle());
    ssr().triggerOnError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT); idle(100);
    ssr().triggerOnError(SpeechRecognizer.ERROR_NO_MATCH);
    idle(7500); idle(1800);
    assertFalse(act.dict.busy()); assertEquals(0,notes().size()); assertNotNull(last("none"));
  }
  @Test public void leavingScreenSavesWhatWasHeard(){
    launch(); ssr().triggerOnReadyForSpeech(new Bundle()); say("나가면서도 저장돼요");
    ctl.pause().stop(); idle(10);
    assertEquals(1,notes().size()); assertEquals("나가면서도 저장돼요",notes().get(0).text);
  }
  @Test public void sixtySecondsSavesAndAutoContinues(){
    launch(); ssr().triggerOnReadyForSpeech(new Bundle());
    say("첫 덩이 이야기"); finalText("첫 덩이 이야기");
    for(int k=0;k<60;k++){
      idle(1000);
      if(sr()!=null&&act.dict.state==Dictation.LISTENING){ say("계속 말해요 "+k); if(k%3==0) finalText("계속 말해요 "+k); }
      if(!notes().isEmpty()) break;
    }
    idle(1700);
    assertTrue("60초에 첫 메모가 저장돼야 해요",notes().size()>=1);
    assertTrue(last("saved").optBoolean("cont")); assertEquals(0,last("saved").optInt("seg"));
    assertTrue("이어서 듣기",act.dict.busy()); assertEquals(1,act.dict.seg);
    assertTrue(notes().get(notes().size()-1).text.startsWith("첫 덩이 이야기"));
  }
  @Test public void segmentsAreCappedAtFive(){
    assertEquals(5,ListenPolicy.MAX_SEGMENTS);
  }
  @Test public void customSilenceFromSettings(){
    app.getSharedPreferences("memo",0).edit().putLong("silenceMs",1500).commit();
    launch(); assertEquals(1500L,ssr().getLastRecognizerIntent().getLongExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,0));
    ssr().triggerOnReadyForSpeech(new Bundle()); say("짧게"); finalText("짧게"); idle(100); idle(1700); idle(1700);
    assertEquals(1,notes().size());
  }

  // ===================== 받아쓰기를 못 쓸 때 =====================
  @Test public void networkErrorFallsBackToAudioWithNotice(){
    launch(); ssr().triggerOnReadyForSpeech(new Bundle());
    ssr().triggerOnError(SpeechRecognizer.ERROR_NETWORK);
    assertTrue(act.dict.audioMode); assertEquals("audio",last("status").optString("state"));
    assertEquals("fallback-audio",last("notice").optString("code")); assertEquals("net",last("notice").optString("msg"));
    AudioCapture.testAmp=5000; idle(500); AudioCapture.testAmp=0; idle(3000); idle(200);
    assertEquals(1,notes().size()); assertEquals(Note.AUDIO,notes().get(0).kind);
  }
  @Test public void networkErrorAfterSomeTextKeepsTheText(){
    launch(); ssr().triggerOnReadyForSpeech(new Bundle()); say("거기까지 들은 말"); finalText("거기까지 들은 말");
    ssr().triggerOnError(SpeechRecognizer.ERROR_SERVER);
    AudioCapture.testAmp=0; idle(3000); idle(200);
    assertEquals(1,notes().size()); assertEquals("거기까지 들은 말",notes().get(0).text);
  }
  @Test public void onDeviceMissingLanguagePackFallsBackToCloudRecognizer(){
    ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true);
    launch(); SpeechRecognizer first=sr(); assertTrue(act.dict.onDevice);
    ssr().triggerOnError(12); idle(200);
    assertTrue("다시 시도",act.dict.listening()); assertFalse("일반 인식기로 바꿈",act.dict.onDevice);
  }
  @Test public void recognizerBusyRetriesThenFallsBack(){
    launch(); ssr().triggerOnReadyForSpeech(new Bundle());
    ssr().triggerOnError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY); idle(500); assertTrue(act.dict.listening()); assertFalse(act.dict.audioMode);
    ssr().triggerOnError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY); idle(500);
    ssr().triggerOnError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY); idle(100);
    assertTrue(act.dict.audioMode);
  }
  @Test public void consentErrorShowsHintAndRestartsWhenBack(){
    launch();
    for(int k=0;k<3;k++){ ssr().triggerOnError(SpeechRecognizer.ERROR_CLIENT); idle(400); }
    JSONObject c=null; for(JSONObject o:act.log) if("consent".equals(o.optString("code"))) c=o; assertNotNull(c); assertTrue(c.optString("msg").contains("Google"));
    assertTrue(act.restartOnResume);
  }
  @Test public void insufficientPermissionWithoutMicAsksAgain(){
    launch(); shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO);
    ssr().triggerOnError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS);
    assertNotNull(last("needmic")); assertFalse(act.dict.busy());
  }
  @Test public void keepAudioSettingRecordsWhileTranscribing(){
    app.getSharedPreferences("memo",0).edit().putBoolean("keepAudio",true).commit();
    launch(); ssr().triggerOnReadyForSpeech(new Bundle()); say("소리도 같이"); finalText("소리도 같이");
    idle(100); idle(2700); idle(1700);
    List<Note> l=notes(); assertEquals(1,l.size()); assertEquals(Note.SPEECH,l.get(0).kind); assertFalse("m4a 도 같이 보관",l.get(0).audio.isEmpty());
  }
  @Test public void audioIsNotKeptByDefault(){
    launch(); ssr().triggerOnReadyForSpeech(new Bundle()); say("글만"); finalText("글만");
    idle(100); idle(2700); idle(1700);
    assertEquals("",notes().get(0).audio);
  }
  @Test public void audioConflictDropsAudioAndKeepsTranscribing(){
    app.getSharedPreferences("memo",0).edit().putBoolean("keepAudio",true).commit();
    launch(); ssr().triggerOnReadyForSpeech(new Bundle());
    ssr().triggerOnError(SpeechRecognizer.ERROR_AUDIO); idle(400);
    assertFalse(act.dict.audioMode); assertEquals("audio-conflict",last("notice").optString("code")); assertTrue(act.dict.listening());
  }

  // ===================== 시작음 줄이기 =====================
  @Test public void mediaVolumeMutedDuringDictationAndRestoredAfter(){
    AudioManager am=(AudioManager)app.getSystemService(Context.AUDIO_SERVICE);
    launch(); assertTrue(am.isStreamMute(AudioManager.STREAM_MUSIC)); assertTrue(act.prefs.muted());
    ssr().triggerOnReadyForSpeech(new Bundle()); act.dict.cancel(); idle(1000);
    assertFalse("끝나면 되돌려요",am.isStreamMute(AudioManager.STREAM_MUSIC)); assertFalse(act.prefs.muted());
  }
  @Test public void staleMuteIsRecoveredOnNextLaunch(){
    AudioManager am=(AudioManager)app.getSystemService(Context.AUDIO_SERVICE);
    am.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_MUTE,0);
    app.getSharedPreferences("memo",0).edit().putBoolean("muted",true).putBoolean("quiet",false).commit();
    launch(); assertFalse(am.isStreamMute(AudioManager.STREAM_MUSIC));
  }
  @Test public void userMutedMusicIsLeftAlone(){
    AudioManager am=(AudioManager)app.getSystemService(Context.AUDIO_SERVICE);
    am.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_MUTE,0);
    launch(); act.dict.cancel(); idle(1000);
    assertTrue("원래 음소거였으면 그대로",am.isStreamMute(AudioManager.STREAM_MUSIC));
  }

  // ===================== 알림: 저장할 때 자동 =====================
  AlarmManager alarms(){ return (AlarmManager)app.getSystemService(Context.ALARM_SERVICE); }
  List<ShadowAlarmManager.ScheduledAlarm> sched(){ return shadowOf(alarms()).getScheduledAlarms(); }
  String saveSpoken(String text){
    ssr().triggerOnReadyForSpeech(new Bundle()); say(text); finalText(text); idle(100); idle(2700); idle(1700);
    return String.valueOf(notes().get(0).id);
  }
  @Test public void autoSetsReminderOnSaveAndKeepsMemoOnCancel() throws Exception {
    launch(); String id=saveSpoken("내일 아침 9시에 알려줘");
    JSONObject s=last("saved"); assertTrue(s.has("remind"));
    JSONObject rem=s.getJSONObject("remind"); assertTrue(rem.toString(),rem.getBoolean("ok")); assertEquals("exact",rem.getString("state"));
    assertTrue(rem.getString("line").contains("오전 9:00 알림 맞춤"));
    assertEquals(1,sched().size()); assertTrue(notes().get(0).remindAt>0);
    // 취소하면 알림만 사라지고 메모는 남아요
    JSONObject u=new JSONObject(act.br.unremind(id)); assertTrue(u.getBoolean("ok"));
    assertEquals(0,sched().size()); assertEquals(0,notes().get(0).remindAt); assertEquals(1,notes().size());
  }
  @Test public void autoSchedulesExactAlarmAtTheRightTime() throws Exception {
    launch(); String id=saveSpoken("내일 9시에 알려줘");
    assertEquals(1,sched().size());
    Note n=notes().get(0);
    Calendar c=Calendar.getInstance(); c.setTimeInMillis(n.createdAt); c.add(Calendar.DATE,1); c.set(Calendar.HOUR_OF_DAY,9); c.set(Calendar.MINUTE,0); c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0);
    assertEquals(c.getTimeInMillis(),sched().get(0).getTriggerAtMs()); assertEquals(c.getTimeInMillis(),n.remindAt); assertTrue(n.remindExact);
  }
  @Test public void relativeThirtyMinutesAutoSet() throws Exception {
    launch(); saveSpoken("30분 뒤에 약 먹기");
    Note n0=notes().get(0);
    assertEquals(n0.createdAt+30*60000L,n0.remindAt);
    assertTrue(last("saved").getJSONObject("remind").getBoolean("ok"));
  }
  @Test public void withoutExactPermissionFallsBackToInexactAutomatically() throws Exception {
    ShadowAlarmManager.setCanScheduleExactAlarms(false);
    launch(); saveSpoken("목요일 2시에 병원");
    JSONObject rem=last("saved").getJSONObject("remind");
    assertTrue(rem.toString(),rem.getBoolean("ok")); assertEquals("inexact",rem.getString("state"));
    assertTrue(rem.getString("line").contains("몇 분 어긋날 수 있어요"));
    assertEquals(1,sched().size()); assertFalse(notes().get(0).remindExact);
  }
  @Test public void withoutNotificationPermissionAsksFirstThenSchedulesAfterGrant() throws Exception {
    shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
    launch(); String id=saveSpoken("내일 9시에 알려줘");
    JSONObject rem=last("saved").getJSONObject("remind");
    assertEquals("need-notif",rem.getString("state"));
    assertEquals(1,sched().size()); // 표시 권한이 없어도 알람은 걸어요
    assertTrue(notes().get(0).remindAt>0);
    assertTrue(rem.getString("line").contains("알람은 맞춰 두었어요"));
    assertEquals(Manifest.permission.POST_NOTIFICATIONS,shadowOf(act).getLastRequestedPermission().requestedPermissions[0]);
    shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
    act.pendingRemind=notes().get(0).id;
    JSONObject after=act.br.remindJson(notes().get(0).id,true,true);
    assertTrue(after.toString(),after.getBoolean("ok"));
    assertEquals("exact",after.getString("state"));
    assertEquals(1,sched().size());
  }
  @Test public void notificationDeniedDoesNotSchedule() throws Exception {
    shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
    launch(); saveSpoken("내일 9시에 알려줘");
    act.onRequestPermissionsResult(MainActivity.REQ_NOTIF,new String[]{Manifest.permission.POST_NOTIFICATIONS},new int[]{-1});
    assertEquals(1,sched().size()); assertEquals("notif-denied",last("remind").getString("state"));
    assertTrue(notes().get(0).remindAt>0);
  }
  @Test public void lockedDoesNotRequestNotificationPermission() throws Exception {
    shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
    locked=true;
    launch(); saveSpoken("내일 9시에 알려줘");
    JSONObject rem=last("saved").getJSONObject("remind");
    assertEquals("need-notif",rem.getString("state"));
    assertEquals(1,sched().size());
    assertFalse(rem.optBoolean("askSettings"));
    assertNull("잠금 화면에서는 권한 창을 띄우지 않아요",shadowOf(act).getLastRequestedPermission());
    assertEquals(0,unlockAsked);
  }
  @Test public void pastTimeIsNotScheduled() throws Exception {
    Note n=note("오늘 오후 2시에 알려줘",System.currentTimeMillis());
    Calendar c=Calendar.getInstance(); c.add(Calendar.HOUR_OF_DAY,-5); n.createdAt=c.getTimeInMillis();
    n.text="30분 뒤에 알려줘"; new NoteStore(app.getFilesDir()).add(n);
    launch(); act.recent.add(n.id);
    JSONObject r=new JSONObject(act.br.remind(String.valueOf(n.id),false)); assertEquals("past",r.getString("state")); assertEquals(0,sched().size());
  }
  @Test public void deletingNoteRemovesItsAlarm() throws Exception {
    launch(); String id=saveSpoken("내일 9시에 알려줘"); assertEquals(1,sched().size());
    new JSONObject(act.br.del(id)); assertEquals(0,sched().size()); assertEquals(0,notes().size());
  }
  @Test public void noTimePhraseNoReminder() throws Exception {
    launch(); saveSpoken("우유랑 계란 사기");
    JSONObject rem=last("saved").getJSONObject("remind");
    assertEquals("notime",rem.getString("state"));
    assertEquals("알림은 맞추지 않았어요",rem.getString("line"));
    assertEquals(0,notes().get(0).remindAt);
    assertEquals(0,sched().size());
    assertEquals("notime",new JSONObject(act.br.remind(String.valueOf(notes().get(0).id),false)).getString("state"));
  }
  @Test public void lockScreenCanCancelJustSavedAlarm() throws Exception {
    launch(); String id=saveSpoken("내일 9시에 알려줘"); assertEquals(1,sched().size());
    locked=true;
    JSONObject u=new JSONObject(act.br.unremind(id)); assertTrue(u.getBoolean("ok"));
    assertEquals(0,sched().size()); assertEquals(0,notes().get(0).remindAt);
  }
  @Test public void usesAlarmClockAndParsesTenSeconds() throws Exception {
    launch(); saveSpoken("10초 뒤에 알람");
    assertEquals("setAlarmClock",Reminders.lastMethod);
    assertEquals(1,sched().size());
    Note n=notes().get(0);
    assertEquals(n.createdAt+10000L,n.remindAt);
    org.json.JSONArray log=AlarmLog.list(app);
    assertEquals(1,log.length());
    assertEquals("setAlarmClock",log.getJSONObject(0).optString("method"));
    assertEquals("10초 뒤에 알람",log.getJSONObject(0).optString("text"));
  }
  @Test public void realPhoneSentencesScheduleExceptGwanggaeto() throws Exception {
    launch();
    for(String t:new String[]{"내일 아침 9시에 깨워 줘","내일 아침 10시에 깨워 줘","내일 10시에 나한테 전화하라고 말해 줘","10초 후에 알람","10초 뒤에 알람"}){
      int before=sched().size();
      if(!act.dict.listening()){ act.startDictation(); idle(80); }
      ssr().triggerOnReadyForSpeech(new android.os.Bundle()); say(t); finalText(t); idle(100); idle(2700); idle(1700);
      assertEquals(t,before+1,sched().size());
    }
    int n=sched().size();
    if(!act.dict.listening()){ act.startDictation(); idle(80); }
    ssr().triggerOnReadyForSpeech(new android.os.Bundle()); say("광개토대왕"); finalText("광개토대왕"); idle(100); idle(2700); idle(1700);
    assertEquals(n,sched().size());
    assertEquals("notime",last("saved").getJSONObject("remind").getString("state"));
  }
  @Test public void rmsNoiseDoesNotBlockSilenceSave() throws Exception {
    launch(); ssr().triggerOnReadyForSpeech(new android.os.Bundle());
    say("10초 뒤에 알람"); finalText("10초 뒤에 알람");
    for(int k=0;k<15;k++){ ssr().triggerOnRmsChanged(8f); idle(200); }
    idle(1800);
    assertFalse(act.dict.busy());
    assertEquals(1,notes().size());
    assertEquals("10초 뒤에 알람",notes().get(0).text);
    assertTrue(notes().get(0).remindAt>0);
  }
  @Test public void endOfSpeechSavesAndSchedules() throws Exception {
    launch(); ssr().triggerOnReadyForSpeech(new android.os.Bundle());
    say("10초 뒤에 알람");
    ssr().triggerOnEndOfSpeech();
    idle(1400); idle(1700);
    assertFalse(act.dict.busy());
    assertEquals(1,notes().size());
    assertEquals("10초 뒤에 알람",notes().get(0).text);
    assertTrue(notes().get(0).remindAt>0);
  }
  @Test public void manualSaveSchedulesAlarm() throws Exception {
    launch(); ssr().triggerOnReadyForSpeech(new android.os.Bundle()); say("내일 아침 10시에 깨워 줘");
    act.br.stop(); idle(10);
    finalText("내일 아침 10시에 깨워 줘");
    assertFalse(act.dict.busy());
    assertEquals("내일 아침 10시에 깨워 줘",notes().get(0).text);
    assertTrue(notes().get(0).remindAt>0);
    assertEquals("setAlarmClock",Reminders.lastMethod);
    assertEquals("saved",act.dstate);
  }
  @Test public void firstOpenAsksNotificationWhenUnlocked(){
    shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
    launch();
    assertEquals(Manifest.permission.POST_NOTIFICATIONS,shadowOf(act).getLastRequestedPermission().requestedPermissions[0]);
    assertTrue(act.prefs.askedNotif());
  }

  // ===================== 비행기 모드·잠금 화면: 클라우드 인식기 금지 =====================
  void setAirplane(boolean on){
    Settings.Global.putInt(app.getContentResolver(),Settings.Global.AIRPLANE_MODE_ON,on?1:0);
  }
  void setKeyguard(boolean on){
    KeyguardManager km=(KeyguardManager)app.getSystemService(Context.KEYGUARD_SERVICE);
    shadowOf(km).setKeyguardLocked(on);
  }
  @Test public void airplaneWithoutOnDeviceSavesAudioAndDoesNotAskUnlock(){
    setAirplane(true);
    ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(false);
    launch();
    assertTrue(act.dict.audioMode);
    assertEquals("audio",last("status").optString("state"));
    assertEquals("no-offline",last("notice").optString("code"));
    assertTrue(last("notice").optString("msg").contains("언어팩"));
    assertEquals(0,unlockAsked);
    assertNull("클라우드 인식기를 시작하면 안 돼요",sr());
  }
  @Test public void lockedWithoutOnDeviceSavesAudioAndDoesNotAskUnlock(){
    setKeyguard(true); locked=true;
    ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(false);
    launch();
    assertTrue(act.dict.audioMode);
    assertEquals("no-offline",last("notice").optString("code"));
    assertEquals(0,unlockAsked);
    assertNull(sr());
  }
  @Test public void onDeviceMissingLanguagePackOnLockGoesToAudioNotCloud(){
    setKeyguard(true); locked=true;
    ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true);
    launch();
    assertTrue(act.dict.onDevice); assertFalse(act.dict.audioMode);
    ssr().triggerOnError(12); idle(200);
    assertTrue(act.dict.audioMode);
    assertEquals("no-offline",last("notice").optString("code"));
    assertFalse("클라우드로 바꾸면 안 돼요",act.dict.onDevice&&!act.dict.audioMode);
    assertEquals(0,unlockAsked);
  }
  @Test public void networkErrorOnLockDoesNotAskUnlock(){
    setKeyguard(true); locked=true;
    ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true);
    launch(); ssr().triggerOnReadyForSpeech(new Bundle());
    ssr().triggerOnError(SpeechRecognizer.ERROR_NETWORK);
    assertTrue(act.dict.audioMode);
    assertEquals(0,unlockAsked);
  }

  @Test public void receiverPostsPrivateNotificationOnce() throws Exception {
    NoteStore s=new NoteStore(app.getFilesDir()); Note n=note("약 먹기",System.currentTimeMillis()); n.remindAt=System.currentTimeMillis()+1000; s.add(n); 
    Intent i=new Intent(Reminders.ACTION).putExtra(Reminders.EXTRA_ID,n.id);
    new ReminderReceiver().onReceive(app,i);
    NotificationManager nm=(NotificationManager)app.getSystemService(Context.NOTIFICATION_SERVICE);
    List<Notification> l=shadowOf(nm).getAllNotifications(); assertEquals(1,l.size());
    Notification x=l.get(0);
    assertEquals("약 먹기",x.extras.getString(Notification.EXTRA_TEXT)); assertEquals(Notification.VISIBILITY_PRIVATE,x.visibility);
    assertNotNull("잠금 화면용 공개 버전(내용 숨김)",x.publicVersion); assertFalse(String.valueOf(x.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT)).contains("약"));
    assertTrue(s.get(n.id).reminded);
    new ReminderReceiver().onReceive(app,i); assertEquals("두 번 울리면 안 돼요",1,shadowOf(nm).getAllNotifications().size());
  }
  @Test public void receiverIgnoresDeletedOrCancelledNote() throws Exception {
    NoteStore s=new NoteStore(app.getFilesDir());
    new ReminderReceiver().onReceive(app,new Intent(Reminders.ACTION).putExtra(Reminders.EXTRA_ID,12345L));
    Note n=note("취소한 알림",System.currentTimeMillis()); s.add(n); // remindAt=0
    new ReminderReceiver().onReceive(app,new Intent(Reminders.ACTION).putExtra(Reminders.EXTRA_ID,n.id));
    assertEquals(0,shadowOf((NotificationManager)app.getSystemService(Context.NOTIFICATION_SERVICE)).getAllNotifications().size());
  }
  @Test public void bootRearmsFutureAndPostsMissed() throws Exception {
    NoteStore s=new NoteStore(app.getFilesDir()); long now=System.currentTimeMillis();
    Note f=note("미래 알림",now); f.remindAt=now+3600000; s.add(f);
    Note p=note("지난 알림",now-7200000); p.remindAt=now-60000; s.add(p);
    new BootReceiver().onReceive(app,new Intent(Intent.ACTION_BOOT_COMPLETED));
    assertEquals(1,sched().size()); assertEquals(f.remindAt,sched().get(0).getTriggerAtMs());
    List<Notification> l=shadowOf((NotificationManager)app.getSystemService(Context.NOTIFICATION_SERVICE)).getAllNotifications();
    assertEquals(1,l.size()); assertTrue(String.valueOf(l.get(0).extras.getCharSequence(Notification.EXTRA_TITLE)).contains("놓친"));
    assertTrue(s.get(p.id).reminded);
  }

  // ===================== 알림을 눌러 열기 =====================
  @Test public void openedFromNotificationDoesNotListenAndAsksUnlock(){
    Note n=new NoteStore(app.getFilesDir()).add(note("알림 메모",1000));
    launch(new Intent(app,MainActivity.class).putExtra(MainActivity.EXTRA_NOTE,n.id));
    assertNull(sr()); assertFalse(act.dict.busy());
    act.onPageReady(); assertEquals(1,unlockAsked); assertEquals("note",act.view);
    assertEquals(String.valueOf(n.id),last("unlocked").optString("note"));
  }
}
