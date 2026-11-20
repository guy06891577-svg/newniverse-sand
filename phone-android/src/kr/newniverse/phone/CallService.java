package kr.newniverse.phone;

import android.app.*;
import android.content.*;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.*;
import android.telecom.*;
import org.json.*;
import java.util.*;

/** 기본 전화 앱이 되면 시스템(Telecom)이 이 서비스에 통화를 넘겨줌. 받기·거절·끊기·음소거·스피커·키패드 신호를 여기서 처리 */
public class CallService extends InCallService {
  interface Listener{ void onCallsChanged(); }
  static volatile CallService inst;
  static final List<Call> calls=Collections.synchronizedList(new ArrayList<>());
  static volatile Listener listener;
  static final Handler H=new Handler(Looper.getMainLooper());
  static final Map<String,String> names=new HashMap<>();
  static final int N_IN=11, N_ON=12;

  private final Call.Callback cb=new Call.Callback(){
    @Override public void onStateChanged(Call c,int st){ changed(); }
    @Override public void onDetailsChanged(Call c,Call.Details d){ changed(); }
  };

  @Override public void onCreate(){ super.onCreate(); inst=this; Nv.channels(this); }
  @Override public void onDestroy(){ if(inst==this) inst=null; NotificationManager nm=getSystemService(NotificationManager.class); if(nm!=null){nm.cancel(N_IN);nm.cancel(N_ON);} super.onDestroy(); }

  @Override public void onCallAdded(Call c){
    calls.add(c); c.registerCallback(cb);
    refreshNotifs();
    // 수신·발신 모두 통화 화면을 띄움 (InCallService는 백그라운드에서도 화면을 띄울 수 있는 예외 대상)
    openUi(this);
    tell();
  }
  @Override public void onCallRemoved(Call c){ c.unregisterCallback(cb); calls.remove(c); refreshNotifs(); tell(); }
  @Override public void onCallAudioStateChanged(CallAudioState s){ tell(); }

  private void changed(){ refreshNotifs(); tell(); }
  static void tell(){ Listener l=listener; if(l!=null) l.onCallsChanged(); }

  static void openUi(Context c){
    try{ Intent i=new Intent(c,CallActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_USER_ACTION); c.startActivity(i); }catch(Exception e){}
  }

  // ---------- 상태 ----------
  @SuppressWarnings("deprecation")
  static int st(Call c){ return Build.VERSION.SDK_INT>=31?c.getDetails().getState():c.getState(); }
  static String stName(int s){
    switch(s){ case Call.STATE_RINGING: return "ringing"; case Call.STATE_DIALING: return "dialing"; case Call.STATE_CONNECTING: return "connecting";
      case Call.STATE_SELECT_PHONE_ACCOUNT: return "connecting"; case Call.STATE_ACTIVE: return "active"; case Call.STATE_HOLDING: return "holding";
      case Call.STATE_DISCONNECTING: return "ending"; case Call.STATE_DISCONNECTED: return "ended"; default: return "other"; }
  }
  static String num(Call c){ try{ Uri h=c.getDetails().getHandle(); return h==null?"":h.getSchemeSpecificPart(); }catch(Exception e){ return ""; } }
  static String name(Context ctx,Call c){
    Call.Details d=c.getDetails();
    if(Build.VERSION.SDK_INT>=30){ String n=d.getContactDisplayName(); if(n!=null&&!n.isEmpty()) return n; }
    String k=num(c); if(k.isEmpty()) return "";
    synchronized(names){ if(names.containsKey(k)) return names.get(k); }
    String n=Nv.lookupName(ctx,k); synchronized(names){ names.put(k,n==null?"":n); } return n==null?"":n;
  }
  static Call ringing(){ synchronized(calls){ for(Call c:calls) if(st(c)==Call.STATE_RINGING) return c; } return null; }
  /** 화면에 크게 보일 통화: 진행 중 > 연결 중 > 대기 중 */
  static Call live(){
    Call best=null; int bp=-1;
    synchronized(calls){ for(Call c:calls){ int s=st(c),p= s==Call.STATE_ACTIVE?4: (s==Call.STATE_DIALING||s==Call.STATE_CONNECTING||s==Call.STATE_SELECT_PHONE_ACCOUNT)?3: s==Call.STATE_HOLDING?2: s==Call.STATE_DISCONNECTING?1:-1; if(p>bp){bp=p;best=c;} } }
    return best;
  }
  static Call holding(){ synchronized(calls){ for(Call c:calls) if(st(c)==Call.STATE_HOLDING) return c; } return null; }

  static String stateJson(Context ctx){
    JSONObject o=new JSONObject();
    try{
      JSONArray a=new JSONArray();
      synchronized(calls){ for(Call c:calls){ Call.Details d=c.getDetails(); JSONObject j=new JSONObject();
        j.put("id",System.identityHashCode(c)); j.put("state",stName(st(c))); j.put("num",num(c)); j.put("name",name(ctx,c));
        j.put("since",d.getConnectTimeMillis());
        int dir=Build.VERSION.SDK_INT>=29?d.getCallDirection():-1; j.put("dir",dir==Call.Details.DIRECTION_OUTGOING?"out":dir==Call.Details.DIRECTION_INCOMING?"in":"");
        j.put("canHold",d.can(Call.Details.CAPABILITY_HOLD)||d.can(Call.Details.CAPABILITY_SUPPORT_HOLD));
        j.put("canMsg",true);
        j.put("pres",d.getHandlePresentation()==TelecomManager.PRESENTATION_ALLOWED?"ok":"hidden");
        DisconnectCause dc=d.getDisconnectCause(); if(dc!=null&&dc.getLabel()!=null) j.put("cause",String.valueOf(dc.getLabel()));
        a.put(j);} }
      o.put("calls",a);
      Call l=live(),r=ringing(); o.put("live",l==null?0:System.identityHashCode(l)); o.put("ringing",r==null?0:System.identityHashCode(r));
      CallService s=inst; JSONObject au=new JSONObject();
      if(s!=null){ CallAudioState cs=s.getCallAudioState(); if(cs!=null){ au.put("muted",cs.isMuted()); int rt=cs.getRoute();
        au.put("route",rt==CallAudioState.ROUTE_SPEAKER?"speaker":rt==CallAudioState.ROUTE_BLUETOOTH?"bt":rt==CallAudioState.ROUTE_WIRED_HEADSET?"wired":"earpiece");
        au.put("bt",(cs.getSupportedRouteMask()&CallAudioState.ROUTE_BLUETOOTH)!=0); } }
      o.put("audio",au); o.put("bound",s!=null);
    }catch(Exception e){}
    return o.toString();
  }

  // ---------- 조작 (메인 스레드에서) ----------
  static void ui(Runnable r){ H.post(()->{ try{ r.run(); }catch(Exception e){} }); }
  static void answer(){ ui(()->{ Call c=ringing(); if(c!=null) c.answer(VideoProfile.STATE_AUDIO_ONLY); }); }
  static void reject(String msg){ ui(()->{ Call c=ringing(); if(c==null) return; if(msg!=null&&!msg.isEmpty()){ try{ c.reject(true,msg); return; }catch(Exception e){} } c.reject(false,null); }); }
  static void hangup(){ ui(()->{ Call c=live(); if(c!=null){ c.disconnect(); return; } Call r=ringing(); if(r!=null) r.reject(false,null); }); }
  static void mute(boolean b){ ui(()->{ CallService s=inst; if(s!=null) s.setMuted(b); }); }
  @SuppressWarnings("deprecation")
  static void speaker(boolean b){ ui(()->{ CallService s=inst; if(s!=null) s.setAudioRoute(b?CallAudioState.ROUTE_SPEAKER:CallAudioState.ROUTE_WIRED_OR_EARPIECE); }); }
  static void dtmf(char ch){ ui(()->{ Call c=live(); if(c==null||st(c)!=Call.STATE_ACTIVE) return; c.playDtmfTone(ch); H.postDelayed(()->{ try{c.stopDtmfTone();}catch(Exception e){} },180); }); }
  static void hold(boolean b){ ui(()->{ Call c=live(); if(c==null) return; if(b&&st(c)==Call.STATE_ACTIVE) c.hold(); else if(!b&&st(c)==Call.STATE_HOLDING) c.unhold(); }); }
  static void swap(){ ui(()->{ Call h=holding(); if(h!=null) h.unhold(); }); }
  static boolean inCall(){ return !calls.isEmpty(); }

  // ---------- 알림 ----------
  static PendingIntent act(Context c,String a,int rq){
    return PendingIntent.getBroadcast(c,rq,new Intent(c,CallActionReceiver.class).setAction(a),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  }
  static PendingIntent screen(Context c,String a,int rq){
    Intent i=new Intent(c,CallActivity.class).setAction(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_USER_ACTION);
    return PendingIntent.getActivity(c,rq,i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  }
  private void refreshNotifs(){
    NotificationManager nm=getSystemService(NotificationManager.class); if(nm==null) return;
    try{
      Call r=ringing();
      if(r!=null){
        String nmz=name(this,r),n=Nv.fmt(num(r)); String title=nmz.isEmpty()?(n.isEmpty()?"발신자 정보 없음":n):nmz;
        PendingIntent fs=screen(this,"show",1), ans=screen(this,CallActivity.ACTION_ANSWER,2), dec=act(this,CallActionReceiver.DECLINE,3);
        Notification.Builder b=new Notification.Builder(this,Nv.CH_IN).setSmallIcon(R.drawable.ic_call).setContentTitle(title)
          .setContentText("걸려오는 전화"+(nmz.isEmpty()||n.isEmpty()?"":" · "+n)).setCategory(Notification.CATEGORY_CALL).setOngoing(true)
          .setVisibility(Notification.VISIBILITY_PUBLIC).setContentIntent(fs).setFullScreenIntent(fs,true).setColor(0xFF8F6BFF);
        boolean styled=false;
        if(Build.VERSION.SDK_INT>=31){ try{ Person p=new Person.Builder().setName(title).setImportant(true).build(); b.setStyle(Notification.CallStyle.forIncomingCall(p,dec,ans)); styled=true; }catch(Exception e){} }
        if(!styled){ b.addAction(new Notification.Action.Builder(Icon.createWithResource(this,R.drawable.ic_end),"거절",dec).build());
          b.addAction(new Notification.Action.Builder(Icon.createWithResource(this,R.drawable.ic_call),"받기",ans).build()); }
        nm.notify(N_IN,b.build());
      } else nm.cancel(N_IN);
    }catch(Exception e){}
    try{
      Call l=live();
      if(l!=null){
        String nmz=name(this,l),n=Nv.fmt(num(l)); String title=nmz.isEmpty()?(n.isEmpty()?"발신자 정보 없음":n):nmz; int s=st(l);
        String txt= s==Call.STATE_ACTIVE?"통화 중": s==Call.STATE_HOLDING?"대기 중":"연결 중…";
        Notification.Builder b=new Notification.Builder(this,Nv.CH_ON).setSmallIcon(R.drawable.ic_call).setContentTitle(title).setContentText(txt)
          .setCategory(Notification.CATEGORY_CALL).setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PUBLIC)
          .setContentIntent(screen(this,"show",4)).setColor(0xFF6FD3FF)
          .addAction(new Notification.Action.Builder(Icon.createWithResource(this,R.drawable.ic_end),"끊기",act(this,CallActionReceiver.HANGUP,5)).build());
        long since=l.getDetails().getConnectTimeMillis();
        if(s==Call.STATE_ACTIVE&&since>0){ b.setUsesChronometer(true).setWhen(since).setShowWhen(true); }
        nm.notify(N_ON,b.build());
      } else nm.cancel(N_ON);
    }catch(Exception e){}
  }
}
