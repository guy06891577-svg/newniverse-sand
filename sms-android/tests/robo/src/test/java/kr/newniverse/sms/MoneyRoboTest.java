package kr.newniverse.sms;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;

/** v10: 가짜 문자함(content://sms)에 은행별 샘플·출금·카드·광고·사칭·출금 예정 문자를 넣고, 요약 JSON·알림·알람·Bridge 를 끝에서 끝까지 확인 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MoneyRoboTest {
  static final List<Map<String,Object>> SMS = new ArrayList<>();
  static final List<String> SELS = new ArrayList<>();
  public static class SmsProv extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
      SELS.add(String.valueOf(s));
      MatrixCursor c = new MatrixCursor(p == null ? new String[]{"_id"} : p);
      List<Map<String,Object>> rows = new ArrayList<>(SMS);
      rows.sort((x, y) -> Long.compare((Long) y.get("date"), (Long) x.get("date")));
      for (Map<String,Object> r : rows) { Object[] v = new Object[p.length]; for (int i = 0; i < p.length; i++) v[i] = r.get(p[i]); c.addRow(v); }
      return c;
    }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
  }
  static long kst(int y, int mo, int d, int h, int mi) { return MoneyTest.ms(y, mo, d, h, mi); }
  static final long NOW = kst(2026, 11, 28, 14, 5);
  Application app; long id = 1;
  void sms(long date, String addr, String body) { Map<String,Object> m = new LinkedHashMap<>(); m.put("_id", id++); m.put("address", addr); m.put("body", body); m.put("date", date); SMS.add(m); }

  @Before public void setUp() {
    SMS.clear(); SELS.clear();
    app = RuntimeEnvironment.getApplication();
    shadowOf(app).grantPermissions(android.Manifest.permission.READ_SMS, android.Manifest.permission.POST_NOTIFICATIONS);
    ShadowContentResolver.registerProviderInternal("sms", Robolectric.buildContentProvider(SmsProv.class).create("sms").get());
    app.getSharedPreferences("nv_money", 0).edit().clear().commit();
    ((NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE)).cancelAll();
  }
  static String nh(String amt, String time, String who) { return "[Web발신]\nNH농협 입금" + amt + "원\n" + time + " 351-****-1234-71 " + who + " 잔액9,999,999원"; }

  void fill() {
    sms(kst(2026,11,28,9,10), "15882100", nh("200,000","11/28 09:10","홍길동"));
    sms(kst(2026,11,28,10,30), "15882100", "[Web발신]\n안동농협 입금 100,000원\n11/28 10:30 790-****-8888-** 박영희 잔액 1,000,000원");
    sms(kst(2026,11,28,13,50), "15889999", "[Web발신]\n[KB]11/28 13:50 279801**027 김철수 전자금융입금 50,000 잔액238,281");
    sms(kst(2026,11,27,23,59), "15882100", nh("30,000","11/27 23:59","이순신"));      // 어제 마지막
    sms(kst(2026,11,24,12,0), "15882100", nh("7,000","11/24 12:00","강감찬"));          // 이번 주(화)
    sms(kst(2026,11,22,12,0), "15882100", nh("9,000","11/22 12:00","지난주"));          // 지난 주(일)
    sms(kst(2026,11,28,11,0), "15882100", "[Web발신]\nNH농협 출금10,000원\n11/28 11:00 351-****-1234-71 홍길동 잔액1,000원");
    sms(kst(2026,11,28,11,5), "15881234", "[Web발신]\n[신한카드] 승인 홍*동 35,000원 일시불 11/28 11:05 스타벅스");
    sms(kst(2026,11,28,12,0), "15882100", "[Web발신]\nNH농협 입금 350,000원 11/28 12:00 351-****-1234-71 확인 http://bit.ly/abc123");
    sms(kst(2026,11,28,12,1), "01012345678", nh("500,000","11/28 12:01","사기꾼"));
    sms(kst(2026,11,28,12,2), "15881234", "(광고) NH농협 입금 즉시 이자 11/28 12:02 *1234 무료수신거부 080-123-4567");
    sms(kst(2026,11,28,12,3), "01099990000", "엄마 용돈 입금했어");
    // 나갈 돈
    sms(kst(2026,11,27,9,0), "15881234", "[KT] 11월 통신요금 55,000원이 11/29 자동이체 예정입니다."); // 내일
    sms(kst(2026,11,26,9,0), "15881234", "[한전] 11월 전기요금 52,340원 납기일 12/10 자동이체 예정");
    sms(kst(2026,11,25,9,0), "15881234", "[신한카드] 결제예정금액 1,234,567원 결제일 12/14");
    sms(kst(2026,11,20,9,0), "15881234", "[KT] 10월 통신요금 55,000원이 10/29 자동이체 예정입니다."); // 지난 날짜
    sms(kst(2026,11,26,9,0), "15881234", "[한전] 전기요금 52,340원 납기일 12/10 http://bit.ly/zzz"); // 링크
    sms(kst(2026,11,26,9,0), "15881234", "[한전] 전기요금 미납 52,340원 납기일 12/10 경과"); // 독촉
  }

  JSONObject j() throws Exception { return MoneyFeed.json(app, NOW); }

  @Test public void summary_endToEnd() throws Exception {
    fill(); JSONObject o = j();
    assertTrue(o.toString(), o.getBoolean("ok")); assertTrue(o.getBoolean("perm"));
    JSONObject t = o.getJSONObject("today");
    assertEquals(350000, t.getLong("sum")); assertEquals(3, t.getInt("n"));
    assertEquals("오늘 입금 35만 원 · 3건", o.getString("line"));
    assertEquals(30000, o.getJSONObject("yesterday").getLong("sum"));
    assertEquals(387000, o.getJSONObject("week").getLong("sum")); // 오늘 35만 + 어제 3만 + 화요일 7천 (일요일 9천 제외)
    assertEquals(5, o.getJSONObject("week").getInt("n"));
    assertEquals("오늘 입금은 35만 원, 3건이에요. 빠른 확인용이라 정확한 내역은 은행 앱에서 봐 주세요.", o.getString("say"));
    // 링크·광고·개인 번호 흉내는 세지 않고 ‘세지 않은 문자’ 로만 (친구 문자는 은행 이름이 없어 아예 제외)
    assertEquals(3, o.getInt("suspect"));
    // 출금·카드 승인은 어디에도 없음
    String all = o.toString(); assertFalse(all.contains("10,000")); assertFalse(all.contains("스타벅스")); assertFalse(all.contains("사기꾼")); assertFalse(all.contains("500000"));
    // 계좌·잔액·본문이 새지 않음
    assertFalse(all.contains("351-")); assertFalse(all.contains("9,999,999")); assertFalse(all.contains("790-")); assertFalse(all.contains("279801")); assertFalse(all.contains("잔액")); assertFalse(all.contains("Web발신"));
    JSONArray it = t.getJSONArray("items");
    assertEquals("KB국민", it.getJSONObject(0).getString("bank")); // 최신순
    assertEquals("안동농협", it.getJSONObject(1).getString("bank")); assertEquals("박영희", it.getJSONObject(1).getString("who"));
    assertEquals("NH농협", it.getJSONObject(2).getString("bank")); assertEquals("홍길동", it.getJSONObject(2).getString("who"));
    for (int i = 0; i < it.length(); i++) assertEquals(new HashSet<>(Arrays.asList("at","bank","who","amt","t")), keys(it.getJSONObject(i)));
  }
  static Set<String> keys(JSONObject o) { Set<String> s = new HashSet<>(); Iterator<String> k = o.keys(); while (k.hasNext()) s.add(k.next()); return s; }

  @Test public void bills_upcoming_tomorrow() throws Exception {
    fill(); JSONObject o = j(); JSONArray b = o.getJSONArray("bills");
    assertEquals(o.toString(), 3, b.length());
    assertEquals("KT", b.getJSONObject(0).getString("org")); assertEquals("내일", b.getJSONObject(0).getString("when"));
    assertEquals("한전", b.getJSONObject(1).getString("org")); assertEquals("12월 10일(목)", b.getJSONObject(1).getString("when"));
    assertEquals("신한카드", b.getJSONObject(2).getString("org")); assertEquals(1234567, b.getJSONObject(2).getLong("amt"));
    assertEquals("내일 출금 예상 · KT · 5만 5,000원", o.getString("billLine"));
    assertEquals(1, o.getJSONArray("tomorrow").length());
    assertEquals(55000 + 52340 + 1234567, o.getLong("billSum"));
    assertFalse(o.toString().contains("bit.ly"));
  }

  @Test public void dateBoundary_midnight() throws Exception {
    sms(kst(2026,11,27,23,59), "15882100", nh("1,000","11/27 23:59","어제"));
    sms(kst(2026,11,28,0,0), "15882100", nh("2,000","11/28 00:00","오늘"));
    JSONObject o = j();
    assertEquals(2000, o.getJSONObject("today").getLong("sum")); assertEquals(1000, o.getJSONObject("yesterday").getLong("sum"));
    // 하루 지나면(11/29 00:01) 어제·오늘이 한 칸씩 밀림
    JSONObject n = MoneyFeed.json(app, kst(2026,11,29,0,1));
    assertEquals(0, n.getJSONObject("today").getInt("n")); assertEquals(2000, n.getJSONObject("yesterday").getLong("sum"));
  }
  @Test public void noPermission_orOff_returnsNothing() throws Exception {
    fill(); shadowOf(app).denyPermissions(android.Manifest.permission.READ_SMS);
    JSONObject o = j(); assertFalse(o.getBoolean("perm")); assertTrue(o.getBoolean("empty")); assertFalse(o.has("today"));
    shadowOf(app).grantPermissions(android.Manifest.permission.READ_SMS); MoneyFeed.setOn(app, false);
    o = j(); assertTrue(o.getBoolean("empty")); assertFalse(o.getBoolean("on")); assertFalse(o.has("today"));
    MoneyFeed.setOn(app, true); assertTrue(j().has("today"));
  }
  @Test public void emptyInbox() throws Exception {
    JSONObject o = j(); assertEquals(0, o.getJSONObject("today").getInt("n")); assertEquals("오늘 입금 없음", o.getString("line")); assertEquals(0, o.getJSONArray("bills").length());
    assertTrue(o.getString("say").startsWith("오늘은 아직 들어온 입금 문자가 없어요"));
  }
  @Test public void feedProvider_money_onlyForDefaultSmsApp() throws Exception {
    fill();
    android.app.role.RoleManager rm = app.getSystemService(android.app.role.RoleManager.class);
    FeedProvider fp = org.robolectric.Robolectric.buildContentProvider(FeedProvider.class).create("kr.newniverse.sms.feed").get();
    android.os.Bundle b = fp.call("money", null, null);
    assertTrue(b.getBoolean("ok")); assertEquals("{\"ok\":false}", b.getString("json"));
    shadowOf(rm).addAvailableRole(android.app.role.RoleManager.ROLE_SMS); shadowOf(rm).addHeldRole(android.app.role.RoleManager.ROLE_SMS);
    b = fp.call("money", null, null);
    JSONObject o = new JSONObject(b.getString("json"));
    assertTrue(b.getString("json"), o.has("line")); assertTrue(o.has("say")); assertTrue(o.has("billLine") || o.has("bills"));
    assertFalse(b.getString("json").contains("bit.ly")); assertFalse(b.getString("json").contains("351-"));
  }
  @Test public void queryIsOnlyInbox_andFiltered() throws Exception { fill(); j(); assertFalse(SELS.isEmpty()); assertTrue(SELS.get(0), SELS.get(0).contains("LIKE")); }

  NotificationManager nm() { return (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE); }

  @Test public void refresh_schedulesAlarm_andNotifiesOnceForTomorrow() throws Exception {
    fill(); BillScheduler.refresh(app, NOW);
    // 한전(12/10)·카드(12/14) 는 하루 전 오전 9시 알람, KT(내일)는 알람 시각이 지나 바로 한 번 알림
    org.robolectric.shadows.ShadowAlarmManager sa = shadowOf((AlarmManager) app.getSystemService(Context.ALARM_SERVICE));
    List<org.robolectric.shadows.ShadowAlarmManager.ScheduledAlarm> al = sa.getScheduledAlarms();
    assertEquals(2, al.size());
    Set<Long> at = new HashSet<>(); for (org.robolectric.shadows.ShadowAlarmManager.ScheduledAlarm s : al) at.add(s.triggerAtTime);
    assertTrue(at.contains(kst(2026,12,9,9,0))); assertTrue(at.contains(kst(2026,12,13,9,0)));
    android.service.notification.StatusBarNotification[] ns = nm().getActiveNotifications();
    assertEquals(1, ns.length);
    Notification n = ns[0].getNotification();
    assertEquals("내일 출금 예상", n.extras.getString(Notification.EXTRA_TITLE));
    assertEquals("KT · 5만 5,000원 · 통신", String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TEXT)));
    // 잠금 화면용(공개) 알림에는 금액·기관이 없음
    assertEquals("내일 출금 예상", n.publicVersion.extras.getString(Notification.EXTRA_TITLE)); assertNull(n.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT));
    assertEquals(Notification.VISIBILITY_PRIVATE, n.visibility);
    nm().cancelAll(); BillScheduler.refresh(app, NOW + 60000); // 다시 계산해도 같은 알림을 또 띄우지 않음
    assertEquals(0, nm().getActiveNotifications().length);
    assertEquals(2, sa.getScheduledAlarms().size());
  }
  @Test public void refresh_off_cancelsAlarms() throws Exception {
    fill(); BillScheduler.refresh(app, NOW);
    org.robolectric.shadows.ShadowAlarmManager sa = shadowOf((AlarmManager) app.getSystemService(Context.ALARM_SERVICE));
    assertEquals(2, sa.getScheduledAlarms().size());
    MoneyFeed.setOn(app, false); BillScheduler.refresh(app, NOW);
    assertEquals(0, sa.getScheduledAlarms().size());
  }
  @Test public void refresh_removesAlarmOfBillNoLongerInInbox() throws Exception {
    fill(); BillScheduler.refresh(app, NOW);
    org.robolectric.shadows.ShadowAlarmManager sa = shadowOf((AlarmManager) app.getSystemService(Context.ALARM_SERVICE));
    SMS.removeIf(m -> String.valueOf(m.get("body")).contains("납기일 12/10 자동이체")); BillScheduler.refresh(app, NOW);
    assertEquals(1, sa.getScheduledAlarms().size());
  }
  @Test public void receiver_postsOnlyWhenDueIsTomorrow() throws Exception {
    String tomorrow = MoneyLogic.day(System.currentTimeMillis()).plusDays(1).toString();
    BillReceiver r = new BillReceiver();
    r.onReceive(app, new Intent(app, BillReceiver.class).setAction(BillScheduler.ACTION).putExtra("org", "한전").putExtra("amt", 52340L).putExtra("cat", "전기").putExtra("due", "2000-01-01"));
    assertEquals(0, nm().getActiveNotifications().length); // 날짜가 어긋난 알람은 알리지 않음
    r.onReceive(app, new Intent(app, BillReceiver.class).setAction(BillScheduler.ACTION).putExtra("org", "한전").putExtra("amt", 52340L).putExtra("cat", "전기").putExtra("due", tomorrow));
    assertEquals(1, nm().getActiveNotifications().length);
    assertEquals("한전 · 5만 2,340원 · 전기", String.valueOf(nm().getActiveNotifications()[0].getNotification().extras.getCharSequence(Notification.EXTRA_TEXT)));
    MoneyFeed.setOn(app, false); nm().cancelAll();
    r.onReceive(app, new Intent(app, BillReceiver.class).setAction(BillScheduler.ACTION).putExtra("org", "한전").putExtra("amt", 52340L).putExtra("due", tomorrow));
    assertEquals(0, nm().getActiveNotifications().length); // 끄면 알리지 않음
  }
  @Test public void receiver_ignoresOtherActions() {
    new BillReceiver().onReceive(app, new Intent("evil.action").putExtra("org","가짜").putExtra("amt",1L));
    assertEquals(0, nm().getActiveNotifications().length);
  }
  @Test public void bridge_moneySummary_andSwitch() throws Exception {
    fill(); Activity a = Robolectric.buildActivity(Activity.class).create().get(); Bridge br = new Bridge(a);
    JSONObject o = new JSONObject(br.moneySummary());
    assertTrue(o.getBoolean("ok")); assertTrue(o.has("today")); assertTrue(o.has("bills"));
    br.moneySet(false); assertFalse(MoneyFeed.on(app)); assertFalse(new JSONObject(br.moneySummary()).has("today"));
    br.moneySet(true); assertTrue(MoneyFeed.on(app)); Thread.sleep(700); nm().cancelAll(); // 바깥 스레드가 다시 맞추는 것을 기다린 뒤 알림 비움
  }
  @Test public void afterNinePm_billArrivesLate_notifiesAtOnce_butNotBeforeDay() throws Exception {
    sms(kst(2026,11,28,13,0), "15881234", "[삼성화재] 11/29 보험료 123,000원 자동이체 예정");
    BillScheduler.refresh(app, NOW);
    assertEquals(1, nm().getActiveNotifications().length);
    nm().cancelAll(); SMS.clear(); sms(kst(2026,11,28,13,0), "15881234", "[삼성화재] 12/20 보험료 123,000원 자동이체 예정");
    BillScheduler.refresh(app, NOW);
    assertEquals(0, nm().getActiveNotifications().length);
  }
}
