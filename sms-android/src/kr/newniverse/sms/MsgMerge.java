package kr.newniverse.sms;

import java.util.*;

/**
 * v4: 문자(SMS)와 사진·장문(MMS)을 한 줄로 합치기 (순수 자바).
 *  - 대화: 날짜 오름차순, 같은 시각이면 SMS 먼저·번호 순, 마지막 lim 개
 *  - 목록: 대화(thread)마다 가장 최근 것 하나, 안 읽은 수·개수
 *  - 런처: 날짜 내림차순, FeedFilter 로 거른 것만 max 개
 * MMS 날짜는 저장소에서 초 단위 → 여기 들어올 때는 이미 밀리초.
 */
final class MsgMerge {
  static final class Item {
    final boolean mms; final long id, thread, date; final boolean in, unread; String addr="", text=""; int box;
    Item(boolean mms,long id,long thread,long dateMs,boolean in,boolean unread){ this.mms=mms; this.id=id; this.thread=thread; this.date=dateMs; this.in=in; this.unread=unread; }
    Item with(String addr,String text){ this.addr=addr==null?"":addr; this.text=text==null?"":text; return this; }
    String key(){ return (mms?"m":"s")+id; }
  }
  static final Comparator<Item> ASC=(a,b)->{ int c=Long.compare(a.date,b.date); if(c!=0) return c; if(a.mms!=b.mms) return a.mms?1:-1; return Long.compare(a.id,b.id); };

  static List<Item> conversation(List<Item> sms,List<Item> mms,int lim){
    List<Item> l=new ArrayList<>(); if(sms!=null) l.addAll(sms); if(mms!=null) l.addAll(mms);
    l.sort(ASC);
    int from=Math.max(0,l.size()-Math.max(1,lim));
    return new ArrayList<>(l.subList(from,l.size()));
  }

  static final class Conv {
    final long id; Item last; int unread, n; long lastIn;
    Conv(long id){ this.id=id; }
  }
  static List<Conv> threads(List<Item> sms,List<Item> mms){
    Map<Long,Conv> m=new HashMap<>();
    List<Item> all=new ArrayList<>(); if(sms!=null) all.addAll(sms); if(mms!=null) all.addAll(mms);
    for(Item it:all){
      Conv t=m.computeIfAbsent(it.thread,Conv::new);
      t.n++; if(it.unread&&it.in) t.unread++;
      if(it.in&&it.date>t.lastIn) t.lastIn=it.date;
      if(t.last==null||ASC.compare(it,t.last)>0) t.last=it;
    }
    List<Conv> l=new ArrayList<>(m.values());
    l.sort((a,b)->ASC.compare(b.last,a.last));
    return l;
  }

  static List<Item> feed(List<Item> sms,List<Item> mms,int max){
    List<Item> l=new ArrayList<>(); if(sms!=null) l.addAll(sms); if(mms!=null) l.addAll(mms);
    l.sort(ASC.reversed());
    List<Item> out=new ArrayList<>();
    for(Item it:l){ if(out.size()>=max) break; if(it.in&&FeedFilter.relevant(it.text)) out.add(it); }
    return out;
  }
}
