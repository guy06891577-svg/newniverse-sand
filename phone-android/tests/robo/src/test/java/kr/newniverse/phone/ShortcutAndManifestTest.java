package kr.newniverse.phone;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import java.io.File;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.*;
import org.w3c.dom.*;

/** 런처 말 명령이 부르는 「화면 없는 문」(같은 서명 앱만 따름) + 매니페스트 약속 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ShortcutAndManifestTest {
  Application app; NotificationManager nm;

  @Before public void setUp(){
    ShadowNotificationManager.reset(); ShadowAlarmManager.reset();
    app=RuntimeEnvironment.getApplication(); nm=app.getSystemService(NotificationManager.class);
    shadowOf(nm).setNotificationPolicyAccessGranted(true); nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
    app.getSharedPreferences(ModeStore.PREF,0).edit().clear().commit();
  }
  @SuppressWarnings("deprecation")
  void install(String pkg,String sig){
    PackageInfo pi=new PackageInfo(); pi.packageName=pkg; pi.applicationInfo=new ApplicationInfo(); pi.applicationInfo.packageName=pkg;
    pi.signatures=new Signature[]{new Signature(sig)};
    shadowOf(app.getPackageManager()).installPackage(pi);
  }
  ActivityController<ModeShortcutActivity> launch(String action,String callingPkg,String mode){
    Intent i=new Intent(action).setClass(app,ModeShortcutActivity.class); if(mode!=null) i.putExtra("mode",mode);
    ActivityController<ModeShortcutActivity> c=Robolectric.buildActivity(ModeShortcutActivity.class,i);
    if(callingPkg!=null) shadowOf(c.get()).setCallingPackage(callingPkg);
    c.create(); return c;
  }
  void trust(){ install(app.getPackageName(),"aa11"); install("kr.newniverse.launcher","aa11"); install("com.evil.app","bb22"); }

  @Test public void sameSignatureLauncherCanTurnOnDrive(){
    trust(); ActivityController<ModeShortcutActivity> c=launch(ModeShortcutActivity.ACTION_ON,"kr.newniverse.launcher","drive");
    assertNotNull(ModeStore.active(app)); assertEquals("drive",ModeStore.active(app).optString("id"));
    assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY,nm.getCurrentInterruptionFilter());
    assertTrue(c.get().isFinishing());
    assertEquals(Activity.RESULT_OK,shadowOf(c.get()).getResultCode());
  }
  @Test public void sameSignatureLauncherCanTurnOff(){
    trust(); launch(ModeShortcutActivity.ACTION_ON,"kr.newniverse.launcher","meeting");
    launch(ModeShortcutActivity.ACTION_OFF,"kr.newniverse.launcher",null);
    assertNull(ModeStore.active(app)); assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL,nm.getCurrentInterruptionFilter());
  }
  @Test public void otherAppsAreIgnored(){
    trust(); ActivityController<ModeShortcutActivity> c=launch(ModeShortcutActivity.ACTION_ON,"com.evil.app","drive");
    assertNull(ModeStore.active(app)); ModeManager.start(app,"drive",0);
    launch(ModeShortcutActivity.ACTION_OFF,"com.evil.app",null); assertNotNull("다른 앱은 끌 수도 없음",ModeStore.active(app)); ModeManager.stop(app,"manual"); assertEquals(Activity.RESULT_CANCELED,shadowOf(c.get()).getResultCode());
  }
  @Test public void unknownCallerIsIgnored(){ trust(); launch(ModeShortcutActivity.ACTION_ON,null,"drive"); assertNull(ModeStore.active(app)); }
  @Test public void spoofedReferrerExtraDoesNotWork(){
    trust();
    Intent i=new Intent(ModeShortcutActivity.ACTION_ON).setClass(app,ModeShortcutActivity.class).putExtra("mode","drive").putExtra(Intent.EXTRA_REFERRER,android.net.Uri.parse("android-app://kr.newniverse.launcher"));
    Robolectric.buildActivity(ModeShortcutActivity.class,i).create();
    assertNull("인텐트에 적힌 referrer는 믿지 않음",ModeStore.active(app));
  }
  @Test public void withoutDndAccessItOpensTheSetupScreenInsteadOfFailingSilently(){
    trust(); shadowOf(nm).setNotificationPolicyAccessGranted(false);
    ActivityController<ModeShortcutActivity> c=launch(ModeShortcutActivity.ACTION_ON,"kr.newniverse.launcher","drive");
    assertNull(ModeStore.active(app));
    Intent next=shadowOf(app).getNextStartedActivity(); assertNotNull(next);
    assertEquals(MainActivity.ACTION_MODES,next.getAction()); assertEquals("dnd",next.getStringExtra("need")); assertEquals("drive",next.getStringExtra("mode"));
  }
  @Test public void badModeIsIgnored(){ trust(); launch(ModeShortcutActivity.ACTION_ON,"kr.newniverse.launcher","fly"); assertNull(ModeStore.active(app)); }
  @Test public void speechTextIsUnderstood(){
    trust(); Intent i=new Intent(ModeShortcutActivity.ACTION_ON).setClass(app,ModeShortcutActivity.class).putExtra("say","나 지금 운전 중이야");
    ActivityController<ModeShortcutActivity> c=Robolectric.buildActivity(ModeShortcutActivity.class,i); shadowOf(c.get()).setCallingPackage("kr.newniverse.launcher"); c.create();
    assertEquals("drive",ModeStore.active(app).optString("id"));
  }

  // ---------- 매니페스트 ----------
  static final String A="http://schemas.android.com/apk/res/android";
  static Document doc;
  static Set<String> perms(){ Set<String> s=new HashSet<>(); NodeList l=doc.getElementsByTagName("uses-permission"); for(int i=0;i<l.getLength();i++) s.add(((Element)l.item(i)).getAttributeNS(A,"name")); return s; }
  static Element byName(String tag,String name){ NodeList l=doc.getElementsByTagName(tag); for(int i=0;i<l.getLength();i++){ Element e=(Element)l.item(i); if(name.equals(e.getAttributeNS(A,"name"))) return e; } return null; }
  @BeforeClass public static void load() throws Exception {
    DocumentBuilderFactory f=DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true); doc=f.newDocumentBuilder().parse(new File("../../AndroidManifest.xml"));
  }
  @Test public void packageAndPermissions(){
    assertEquals("kr.newniverse.phone",doc.getDocumentElement().getAttribute("package"));
    Set<String> p=perms();
    for(String x:new String[]{"SEND_SMS","READ_SMS","READ_PHONE_STATE","ACCESS_NOTIFICATION_POLICY","RECEIVE_BOOT_COMPLETED","READ_CALL_LOG","READ_CONTACTS","CALL_PHONE"}) assertTrue(x,p.contains("android.permission."+x));
    assertFalse("인터넷 권한 없음",p.contains("android.permission.INTERNET"));
    assertFalse(p.contains("android.permission.WRITE_SMS")); assertFalse(p.contains("android.permission.RECEIVE_SMS")); assertFalse(p.contains("android.permission.SCHEDULE_EXACT_ALARM")); assertFalse(p.contains("android.permission.USE_EXACT_ALARM"));
    assertFalse(p.contains("android.permission.CHANGE_NETWORK_STATE")); assertFalse("항공기 모드는 건드리지 않음",p.contains("android.permission.WRITE_SECURE_SETTINGS"));
  }
  @Test public void receiversAndShortcutExposure(){
    assertEquals("false",byName("receiver",".ModeActionReceiver").getAttributeNS(A,"exported"));
    assertEquals("true",byName("receiver",".ModeSystemReceiver").getAttributeNS(A,"exported"));
    Element sc=byName("activity",".ModeShortcutActivity"); assertEquals("true",sc.getAttributeNS(A,"exported"));
    assertEquals("전화 화면 없이 목록(최근 앱)에 안 남게","true",sc.getAttributeNS(A,"excludeFromRecents"));
    assertEquals("",sc.getAttributeNS(A,"launchMode"));   // singleTask 면 호출한 앱 이름을 받을 수 없음
    assertEquals("0",""+sc.getElementsByTagName("intent-filter").getLength());   // 인텐트 필터 없음 = 이름을 아는 앱만 부를 수 있음
    NodeList acts=byName("receiver",".ModeSystemReceiver").getElementsByTagName("action"); Set<String> a=new HashSet<>(); for(int i=0;i<acts.getLength();i++) a.add(((Element)acts.item(i)).getAttributeNS(A,"name"));
    assertTrue(a.contains("android.intent.action.BOOT_COMPLETED")); assertTrue(a.contains("android.intent.action.MY_PACKAGE_REPLACED")); assertTrue(a.contains("android.intent.action.PHONE_STATE"));
  }
  @Test public void noYuniAnywhereInUserFacingStrings() throws Exception {
    String all=new String(java.nio.file.Files.readAllBytes(new File("../../AndroidManifest.xml").toPath()),"UTF-8");
    assertFalse(all.contains("유니")); 
    for(File f:new File("../../src/kr/newniverse/phone").listFiles((d,n)->n.startsWith("Mode")||n.equals("Dnd.java")||n.equals("CallTracker.java")))
      assertFalse(f.getName(),new String(java.nio.file.Files.readAllBytes(f.toPath()),"UTF-8").contains("유니"));
    String html=new String(java.nio.file.Files.readAllBytes(new File("../../assets/www/index.html").toPath()),"UTF-8");
    assertFalse("화면 글에 「유니」 없음",html.contains("유니"));
  }
  @Test public void iconShortcutsOnlyOpenTheConfirmScreen() throws Exception {
    String m=new String(java.nio.file.Files.readAllBytes(new File("../../AndroidManifest.xml").toPath()),"UTF-8");
    assertTrue(m.contains("android.app.shortcuts")); assertTrue(m.contains("@xml/shortcuts"));
    String x=new String(java.nio.file.Files.readAllBytes(new File("../../res/xml/shortcuts.xml").toPath()),"UTF-8");
    assertEquals(2,x.split("<shortcut ").length-1);
    assertEquals("바로가기는 켜는 것이 아니라 화면만 열어요",2,x.split("targetClass=\"kr.newniverse.phone.MainActivity\"").length-1);
    assertFalse("바로가기가 곧바로 켜는 문을 부르면 안 됨",x.contains("ModeShortcutActivity"));
    assertTrue(x.contains("value=\"ask\""));
    String st=new String(java.nio.file.Files.readAllBytes(new File("../../res/values/modes_strings.xml").toPath()),"UTF-8");
    assertTrue(st.contains("운전 중")&&st.contains("회의 중")); assertFalse(st.contains("유니"));
  }
}
