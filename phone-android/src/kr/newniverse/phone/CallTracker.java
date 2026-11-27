package kr.newniverse.phone;

import android.content.Context;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 걸려오는 전화마다 「받았는지 · 문자로 거절했는지 · 조용히 처리했는지」를 적어 두었다가, 전화가 끝나면
 * 안 받은 전화(부재중·거절)를 상황 모드의 자동 답장 판단으로 넘겨요. (전화 객체 대신 아무 키나 받아서 시험하기 쉬움)
 */
final class CallTracker {
  static final class Track { String num=""; boolean incoming, quiet, answered, msg; }
  final Map<Object,Track> m=new ConcurrentHashMap<>();

  /** 전화가 생겼을 때. 걸려오는 전화이고 상황 모드가 이 번호를 조용히 처리하라고 하면 quiet=true */
  Track added(Context c,Object call,String num,boolean ringing,boolean active){
    Track t=new Track(); t.incoming=ringing; t.num=num==null?"":num; t.answered=active;
    if(ringing){ try{ ModeManager.tick(c); t.quiet=ModeManager.quiet(c,t.num); }catch(Exception e){} }
    m.put(call,t);
    return t;
  }
  Track get(Object call){ return call==null?null:m.get(call); }
  void active(Object call){ Track t=get(call); if(t!=null) t.answered=true; }
  void rejectedWithMessage(Object call){ Track t=get(call); if(t!=null) t.msg=true; }
  /** 전화가 끝났을 때: 걸려온 전화를 안 받았으면 자동 답장 판단으로 */
  void removed(Context c,Object call){
    Track t=call==null?null:m.remove(call);
    if(t!=null&&t.incoming&&!t.answered){ try{ ModeManager.onUnanswered(c,t.num,t.msg); }catch(Exception e){} }
  }
}
