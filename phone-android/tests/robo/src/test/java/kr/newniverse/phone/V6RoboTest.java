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
import android.os.Bundle;
import android.provider.ContactsContract;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.*;

/** v6 끝에서 끝까지: 통화 메모 저장소 · 차단 목록(가짜 공용 차단 목록) · 명함 → 주소록 · 일정 10분 전 묻기 · 잠금 화면 단골 한 줄 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class V6RoboTest {
  Application app; NotificationManager nm; long t0=1_795_000_000_000L; long[] clk={t0};

  // ---------- 가짜 공용 차단 목록 (com.android.blockednumber) ----------
  static final List<Object[]> BLOCKED=new ArrayList<>(); static long bid=0; static boolean canBlock=true;
  public static class BlockProv extends ContentProvider {
    @Override public boolean onCreate(){ return true; }
    @Override public Bundle call(String method,String arg,Bundle extras){ Bundle b=new Bundle(); if("can_current_user_block_numbers".equals(method)) b.putBoolean("can_block",canBlock); return b; }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){
      MatrixCursor c=new MatrixCursor(new String[]{"_id","original_number"}); List<Object[]> l=new ArrayList<>(BLOCKED); Collections.reverse(l); for(Object[] r:l) c.addRow(r); return c; }
    @Override public Uri insert(Uri u,ContentValues v){ Object[] r={++bid,v.getAsString("original_number")}; BLOCKED.add(r); return ContentUris.withAppendedId(u,bid); }
    @Override public int delete(Uri u,String s,String[] a){ long id=ContentUris.parseId(u); int n=0; for(Iterator<Object[]> it=BLOCKED.iterator();it.hasNext();){ if((Long)it.next()[0]==id){ it.remove(); n++; } } return n; }
    @Override public String getType(Uri u){ return null; } @Override public int update(Uri u,ContentValues v,String s,String[] a){ return 0; }
  }
  // ---------- 가짜 주소록 (applyBatch → insert) ----------
  static final List<ContentValues> CONTACT_ROWS=new ArrayList<>(); static final List<String> CONTACT_URIS=new ArrayList<>();
  public static class ContactsProv extends ContentProvider {
    @Override public boolean onCreate(){ return true; }
    @Override public Uri insert(Uri u,ContentValues v){ CONTACT_ROWS.add(v); CONTACT_URIS.add(u.getPath()); return ContentUris.withAppendedId(u,CONTACT_ROWS.size()); }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){ return new MatrixCursor(p==null?new String[]{"_id"}:p); }
    @Override public String getType(Uri u){ return null; } @Override public int delete(Uri u,String s,String[] a){ return 0; } @Override public int update(Uri u,ContentValues v,String s,String[] a){ return 0; }
  }
  // ---------- 가짜 달력 (Instances) : {eventId, begin, end, title, allDay, selfStatus, visible} ----------
  static final List<Object[]> EVENTS=new ArrayList<>();
  public static class CalProv extends ContentProvider {
    @Override public boolean onCreate(){ return true; }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){
      List<String> seg=u.getPathSegments(); long from=Long.parseLong(seg.get(seg.size()-2)), to=Long.parseLong(seg.get(seg.size()-1));
      MatrixCursor c=new MatrixCursor(p); List<Object[]> l=new ArrayList<>(EVENTS); l.sort(Comparator.comparingLong(r->(Long)r[1]));
      for(Object[] r:l){ long b=(Long)r[1], e=(Long)r[2]; if(e<from||b>to) continue; c.addRow(r); }   // 진짜처럼 겹치는 것 다 줌 (걸러내는 건 앱이)
      return c; }
    @Override public String getType(Uri u){ return null; } @Override public Uri insert(Uri u,ContentValues v){ return null; }
    @Override public int delete(Uri u,String s,String[] a){ return 0; } @Override public int update(Uri u,ContentValues v,String s,String[] a){ return 0; }
  }
  public static class EmptyCallLog extends ContentProvider {
    static final List<Object[]> ROWS=new ArrayList<>();
    @Override public boolean onCreate(){ return true; }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){ MatrixCursor c=new MatrixCursor(new String[]{"number","date"}); for(Object[] r:ROWS) c.addRow(r); return c; }
    @Override public String getType(Uri u){ return null; } @Override public Uri insert(Uri u,ContentValues v){ return null; }
    @Override public int delete(Uri u,String s,String[] a){ return 0; } @Override public int update(Uri u,ContentValues v,String s,String[] a){ return 0; }
  }

  @Before public void setUp(){
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    app=RuntimeEnvironment.getApplication(); nm=app.getSystemService(NotificationManager.class);
    ShadowAlarmManager.reset(); ShadowNotificationManager.reset();
    shadowOf(app).grantPermissions(Manifest.permission.READ_CALL_LOG,Manifest.permission.READ_CONTACTS,Manifest.permission.POST_NOTIFICATIONS,Manifest.permission.READ_CALENDAR,Manifest.permission.WRITE_CONTACTS);
    shadowOf(nm).setNotificationPolicyAccessGranted(true); nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
    for(String p:new String[]{MemoStore.PREF,ModeStore.PREF,CalWatch.PREF}) app.getSharedPreferences(p,0).edit().clear().commit();
    BLOCKED.clear(); bid=0; canBlock=true; CONTACT_ROWS.clear(); CONTACT_URIS.clear(); EVENTS.clear(); EmptyCallLog.ROWS.clear(); Bridge.lineCache.clear();
    clk[0]=t0; MemoStore.clock=()->clk[0]; CalWatch.clock=()->clk[0]; ModeManager.clock=()->clk[0];
    ShadowContentResolver.registerProviderInternal("com.android.blockednumber",Robolectric.setupContentProvider(BlockProv.class,"com.android.blockednumber"));
    ShadowContentResolver.registerProviderInternal("com.android.contacts",Robolectric.setupContentProvider(ContactsProv.class,"com.android.contacts"));
    ShadowContentResolver.registerProviderInternal("com.android.calendar",Robolectric.setupContentProvider(CalProv.class,"com.android.calendar"));
    ShadowContentResolver.registerProviderInternal("call_log",Robolectric.setupContentProvider(EmptyCallLog.class,"call_log"));
    shadowOf(app.getSystemService(KeyguardManager.class)).setKeyguardLocked(false);
  }
  @After public void tearDown(){ MemoStore.clock=null; CalWatch.clock=null; ModeManager.clock=System::currentTimeMillis; }
  void dialer(boolean on){ ShadowRoleManager r=shadowOf(app.getSystemService(RoleManager.class)); boolean held=app.getSystemService(RoleManager.class).isRoleHeld(RoleManager.ROLE_DIALER); if(on&&!held) r.addHeldRole(RoleManager.ROLE_DIALER); else if(!on&&held) r.removeHeldRole(RoleManager.ROLE_DIALER); }

  // ---------- 통화 메모 ----------
  @Test public void memoAddEditDeleteRestorePerNumber() throws Exception {
    JSONObject a=MemoStore.add(app,"010-2345-6789","김민수","두부 2판 · 내일 오전","text",20_000);
    assertNotNull(a); clk[0]+=60_000;
    JSONObject b=MemoStore.add(app,"+82 10-2345-6789","김민수","견적 다시 보내기","voice",5_000);
    MemoStore.add(app,"02-555-1234","가게","다른 번호","text",0);
    assertNull("빈 글은 안 남김",MemoStore.add(app,"010-2345-6789","","   ","text",0));
    assertNull("번호 없음",MemoStore.add(app,"","","글","text",0));
    List<JSONObject> l=MemoStore.forNum(app,"01023456789");
    assertEquals("+82 모양도 같은 번호",2,l.size()); assertEquals("견적 다시 보내기",l.get(0).getString("text")); assertEquals("voice",l.get(0).getString("src"));
    assertEquals("견적 다시 보내기",MemoStore.latest(app,"010 2345 6789").getString("text"));
    assertTrue(MemoStore.edit(app,a.getLong("id"),"두부 3판")); assertFalse("빈 글로는 못 고침",MemoStore.edit(app,a.getLong("id"),"  "));
    assertEquals("두부 3판",MemoStore.forNum(app,"01023456789").get(1).getString("text"));
    JSONObject gone=MemoStore.del(app,b.getLong("id")); assertNotNull(gone);
    assertEquals(1,MemoStore.forNum(app,"01023456789").size());
    assertTrue("되돌리기",MemoStore.restore(app,gone)); assertFalse("두 번 되돌리지 않음",MemoStore.restore(app,gone));
    assertEquals(2,MemoStore.forNum(app,"01023456789").size());
    JSONArray ppl=MemoStore.people(app); assertEquals("사람별로 묶음",2,ppl.length()); int n=0; for(int i=0;i<2;i++) n+=ppl.getJSONObject(i).getInt("n"); assertEquals(3,n);
  }

  // ---------- 단골 한 줄 ----------
  @Test public void callerLineHidesMemoWhenLocked() throws Exception {
    MemoStore.clock=null;
    MemoStore.add(app,"010-2345-6789","김민수","비밀 주문 내용","text",30_000);
    long now=System.currentTimeMillis(); EmptyCallLog.ROWS.add(new Object[]{"01023456789",now-2*86400000L}); EmptyCallLog.ROWS.add(new Object[]{"010-2345-6789",now-3*86400000L});
    Activity act=Robolectric.buildActivity(Activity.class).create().get(); Bridge br=new Bridge(act);
    JSONObject o=new JSONObject(br.callerLine("010-2345-6789"));
    assertFalse(o.getBoolean("locked")); assertEquals("비밀 주문 내용",o.getString("memo")); assertEquals("오늘",o.getString("memoAgo"));
    assertEquals("2일 전",o.getString("lastAgo")); assertEquals(2,o.getInt("n15"));
    shadowOf(app.getSystemService(KeyguardManager.class)).setKeyguardLocked(true);
    String locked=br.callerLine("010-2345-6789"); JSONObject lo=new JSONObject(locked);
    assertTrue(lo.getBoolean("locked")); assertFalse("잠금 화면엔 메모 글이 아예 안 나감",locked.contains("비밀")); assertFalse(lo.has("memo")); assertFalse(lo.has("lastAgo"));
  }

  // ---------- 차단 ----------
  @Test public void blockNeedsDefaultDialerAndUnblocksAllShapes(){
    dialer(false);
    assertFalse(Blocker.can(app)); assertEquals("role",Blocker.block(app,"010-5555-6666")); assertEquals(-1,Blocker.unblock(app,"010-5555-6666"));
    dialer(true);
    assertTrue(Blocker.can(app));
    assertEquals("ok",Blocker.block(app,"010-5555-6666"));
    assertEquals("already",Blocker.block(app,"+82 10 5555 6666"));
    assertEquals("bad",Blocker.block(app,"광고"));
    assertEquals("emergency",Blocker.block(app,"112")); assertEquals("emergency",Blocker.block(app,"119"));
    assertTrue(Blocker.isBlocked(app,"01055556666")); assertFalse(Blocker.isBlocked(app,"01055556667"));
    BLOCKED.add(new Object[]{++bid,"+821055556666"});   // 다른 앱이 다른 모양으로 넣은 같은 번호
    assertEquals(2,Blocker.list(app).length());
    assertEquals("모양이 달라도 같은 번호는 다 풂",2,Blocker.unblock(app,"010-5555-6666"));
    assertEquals(0,Blocker.list(app).length()); assertFalse(Blocker.isBlocked(app,"01055556666"));
    canBlock=false; assertFalse("다른 사용자(업무 프로필 등)는 못 막음",Blocker.can(app));
  }

  // ---------- 명함 → 주소록 ----------
  @Test public void cardSavesOneContactWithoutPhoto() throws Exception {
    JSONObject f=new JSONObject().put("name","김민수").put("mobile","010-2345-6789").put("tel","02-555-1234").put("company","(주)한빛상사").put("title","영업부 과장").put("email","minsu.kim@hanbit.co.kr");
    assertEquals("ok",ContactSaver.save(app,f));
    Set<String> mimes=new HashSet<>(); int raw=0;
    for(int i=0;i<CONTACT_ROWS.size();i++){ ContentValues v=CONTACT_ROWS.get(i); if(CONTACT_URIS.get(i).contains("raw_contacts")) raw++; String m=v.getAsString(ContactsContract.Data.MIMETYPE); if(m!=null) mimes.add(m); }
    assertEquals("연락처 하나",1,raw);
    assertTrue(mimes.contains(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)); assertTrue(mimes.contains(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE));
    assertTrue(mimes.contains(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)); assertTrue(mimes.contains(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE));
    assertFalse("사진은 저장 안 함",mimes.contains(ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE));
    assertEquals(6,CONTACT_ROWS.size());   // raw + 이름 + 휴대폰 + 전화 + 이메일 + 회사
    assertEquals("empty",ContactSaver.save(app,new JSONObject().put("company","가게만")));
    shadowOf(app).denyPermissions(Manifest.permission.WRITE_CONTACTS);
    assertEquals("perm",ContactSaver.save(app,f));
  }

  // ---------- 일정 10분 전 ----------
  void ev(long id,long beginFromNow,long durMin,boolean allDay){ long b=t0+beginFromNow; EVENTS.add(new Object[]{id,b,b+durMin*60000L,"병원 예약 "+id,allDay?1:0,0,1}); }
  Notification calNotif(){ for(android.service.notification.StatusBarNotification s:shadowOf(nm).getActiveNotifications()) if(s.getId()==CalWatch.N_CAL) return s.getNotification(); return null; }

  @Test public void calendarAsksTenMinutesBeforeAndSkipsLongAllDay(){
    ev(1,9*60000L,60,false); ev(2,8*60000L,240,false); ev(3,7*60000L,1440,true);
    long at=CalWatch.reschedule(app);
    assertEquals("가장 가까운 묻기 대상 일정(1번)의 10분 전 = 지금 곧바로",t0+5000L,at);
    CalWatch.onAlarm(app);
    Notification n=calNotif(); assertNotNull(n);
    assertTrue(n.extras.getString(Notification.EXTRA_TITLE).contains("병원 예약 1"));
    assertEquals(2,n.actions.length); assertEquals("켜기",n.actions[0].title.toString()); assertEquals("괜찮아요",n.actions[1].title.toString());
    assertEquals(Notification.VISIBILITY_PRIVATE,n.visibility);
    // 같은 일정은 두 번 묻지 않음
    nm.cancelAll(); CalWatch.onAlarm(app); assertNull(calNotif());
    JSONObject s=CalWatch.soonJson(app); assertEquals("ask",s.optString("mode")); assertEquals("1@"+(t0+9*60000L),s.optString("key"));
    CalWatch.dismiss(app,s.optString("key")); assertFalse("괜찮아요 뒤엔 앱 안 카드도 사라짐",CalWatch.soonJson(app).has("key"));
  }
  @Test public void calendarFarEventSchedulesAlarmAtMinusTen(){
    ev(7,35*60000L,30,false);
    assertEquals("35분 뒤 일정 → 25분 뒤(10분 전)에 알람",t0+25*60000L,CalWatch.reschedule(app));
    EVENTS.clear(); ev(8,3*60*60000L,30,false);
    assertEquals("먼 일정뿐이면 30분 뒤 다시 훑기",t0+30*60000L,CalWatch.reschedule(app));
    ShadowAlarmManager.ScheduledAlarm sa=shadowOf(app.getSystemService(AlarmManager.class)).peekNextScheduledAlarm(); assertNotNull(sa);
  }
  @Test public void calendarSilentWhileDriving(){
    ModeManager.start(app,ModeDefs.DRIVE,0);
    ev(1,9*60000L,60,false); CalWatch.onAlarm(app);
    assertNull("운전 중엔 안 띄움",calNotif());
    assertNull(CalWatch.soon(app));
    ModeManager.stop(app,"manual"); nm.cancelAll(); CalWatch.onAlarm(app);
    assertNull("운전 중에 건너뛴 일정은 끝난 뒤에도 다시 묻지 않음",calNotif());
  }
  @Test public void calendarAutoTurnsMeetingOnUntilEventEnd(){
    CalWatch.setMode(app,"auto"); ev(1,9*60000L,45,false); CalWatch.onAlarm(app);
    JSONObject a=ModeStore.active(app); assertNotNull(a); assertEquals(ModeDefs.MEETING,a.optString("id"));
    assertNull("자동이면 묻지 않음",calNotif());
    ModeManager.stop(app,"manual");
  }
  @Test public void calendarOffOrNoPermissionDoesNothing(){
    ev(1,9*60000L,60,false);
    CalWatch.setMode(app,"off"); assertEquals(0,CalWatch.reschedule(app)); CalWatch.onAlarm(app); assertNull(calNotif());
    CalWatch.setMode(app,"ask"); shadowOf(app).denyPermissions(Manifest.permission.READ_CALENDAR);
    assertEquals(0,CalWatch.reschedule(app)); CalWatch.onAlarm(app); assertNull(calNotif()); assertFalse(CalWatch.soonJson(app).optBoolean("perm"));
  }
}
