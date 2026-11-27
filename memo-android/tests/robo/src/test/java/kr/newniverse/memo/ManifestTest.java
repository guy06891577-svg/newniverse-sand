package kr.newniverse.memo;

import static org.junit.Assert.*;
import java.io.File;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.*;
import org.w3c.dom.*;

/** 매니페스트 약속: 패키지·잠금 화면 표시·권한(인터넷 없음)·알림 수신기·한국어 이름('유니' 금지) */
public class ManifestTest {
  static final String A="http://schemas.android.com/apk/res/android";
  static Document doc; static String raw;
  @BeforeClass public static void load() throws Exception {
    File f=new File("../../AndroidManifest.xml");
    raw=new String(java.nio.file.Files.readAllBytes(f.toPath()),"UTF-8");
    DocumentBuilderFactory fac=DocumentBuilderFactory.newInstance(); fac.setNamespaceAware(true);
    doc=fac.newDocumentBuilder().parse(f);
  }
  static Set<String> perms(){ Set<String> s=new HashSet<>(); NodeList l=doc.getElementsByTagName("uses-permission"); for(int i=0;i<l.getLength();i++) s.add(((Element)l.item(i)).getAttributeNS(A,"name")); return s; }
  static Element activity(){ return (Element)doc.getElementsByTagName("activity").item(0); }

  @Test public void packageAndSdk(){
    assertEquals("kr.newniverse.memo",doc.getDocumentElement().getAttribute("package"));
    Element u=(Element)doc.getElementsByTagName("uses-sdk").item(0);
    assertEquals("26",u.getAttributeNS(A,"minSdkVersion")); assertEquals("34",u.getAttributeNS(A,"targetSdkVersion"));
  }
  @Test public void showsOverLockScreenAndTurnsScreenOn(){
    Element a=activity();
    assertEquals("true",a.getAttributeNS(A,"showWhenLocked")); assertEquals("true",a.getAttributeNS(A,"turnScreenOn"));
    assertEquals("true",a.getAttributeNS(A,"exported"));
    assertEquals("singleTask",a.getAttributeNS(A,"launchMode"));
    NodeList cats=a.getElementsByTagName("category"); boolean launcher=false;
    for(int i=0;i<cats.getLength();i++) if("android.intent.category.LAUNCHER".equals(((Element)cats.item(i)).getAttributeNS(A,"name"))) launcher=true;
    assertTrue("‘앱 열기’ 목록에 뜨려면 LAUNCHER 가 있어야 해요",launcher);
  }
  @Test public void permissions(){
    Set<String> p=perms();
    for(String x:new String[]{"RECORD_AUDIO","VIBRATE","POST_NOTIFICATIONS","SCHEDULE_EXACT_ALARM","RECEIVE_BOOT_COMPLETED","USE_FULL_SCREEN_INTENT"}) assertTrue(x,p.contains("android.permission."+x));
    assertFalse("인터넷 권한이 없어야 해요",p.contains("android.permission.INTERNET"));
    assertFalse(p.contains("android.permission.READ_SMS")); assertFalse(p.contains("android.permission.WRITE_EXTERNAL_STORAGE"));
    assertFalse(p.contains("android.permission.USE_EXACT_ALARM"));
  }
  @Test public void recognitionServiceVisibility(){ // 안드로이드 11+ 에서 음성 인식기를 찾으려면 필요
    assertTrue(raw.contains("android.speech.RecognitionService"));
  }
  @Test public void receivers(){
    NodeList l=doc.getElementsByTagName("receiver"); Map<String,String> ex=new HashMap<>();
    for(int i=0;i<l.getLength();i++){ Element e=(Element)l.item(i); ex.put(e.getAttributeNS(A,"name"),e.getAttributeNS(A,"exported")); }
    assertEquals("false",ex.get(".ReminderReceiver")); assertEquals("true",ex.get(".BootReceiver"));
    assertTrue(raw.contains("BOOT_COMPLETED"));
  }
  @Test public void nameAndPrivacy(){
    assertTrue(raw.contains("android:label=\"뉴니버스 빠른 메모\""));
    assertEquals("false",((Element)doc.getElementsByTagName("application").item(0)).getAttributeNS(A,"allowBackup"));
  }
  @Test public void noForbiddenWordInSources() throws Exception {
    // 앱 이름·화면 글은 ‘뉴니버스’만. ‘유니’는 어디에도 없어야 해요 (‘뉴니버스’ 안의 글자는 제외)
    List<File> fs=new ArrayList<>(); collect(new File("../../src"),fs); collect(new File("../../assets"),fs); fs.add(new File("../../AndroidManifest.xml"));
    for(File f:fs){ String t=new String(java.nio.file.Files.readAllBytes(f.toPath()),"UTF-8").replace("뉴니버스","");
      assertFalse(f.getName()+" 에 ‘유니’가 있어요",t.contains("유니")); }
  }
  static void collect(File d,List<File> out){ File[] l=d.listFiles(); if(l==null) return; for(File f:l){ if(f.isDirectory()) collect(f,out); else if(f.getName().matches(".*\\.(java|html|xml)")) out.add(f); } }
}
