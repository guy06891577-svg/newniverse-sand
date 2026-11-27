package kr.newniverse.phone;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.service.notification.StatusBarNotification;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.*;

/** 상황 모드 끝에서 끝까지: 방해 금지 켜고 되돌리기 · 자동 답장 규칙 · 시간 끝 · 전화 이어붙이기 · 끝난 뒤 요약 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ModeRoboTest {
  Application app; NotificationManager nm; long t0=1_700_000_000_000L; long[] clk={t0};
  static final List<Object[]> CALLS=new ArrayList<>(), SMS=new ArrayList<>(); static final Map<String,Object[]> CONTACTS=new HashMap<>();

  /** 통화 기록 가짜 저장소: 선택 조건(시작·끝·종류 둘)을 코드에서 직접 걸러 줌 */
  public static class CallProv extends ContentProvider {
    @Override public boolean onCreate(){ return true; }
    @Override public Cursor query(Uri u,String[] p,String sel,String[] a,String o){
      MatrixCursor c=new MatrixCursor(new String[]{"number","name","date","pres","type"});
      for(Object[] r:CALLS){ long d=(Long)r[2]; int ty=(Integer)r[4];
        if(a!=null&&a.length>=4&&!(d>=Long.parseLong(a[0])&&d<=Long.parseLong(a[1])&&(ty==Integer.parseInt(a[2])||ty==Integer.parseInt(a[3])))) continue; c.addRow(r); }
      // 요청한 열 순서(NUMBER, CACHED_NAME, DATE, NUMBER_PRESENTATION)에 맞춤
      MatrixCursor out=new MatrixCursor(new String[]{"number","name","date","pres"});
      c.moveToPosition(-1); while(c.moveToNext()) out.addRow(new Object[]{c.getString(0),c.getString(1),c.getLong(2),c.getInt(3)});
      return out; }
    @Override public String getType(Uri u){ return null; } @Override public Uri insert(Uri u,ContentValues v){ return null; }
    @Override public int delete(Uri u,String s,String[] a){ return 0; } @Override public int update(Uri u,ContentValues v,String s,String[] a){ return 0; }
  }
  public static class SmsProv extends ContentProvider {
    @Override public boolean onCreate(){ return true; }
    @Override public Cursor query(Uri u,String[] p,String sel,String[] a,String o){
      MatrixCursor c=new MatrixCursor(new String[]{"address","date","body"});
      for(Object[] r:SMS){ long d=(Long)r[1]; if(a!=null&&a.length>=2&&!(d>=Long.parseLong(a[0])&&d<=Long.parseLong(a[1]))) continue; c.addRow(r); }
      return c; }
    @Override public String getType(Uri u){ return null; } @Override public Uri insert(Uri u,ContentValues v){ return null; }
    @Override public int delete(Uri u,String s,String[] a){ return 0; } @Override public int update(Uri u,ContentValues v,String s,String[] a){ return 0; }
  }
  /** 연락처 가짜: CONTACTS[번호 끝 8자리] = {이름, 별표(0/1)} */
  public static class ContactProv extends ContentProvider {
    @Override public boolean onCreate(){ return true; }
    @Override public Cursor query(Uri u,String[] p,String sel,String[] a,String o){
      String num=Uri.decode(u.getLastPathSegment()); String d=num.replaceAll("[^0-9]",""); String k=d.length()>=8?d.substring(d.length()-8):d;
      Object[] r=CONTACTS.get(k); MatrixCursor c=new MatrixCursor(p);
      if(r!=null){ Object[] row=new Object[p.length]; for(int i=0;i<p.length;i++) row[i]=p[i].equals("display_name")?r[0]:(p[i].equals("starred")?r[1]:null); c.addRow(row); }
      return c; }
    @Override public String getType(Uri u){ return null; } @Override public Uri insert(Uri u,ContentValues v){ return null; }
    @Override public int delete(Uri u,String s,String[] a){ return 0; } @Override public int update(Uri u,ContentValues v,String s,String[] a){ return 0; }
  }

  @Before public void setUp(){
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    app=RuntimeEnvironment.getApplication(); nm=app.getSystemService(NotificationManager.class);
    ShadowAlarmManager.reset(); ShadowNotificationManager.reset(); ShadowSmsManager.reset();
    shadowOf(app).grantPermissions(Manifest.permission.SEND_SMS,Manifest.permission.READ_CALL_LOG,Manifest.permission.READ_CONTACTS,Manifest.permission.READ_SMS,Manifest.permission.POST_NOTIFICATIONS);
    shadowOf(nm).setNotificationPolicyAccessGranted(true);
    nm.setNotificationPolicy(new NotificationManager.Policy(NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES|NotificationManager.Policy.PRIORITY_CATEGORY_EVENTS,NotificationManager.Policy.PRIORITY_SENDERS_ANY,NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS,0));
    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
    app.getSharedPreferences(ModeStore.PREF,0).edit().clear().commit(); app.getSharedPreferences("nvp_ps",0).edit().clear().commit();
    CALLS.clear(); SMS.clear(); CONTACTS.clear(); clk[0]=t0; ModeManager.clock=()->clk[0];
    ModeManager.splitter=t->{ ArrayList<String> l=new ArrayList<>(); if(t.length()<=70) l.add(t); else for(int i=0;i<t.length();i+=67) l.add(t.substring(i,Math.min(t.length(),i+67))); return l; };   // 한글 문자 길이 규칙(70자 / 나뉘면 67자)
    ShadowContentResolver.registerProviderInternal("call_log",Robolectric.setupContentProvider(CallProv.class,"call_log"));
    ShadowContentResolver.registerProviderInternal("sms",Robolectric.setupContentProvider(SmsProv.class,"sms"));
    ShadowContentResolver.registerProviderInternal("com.android.contacts",Robolectric.setupContentProvider(ContactProv.class,"com.android.contacts"));
  }
  @After public void tearDown(){ ModeManager.clock=System::currentTimeMillis; ModeManager.splitter=null; }

  // ---------- 도우미 ----------
  JSONObject start(String id){ return ModeManager.start(app,id,0); }
  ShadowSmsManager sms(){ return shadowOf(app.getSystemService(SmsManager.class)); }
  String lastSmsTo(){ ShadowSmsManager.TextSmsParams p=sms().getLastSentTextMessageParams(); return p==null?null:p.getDestinationAddress(); }
  String lastSmsText(){ ShadowSmsManager.TextSmsParams p=sms().getLastSentTextMessageParams(); return p==null?null:p.getText(); }
  /** 안 받은 전화 하나를 넣고, 문자가 나갔는지 */
  boolean replied(String num){ sms().clearLastSentTextMessageParams(); sms().clearLastSentMultipartTextMessageParams(); ModeManager.onUnanswered(app,num,false); return sms().getLastSentTextMessageParams()!=null||sms().getLastSentMultipartTextMessageParams()!=null; }
  JSONObject cfg(String id){ return ModeStore.cfg(app,id); }
  void setCfg(String id,String k,Object v) throws Exception { JSONObject c=cfg(id); c.put(k,v); ModeStore.saveCfg(app,id,c); }
  int sent(){ JSONObject a=ModeStore.active(app); return a==null?-1:a.optInt("sent"); }
  int filter(){ return nm.getCurrentInterruptionFilter(); }

  // ---------- 방해 금지 ----------
  @Test public void dndOnThenRestoredToPrevious(){
    JSONObject r=start("drive");
    assertTrue(r.optBoolean("ok")); assertTrue(r.optBoolean("dnd"));
    assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY,filter());
    NotificationManager.Policy p=nm.getNotificationPolicy();
    assertTrue("알람은 항상",(p.priorityCategories&NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS)!=0);
    assertEquals("기본은 아무도 안 울림",0,p.priorityCategories&NotificationManager.Policy.PRIORITY_CATEGORY_CALLS);
    assertEquals(0,p.priorityCategories&NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES);
    assertNotNull(ModeManager.stop(app,"manual"));
    assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL,filter());
    p=nm.getNotificationPolicy();
    assertEquals("원래 설정으로",NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES|NotificationManager.Policy.PRIORITY_CATEGORY_EVENTS,p.priorityCategories);
    assertEquals(NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS,p.priorityMessageSenders);
  }
  @Test public void allowStarredAndContactsAndRepeat() throws Exception {
    setCfg("drive","allow","starred"); setCfg("drive","repeat",true);
    start("drive");
    NotificationManager.Policy p=nm.getNotificationPolicy();
    assertNotEquals(0,p.priorityCategories&NotificationManager.Policy.PRIORITY_CATEGORY_CALLS);
    assertEquals(NotificationManager.Policy.PRIORITY_SENDERS_STARRED,p.priorityCallSenders);
    assertNotEquals(0,p.priorityCategories&NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS);
    ModeManager.stop(app,"manual");
    setCfg("drive","allow","contacts"); setCfg("drive","repeat",false);
    start("drive"); p=nm.getNotificationPolicy();
    assertEquals(NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS,p.priorityCallSenders);
    assertEquals(0,p.priorityCategories&NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS);
  }
  @Test public void changingAllowWhileOnIsAppliedAndStillRestoresOriginal() throws Exception {
    start("drive");
    Bridge b=new Bridge(Robolectric.buildActivity(MainActivity.class).create().get());
    JSONObject c=cfg("drive"); c.put("allow","starred");
    b.modeSave("drive",c.toString());
    assertEquals(NotificationManager.Policy.PRIORITY_SENDERS_STARRED,nm.getNotificationPolicy().priorityCallSenders);
    ModeManager.stop(app,"manual");
    assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL,filter());
    assertEquals(NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES|NotificationManager.Policy.PRIORITY_CATEGORY_EVENTS,nm.getNotificationPolicy().priorityCategories);
  }
  @Test public void withoutDndAccessModeStillStartsAndSaysSo(){
    shadowOf(nm).setNotificationPolicyAccessGranted(false);
    JSONObject r=start("drive");
    assertTrue(r.optBoolean("ok")); assertFalse(r.optBoolean("dnd"));
    assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL,filter());
    assertTrue(replied("010-1234-5678"));      // 자동 답장은 방해 금지와 상관없이 됨
    ModeManager.stop(app,"manual"); assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL,filter());
  }
  @Test public void strictDndAlreadyOnIsLeftAlone(){
    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
    JSONObject r=start("drive");
    assertTrue(r.optBoolean("keep")); assertEquals(NotificationManager.INTERRUPTION_FILTER_NONE,filter());
    ModeManager.stop(app,"manual"); assertEquals("끝나도 원래 엄격한 설정 그대로",NotificationManager.INTERRUPTION_FILTER_NONE,filter());
  }
  @Test public void userTurnedDndOffMidwayIsRespected(){
    start("drive"); nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
    ModeManager.stop(app,"manual"); assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL,filter());
  }
  @Test public void priorityDndWasAlreadyOnIsRestoredToThatPolicy(){
    nm.setNotificationPolicy(new NotificationManager.Policy(NotificationManager.Policy.PRIORITY_CATEGORY_CALLS,NotificationManager.Policy.PRIORITY_SENDERS_STARRED,NotificationManager.Policy.PRIORITY_SENDERS_ANY,0));
    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
    start("drive"); ModeManager.stop(app,"manual");
    assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY,filter());
    assertEquals(NotificationManager.Policy.PRIORITY_CATEGORY_CALLS,nm.getNotificationPolicy().priorityCategories);
  }
  @Test public void switchingModesRestoresBeforeApplyingNew(){
    start("drive"); start("meeting");
    assertEquals("meeting",ModeStore.active(app).optString("id"));
    ModeManager.stop(app,"manual");
    assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL,filter());
    assertEquals(NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES|NotificationManager.Policy.PRIORITY_CATEGORY_EVENTS,nm.getNotificationPolicy().priorityCategories);
  }

  // ---------- 자동 답장 ----------
  @Test public void repliesOncePerNumberWithEditableText() throws Exception {
    setCfg("drive","text","운전 중이에요. 내려서 전화드릴게요.");
    start("drive");
    assertTrue(replied("010-1234-5678"));
    assertEquals("010-1234-5678",lastSmsTo()); assertEquals("운전 중이에요. 내려서 전화드릴게요.",lastSmsText());
    assertFalse("같은 번호는 한 번만",replied("010-1234-5678"));
    assertFalse("다른 모양으로 써도 한 번만",replied("+82 10 1234 5678"));
    assertTrue(replied("010-9999-0000"));
    assertEquals(2,sent());
  }
  @Test public void defaultTextIsTheOneInTheSpec(){
    start("drive"); assertTrue(replied("010-1234-5678"));
    assertEquals("지금 운전 중이라 전화를 못 받아요. 도착하면 연락드릴게요.",lastSmsText());
  }
  @Test public void newSessionRepliesAgain(){
    start("drive"); assertTrue(replied("010-1234-5678")); ModeManager.stop(app,"manual");
    start("drive"); assertTrue("새로 켜면 다시 보냄",replied("010-1234-5678"));
  }
  @Test public void neverRepliesToBusinessShortOrHiddenEvenIfOtherSettingOn() throws Exception {
    start("drive");
    for(String n:new String[]{"1588-1234","1644-0000","1833-1234","114","119","080-111-2222","060-700-1234","","-2","KB국민카드","02-123-4567","070-1111-2222","+1 415 555 0123"})
      assertFalse(n,replied(n));
    assertEquals(0,sent());
    ModeManager.stop(app,"manual");
    setCfg("drive","other",true); start("drive");
    for(String n:new String[]{"1588-1234","1644-0000","114","080-111-2222","","-2","KB국민카드"}) assertFalse("설정을 켜도 안 보냄: "+n,replied(n));
    assertTrue(replied("02-123-4567")); assertTrue(replied("070-1111-2222")); assertTrue(replied("+1 415 555 0123"));
  }
  @Test public void skipReasonsAreCounted(){
    start("drive"); replied("1588-1234"); replied("1644-0000"); replied("02-123-4567"); replied("010-1111-2222"); replied("010-1111-2222");
    JSONObject sk=ModeStore.active(app).optJSONObject("skips");
    assertEquals(2,sk.optInt("기업 대표번호")); assertEquals(1,sk.optInt("일반 전화")); assertEquals(1,sk.optInt("이미 보낸 번호"));
  }
  @Test public void answeredOrRejectedWithMessageGetsNoReply(){
    start("drive"); sms().clearLastSentTextMessageParams();
    ModeManager.onUnanswered(app,"010-1234-5678",true); assertNull(sms().getLastSentTextMessageParams());
    assertTrue("그다음 진짜 부재중이면 보냄",replied("010-1234-5678"));
  }
  @Test public void replyOffMeansNoText() throws Exception {
    setCfg("drive","reply",false); start("drive"); assertFalse(replied("010-1234-5678")); assertEquals(0,sent());
  }
  @Test public void clearedTextMeansNoReply() throws Exception {
    setCfg("drive","text",""); start("drive"); assertFalse(replied("010-1234-5678"));
  }
  @Test public void contactsOnlyOption() throws Exception {
    CONTACTS.put("12345678",new Object[]{"김사장",0});
    setCfg("drive","contactsOnly",true); start("drive");
    assertFalse(replied("010-9999-0000")); assertTrue(replied("010-1234-5678"));
  }
  @Test public void noModeNoReply(){ assertFalse(replied("010-1234-5678")); ModeManager.onPhoneState(app,"RINGING","010-1234-5678"); }
  @Test public void noSendSmsPermissionIsReportedNotSilent(){
    shadowOf(app).denyPermissions(Manifest.permission.SEND_SMS);
    start("drive"); assertFalse(replied("010-1234-5678"));
    assertEquals(1,ModeStore.active(app).optJSONObject("skips").optInt("문자 보내기 허용이 없음"));
    assertEquals(0,sent());
  }
  @Test public void capStopsRunawaySending(){
    start("drive");
    for(int i=0;i<ModeRules.MAX_REPLIES;i++) assertTrue("#"+i,replied(String.format("010-%04d-%04d",1000+i,2000+i)));
    assertFalse("상한을 넘으면 안 보냄",replied("010-7777-8888"));
    assertEquals(ModeRules.MAX_REPLIES,sent());
  }
  @Test public void longTextGoesAsMultipart() throws Exception {
    StringBuilder sb=new StringBuilder(); for(int i=0;i<100;i++) sb.append("가");
    setCfg("drive","text",sb.toString()); start("drive");
    assertTrue(replied("010-1234-5678")); assertNotNull(sms().getLastSentMultipartTextMessageParams()); assertEquals(2,sms().getLastSentMultipartTextMessageParams().getParts().size());
  }
  @Test public void sentResultMarksLog(){
    start("drive"); replied("010-1234-5678");
    JSONArray log=ModeStore.active(app).optJSONArray("log"); assertEquals("wait",log.optJSONObject(0).optString("st"));
    new ModeActionReceiver().onReceive(app,new Intent(ModeManager.ACT_SMS_SENT).putExtra("k","01012345678"));
    // 결과 코드가 OK(-1)가 아니면 실패 — 시험 환경의 기본 결과 코드는 0
    assertEquals("fail",ModeStore.active(app).optJSONArray("log").optJSONObject(0).optString("st"));
    ModeStore.markSent(app,"01012345678",true);
    assertEquals("실패 뒤에 성공 신호가 와도 되살리지 않음","fail",ModeStore.active(app).optJSONArray("log").optJSONObject(0).optString("st"));
  }
  @Test public void sentOkMarksOk(){
    start("drive"); replied("010-1234-5678"); ModeStore.markSent(app,"01012345678",true);
    assertEquals("ok",ModeStore.active(app).optJSONArray("log").optJSONObject(0).optString("st"));
  }

  // ---------- 전화 이어붙이기(CallTracker) ----------
  @Test public void trackerUnansweredCallTriggersReply(){
    start("drive"); sms().clearLastSentTextMessageParams();
    CallTracker t=new CallTracker(); Object call=new Object();
    t.added(app,call,"010-1234-5678",true,false); t.removed(app,call);
    assertEquals("010-1234-5678",lastSmsTo());
  }
  @Test public void trackerAnsweredCallGetsNothing(){
    start("drive"); sms().clearLastSentTextMessageParams();
    CallTracker t=new CallTracker(); Object call=new Object();
    t.added(app,call,"010-1234-5678",true,false); t.active(call); t.removed(app,call);
    assertNull(lastSmsTo());
  }
  @Test public void trackerRejectedWithMessageGetsNothingMore(){
    start("drive"); sms().clearLastSentTextMessageParams();
    CallTracker t=new CallTracker(); Object call=new Object();
    t.added(app,call,"010-1234-5678",true,false); t.rejectedWithMessage(call); t.removed(app,call);
    assertNull(lastSmsTo());
  }
  @Test public void trackerOutgoingCallGetsNothing(){
    start("drive"); sms().clearLastSentTextMessageParams();
    CallTracker t=new CallTracker(); Object call=new Object();
    t.added(app,call,"010-1234-5678",false,false); t.removed(app,call);
    assertNull(lastSmsTo());
  }
  @Test public void trackerDeclinedWithoutMessageStillGetsReply(){
    start("drive"); sms().clearLastSentTextMessageParams();
    CallTracker t=new CallTracker(); Object call=new Object();
    t.added(app,call,"010-1234-5678",true,false); t.removed(app,call);   // 거절 = 안 받음
    assertEquals("010-1234-5678",lastSmsTo());
  }

  // ---------- 조용히 알릴지(quiet) ----------
  @Test public void quietDependsOnAllowList() throws Exception {
    CONTACTS.put("12345678",new Object[]{"김사장",0}); CONTACTS.put("55556666",new Object[]{"가족",1});
    start("drive"); // 아무도 안 울림
    assertTrue(ModeManager.quiet(app,"010-1234-5678")); assertTrue(ModeManager.quiet(app,"010-5555-6666")); assertTrue(ModeManager.quiet(app,"010-0000-0000"));
    ModeManager.stop(app,"manual");
    setCfg("drive","allow","starred"); start("drive");
    assertTrue(ModeManager.quiet(app,"010-1234-5678")); assertFalse("별표는 울림",ModeManager.quiet(app,"010-5555-6666")); assertTrue(ModeManager.quiet(app,"010-0000-0000"));
    ModeManager.stop(app,"manual");
    setCfg("drive","allow","contacts"); start("drive");
    assertFalse(ModeManager.quiet(app,"010-1234-5678")); assertFalse(ModeManager.quiet(app,"010-5555-6666")); assertTrue(ModeManager.quiet(app,"010-0000-0000"));
  }
  @Test public void quietFalseWhenModeOffOrDndNotApplied(){
    assertFalse(ModeManager.quiet(app,"010-1234-5678"));
    shadowOf(nm).setNotificationPolicyAccessGranted(false); start("drive");
    assertFalse("방해 금지가 안 켜졌으면 평소처럼 울림",ModeManager.quiet(app,"010-1234-5678"));
  }
  @Test public void repeatCallerWithinFifteenMinutesIsNotQuiet() throws Exception {
    setCfg("drive","repeat",true); start("drive");
    assertTrue(ModeManager.quiet(app,"010-1234-5678"));
    ModeManager.onUnanswered(app,"010-1234-5678",false);
    assertFalse("15분 안에 다시 걸면 울림",ModeManager.quiet(app,"010-1234-5678"));
    clk[0]+=16*60000L; assertTrue(ModeManager.quiet(app,"010-1234-5678"));
  }
  @Test public void strictDndKeptMeansQuiet(){ nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE); start("drive"); assertTrue(ModeManager.quiet(app,"010-1234-5678")); }

  // ---------- 전화 상태(기본 전화 앱이 아닐 때) ----------
  @Test public void phoneStateRingThenIdleIsMissed(){
    start("drive"); sms().clearLastSentTextMessageParams();
    ModeManager.onPhoneState(app,"RINGING",null); ModeManager.onPhoneState(app,"RINGING","010-1234-5678"); ModeManager.onPhoneState(app,"IDLE",null);
    assertEquals("010-1234-5678",lastSmsTo());
  }
  @Test public void phoneStateAnsweredIsNotMissed(){
    start("drive"); sms().clearLastSentTextMessageParams();
    ModeManager.onPhoneState(app,"RINGING","010-1234-5678"); ModeManager.onPhoneState(app,"OFFHOOK",null); ModeManager.onPhoneState(app,"IDLE",null);
    assertNull(lastSmsTo());
  }
  @Test public void phoneStateOutgoingCallIsIgnored(){
    start("drive"); sms().clearLastSentTextMessageParams();
    ModeManager.onPhoneState(app,"OFFHOOK",null); ModeManager.onPhoneState(app,"IDLE",null);
    assertNull(lastSmsTo());
  }
  @Test public void phoneStateWorksThroughTheReceiver(){
    start("drive"); sms().clearLastSentTextMessageParams();
    ModeSystemReceiver r=new ModeSystemReceiver();
    r.onReceive(app,new Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).putExtra(TelephonyManager.EXTRA_STATE,"RINGING").putExtra(TelephonyManager.EXTRA_INCOMING_NUMBER,"010-4444-5555"));
    r.onReceive(app,new Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).putExtra(TelephonyManager.EXTRA_STATE,"IDLE"));
    assertEquals("010-4444-5555",lastSmsTo());
  }
  @Test public void phoneStateIgnoredWhenWeAreDefaultDialer(){
    shadowOf((RoleManager)app.getSystemService(RoleManager.class)).addHeldRole(RoleManager.ROLE_DIALER);
    start("drive"); sms().clearLastSentTextMessageParams();
    ModeManager.onPhoneState(app,"RINGING","010-1234-5678"); ModeManager.onPhoneState(app,"IDLE",null);
    assertNull("기본 전화 앱이면 CallService가 맡으니 중복으로 안 보냄",lastSmsTo());
  }

  // ---------- 회의 타이머 ----------
  @Test public void meetingTimerSchedulesAndEnds() throws Exception {
    JSONObject r=ModeManager.start(app,"meeting",30);
    assertTrue(r.optBoolean("ok"));
    assertEquals(t0+30*60000L,ModeStore.active(app).optLong("end"));
    assertEquals("고른 시간은 기억해 둠",30,cfg("meeting").optInt("timer"));
    ShadowAlarmManager.ScheduledAlarm al=shadowOf((AlarmManager)app.getSystemService(Context.ALARM_SERVICE)).peekNextScheduledAlarm();
    assertNotNull("끝낼 알람이 걸림",al); assertEquals(t0+30*60000L,al.triggerAtTime);
    clk[0]+=29*60000L; ModeManager.tick(app); assertNotNull("아직 켜져 있음",ModeStore.active(app));
    clk[0]+=61000L; ModeManager.tick(app);
    assertNull(ModeStore.active(app)); assertEquals("timer",ModeStore.summary(app).optString("why"));
    assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL,filter());
  }
  @Test public void alarmReceiverEndsMeeting(){
    ModeManager.start(app,"meeting",60); clk[0]+=61*60000L;
    new ModeActionReceiver().onReceive(app,new Intent(ModeManager.ACT_END));
    assertNull(ModeStore.active(app)); assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL,filter());
  }
  @Test public void driveIgnoresTimerAndMeetingAcceptsOnlyKnownChoices(){
    ModeManager.start(app,"drive",30); assertEquals(0,ModeStore.active(app).optLong("end"));
    ModeManager.stop(app,"manual");
    ModeManager.start(app,"meeting",45); assertEquals("엉뚱한 시간은 직접 끌 때까지",0,ModeStore.active(app).optLong("end"));
  }
  @Test public void noTimerNoAlarm(){ start("drive"); assertNull(shadowOf((AlarmManager)app.getSystemService(Context.ALARM_SERVICE)).peekNextScheduledAlarm()); }
  @Test public void stopCancelsAlarm(){ ModeManager.start(app,"meeting",30); ModeManager.stop(app,"manual"); assertNull(shadowOf((AlarmManager)app.getSystemService(Context.ALARM_SERVICE)).peekNextScheduledAlarm()); }
  @Test public void repliesAfterTimerExpiredAreNotSent(){
    ModeManager.start(app,"meeting",30); clk[0]+=31*60000L; sms().clearLastSentTextMessageParams();
    ModeManager.onUnanswered(app,"010-1234-5678",false); assertNull(lastSmsTo()); assertNull(ModeStore.active(app));
  }
  @Test public void bootResumesAlarmAndNotification(){
    ModeManager.start(app,"meeting",60); ShadowAlarmManager.reset(); ShadowNotificationManager.reset();
    new ModeSystemReceiver().onReceive(app,new Intent(Intent.ACTION_BOOT_COMPLETED));
    assertNotNull(shadowOf((AlarmManager)app.getSystemService(Context.ALARM_SERVICE)).peekNextScheduledAlarm());
    assertTrue(hasNotification(ModeManager.N_MODE));
  }
  @Test public void bootAfterExpiryEndsMode(){
    ModeManager.start(app,"meeting",30); clk[0]+=40*60000L;
    new ModeSystemReceiver().onReceive(app,new Intent(Intent.ACTION_BOOT_COMPLETED));
    assertNull(ModeStore.active(app)); assertNotNull(ModeStore.summary(app)); assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL,filter());
  }

  // ---------- 알림 ----------
  boolean hasNotification(int id){ for(StatusBarNotification n:nm.getActiveNotifications()) if(n.getId()==id) return true; return false; }
  Notification notif(int id){ for(StatusBarNotification n:nm.getActiveNotifications()) if(n.getId()==id) return n.getNotification(); return null; }
  @Test public void ongoingNotificationHasStopButtonAndStops(){
    start("drive"); Notification n=notif(ModeManager.N_MODE); assertNotNull(n);
    assertEquals(1,n.actions.length); assertEquals("끄기",n.actions[0].title.toString());
    assertTrue(shadowOf(n).getContentTitle().toString().contains("운전 중"));
    new ModeActionReceiver().onReceive(app,new Intent(ModeManager.ACT_STOP));
    assertNull(ModeStore.active(app)); assertFalse(hasNotification(ModeManager.N_MODE)); assertTrue("끝난 뒤 요약 알림",hasNotification(ModeManager.N_DONE));
  }
  @Test public void notificationTextIsPlainKoreanAndNeverSaysYuni(){
    start("meeting"); String all=shadowOf(notif(ModeManager.N_MODE)).getContentTitle()+" "+shadowOf(notif(ModeManager.N_MODE)).getContentText();
    assertFalse(all.contains("유니")); assertTrue(all.contains("회의 중"));
  }

  // ---------- 끝난 뒤 요약 ----------
  @Test public void summaryListsMissedCallsAndTextsAndReplies() throws Exception {
    CONTACTS.put("12345678",new Object[]{"김사장",0});
    clk[0]=t0;
    CALLS.add(new Object[]{"010-1111-2222","",t0-60000,CallLog.Calls.PRESENTATION_ALLOWED,CallLog.Calls.MISSED_TYPE});   // 모드 전 → 제외
    start("drive");
    clk[0]=t0+10*60000L; replied("010-1234-5678"); replied("1588-1234");
    CALLS.add(new Object[]{"010-1234-5678","",t0+5*60000L,CallLog.Calls.PRESENTATION_ALLOWED,CallLog.Calls.MISSED_TYPE});
    CALLS.add(new Object[]{"010-1234-5678","",t0+9*60000L,CallLog.Calls.PRESENTATION_ALLOWED,CallLog.Calls.MISSED_TYPE});
    CALLS.add(new Object[]{"010-3333-4444","박과장",t0+8*60000L,CallLog.Calls.PRESENTATION_ALLOWED,CallLog.Calls.REJECTED_TYPE});
    CALLS.add(new Object[]{"1588-1234","",t0+7*60000L,CallLog.Calls.PRESENTATION_ALLOWED,CallLog.Calls.MISSED_TYPE});
    CALLS.add(new Object[]{"","",t0+6*60000L,CallLog.Calls.PRESENTATION_RESTRICTED,CallLog.Calls.MISSED_TYPE});
    CALLS.add(new Object[]{"010-5555-6666","",t0+4*60000L,CallLog.Calls.PRESENTATION_ALLOWED,CallLog.Calls.INCOMING_TYPE}); // 받은 전화 → 제외
    SMS.add(new Object[]{"010-3333-4444",t0+3*60000L,"회의 자료 보냈어요"}); SMS.add(new Object[]{"010-3333-4444",t0+4*60000L,"확인 부탁드려요"}); SMS.add(new Object[]{"15881234",t0+2*60000L,"[광고] 할인"});
    SMS.add(new Object[]{"010-9999-9999",t0-5000,"모드 전 문자"});
    clk[0]=t0+42*60000L;
    JSONObject s=ModeManager.stop(app,"manual"); assertNotNull(s);
    JSONObject f=ModeManager.summaryFull(app);
    assertEquals("운전 중",f.optString("name")); assertEquals("42분",f.optString("durText"));
    JSONArray calls=f.optJSONArray("calls"); assertEquals(4,calls.length());   // 1234·3333·1588·숨김
    JSONObject first=calls.optJSONObject(0); assertEquals("01012345678",first.optString("key")); assertEquals(2,first.optInt("n")); assertEquals("김사장",first.optString("name")); assertTrue(first.optBoolean("callable"));
    boolean hidden=false; for(int i=0;i<calls.length();i++){ JSONObject c=calls.optJSONObject(i); if("hidden".equals(c.optString("key"))){ hidden=true; assertFalse(c.optBoolean("callable")); } }
    assertTrue(hidden);
    JSONArray texts=f.optJSONArray("texts"); assertEquals(2,texts.length());
    assertEquals("010-3333-4444",texts.optJSONObject(0).optString("num")); assertEquals(2,texts.optJSONObject(0).optInt("n")); assertEquals("확인 부탁드려요",texts.optJSONObject(0).optString("x"));
    assertEquals(1,f.optJSONArray("log").length()); assertEquals("01012345678",f.optJSONArray("log").optJSONObject(0).optString("k"));
    assertEquals(1,f.optJSONObject("skips").optInt("기업 대표번호"));
    assertFalse(f.optBoolean("needLog")); assertFalse(f.optBoolean("needSms"));
    assertTrue("끝난 뒤 알림 글",shadowOf(notif(ModeManager.N_DONE)).getContentText().toString().contains("부재중 4명"));
  }
  @Test public void summaryWithoutPermissionsSaysSoInsteadOfLying(){
    shadowOf(app).denyPermissions(Manifest.permission.READ_CALL_LOG,Manifest.permission.READ_SMS);
    start("drive"); ModeManager.stop(app,"manual"); JSONObject f=ModeManager.summaryFull(app);
    assertTrue(f.optBoolean("needLog")); assertTrue(f.optBoolean("needSms")); assertEquals(0,f.optJSONArray("calls").length());
  }
  @Test public void textsOffMeansNoTextSection() throws Exception {
    setCfg("drive","texts",false); start("drive"); SMS.add(new Object[]{"010-3333-4444",t0+1000,"hi"}); clk[0]=t0+60000L; ModeManager.stop(app,"manual");
    assertEquals(0,ModeManager.summaryFull(app).optJSONArray("texts").length());
  }
  @Test public void emptySummaryNotificationSaysNothingMissed(){
    start("drive"); ModeManager.stop(app,"manual");
    assertEquals("놓친 연락이 없어요",shadowOf(notif(ModeManager.N_DONE)).getContentText().toString());
  }
  @Test public void dismissClearsSummary(){
    start("drive"); ModeManager.stop(app,"manual"); assertNotNull(ModeStore.summary(app));
    Bridge b=new Bridge(Robolectric.buildActivity(MainActivity.class).create().get()); b.modeDismiss();
    assertNull(ModeStore.summary(app)); assertFalse(hasNotification(ModeManager.N_DONE));
  }
  @Test public void startingAgainClearsOldSummary(){ start("drive"); ModeManager.stop(app,"manual"); start("meeting"); assertNull(ModeStore.summary(app)); }

  // ---------- 설정 저장·화면에 주는 상태 ----------
  @Test public void saveSanitizesInput() throws Exception {
    Bridge b=new Bridge(Robolectric.buildActivity(MainActivity.class).create().get());
    JSONObject in=new JSONObject(); in.put("text","  안녕\n하세요   "); in.put("allow","아무나"); in.put("timer",45); in.put("repeat",true);
    JSONObject out=new JSONObject(b.modeSave("meeting",in.toString()));
    assertEquals("안녕 하세요",out.optString("text")); assertEquals("none",out.optString("allow")); assertEquals(0,out.optInt("timer")); assertTrue(out.optBoolean("repeat"));
    assertEquals("{}",b.modeSave("nope","{}"));
    in.put("timer",60); assertEquals(60,new JSONObject(b.modeSave("meeting",in.toString())).optInt("timer"));
    in.put("timer",60); assertEquals("운전엔 타이머 없음",0,new JSONObject(b.modeSave("drive",in.toString())).optInt("timer"));
  }
  @Test public void stateJsonForTheScreen() throws Exception {
    JSONObject s=ModeManager.state(app);
    assertEquals(2,s.getJSONArray("defs").length()); assertTrue(s.getJSONObject("perms").getBoolean("dnd")); assertTrue(s.getJSONObject("perms").getBoolean("sms")); assertFalse(s.getBoolean("def"));
    assertFalse(s.has("active"));
    start("drive"); s=ModeManager.state(app); assertEquals("drive",s.getJSONObject("active").getString("id"));
    ModeManager.stop(app,"manual"); s=ModeManager.state(app); assertFalse(s.has("active")); assertEquals("drive",s.getJSONObject("pending").getString("id"));
  }
  @Test public void startingTwiceIsHarmless(){ start("drive"); replied("010-1234-5678"); JSONObject r=start("drive"); assertTrue(r.optBoolean("already")); assertEquals("진행 중 기록은 그대로",1,sent()); }
  @Test public void invalidModeRefused(){ assertFalse(ModeManager.start(app,"fly",0).optBoolean("ok")); assertNull(ModeStore.active(app)); }
  @Test public void stopWhenNothingIsOnReturnsNull(){ assertNull(ModeManager.stop(app,"manual")); }
}
