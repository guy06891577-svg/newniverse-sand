package kr.newniverse.sms;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.*;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import org.robolectric.RuntimeEnvironment;
import java.util.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;

/**
 * v7: 폰 안의 문자함(content://sms · content://mms)을 흉내 낸 가짜 제공자 + 가짜 달력 제공자로,
 * ‘마지막 문자 다시 읽기’(AutoCal.reread)를 달력에 쓰는 곳까지 끝에서 끝까지 돌려 본다.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RereadRoboTest {
  // ---- 가짜 문자함 ----
  static final List<Map<String,Object>> SMS = new ArrayList<>();
  static final List<Map<String,Object>> MMS = new ArrayList<>();
  static final List<Map<String,Object>> PARTS = new ArrayList<>();
  static final List<Map<String,Object>> ADDR = new ArrayList<>();
  static final List<String> QUERIES = new ArrayList<>();

  static MatrixCursor cur(String[] proj, List<Map<String,Object>> rows) {
    if (proj == null) proj = new String[]{"_id"};
    MatrixCursor c = new MatrixCursor(proj);
    for (Map<String,Object> r : rows) { Object[] v = new Object[proj.length]; for (int i = 0; i < proj.length; i++) v[i] = r.get(proj[i]); c.addRow(v); }
    return c;
  }
  static Map<String,Object> row(Object... kv) { Map<String,Object> m = new LinkedHashMap<>(); for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]); return m; }

  public static class SmsProv extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
      QUERIES.add("sms " + u);
      List<Map<String,Object>> rows = new ArrayList<>();
      for (Map<String,Object> r : SMS) if (u.getPath().equals("/inbox") ? Integer.valueOf(1).equals(r.get("type")) : true) rows.add(r);
      rows.sort((x, y) -> Long.compare((Long) y.get("date"), (Long) x.get("date")));
      return cur(p, rows);
    }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
  }
  public static class MmsProv extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
      QUERIES.add("mms " + u + " sel=" + s);
      if (MMS_FAIL != null) throw MMS_FAIL;
      List<String> seg = u.getPathSegments();
      if (seg.isEmpty() && s != null && s.startsWith("m_id=")) { // MmsInbox.existing(): 같은 m_id 가 이미 저장돼 있는지
        List<Map<String,Object>> rows = new ArrayList<>(); for (Map<String,Object> r : MMS) if (String.valueOf(r.get("m_id")).equals(a[0])) rows.add(r);
        return cur(p, rows);
      }
      if (seg.size() == 1 && seg.get(0).equals("inbox")) {
        List<Map<String,Object>> rows = new ArrayList<>(); for (Map<String,Object> r : MMS) if (Integer.valueOf(1).equals(r.get("msg_box"))) rows.add(r);
        rows.sort((x, y) -> Long.compare((Long) y.get("date"), (Long) x.get("date")));
        return cur(p, rows);
      }
      if (seg.size() == 1 && seg.get(0).equals("part")) {
        List<Map<String,Object>> rows = new ArrayList<>();
        for (Map<String,Object> r : PARTS) if (s != null && s.startsWith("mid=") && String.valueOf(r.get("mid")).equals(a[0])) rows.add(r);
        rows.sort((x, y) -> Integer.compare((Integer) x.get("seq"), (Integer) y.get("seq")));
        return cur(p, rows);
      }
      if (seg.size() == 2 && seg.get(1).equals("addr")) {
        List<Map<String,Object>> rows = new ArrayList<>(); for (Map<String,Object> r : ADDR) if (String.valueOf(r.get("msg_id")).equals(seg.get(0))) rows.add(r);
        return cur(p, rows);
      }
      return cur(p, new ArrayList<>());
    }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) {
      if (MMS_FAIL != null) throw MMS_FAIL;
      List<String> seg = u.getPathSegments(); Map<String,Object> m = new LinkedHashMap<>();
      for (String k : v.keySet()) m.put(k, v.get(k));
      if (seg.size() == 1 && seg.get(0).equals("inbox")) { long id = 100 + MMS.size(); m.put("_id", id); MMS.add(m); return Uri.parse("content://mms/" + id); }
      if (seg.size() == 2 && seg.get(1).equals("part")) { m.put("mid", Long.parseLong(seg.get(0))); m.put("_id", 9000L + PARTS.size()); PARTS.add(m); return Uri.parse("content://mms/part/" + m.get("_id")); }
      if (seg.size() == 2 && seg.get(1).equals("addr")) { ADDR.add(m); return Uri.parse("content://mms/" + seg.get(0) + "/addr/1"); }
      return null;
    }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
    @Override public android.os.ParcelFileDescriptor openFile(Uri u, String mode) throws java.io.FileNotFoundException {
      List<String> seg = u.getPathSegments();
      if (seg.size() == 2 && seg.get(0).equals("part")) for (Map<String,Object> r : PARTS) if (String.valueOf(r.get("_id")).equals(seg.get(1)) && r.get("_file") != null)
        return android.os.ParcelFileDescriptor.open(new java.io.File((String) r.get("_file")), android.os.ParcelFileDescriptor.MODE_READ_ONLY);
      throw new java.io.FileNotFoundException();
    }
  }
  static volatile RuntimeException MMS_FAIL = null;
  // ---- 가짜 달력 ----
  static final List<ContentValues> EVENTS = new ArrayList<>();
  static final List<ContentValues> CALS = new ArrayList<>();
  public static class CalProv extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
      MatrixCursor c = new MatrixCursor(p == null ? new String[]{"_id"} : p);
      if (u.getPath().startsWith("/calendars")) for (int i = 0; i < CALS.size(); i++) { Object[] v = new Object[c.getColumnCount()]; v[0] = (long) (i + 1); c.addRow(v); }
      return c;
    }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) {
      if (u.getPath().startsWith("/calendars")) { CALS.add(new ContentValues(v)); return ContentUris.withAppendedId(u, CALS.size()); }
      if (u.getPath().startsWith("/events")) { EVENTS.add(new ContentValues(v)); return ContentUris.withAppendedId(u, 500 + EVENTS.size()); }
      return ContentUris.withAppendedId(u, 1);
    }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
  }

  static long kst(int y, int mo, int d, int h, int mi) { return CalV5Test.ms(y, mo, d, h, mi); }
  static final long NOW = kst(2026, 11, 26, 18, 45);
  static final String LMS = CalV5Test.LMS;

  /** 실제 시계로 도는 경로(받기·Bridge)용: 일시 줄을 ‘오늘+5일 16:00’ 으로 바꾼 학교 문자 (날짜가 지나도 시험이 깨지지 않게) */
  static String dynSchool() {
    java.util.Calendar c = java.util.Calendar.getInstance(ApptParser.TZ); c.add(java.util.Calendar.DATE, 5);
    String[] w = {"일", "월", "화", "수", "목", "금", "토"};
    String d = c.get(java.util.Calendar.YEAR) + ". " + (c.get(java.util.Calendar.MONTH) + 1) + ". " + c.get(java.util.Calendar.DATE) + ".(" + w[c.get(java.util.Calendar.DAY_OF_WEEK) - 1] + ") 16:00";
    return LMS.replace("2026. 11. 27.(금) 16:00", d);
  }

  Application app;

  @Before public void setUp() {
    SMS.clear(); MMS.clear(); PARTS.clear(); ADDR.clear(); QUERIES.clear(); MMS_FAIL = null; EVENTS.clear(); CALS.clear();
    app = RuntimeEnvironment.getApplication();
    shadowOf(app).grantPermissions(android.Manifest.permission.READ_SMS, android.Manifest.permission.RECEIVE_SMS, android.Manifest.permission.SEND_SMS,
        android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR, android.Manifest.permission.POST_NOTIFICATIONS);
    ShadowContentResolver.registerProviderInternal("sms", Robolectric.buildContentProvider(SmsProv.class).create("sms").get());
    ShadowContentResolver.registerProviderInternal("mms", Robolectric.buildContentProvider(MmsProv.class).create("mms").get());
    ShadowContentResolver.registerProviderInternal("com.android.calendar", Robolectric.buildContentProvider(CalProv.class).create("com.android.calendar").get());
    ProviderInfo pi = new ProviderInfo(); pi.authority = "com.android.calendar"; pi.name = CalProv.class.getName(); pi.packageName = "com.samsung.android.calendar";
    pi.applicationInfo = app.getApplicationInfo();
    shadowOf(app.getPackageManager()).addOrUpdateProvider(pi);
    app.getSharedPreferences("nv_diag", 0).edit().clear().commit();
    app.getSharedPreferences("nv_cal", 0).edit().clear().commit();
  }

  /** 우리 앱이 content://mms 에 저장한 모양 그대로(날짜는 초, 글은 part.text) */
  static long addMms(long id, long dateMs, String subject, String from, String... texts) {
    MMS.add(row("_id", id, "msg_box", 1, "date", dateMs / 1000L, "sub", subject, "sub_cs", subject == null || subject.isEmpty() ? null : 106, "m_type", 132));
    int seq = 0;
    PARTS.add(row("_id", id * 10, "mid", id, "ct", "application/smil", "text", "<smil/>", "seq", -1));
    for (String t : texts) PARTS.add(row("_id", id * 10 + 1 + seq, "mid", id, "ct", "text/plain", "text", t, "seq", seq++));
    ADDR.add(row("msg_id", id, "address", from, "type", 137));
    ADDR.add(row("msg_id", id, "address", "01099998888", "type", 151));
    return id;
  }
  static void addSms(long id, long dateMs, String addr, String body) { SMS.add(row("_id", id, "type", 1, "address", addr, "body", body, "date", dateMs)); }

  JSONObject reread() throws Exception { return new JSONObject(AutoCal.reread(app, NOW)); }
  Map<String,?> diag() { return app.getSharedPreferences("nv_diag", 0).getAll(); }
  int asks() { int n = 0; for (android.service.notification.StatusBarNotification s : ((android.app.NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE)).getActiveNotifications()) if ("달력에 넣을까요?".equals(s.getNotification().extras.getString(Notification.EXTRA_TITLE))) n++; return n; }

  // ---------------------------------------------------------------

  @Test public void mmsLms_reread_asksAndWritesNewRecord() throws Exception {
    addMms(7, NOW, "", "01055556666", LMS);
    long before = 0;
    JSONObject r = reread();
    assertTrue(r.toString(), r.getBoolean("ok"));
    assertEquals("ask", r.getString("steps"));
    assertTrue("새 기록 시각", app.getSharedPreferences("nv_diag", 0).getLong("cal_at", 0) > 0);
    assertEquals(1, asks());
    // 넣기 단추: 같은 처리로 달력 저장소에 일정이 써진다
    assertEquals("ok", AutoCal.manual(app, "01055556666", LMS, NOW));
    assertEquals(1, EVENTS.size());
    ContentValues ev = EVENTS.get(0);
    assertEquals(kst(2026, 11, 27, 16, 0), (long) ev.getAsLong("dtstart"));
    assertEquals("안동시 중앙로 1-2 솜씨쟁이", ev.getAsString("eventLocation"));
    assertEquals("길주중학교 · 원목 트레이, 원목 수납장 만들기", ev.getAsString("title"));
  }

  @Test public void variants_allReachAsk() throws Exception {
    String[][] v = {
      {"CRLF", LMS.replace("\n", "\r\n")}, {"NBSP", LMS.replace(" ", "\u00A0")}, {"fullwidth colon", LMS.replace(":", "：")},
      {"fullwidth tilde", LMS.replace("~", "～")}, {"ㅇ bullet", LMS.replace("○", "ㅇ")}, {"one line", LMS.replace("\n", " ")},
      {"all", LMS.replace("\n", "\r\n").replace(" ", "\u00A0").replace(":", "：").replace("~", "～").replace("○", "ㅇ")}};
    for (String[] x : v) {
      setUp();
      addMms(8, NOW, "", "01055556666", x[1]);
      JSONObject r = reread();
      assertEquals(x[0] + " " + r, "ask", r.getString("steps"));
      assertEquals(x[0], 1, asks());
    }
  }

  @Test public void mmsSubjectVariants() throws Exception {
    String[] subjects = {"", null, "제목없음", LMS, LMS.replace("\n", "\r\n"), "[길주중학교]"};
    for (String sj : subjects) {
      setUp();
      addMms(9, NOW, sj, "01055556666", LMS);
      JSONObject r = reread();
      assertEquals("subject=" + sj + " " + r, "ask", r.getString("steps"));
    }
  }

  @Test public void mmsTextStoredInDataFile_isStillRead() throws Exception {
    java.io.File f = java.io.File.createTempFile("part", ".txt");
    java.nio.file.Files.write(f.toPath(), LMS.replace(" ", "\u00A0").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    MMS.add(row("_id", 31L, "msg_box", 1, "date", NOW / 1000L, "sub", null, "sub_cs", null, "m_type", 132));
    PARTS.add(row("_id", 3100L, "mid", 31L, "ct", "text/plain", "text", null, "seq", 0, "_file", f.getAbsolutePath()));
    ADDR.add(row("msg_id", 31L, "address", "01055556666", "type", 137));
    shadowOf(app.getContentResolver()).registerInputStream(Uri.parse("content://mms/part/3100"), new java.io.FileInputStream(f)); // Robolectric 은 제공자 openFile 대신 이 등록을 씀
    JSONObject r = reread();
    assertEquals(r.toString(), "ask", r.getString("steps"));
    assertEquals("MMS", r.getString("type"));
    assertEquals("parse:ok", r.getString("stage"));
  }

  @Test public void mmsSplitParts() throws Exception {
    String[] ln = LMS.split("\n");
    addMms(10, NOW, "", "01055556666", ln[0] + "\n" + ln[1] + "\n" + ln[2], String.join("\n", Arrays.copyOfRange(ln, 3, ln.length)));
    JSONObject r = reread();
    assertEquals(r.toString(), "ask", r.getString("steps"));
  }

  @Test public void smsInbox_reread() throws Exception {
    addSms(1, NOW, "01055556666", LMS.replace(" ", "\u00A0"));
    JSONObject r = reread();
    assertEquals(r.toString(), "ask", r.getString("steps"));
  }

  @Test public void confirmedAppointment_autoInserts() throws Exception {
    addMms(11, NOW, "", "031-123-4567", "[예약확정] 한마음의원 11월 28일(토) 오후 3시 예약되었습니다.");
    JSONObject r = reread();
    assertTrue(r.toString(), r.getBoolean("ok"));
    assertEquals(r.toString(), "try>ok", r.getString("steps"));
    assertEquals(1, EVENTS.size());
  }

  @Test public void picksNewestDatedMessage_andMmsSecondsNotMs() throws Exception {
    addSms(1, NOW - 3600_000L, "01011112222", "오늘 저녁 7시에 만나요 11월 27일");
    addMms(12, NOW, "", "01055556666", LMS);           // 초 단위로 저장됨 → 더 최근
    addSms(2, NOW + 60_000L, "01033334444", "ㅋㅋㅋ 밥 먹었어?");   // 날짜 없는 더 최근 문자
    JSONObject r = reread();
    assertEquals(r.toString(), "ask", r.getString("steps"));
  }

  // ================= v7: 끝에서 끝까지 =================

  static String pduPath(Context c) { return "x"; }

  /** 진짜 받기 경로: M-Retrieve.conf 바이트 → MmsInbox.onDownloaded → (저장 + 달력 처리) */
  void receiveMms(String subjectOrNull, String body, String from) throws Exception {
    List<byte[]> ps = new ArrayList<>(); ps.add(MmsTest.smilPart());
    ps.add(MmsTest.part(MmsTest.ctGeneral(0x03, 106), MmsTest.concat(MmsTest.hdrCid("text_0"), MmsTest.hdrLoc("text_0.txt")), body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    byte[] pdu = MmsTest.retrieveConf("tr-" + System.nanoTime(), "mid-" + System.nanoTime(), from + "/TYPE=PLMN", 106, subjectOrNull == null ? null : subjectOrNull.getBytes(java.nio.charset.StandardCharsets.UTF_8), NOW / 1000L, ps);
    String key = "k1", name = "k1-1.pdu";
    java.io.File f = MmsFileProvider.file(app, name); f.getParentFile().mkdirs();
    try (java.io.FileOutputStream os = new java.io.FileOutputStream(f)) { os.write(pdu); }
    JSONObject o = new JSONObject(); o.put("loc", "http://mms/x"); o.put("tr", "tr1"); o.put("sub", -1); o.put("at", NOW); o.put("tries", 1); o.put("from", from);
    MmsInbox.pend(app).edit().putString(key, o.toString()).commit();
    Intent i = new Intent().putExtra("key", key).putExtra("file", name);
    MmsInbox.onDownloaded(app, i, android.app.Activity.RESULT_OK);
  }

  @Test public void receivePath_usesSameParser_thenRereadReadsWhatWasStored() throws Exception {
    String body = dynSchool().replace("\n", "\r\n").replace(" ", "\u00A0").replace("○", "ㅇ").replace(":", "：");
    receiveMms("", body, "01055556666");
    assertEquals("받기 경로가 MMS 행을 저장", 1, MMS.size());
    assertEquals("ask", app.getSharedPreferences("nv_diag", 0).getString("cal_steps", ""));   // 받는 순간 같은 파서로 ‘넣을까요?’
    assertEquals(1, asks());
    // 저장된 모양 확인: 날짜는 초 단위, 글은 part.text, msg_box=1
    assertTrue((Long) MMS.get(0).get("date") < 100_000_000_000L);
    assertEquals(1, ((Number) MMS.get(0).get("msg_box")).intValue());
    // 이제 다시 읽기: 같은 문자를 찾아 같은 결과
    app.getSharedPreferences("nv_diag", 0).edit().remove("cal_steps").commit();
    JSONObject r = reread();
    assertEquals(r.toString(), "ask", r.getString("steps"));
    assertEquals("MMS", r.getString("type"));
    assertEquals("parse:ok", r.getString("stage"));
    assertEquals(1, r.getInt("mms"));
  }

  @Test public void receivePath_smsDeliver_usesSameParser() throws Exception {
    AutoCal.onSms(app, "01055556666", dynSchool().replace(" ", "\u00A0").replace("\n", "\r\n"), System.currentTimeMillis());
    assertEquals("ask", app.getSharedPreferences("nv_diag", 0).getString("cal_steps", ""));
  }

  @Test public void staleV5Record_isReplacedByNewRecord_andPanelDataSaysSo() throws Exception {
    long old = NOW - 15 * 60_000L;
    app.getSharedPreferences("nv_diag", 0).edit().putLong("cal_at", old).putString("cal_steps", "skip").putString("cal_reason", "noparse").commit(); // v5 가 남긴 모양(버전 없음, 단계 없음)
    JSONObject d = SmsDiag.json(app);
    assertFalse("예전 기록은 현재 버전 기록이 아님", d.getBoolean("calCurrent"));
    assertEquals(0, d.getLong("rrAt"));
    addMms(7, NOW, "", "01055556666", LMS);
    long t0 = System.currentTimeMillis();
    JSONObject r = reread();
    d = SmsDiag.json(app);
    assertTrue("cal_at 갱신", d.getLong("calAt") >= t0);
    assertTrue(d.getBoolean("calCurrent"));
    assertEquals("8-test", d.getString("calVer"));
    assertTrue(d.getLong("rrAt") >= t0);
    assertEquals("MMS", d.getString("rrType"));
    assertEquals(LMS.length(), d.getInt("rrLen"));
    assertEquals(NOW / 1000 * 1000, d.getLong("rrMsgAt"));
    assertEquals("parse:ok", d.getString("rrStage"));
    assertEquals("ask", d.getString("rrSteps"));
    assertEquals(1, d.getInt("rrMms"));
    assertFalse("본문이 기록에 없음", d.toString().contains("길주중학교") || d.toString().contains("솜씨쟁이") || d.toString().contains("01055556666"));
  }

  @Test public void unknownSender_notSchool_isSkipped_butRereadForcesAsk() throws Exception {
    String text = LMS.replace("[길주중학교]", "");
    AutoCal.onSms(app, "01055556666", text, NOW);
    assertEquals("skip", app.getSharedPreferences("nv_diag", 0).getString("cal_steps", ""));
    assertEquals("untrusted", app.getSharedPreferences("nv_diag", 0).getString("cal_reason", ""));
    addMms(7, NOW, "", "01055556666", text);
    JSONObject r = reread();
    assertEquals(r.toString(), "ask", r.getString("steps"));
    assertTrue(r.getString("label"), r.getString("label").contains("parse:ok"));
  }

  @Test public void stageShownWhenParseFails() throws Exception {
    addMms(7, NOW, "", "01055556666", "[길주중학교]\n학부모님 안녕하세요. 신청 기간: 2026. 11. 20. ~ 11. 25.(수)까지 신청해 주세요.");
    JSONObject r = reread();
    assertEquals("skip", r.getString("steps"));
    assertEquals(r.toString(), "parse:deadline-only", r.getString("stage"));
    assertTrue(r.getString("label"), r.getString("label").contains("parse:deadline-only"));
    assertEquals("parse:deadline-only", SmsDiag.json(app).getString("rrStage"));
  }

  @Test public void emptyInbox_saysSo_andStillWritesRecord() throws Exception {
    JSONObject r = reread();
    assertTrue(r.getString("label"), r.getString("label").contains("못 찾았어요"));
    assertTrue(SmsDiag.json(app).getLong("rrAt") > 0);
  }

  @Test public void mmsQueryFails_reportsErrorClass_andFallsBackToSms() throws Exception {
    MMS_FAIL = new SecurityException("nope");
    addSms(1, NOW, "01055556666", LMS);
    JSONObject r = reread();
    assertEquals(r.toString(), "ask", r.getString("steps"));
    assertTrue(SmsDiag.json(app).getString("rrQerr").contains("mms:SecurityException"));
  }

  @Test public void hugeBodies_doNotFreeze() throws Exception {
    String big = ("가나다 2026. 11. 27.(금) 오후 4시 ").repeat(1500);
    for (int k = 0; k < 30; k++) addSms(k + 1, NOW - k * 1000L, "01011112222", big);
    for (int k = 0; k < 30; k++) addMms(200 + k, NOW - k * 1000L - 500, "", "01011112222", "1/2/3/4/5/6/7/8/9 ".repeat(2000));
    long t0 = System.currentTimeMillis();
    JSONObject r = reread();
    long ms = System.currentTimeMillis() - t0;
    assertTrue("너무 오래 걸림 " + ms + "ms " + r, ms < 8000);
  }

  @Test public void bridgeStart_runsOnBackgroundThread_andWritesRecord() throws Exception {
    addMms(7, NOW, "", "01055556666", LMS);
    android.app.Activity act = Robolectric.buildActivity(android.app.Activity.class).create().get();
    long t0 = System.currentTimeMillis();
    String ret = new Bridge(act).calRereadStart();
    assertTrue(ret, new JSONObject(ret).getBoolean("started"));
    long end = System.currentTimeMillis() + 15000;
    while (System.currentTimeMillis() < end && SmsDiag.json(app).getLong("rrAt") < t0) Thread.sleep(50);
    assertTrue("Bridge 로 시작한 다시 읽기가 새 기록을 씀", SmsDiag.json(app).getLong("rrAt") >= t0);
    assertEquals("ask", SmsDiag.json(app).getString("rrSteps"));
  }

  @Test public void noCalendarPermission_isReportedNotSilent() throws Exception {
    shadowOf(app).denyPermissions(android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR);
    addMms(7, NOW, "", "01055556666", LMS);
    JSONObject r = reread();
    assertEquals("skip", r.getString("steps"));
    assertEquals("noperm", SmsDiag.json(app).getString("rrReason"));
  }

  @Test public void breadcrumbs_showRequestAndPhase() throws Exception {
    addMms(7, NOW, "", "01055556666", LMS);
    long t0 = System.currentTimeMillis();
    reread();
    JSONObject d = SmsDiag.json(app);
    assertTrue("요청 시각 기록", d.getLong("rrStart") >= t0);
    assertEquals("done", d.getString("rrPhase"));
    assertTrue(d.getLong("rrAt") >= d.getLong("rrStart"));
    // 멈춘 경우 흉내: 시작만 기록하고 끝내지 않음
    SmsDiag.rrPhase(app, "start"); SmsDiag.rrPhase(app, "parse");
    d = SmsDiag.json(app);
    assertEquals("parse", d.getString("rrPhase"));
    assertTrue("끝나지 않음을 알 수 있음", d.getLong("rrStart") >= d.getLong("rrAt"));
  }

  @Test public void errDesc_namesRegexSyntaxErrors() {
    Throwable t = new ExceptionInInitializerError(new java.util.regex.PatternSyntaxException("Unclosed group", "(", 1));
    assertTrue(AutoCal.errDesc(t), AutoCal.errDesc(t).contains("ExceptionInInitializerError←PatternSyntaxException Unclosed group@1"));
    assertEquals("NullPointerException", AutoCal.errDesc(new NullPointerException("x")));
  }

  // =====================  v8  =====================
  static String clinicFuture() { return CalV8Test.clinic("2026-12-03 09시 20분"); }
  static String clinicPast() { return CalV8Test.clinic("2026-10-31 09시 20분"); }
  android.app.NotificationManager nm() { return (android.app.NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE); }
  Bridge bridge() { return new Bridge(Robolectric.buildActivity(android.app.Activity.class).create().get()); }
  JSONObject lineOf(JSONObject d, int i) throws Exception { return d.getJSONArray("rrLines").getJSONObject(i); }

  // ---- 최근 문자 훑기 ----
  @Test public void clinicPastNewest_schoolOlder_linesListed_andSchoolAsked() throws Exception {
    addMms(31, NOW - 60_000, "", "01055556666", LMS);          // 학교(미래 일시) — 더 오래됨
    addMms(32, NOW, "", "07012345678", clinicPast());          // 병원(지난 예약) — 가장 최근
    for (int k = 0; k < 4; k++) addSms(k + 1, NOW - 120_000L - k * 1000L, "01011112222", "안녕 오늘 저녁 뭐 먹어?");
    JSONObject r = reread();
    assertEquals(r.toString(), "ask", r.getString("steps"));
    assertEquals(2, r.getInt("rank"));
    JSONObject d = SmsDiag.json(app);
    assertEquals(6, d.getJSONArray("rrLines").length());
    assertEquals("parse:past", lineOf(d, 0).getString("st"));
    assertEquals("MMS", lineOf(d, 0).getString("ty"));
    assertEquals(clinicPast().length(), lineOf(d, 0).getInt("len"));
    assertEquals("parse:ok", lineOf(d, 1).getString("st"));
    assertTrue(lineOf(d, 1).getBoolean("sel"));
    assertFalse(lineOf(d, 0).has("sel"));
    assertEquals(1, asks());
    // 진단 줄에는 본문·번호가 없음
    String all = d.getJSONArray("rrLines").toString() + d.toString().replaceAll("\"rrLines\":\\[.*?\\]", "");
    assertFalse(all.contains("길주")); assertFalse(all.contains("더브레인")); assertFalse(all.contains("01055556666")); assertFalse(all.contains("07012345678"));
    assertTrue(lineOf(d, 0).getString("lb").contains("일시"));
  }

  @Test public void clinicPastOnly_reportsPast_notOther() throws Exception {
    addMms(41, NOW, "", "07012345678", clinicPast());
    JSONObject r = reread();
    assertEquals("skip", r.getString("steps"));
    assertEquals("parse:past", r.getString("stage"));
    assertEquals(0, asks());
    assertEquals(0, EVENTS.size());
  }

  @Test public void futureClinicNewest_autoInserts() throws Exception {
    addMms(51, NOW, "", "07012345678", clinicFuture());
    addMms(52, NOW - 60_000, "", "01055556666", LMS);
    JSONObject r = reread();
    assertEquals(r.toString(), "try>ok", r.getString("steps"));
    assertEquals(1, r.getInt("rank"));
    assertEquals(1, EVENTS.size());
    assertEquals(kst(2026, 12, 3, 9, 20), (long) EVENTS.get(0).getAsLong("dtstart"));
  }

  @Test public void twelveMessages_onlyTenLines() throws Exception {
    for (int k = 0; k < 12; k++) addSms(k + 1, NOW - k * 60_000L, "01011112222", "그냥 안부 " + k);
    JSONObject r = reread();
    assertEquals("skip", r.getString("steps"));
    assertEquals(10, SmsDiag.json(app).getJSONArray("rrLines").length());
    assertEquals(12, r.getInt("sms"));
  }

  // ---- 여러 조각 MMS ----
  @Test public void multipartRows_ctParams_dataFile_imageBetween_areConcatenatedInSeqOrder() throws Exception {
    String[] ln = LMS.split("\n"); int a = 3, b = 7;
    String p1 = String.join("\n", Arrays.copyOfRange(ln, 0, a)), p2 = String.join("\n", Arrays.copyOfRange(ln, a, b)), p3 = String.join("\n", Arrays.copyOfRange(ln, b, ln.length));
    java.io.File f = java.io.File.createTempFile("part", ".txt"); java.nio.file.Files.write(f.toPath(), p2.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    MMS.add(row("_id", 60L, "msg_box", 1, "date", NOW / 1000L, "sub", "", "sub_cs", null, "m_type", 132));
    PARTS.add(row("_id", 601L, "mid", 60L, "ct", "application/smil", "text", "<smil/>", "seq", -1));
    PARTS.add(row("_id", 604L, "mid", 60L, "ct", "text/plain; charset=utf-8", "text", p3, "seq", 3));                  // seq 가 뒤인데 먼저 저장됨
    PARTS.add(row("_id", 602L, "mid", 60L, "ct", "text/plain", "text", p1, "seq", 0));
    PARTS.add(row("_id", 605L, "mid", 60L, "ct", "image/jpeg", "text", null, "seq", 1));
    PARTS.add(row("_id", 603L, "mid", 60L, "ct", "text/plain", "text", null, "_data", "/data/x", "_file", f.getPath(), "seq", 2));  // 글이 파일에 있음
    ADDR.add(row("msg_id", 60L, "address", "01055556666", "type", 137));
    shadowOf(app.getContentResolver()).registerInputStream(Uri.parse("content://mms/part/603"), new java.io.FileInputStream(f));
    JSONObject r = reread();
    assertEquals(r.toString(), "ask", r.getString("steps"));
    assertEquals(LMS.length(), r.getInt("len"));
    JSONObject d = SmsDiag.json(app);
    String pc = lineOf(d, 0).getString("pc");
    assertTrue(pc, pc.startsWith("5개") && pc.contains("image/jpeg"));
  }

  @Test public void receivedMultipartPdu_isStoredWholeAndSummarized() throws Exception {
    String body = dynSchool();
    String[] ln = body.split("\n");
    List<byte[]> ps = new ArrayList<>(); ps.add(MmsTest.smilPart());
    java.nio.charset.Charset UTF = java.nio.charset.StandardCharsets.UTF_8;
    ps.add(MmsTest.part(MmsTest.ctGeneral(0x03, 106), MmsTest.hdrCid("t0"), String.join("\n", Arrays.copyOfRange(ln, 0, 4)).getBytes(UTF)));
    ps.add(MmsTest.part(MmsTest.ctText("text/plain", 106), MmsTest.hdrCid("t1"), String.join("\n", Arrays.copyOfRange(ln, 4, 8)).getBytes(UTF)));
    ps.add(MmsTest.part(MmsTest.ctGeneral(0x03, 106), MmsTest.hdrCid("t2"), String.join("\n", Arrays.copyOfRange(ln, 8, ln.length)).getBytes(UTF)));
    deliver(MmsTest.retrieveConf("trm", "mid-multi", "01055556666/TYPE=PLMN", 106, null, NOW / 1000L, ps), "01055556666", "km");
    assertEquals(1, MMS.size());
    assertEquals("조각 3개가 따로 저장됨", 3, PARTS.stream().filter(x -> "text/plain".equals(x.get("ct"))).count());
    assertEquals("ask", diag().get("cal_steps"));
    assertEquals("자동 처리 기록: MMS 전체 글자 수", body.length(), ((Number) diag().get("auto_len")).intValue());
    assertTrue(String.valueOf(diag().get("mms_parts")).startsWith("4개"));
    JSONObject r = reread();
    assertEquals("ask", r.getString("steps")); assertEquals(body.length(), r.getInt("len"));
  }

  // ---- 받는 순간 자동 처리 ----
  void deliver(byte[] pdu, String from, String key) throws Exception {
    String name = key + "-1.pdu";
    java.io.File f = MmsFileProvider.file(app, name); f.getParentFile().mkdirs();
    try (java.io.FileOutputStream os = new java.io.FileOutputStream(f)) { os.write(pdu); }
    JSONObject o = new JSONObject(); o.put("loc", "http://mms/x"); o.put("tr", "tr1"); o.put("sub", -1); o.put("at", NOW); o.put("tries", 1); o.put("from", from);
    MmsInbox.pend(app).edit().putString(key, o.toString()).commit();
    MmsInbox.onDownloaded(app, new Intent().putExtra("key", key).putExtra("file", name), android.app.Activity.RESULT_OK);
  }
  byte[] oneTextPdu(String mid, String body) {
    List<byte[]> ps = new ArrayList<>(); ps.add(MmsTest.smilPart());
    ps.add(MmsTest.part(MmsTest.ctGeneral(0x03, 106), MmsTest.hdrCid("t0"), body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    return MmsTest.retrieveConf("tr-" + mid, mid, "01055556666/TYPE=PLMN", 106, null, NOW / 1000L, ps);
  }

  @Test public void incomingMms_runsAutoCal_andWritesAutoRecord() throws Exception {
    long t0 = System.currentTimeMillis();
    deliver(oneTextPdu("mid-a", dynSchool()), "01055556666", "ka");
    Map<String,?> d = diag();
    assertTrue(((Long) d.get("auto_at")) >= t0);
    assertEquals("MMS", d.get("auto_src")); assertEquals("parse:ok", d.get("auto_stage")); assertEquals("ask", d.get("auto_steps"));
    assertEquals("done", d.get("auto_phase"));
    assertNull("다시 읽기 기록과 따로", d.get("rr_at"));
    assertEquals(1, asks());
    JSONObject j = SmsDiag.json(app);
    assertEquals("MMS", j.getString("autoSrc")); assertTrue(j.getBoolean("autoGood")); assertEquals(0, j.getLong("rrAt"));
  }

  @Test public void incomingMms_duplicateRow_stillRunsAutoCal() throws Exception {
    byte[] pdu = oneTextPdu("mid-dup", dynSchool());
    deliver(pdu, "01055556666", "kd1");
    app.getSharedPreferences("nv_diag", 0).edit().clear().commit();
    nm().cancelAll();
    deliver(pdu, "01055556666", "kd2");                       // 같은 m_id → dup (시스템이 이미 저장한 경우와 같음)
    assertEquals("dup 이어도 행은 하나", 1, MMS.size());
    assertEquals("MMS", diag().get("auto_src"));
    assertEquals("ask", diag().get("auto_steps"));
    assertEquals(1, asks());
  }

  @Test public void incomingMms_autoCalRuns_evenIfNotificationServiceMissing() throws Exception {
    // 알림/신호 단계가 던져도 달력 처리는 건너뛰지 않음
    shadowOf(app).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
    deliver(oneTextPdu("mid-n", dynSchool()), "01055556666", "kn");
    assertEquals("MMS", diag().get("auto_src"));
    assertEquals("ask>blocked", diag().get("auto_steps"));
  }

  @Test public void incomingSms_announce_runsAutoCal_evenWhenNotSaved() throws Exception {
    SmsInbox.announce(app, "01055556666", dynSchool(), false);
    assertEquals("SMS", diag().get("auto_src"));
    assertEquals("ask", diag().get("auto_steps"));
    assertEquals("ask", diag().get("cal_steps"));
  }

  @Test public void incomingSms_announce_saved() throws Exception {
    SmsInbox.announce(app, "01055556666", dynSchool(), true);
    assertEquals("SMS", diag().get("auto_src")); assertEquals("ask", diag().get("auto_steps"));
  }

  @Test public void incomingPastClinic_autoRecordShowsPastStage() throws Exception {
    deliver(oneTextPdu("mid-c", clinicPast()), "07012345678", "kc");
    assertEquals("parse:past", diag().get("auto_stage"));
    assertEquals("skip", diag().get("auto_steps"));
  }

  @Test public void plainMessage_doesNotOverwriteAutoRecord_butLeavesATimestamp() throws Exception {
    deliver(oneTextPdu("mid-q", dynSchool()), "01055556666", "kq");
    SmsInbox.announce(app, "01011112222", "안녕 저녁 먹었어?", true);
    assertEquals("MMS", diag().get("auto_src")); assertEquals("ask", diag().get("auto_steps"));
    assertEquals("SMS", diag().get("auto_nd_src")); assertTrue(((Long) diag().get("auto_nd_at")) > 0);
    assertEquals("ask", SmsDiag.json(app).getString("autoSteps"));
  }

  @Test public void autoRecord_neverHoldsBodyOrNumber() throws Exception {
    deliver(oneTextPdu("mid-z", dynSchool()), "01055556666", "kz");
    for (Map.Entry<String,?> e : diag().entrySet()) { String v = String.valueOf(e.getValue()); assertFalse(e.getKey(), v.contains("길주") || v.contains("01055556666") || v.contains("솜씨")); }
  }

  // ---- 알림 상태 ----
  @Test public void notification_posted_isConfirmed_andReportedAsPosted() throws Exception {
    addMms(7, NOW, "", "01055556666", LMS);
    JSONObject r = reread();
    assertEquals("ask", r.getString("steps"));
    assertEquals(1, asks());
    JSONObject d = SmsDiag.json(app);
    assertEquals("posted", d.getString("calNotif")); assertTrue(d.getBoolean("rrGood")); assertEquals("", d.getString("notifOk"));
    assertEquals("[]", AutoCal.pendJson(app));
  }

  @Test public void notification_permissionMissing_isNotSuccess_andCardIsKept_andCardAddsEvent() throws Exception {
    shadowOf(app).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
    addMms(7, NOW, "", "01055556666", LMS);
    JSONObject r = reread();
    assertEquals(r.toString(), "ask>blocked", r.getString("steps"));
    assertFalse("성공이라고 말하면 안 됨", r.getString("label").contains("성공"));
    assertTrue(r.getString("label"), r.getString("label").contains("알림 권한이 없음"));
    assertEquals(0, asks());
    JSONObject d = SmsDiag.json(app);
    assertEquals("perm", d.getString("calNotif")); assertFalse(d.getBoolean("rrGood")); assertEquals("perm", d.getString("notifOk"));
    assertEquals(1, d.getInt("pendCount"));
    // 앱 안 카드
    Bridge br = bridge();
    org.json.JSONArray cards = new org.json.JSONArray(br.calPending());
    assertEquals(1, cards.length());
    JSONObject c = cards.getJSONObject(0);
    assertEquals("길주중학교 · 원목 트레이, 원목 수납장 만들기", c.getString("title")); assertEquals(kst(2026, 11, 27, 16, 0), c.getLong("when"));
    assertEquals("ok", br.calPendAdd(String.valueOf(c.getLong("id"))));
    assertEquals(1, EVENTS.size());
    assertEquals(kst(2026, 11, 27, 16, 0), (long) EVENTS.get(0).getAsLong("dtstart"));
    assertEquals("[]", br.calPending());
    assertEquals("ask>ok", diag().get("cal_steps"));
  }

  @Test public void notification_cardNo_addsNothing() throws Exception {
    shadowOf(app).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
    addMms(7, NOW, "", "01055556666", LMS);
    reread();
    Bridge br = bridge();
    long id = new org.json.JSONArray(br.calPending()).getJSONObject(0).getLong("id");
    assertEquals("no", br.calPendNo(String.valueOf(id)));
    assertEquals("[]", br.calPending()); assertEquals(0, EVENTS.size());
    assertEquals("none", br.calPendAdd(String.valueOf(id)));
  }

  @Test public void notification_appDisabled_isReported() throws Exception {
    shadowOf(nm()).setNotificationsEnabled(false);
    addMms(7, NOW, "", "01055556666", LMS);
    JSONObject r = reread();
    assertEquals("ask>blocked", r.getString("steps"));
    assertTrue(r.getString("label"), r.getString("label").contains("알림이 꺼져"));
    assertEquals("off", SmsDiag.json(app).getString("calNotif"));
    assertEquals(1, new org.json.JSONArray(AutoCal.pendJson(app)).length());
  }

  @Test public void notification_channelBlocked_isReported() throws Exception {
    nm().createNotificationChannel(new android.app.NotificationChannel(AutoCal.CH_ASK, "x", android.app.NotificationManager.IMPORTANCE_NONE));
    addMms(7, NOW, "", "01055556666", LMS);
    JSONObject r = reread();
    assertEquals("ask>blocked", r.getString("steps"));
    assertTrue(r.getString("label"), r.getString("label").contains("알림 종류가 꺼져"));
    assertEquals("channel", SmsDiag.json(app).getString("calNotif"));
  }

  @Test public void notification_channelIsHighImportance_andAskUsesFlagImmutable() throws Exception {
    addMms(7, NOW, "", "01055556666", LMS);
    reread();
    assertEquals(android.app.NotificationManager.IMPORTANCE_HIGH, nm().getNotificationChannel(AutoCal.CH_ASK).getImportance());
    android.service.notification.StatusBarNotification[] a = nm().getActiveNotifications();
    boolean found = false;
    for (android.service.notification.StatusBarNotification s : a) if ("달력에 넣을까요?".equals(String.valueOf(s.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE)))) {
      found = true; assertTrue(s.getNotification().actions.length == 2); assertTrue(s.getNotification().getSmallIcon() != null);
      assertTrue((shadowOf(s.getNotification().actions[0].actionIntent).getFlags() & PendingIntent.FLAG_IMMUTABLE) != 0);
    }
    assertTrue(found);
  }

  @Test public void autoInsertNotification_blocked_isRecorded_butEventIsInserted() throws Exception {
    shadowOf(app).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
    addMms(51, NOW, "", "07012345678", clinicFuture());
    JSONObject r = reread();
    assertEquals("try>ok", r.getString("steps"));
    assertEquals(1, EVENTS.size());
    assertEquals("perm", SmsDiag.json(app).getString("calNotif"));
  }

  @Test public void openNotifSettings_exists_onBridge() throws Exception {
    Bridge br = bridge(); br.openNotifSettings();
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    Intent i = shadowOf(app).getNextStartedActivity();
    assertNotNull(i); assertEquals(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS, i.getAction());
    assertEquals(app.getPackageName(), i.getStringExtra(android.provider.Settings.EXTRA_APP_PACKAGE));
  }
}
