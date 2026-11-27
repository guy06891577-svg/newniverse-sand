package kr.newniverse.memo;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 폰 안에만 저장하는 메모 보관함(JSON 파일 하나). 인터넷 권한이 없는 앱이라 밖으로 나가지 않아요.
 * 쓰기: 임시 파일에 쓰고 → 바꿔치기(중간에 꺼져도 파일이 깨지지 않게) · 직전 정상본은 .bak 으로 남겨요.
 * 읽다가 깨진 파일을 만나면 .bak 으로 복구하고, 그것도 안 되면 깨진 파일을 따로 보관하고 빈 목록으로 시작해요.
 */
final class NoteStore {
  final File dir, file, bak, audioDir;
  private List<Note> cache;

  NoteStore(File dir){
    this.dir=dir; dir.mkdirs();
    file=new File(dir,"notes.json"); bak=new File(dir,"notes.json.bak"); audioDir=new File(dir,"audio"); audioDir.mkdirs();
  }

  synchronized List<Note> all(){
    load();
    List<Note> r=new ArrayList<>(cache);
    Collections.sort(r,(a,b)->Long.compare(b.createdAt,a.createdAt)!=0?Long.compare(b.createdAt,a.createdAt):Long.compare(b.id,a.id));
    return r;
  }
  synchronized Note get(long id){ load(); for(Note n:cache) if(n.id==id) return n; return null; }

  synchronized Note add(Note n){
    load();
    if(n.id<=0) n.id=n.createdAt>0?n.createdAt:System.currentTimeMillis();
    while(get(n.id)!=null) n.id++;
    if(n.createdAt<=0) n.createdAt=n.id;
    cache.add(n); save();
    return n;
  }
  synchronized boolean update(Note n){
    load();
    for(int i=0;i<cache.size();i++) if(cache.get(i).id==n.id){ cache.set(i,n); save(); return true; }
    return false;
  }
  synchronized boolean delete(long id){
    load();
    for(int i=0;i<cache.size();i++) if(cache.get(i).id==id){
      Note n=cache.remove(i);
      if(!n.audio.isEmpty()) audioFile(n.audio).delete();
      save(); return true;
    }
    return false;
  }
  /** 띄어쓰기로 나눈 낱말이 모두 들어 있는 메모(대소문자 무시). 빈 검색어면 전부. */
  synchronized List<Note> search(String q){
    List<Note> all=all();
    if(q==null||q.trim().isEmpty()) return all;
    String[] ws=q.trim().toLowerCase(Locale.ROOT).split("\\s+");
    List<Note> r=new ArrayList<>();
    for(Note n:all){
      String t=n.text.toLowerCase(Locale.ROOT); boolean ok=true;
      for(String w:ws) if(!t.contains(w)){ ok=false; break; }
      if(ok) r.add(n);
    }
    return r;
  }
  /** 알림이 맞춰져 있고 아직 안 울린 메모 */
  synchronized List<Note> pendingReminders(){
    List<Note> r=new ArrayList<>();
    for(Note n:all()) if(n.remindAt>0&&!n.reminded) r.add(n);
    return r;
  }
  File audioFile(String name){ return new File(audioDir,new File(name).getName()); } // 이름만 받아서 폴더 밖으로 못 나감

  // ---------- 파일 ----------
  /** 알림 수신기·화면이 각자 이 보관함을 열 수 있어서, 매번 파일에서 새로 읽어요(작은 파일이라 빨라요) */
  private void load(){
    cache=readFile(file);
    if(cache==null){
      if(file.exists()){ file.renameTo(new File(dir,"notes.corrupt-"+System.currentTimeMillis()+".json")); }
      cache=readFile(bak);
      if(cache==null) cache=new ArrayList<>(); else save();
    }
  }
  private static List<Note> readFile(File f){
    if(!f.exists()) return f.getName().equals("notes.json")?new ArrayList<>():null;
    try{
      byte[] b=new byte[(int)f.length()]; try(FileInputStream in=new FileInputStream(f)){ int o=0,k; while(o<b.length&&(k=in.read(b,o,b.length-o))>0) o+=k; }
      JSONArray a=new JSONObject(new String(b,StandardCharsets.UTF_8)).getJSONArray("notes");
      List<Note> r=new ArrayList<>(); for(int i=0;i<a.length();i++) r.add(Note.fromJson(a.getJSONObject(i)));
      return r;
    }catch(Exception e){ return null; }
  }
  private void save(){
    try{
      JSONArray a=new JSONArray(); for(Note n:cache) a.put(n.toJson());
      byte[] b=new JSONObject().put("v",1).put("notes",a).toString().getBytes(StandardCharsets.UTF_8);
      File tmp=new File(dir,"notes.json.tmp");
      try(FileOutputStream o=new FileOutputStream(tmp)){ o.write(b); o.getFD().sync(); }
      if(file.exists()){ bak.delete(); file.renameTo(bak); }
      if(!tmp.renameTo(file)) throw new IOException("rename");
    }catch(Exception e){ throw new RuntimeException("save failed: "+e,e); }
  }
}
