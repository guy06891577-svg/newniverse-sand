package kr.newniverse.sms;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import static kr.newniverse.sms.RereadRoboTest.*;

import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.role.RoleManager;
import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.telephony.SmsManager;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.shadows.ShadowSmsManager;

/** v9: 장문(LMS) 보내기 — 만들기 → 파일 → 보낸 편지함 저장 → SmsManager.sendMultimediaMessage → 결과로 보냄/실패 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SendRoboTest {
  static final String TO = "010-9876-5432";
  static String longText() { StringBuilder b = new StringBuilder("안녕하세요, 뉴니버스 장문 시험이에요 😀\n"); while (b.length() < 320) b.append("한글 장문이 한 통으로 가는지 확인해요. "); return b.toString(); }

  /** Telephony.Threads.getOrCreateThreadId 가 묻는 content://mms-sms/threadID */
  public static class ThreadProv extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { MatrixCursor c = new MatrixCursor(new String[]{"_id"}); c.addRow(new Object[]{42L}); return c; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
  }

  Application app;
  @Before public void setUp() {
    SMS.clear(); MMS.clear(); PARTS.clear(); ADDR.clear(); QUERIES.clear(); SMS_INSERTS.clear(); MMS_FAIL = null; UPDATES.clear(); REJECT_EXTRA = false; EVENTS.clear(); CALS.clear();
    app = RuntimeEnvironment.getApplication();
    shadowOf(app).grantPermissions(android.Manifest.permission.READ_SMS, android.Manifest.permission.RECEIVE_SMS, android.Manifest.permission.SEND_SMS, android.Manifest.permission.POST_NOTIFICATIONS);
    ShadowContentResolver.registerProviderInternal("sms", Robolectric.buildContentProvider(SmsProv.class).create("sms").get());
    ShadowContentResolver.registerProviderInternal("mms", Robolectric.buildContentProvider(MmsProv.class).create("mms").get());
    ShadowContentResolver.registerProviderInternal("com.android.calendar", Robolectric.buildContentProvider(CalProv.class).create("com.android.calendar").get());
    android.content.pm.ProviderInfo pi = new android.content.pm.ProviderInfo(); pi.authority = "com.android.calendar"; pi.name = CalProv.class.getName(); pi.packageName = "com.samsung.android.calendar";
    pi.applicationInfo = app.getApplicationInfo(); shadowOf(app.getPackageManager()).addOrUpdateProvider(pi);
    ShadowContentResolver.registerProviderInternal("mms-sms", Robolectric.buildContentProvider(ThreadProv.class).create("mms-sms").get());
    app.getSharedPreferences("nv_diag", 0).edit().clear().commit();
    app.getSharedPreferences("nv_cal", 0).edit().clear().commit();
    shadowOf(app.getPackageManager()).setSystemFeature("android.hardware.telephony.messaging", true); // 문자 되는 폰처럼
    shadowOf(app.getSystemService(android.telephony.TelephonyManager.class)).setIsSmsCapable(true);
    setDefault(true);
    mgr().clearLastSentMultimediaMessageParams(); mgr().clearLastSentMultipartTextMessageParams(); mgr().clearLastSentTextMessageParams();
  }
  void setDefault(boolean on) { RoleManager rm = app.getSystemService(RoleManager.class); if (on) shadowOf(rm).addHeldRole(RoleManager.ROLE_SMS); else shadowOf(rm).removeHeldRole(RoleManager.ROLE_SMS); }
  ShadowSmsManager mgr() { return shadowOf(MmsInbox.mgr(app, MmsOutbox.smsSub())); }
  Map<String,?> diag() { return app.getSharedPreferences("nv_diag", 0).getAll(); }
  Map<String,Object> outRow() { assertEquals(1, MMS.size()); return MMS.get(0); }

  /** 시스템이 결과 PendingIntent 에 채워 넣는 것과 같은 Intent */
  Intent resultIntent(ShadowSmsManager.SendMultimediaMessageParams p, byte[] conf, int http) {
    Intent i = shadowOf(p.getSentIntent()).getSavedIntent();
    Intent r = new Intent(i);
    if (conf != null) r.putExtra("android.telephony.extra.MMS_DATA", conf);
    if (http != 0) r.putExtra("android.telephony.extra.MMS_HTTP_STATUS", http);
    return r;
  }
  static byte[] conf(int status, String mid) {
    java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
    o.write(0x8C); o.write(0x81); o.write(0x98); o.writeBytes("T1".getBytes()); o.write(0); o.write(0x8D); o.write(0x92); o.write(0x92); o.write(status);
    if (mid != null) { o.write(0x8B); o.writeBytes(mid.getBytes()); o.write(0); }
    return o.toByteArray();
  }

  @Test public void longText_goesAsOneLms_persistedAsOutbox() throws Exception {
    String t = longText();
    long t0 = System.currentTimeMillis();
    assertEquals("ok", Nv.send(app, TO, t));
    // SMS 로 나눠 보내지 않음
    assertNull(mgr().getLastSentMultipartTextMessageParams());
    assertNull(mgr().getLastSentTextMessageParams());
    ShadowSmsManager.SendMultimediaMessageParams p = mgr().getLastSentMultimediaMessageParams();
    assertNotNull("sendMultimediaMessage 불림", p);
    Uri u = p.getContentUri();
    assertEquals(MmsFileProvider.AUTH, u.getAuthority());
    assertNull("locationUrl 없음(통신사 설정 그대로)", p.getLocationUrl());
    // 결과 PendingIntent 는 MUTABLE (시스템이 MMS_DATA/HTTP 상태를 채움)
    assertTrue(shadowOf(p.getSentIntent()).isBroadcast());
    assertEquals(0, shadowOf(p.getSentIntent()).getFlags() & android.app.PendingIntent.FLAG_IMMUTABLE);
    assertTrue((shadowOf(p.getSentIntent()).getFlags() & android.app.PendingIntent.FLAG_MUTABLE) != 0);
    // 파일 = M-Send.req, 되읽으면 같은 글
    File f = MmsFileProvider.file(app, u.getLastPathSegment());
    assertTrue(f.exists());
    MmsPdu.Pdu d = MmsPdu.parse(Files.readAllBytes(f.toPath()));
    assertEquals(MmsPdu.SEND_REQ, d.type);
    assertTrue(d.fromInsertToken);
    assertEquals("01098765432/TYPE=PLMN", d.to.get(0).str());
    assertEquals(t, d.text());
    assertTrue(d.subjectStr().isEmpty());
    // 보낸 편지함(보내는 중) 저장 → 대화에 바로 보임
    Map<String,Object> r = outRow();
    assertEquals("outbox", r.get("_uri"));
    assertEquals(4, r.get("msg_box"));
    assertEquals(128, r.get("m_type"));
    assertEquals(42L, r.get("thread_id"));
    assertEquals(d.transactionId, r.get("tr_id"));
    assertTrue((Long) r.get("date") >= t0 / 1000 - 1);
    long id = (Long) r.get("_id");
    List<Map<String,Object>> parts = new ArrayList<>(); for (Map<String,Object> x : PARTS) if (Long.valueOf(id).equals(x.get("mid"))) parts.add(x);
    assertEquals(2, parts.size());
    Map<String,Object> tp = null; for (Map<String,Object> x : parts) if ("text/plain".equals(x.get("ct"))) tp = x;
    assertNotNull(tp); assertEquals(t, tp.get("text")); assertEquals(106, tp.get("chset")); assertEquals(0, tp.get("seq"));
    int from = 0, to = 0; for (Map<String,Object> a : ADDR) { if (Integer.valueOf(137).equals(a.get("type"))) { from++; assertEquals("insert-address-token", a.get("address")); } if (Integer.valueOf(151).equals(a.get("type"))) { to++; assertEquals("01098765432", a.get("address")); } }
    assertEquals(1, from); assertEquals(1, to);
    // 진단: 넘김 단계, 본문·번호 없음
    Map<String,?> g = diag();
    assertEquals("handed", g.get("mo_stage"));
    assertEquals(t.length(), g.get("mo_len"));
    assertEquals(true, g.get("mo_saved"));
    assertTrue((Integer) g.get("mo_bytes") > t.getBytes("UTF-8").length);
    assertFalse(g.containsKey("mo_done_at"));
    String all = g.toString() + SmsDiag.json(app).toString();
    assertFalse("본문 없음", all.contains("장문이 한 통") || all.contains("안녕하세요"));
    assertFalse("번호 없음", all.contains("9876") || all.contains("5432"));
    assertEquals("handed", SmsDiag.json(app).getString("moStage"));
    assertTrue(SmsDiag.json(app).getString("moStageLabel").length() > 0);
  }

  @Test public void result_ok_marksSent_andDeletesFile() throws Exception {
    Nv.send(app, TO, longText());
    ShadowSmsManager.SendMultimediaMessageParams p = mgr().getLastSentMultimediaMessageParams();
    File f = MmsFileProvider.file(app, p.getContentUri().getLastPathSegment());
    MmsOutbox.onSent(app, resultIntent(p, conf(0x80, "SKT123"), 200), Activity.RESULT_OK);
    Map<String,Object> r = outRow();
    assertEquals(2, r.get("msg_box"));
    assertEquals("SKT123", r.get("m_id"));
    assertEquals(0x80, r.get("resp_st"));
    assertNotNull(r.get("date_sent"));
    assertFalse("임시 파일 지움", f.exists());
    Map<String,?> g = diag();
    assertEquals("result", g.get("mo_stage"));
    assertEquals(-1, g.get("mo_code"));
    assertEquals(200, g.get("mo_http"));
    assertEquals(0x80, g.get("mo_resp"));
    assertEquals(true, g.get("mo_ok"));
    assertTrue(g.containsKey("mo_done_at"));
    JSONObject j = SmsDiag.json(app);
    assertEquals("통신사가 받음", j.getString("moRespLabel"));
    assertEquals("보냄", j.getString("moCodeLabel"));
    assertTrue(j.getBoolean("moOk"));
    assertFalse(j.toString().contains("SKT123"));
  }

  @Test public void result_okWithoutConf_isSent() throws Exception {
    Nv.send(app, TO, longText());
    ShadowSmsManager.SendMultimediaMessageParams p = mgr().getLastSentMultimediaMessageParams();
    MmsOutbox.onSent(app, resultIntent(p, null, 0), Activity.RESULT_OK);
    assertEquals(2, outRow().get("msg_box"));
    assertEquals(true, diag().get("mo_ok"));
  }

  @Test public void result_carrierRejected_marksFailed_code106() throws Exception {
    Nv.send(app, TO, longText());
    ShadowSmsManager.SendMultimediaMessageParams p = mgr().getLastSentMultimediaMessageParams();
    MmsOutbox.onSent(app, resultIntent(p, conf(0xE3, null), 200), Activity.RESULT_OK);
    assertEquals(5, outRow().get("msg_box"));
    assertNull(outRow().get("date_sent"));
    assertEquals(106, diag().get("mo_code"));
    assertEquals(0xE3, diag().get("mo_resp"));
    assertEquals(false, diag().get("mo_ok"));
    assertTrue(SmsDiag.json(app).getString("moCodeLabel").contains("거절"));
  }

  @Test public void result_ioError_marksFailed() throws Exception {
    Nv.send(app, TO, longText());
    ShadowSmsManager.SendMultimediaMessageParams p = mgr().getLastSentMultimediaMessageParams();
    MmsOutbox.onSent(app, resultIntent(p, null, 500), 5 /* MMS_ERROR_HTTP_FAILURE */);
    assertEquals(5, outRow().get("msg_box"));
    assertEquals(5, diag().get("mo_code"));
    assertEquals(500, diag().get("mo_http"));
    assertEquals(false, diag().get("mo_ok"));
  }

  @Test public void viaReceiver_errorPathRecorded() throws Exception {
    Nv.send(app, TO, longText());
    ShadowSmsManager.SendMultimediaMessageParams p = mgr().getLastSentMultimediaMessageParams();
    Intent i = resultIntent(p, conf(0x80, "M1"), 200);
    assertTrue(i.getAction().startsWith(MmsOutbox.ACT_SENT));
    assertEquals(MmsResultReceiver.class.getName(), i.getComponent().getClassName());
  }

  @Test public void retry_afterFailure_sendsNewLms() throws Exception {
    String t = longText();
    Nv.send(app, TO, t);
    MmsOutbox.onSent(app, resultIntent(mgr().getLastSentMultimediaMessageParams(), null, 0), 1);
    assertEquals(5, MMS.get(0).get("msg_box"));
    mgr().clearLastSentMultimediaMessageParams();
    assertEquals("ok", Nv.send(app, TO, t)); // ‘다시 보내기’ = 같은 보내기
    assertNotNull(mgr().getLastSentMultimediaMessageParams());
    assertEquals(2, MMS.size());
    assertEquals(4, MMS.get(1).get("msg_box"));
    assertEquals(2, diag().get("mo_n"));
    assertFalse("새 보내기는 이전 결과를 지움", diag().containsKey("mo_done_at"));
  }

  @Test public void notDefaultApp_sendsButDoesNotPersist() throws Exception {
    setDefault(false);
    assertEquals("ok", Nv.send(app, TO, longText()));
    assertNotNull(mgr().getLastSentMultimediaMessageParams());
    assertEquals(0, MMS.size());
    assertEquals(false, diag().get("mo_saved"));
    assertEquals("notdefault", diag().get("mo_err"));
  }

  @Test public void providerRejectsExtraColumns_retriesWithCoreColumns() throws Exception {
    REJECT_EXTRA = true;
    assertEquals("ok", Nv.send(app, TO, longText()));
    Map<String,Object> r = outRow();
    assertEquals(4, r.get("msg_box")); assertFalse(r.containsKey("text_only")); assertEquals(42L, r.get("thread_id"));
    assertEquals(true, diag().get("mo_saved"));
    REJECT_EXTRA = false;
  }

  @Test public void providerFails_stillSends() throws Exception {
    MMS_FAIL = new SecurityException("x");
    assertEquals("ok", Nv.send(app, TO, longText()));
    assertNotNull(mgr().getLastSentMultimediaMessageParams());
    assertEquals("nosave", diag().get("mo_err"));
    MMS_FAIL = null;
  }

  /** 짧은 글은 v8 그대로 SMS 경로(보낸 편지함 SMS 저장 → SmsManager.divideMessage …). Robolectric 에는 문자 서비스(isms)가 없어 그 뒤는 "err" 로 끝남 — 경로만 확인 */
  @Test public void shortText_staysSms() throws Exception {
    for (String t : new String[]{"가".repeat(70), "a".repeat(160), "짧은 글"}) {
      SMS_INSERTS.clear();
      String r = Nv.send(app, TO, t);
      assertTrue(r, r.equals("ok") || r.equals("err"));
      assertEquals(t, Collections.singletonList("/outbox"), SMS_INSERTS);
      assertNull(mgr().getLastSentMultimediaMessageParams());
    }
    assertNull(diag().get("mo_at"));
    assertEquals(0, MMS.size());
  }

  @Test public void boundary_71Korean_isLms() throws Exception {
    assertEquals("ok", Nv.send(app, TO, "가".repeat(71)));
    assertTrue("SMS 보낸 편지함에 안 씀", SMS_INSERTS.isEmpty());
    assertNotNull(mgr().getLastSentMultimediaMessageParams());
    assertNull(mgr().getLastSentMultipartTextMessageParams());
  }

  @Test public void bridgeSegments_reportsLmsFlag() throws Exception {
    assertEquals("0", Bridge.segmentsOf("가".repeat(70)).split(",")[3]);
    assertEquals("1", Bridge.segmentsOf("가".repeat(71)).split(",")[3]);
    assertEquals("1", Bridge.segmentsOf("a".repeat(161)).split(",")[3]);
  }

  @Test public void noPermission_noSend() throws Exception {
    shadowOf(app).denyPermissions(android.Manifest.permission.SEND_SMS);
    assertEquals("perm", Nv.send(app, TO, longText()));
    assertNull(mgr().getLastSentMultimediaMessageParams());
  }

  // ---- v9 일상 약속 문자: 받기 경로에서 ‘물어보기’ + 날짜를 풀어 쓴 알림 ----
  @Test public void casual_receivePath_asksWithResolvedDate() throws Exception {
    shadowOf(app).grantPermissions(android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR);
    long now = RereadRoboTest.kst(2026, 11, 27, 15, 0);
    AutoCal.onSms(app, "01055556666", "다음주 목요일 2시에 안동대학교서 봐", now);
    assertEquals("ask", diag().get("cal_steps"));
    assertEquals(0, EVENTS.size()); // 절대 저절로 넣지 않음
    String text = null, big = null, title = null;
    for (android.service.notification.StatusBarNotification s : ((android.app.NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE)).getActiveNotifications()) {
      title = s.getNotification().extras.getString(Notification.EXTRA_TITLE);
      text = String.valueOf(s.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT));
      big = String.valueOf(s.getNotification().extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
    }
    assertEquals("달력에 넣을까요?", title);
    assertEquals("12월 3일(목) 오후 2시 · 안동대학교 — 달력에 넣을까요?", text);
    assertTrue(big, big.startsWith("약속 · 6666\n"));
    assertFalse("진단에 본문 없음", diag().toString().contains("안동대학교"));
    // 넣기 → 달력에 그 시각
    assertEquals("ok", AutoCal.manual(app, "01055556666", "다음주 목요일 2시에 안동대학교서 봐", now));
    assertEquals(1, EVENTS.size());
    assertEquals(RereadRoboTest.kst(2026, 12, 3, 14, 0), (long) EVENTS.get(0).getAsLong("dtstart"));
    assertEquals("약속 · 6666", EVENTS.get(0).getAsString("title"));
    assertEquals("안동대학교", EVENTS.get(0).getAsString("eventLocation"));
  }

  @Test public void casual_pastTense_notAsked() throws Exception {
    shadowOf(app).grantPermissions(android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR);
    AutoCal.onSms(app, "01055556666", "어제 안동대학교서 봤어", RereadRoboTest.kst(2026, 11, 27, 15, 0));
    assertNotEquals("ask", diag().get("cal_steps"));
    assertEquals(0, EVENTS.size());
  }
}
