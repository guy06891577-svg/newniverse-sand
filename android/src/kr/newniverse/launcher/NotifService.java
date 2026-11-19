package kr.newniverse.launcher;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import org.json.JSONArray;
import org.json.JSONObject;

/** v29 알림 배지·미리보기. 알림 내용은 이 폰 안(런처 화면)에서만 보여 주고, 저장하거나 밖으로 보내지 않음. */
public class NotifService extends NotificationListenerService {
  static volatile NotifService inst;
  static volatile Runnable onChange;
  @Override public void onListenerConnected(){ inst=this; fire(); }
  @Override public void onListenerDisconnected(){ inst=null; fire(); }
  @Override public void onDestroy(){ inst=null; super.onDestroy(); }
  @Override public void onNotificationPosted(StatusBarNotification s){ fire(); }
  @Override public void onNotificationRemoved(StatusBarNotification s){ fire(); }
  static void fire(){ Runnable r=onChange; if(r!=null) r.run(); }

  static boolean counts(StatusBarNotification s){
    Notification n=s.getNotification(); if(n==null) return false;
    if((n.flags&Notification.FLAG_GROUP_SUMMARY)!=0) return false;
    if(s.isOngoing()) return false;
    if((n.flags&Notification.FLAG_FOREGROUND_SERVICE)!=0) return false;
    return true;
  }
  static StatusBarNotification[] act(){ NotifService i=inst; if(i==null) return new StatusBarNotification[0]; try{ StatusBarNotification[] a=i.getActiveNotifications(); return a==null?new StatusBarNotification[0]:a; }catch(Exception e){ return new StatusBarNotification[0]; } }
  /** {pkg:개수} */
  static String summary(){ JSONObject o=new JSONObject(); try{ for(StatusBarNotification s:act()){ if(!counts(s)) continue; String p=s.getPackageName(); o.put(p,o.optInt(p,0)+1); } }catch(Exception e){} return o.toString(); }
  static String cut(CharSequence c,int n){ if(c==null) return ""; String s=c.toString().replace('\n',' ').trim(); return s.length()>n?s.substring(0,n)+"…":s; }
  /** 한 앱의 최근 알림 (최신순, 최대 5개) */
  static String list(String pkg){ JSONArray a=new JSONArray(); try{ java.util.List<StatusBarNotification> L=new java.util.ArrayList<>();
      for(StatusBarNotification s:act()) if(pkg.equals(s.getPackageName())&&counts(s)) L.add(s);
      java.util.Collections.sort(L,(x,y)->Long.compare(y.getPostTime(),x.getPostTime()));
      for(int k=0;k<Math.min(5,L.size());k++){ StatusBarNotification s=L.get(k); android.os.Bundle e=s.getNotification().extras;
        CharSequence t=e.getCharSequence(Notification.EXTRA_TITLE), x=e.getCharSequence(Notification.EXTRA_BIG_TEXT); if(x==null) x=e.getCharSequence(Notification.EXTRA_TEXT);
        JSONObject o=new JSONObject(); o.put("key",s.getKey()); o.put("t",cut(t,40)); o.put("x",cut(x,90)); o.put("at",s.getPostTime()); a.put(o); }
    }catch(Exception e){} return a.toString(); }
  static StatusBarNotification byKey(String key){ for(StatusBarNotification s:act()) if(s.getKey().equals(key)) return s; return null; }
}
