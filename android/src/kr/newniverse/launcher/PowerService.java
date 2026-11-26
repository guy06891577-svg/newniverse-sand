package kr.newniverse.launcher;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityEvent;

/**
 * v44b 시험판: 안드로이드 전원 창만 띄운다 (GLOBAL_ACTION_POWER_DIALOG).
 * 끄거나 다시 시작하는 단추는 사용자가 직접 누르며, 이 서비스가 전원을 끄거나 다시 시작하지 않는다.
 * 화면 내용은 읽지 않음: canRetrieveWindowContent=false, 이벤트 종류 없음(typeNone), 창 내용·제스처·키 필터 없음.
 *
 * Play 스토어에 올리려면 이 서비스를 빼거나(./build.sh basic play 또는 ./build.sh plus play)
 * 접근성 선언서를 내야 한다. Flavor.POWER=false 인 판에는 이 클래스가 포함되지 않는다.
 */
public class PowerService extends AccessibilityService {
  static volatile PowerService inst;
  @Override protected void onServiceConnected(){
    inst=this;
    // XML 에 typeNone 을 적을 수 없어, 붙는 순간 이벤트 구독을 모두 끈다. 창 내용도 읽지 않음.
    try{ AccessibilityServiceInfo i=new AccessibilityServiceInfo(); i.eventTypes=0; i.feedbackType=AccessibilityServiceInfo.FEEDBACK_GENERIC; i.flags=0; i.notificationTimeout=0; setServiceInfo(i); }catch(Exception e){}
  }
  @Override public boolean onUnbind(android.content.Intent i){ inst=null; return super.onUnbind(i); }
  @Override public void onDestroy(){ inst=null; super.onDestroy(); }
  @Override public void onAccessibilityEvent(AccessibilityEvent e){ /* 이벤트를 구독하지 않음. 화면 내용도 읽지 않음. */ }
  @Override public void onInterrupt(){}

  /** 전원 창만 연다. 추가판에서 화면 끄기 서비스가 이미 켜져 있으면 그 서비스의 performGlobalAction만 빌린다(화면은 읽지 않음). */
  static boolean dialog(){
    PowerService s=inst; if(s!=null) return s.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
    try{
      Object o=Class.forName("kr.newniverse.launcher.LockService").getDeclaredField("inst").get(null);
      if(o instanceof AccessibilityService) return ((AccessibilityService)o).performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
    }catch(Throwable e){}
    return false;
  }
  static boolean connected(){
    if(inst!=null) return true;
    try{ return Class.forName("kr.newniverse.launcher.LockService").getDeclaredField("inst").get(null)!=null; }catch(Throwable e){ return false; }
  }
}
