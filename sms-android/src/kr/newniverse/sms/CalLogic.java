package kr.newniverse.sms;

import java.util.*;

/** v2: 달력에 넣기·고치기·빼기 규칙(순수 자바). 실제 달력 쓰기는 Store 가 함 (CalStore). */
final class CalLogic {
  static final class Entry { long id; String sender="", org="", title=""; long start; boolean allDay, undone;
    Entry copy(){ Entry e=new Entry(); e.id=id; e.sender=sender; e.org=org; e.title=title; e.start=start; e.allDay=allDay; e.undone=undone; return e; } }
  interface Store { List<Entry> load(); void save(List<Entry> l); long insert(Entry e); void update(Entry e); void delete(Entry e); }
  static final class Out { String op="none"; Entry e, prev; }

  static String dg(String s){ return s==null?"":s.replaceAll("[^0-9+]",""); }
  static Entry find(List<Entry> l,String sender,String org,long day){
    for(Entry e:l) if(!e.undone&&e.id>0&&e.org.equals(org)&&dg(e.sender).equals(dg(sender))&&ApptParser.dayOf(e.start)==day) return e; return null; }

  static Out apply(Store s,ApptParser.R r,String sender,boolean manual){
    Out o=new Out(); List<Entry> l=s.load();
    if(r.kind==ApptParser.CANCEL){ Entry m=find(l,sender,r.org,ApptParser.dayOf(r.when)); if(m==null) return o;
      o.prev=m.copy(); s.delete(m); l.remove(m); s.save(l); o.op="delete"; return o; }
    if(r.kind==ApptParser.CHANGE&&r.oldWhen!=0){ Entry m=find(l,sender,r.org,ApptParser.dayOf(r.oldWhen));
      if(m!=null){ o.prev=m.copy(); m.start=r.when; m.allDay=!r.hasTime; s.update(m); s.save(l); o.e=m; o.op="update"; return o; } }
    long start=r.when; boolean all=!r.hasTime||r.kind==ApptParser.PARCEL;
    for(Entry e:l) if(e.org.equals(r.org)&&e.start==start&&e.allDay==all){
      if(e.undone&&manual){ l.remove(e); break; }
      o.op="dup"; o.e=e; return o; }
    Entry n=new Entry(); n.sender=sender==null?"":sender; n.org=r.org; n.title=r.title; n.start=start; n.allDay=all;
    n.id=s.insert(n); if(n.id<=0) return o;
    l.add(n); while(l.size()>300) l.remove(0); s.save(l); o.e=n; o.op="add"; return o;
  }
  /** 알림의 '취소' */
  static void undo(Store s,String op,Entry e,Entry prev){
    List<Entry> l=s.load();
    if("add".equals(op)&&e!=null){ Entry m=null; for(Entry x:l) if(x.id==e.id) m=x; if(m!=null){ s.delete(m); m.undone=true; m.id=-System.nanoTime()%1000000-1; } else { Entry t=e.copy(); s.delete(e); t.undone=true; t.id=-1; l.add(t); } s.save(l); }
    else if("update".equals(op)&&e!=null&&prev!=null){ for(Entry x:l) if(x.id==e.id){ x.start=prev.start; x.allDay=prev.allDay; s.update(x); } s.save(l); }
    else if("delete".equals(op)&&prev!=null){ Entry n=prev.copy(); n.id=s.insert(n); if(n.id>0){ l.add(n); s.save(l); } }
  }
}
