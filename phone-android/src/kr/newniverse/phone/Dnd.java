package kr.newniverse.phone;

import android.app.NotificationManager;
import android.content.Context;
import org.json.JSONObject;

/**
 * 방해 금지(DND) 켜고 되돌리기. 「방해 금지 접근」을 사용자가 한 번 허용해야 해요(앱이 스스로 켤 수 없음).
 * 켜기 전의 상태를 적어 두었다가, 끌 때 그대로 되돌려요. 이미 더 엄격한 방해 금지(알람만·완전 무음)가 켜져 있으면 건드리지 않아요.
 * 알람은 항상 울려요.
 */
final class Dnd {
  private Dnd(){}

  static boolean granted(Context c){
    try{ NotificationManager nm=c.getSystemService(NotificationManager.class); return nm!=null&&nm.isNotificationPolicyAccessGranted(); }catch(Exception e){ return false; }
  }

  /** 켜기. 반환: 되돌릴 때 쓸 이전 상태(JSON). 허용이 없거나 실패하면 null. "keep":true 면 더 엄격한 설정이 이미 켜져 있어 그대로 둔 것 */
  static JSONObject apply(Context c,String allow,boolean repeat){
    try{
      NotificationManager nm=c.getSystemService(NotificationManager.class);
      if(nm==null||!nm.isNotificationPolicyAccessGranted()) return null;
      NotificationManager.Policy op=nm.getNotificationPolicy();
      int f=nm.getCurrentInterruptionFilter();
      JSONObject prev=new JSONObject();
      prev.put("f",f);
      if(op!=null){ prev.put("c",op.priorityCategories); prev.put("cs",op.priorityCallSenders); prev.put("ms",op.priorityMessageSenders); prev.put("sv",op.suppressedVisualEffects); }
      if(f==NotificationManager.INTERRUPTION_FILTER_NONE||f==NotificationManager.INTERRUPTION_FILTER_ALARMS){ prev.put("keep",true); return prev; }
      set(nm,op,allow,repeat);
      return prev;
    }catch(Exception e){ return null; }
  }

  /** 모드가 켜진 채로 「울릴 사람」「급한 전화」 설정을 바꿨을 때: 이전 상태 기록은 그대로 두고 규칙만 다시 적용 */
  static void reapply(Context c,String allow,boolean repeat){
    try{
      NotificationManager nm=c.getSystemService(NotificationManager.class);
      if(nm==null||!nm.isNotificationPolicyAccessGranted()) return;
      set(nm,nm.getNotificationPolicy(),allow,repeat);
    }catch(Exception e){}
  }

  private static void set(NotificationManager nm,NotificationManager.Policy op,String allow,boolean repeat){
    int cat=NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS;   // 알람은 항상 울림
    int cs=op!=null?op.priorityCallSenders:NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS;
    if(ModeDefs.ALLOW_STARRED.equals(allow)){ cat|=NotificationManager.Policy.PRIORITY_CATEGORY_CALLS; cs=NotificationManager.Policy.PRIORITY_SENDERS_STARRED; }
    else if(ModeDefs.ALLOW_CONTACTS.equals(allow)){ cat|=NotificationManager.Policy.PRIORITY_CATEGORY_CALLS; cs=NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS; }
    if(repeat) cat|=NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS;
    nm.setNotificationPolicy(new NotificationManager.Policy(cat,cs,op!=null?op.priorityMessageSenders:NotificationManager.Policy.PRIORITY_SENDERS_ANY,op!=null?op.suppressedVisualEffects:0));
    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
  }

  /** 끄기: 켜기 전 상태로. 사용자가 중간에 직접 방해 금지를 끄거나 바꿨으면 그 선택을 존중해서 다시 켜지 않아요 */
  static void restore(Context c,JSONObject prev){
    if(prev==null||prev.optBoolean("keep")) return;
    try{
      NotificationManager nm=c.getSystemService(NotificationManager.class);
      if(nm==null||!nm.isNotificationPolicyAccessGranted()) return;
      int now=nm.getCurrentInterruptionFilter();
      if(now==NotificationManager.INTERRUPTION_FILTER_PRIORITY){
        int pf=prev.optInt("f",NotificationManager.INTERRUPTION_FILTER_ALL);
        nm.setInterruptionFilter(pf==NotificationManager.INTERRUPTION_FILTER_UNKNOWN?NotificationManager.INTERRUPTION_FILTER_ALL:pf);
      }
      if(prev.has("c")){
        nm.setNotificationPolicy(new NotificationManager.Policy(prev.optInt("c"),prev.optInt("cs"),prev.optInt("ms"),prev.optInt("sv")));
      }
    }catch(Exception e){}
  }
}
