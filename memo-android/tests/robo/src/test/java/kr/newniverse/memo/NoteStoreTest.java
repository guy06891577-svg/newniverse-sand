package kr.newniverse.memo;

import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** 메모 보관함: 저장·찾기·지우기·깨진 파일 복구·재시작 후 유지 */
public class NoteStoreTest {
  @Rule public TemporaryFolder tmp=new TemporaryFolder();
  File dir; NoteStore s;
  @Before public void up() throws Exception { dir=tmp.newFolder("memo"); s=new NoteStore(dir); }

  static Note n(String text,long at){ Note n=new Note(); n.text=text; n.createdAt=at; n.durationMs=4200; return n; }

  @Test public void addAndGet(){
    Note a=s.add(n("내일 9시에 알려줘",1000));
    assertTrue(a.id>0); assertEquals("내일 9시에 알려줘",s.get(a.id).text); assertEquals(4200,s.get(a.id).durationMs);
    assertEquals(Note.SPEECH,a.kind); assertEquals(0,a.remindAt);
  }
  @Test public void uniqueIdsEvenAtSameMillisecond(){
    Note a=s.add(n("a",5000)), b=s.add(n("b",5000)), c=s.add(n("c",5000));
    assertEquals(3,new HashSet<>(Arrays.asList(a.id,b.id,c.id)).size());
  }
  @Test public void newestFirst(){
    s.add(n("옛날",1000)); s.add(n("지금",3000)); s.add(n("중간",2000));
    List<Note> l=s.all(); assertEquals("지금",l.get(0).text); assertEquals("중간",l.get(1).text); assertEquals("옛날",l.get(2).text);
  }
  @Test public void searchAllWordsKoreanSubstring(){
    s.add(n("내일 오전 9시에 치과 예약",1)); s.add(n("우유랑 계란 사기",2)); s.add(n("치과 끝나고 약국",3));
    assertEquals(2,s.search("치과").size());
    assertEquals(1,s.search("치과 약국").size());      // 낱말이 모두 들어간 것만
    assertEquals(1,s.search("계란").size());
    assertEquals(3,s.search("").size()); assertEquals(3,s.search(null).size()); assertEquals(3,s.search("  ").size());
    assertEquals(0,s.search("없는말").size());
    s.add(n("Call Mom",4)); assertEquals(1,s.search("call mom").size()); // 대소문자 무시
  }
  @Test public void updateAndDelete() throws Exception {
    Note a=s.add(n("고칠 글",1)); a.text="고친 글"; a.remindAt=99; assertTrue(s.update(a));
    assertEquals("고친 글",s.get(a.id).text); assertEquals(99,s.get(a.id).remindAt);
    File f=new File(s.audioDir,"x.m4a"); Files.write(f.toPath(),new byte[2000]); a.audio="x.m4a"; s.update(a);
    assertTrue(s.delete(a.id)); assertNull(s.get(a.id)); assertFalse("소리 파일도 같이 지워져야 해요",f.exists());
    assertFalse(s.delete(a.id));
    assertFalse(s.update(a));
  }
  @Test public void survivesRestart(){
    Note a=s.add(n("남아야 해요",1)); a.remindAt=555; a.remindExact=true; s.update(a);
    NoteStore s2=new NoteStore(dir);
    assertEquals(1,s2.all().size()); Note b=s2.get(a.id); assertEquals("남아야 해요",b.text); assertEquals(555,b.remindAt); assertTrue(b.remindExact);
  }
  @Test public void pendingReminders(){
    Note a=s.add(n("a",1)); a.remindAt=10; s.update(a);
    Note b=s.add(n("b",2)); b.remindAt=20; b.reminded=true; s.update(b);
    s.add(n("c",3));
    List<Note> p=s.pendingReminders(); assertEquals(1,p.size()); assertEquals("a",p.get(0).text);
  }
  @Test public void corruptFileRecoversFromBackup() throws Exception {
    s.add(n("첫째",1)); s.add(n("둘째",2)); // 두 번 저장 → .bak 에 첫째만 든 정상본
    Files.write(new File(dir,"notes.json").toPath(),"{깨진 파일".getBytes(StandardCharsets.UTF_8));
    NoteStore s2=new NoteStore(dir);
    assertEquals("백업에서 되살려야 해요",1,s2.all().size());
    File[] bad=dir.listFiles((d,name)->name.startsWith("notes.corrupt-")); assertEquals("깨진 파일은 따로 보관",1,bad.length);
    s2.add(n("새 글",3)); assertEquals(2,new NoteStore(dir).all().size());
  }
  @Test public void corruptWithoutBackupStartsEmptyButKeepsFile() throws Exception {
    Files.write(new File(dir,"notes.json").toPath(),"not json".getBytes());
    NoteStore s2=new NoteStore(dir); assertEquals(0,s2.all().size());
    assertEquals(1,dir.listFiles((d,name)->name.startsWith("notes.corrupt-")).length);
  }
  @Test public void emptyStore(){ assertEquals(0,s.all().size()); assertNull(s.get(1)); assertEquals(0,s.pendingReminders().size()); }
  @Test public void audioFileNameCannotEscapeFolder(){
    assertEquals(s.audioDir,s.audioFile("../../etc/passwd").getParentFile());
  }
  @Test public void koreanAndEmojiTextRoundTrip(){
    Note a=s.add(n("안녕하세요 \"따옴표\" 와 줄바꿈\n다음 줄 😀",1));
    assertEquals("안녕하세요 \"따옴표\" 와 줄바꿈\n다음 줄 😀",new NoteStore(dir).get(a.id).text);
  }
  @Test public void viewHidesAudioPath(){
    Note a=n("x",1); a.audio="secret.m4a"; org.json.JSONObject v=a.toView();
    assertFalse(v.has("audio")); assertTrue(v.optBoolean("hasAudio"));
  }
  @Test public void noTempFileLeftBehind(){
    s.add(n("a",1)); s.add(n("b",2));
    assertFalse(new File(dir,"notes.json.tmp").exists());
  }
}
