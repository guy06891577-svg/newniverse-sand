package kr.newniverse.launcher;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/** 화면 끄기(잠금) 전용 접근성 서비스. 화면 내용은 읽지 않음(canRetrieveWindowContent=false). */
public class LockService extends AccessibilityService {
  static LockService inst;
  @Override protected void onServiceConnected(){ inst=this; }
  @Override public boolean onUnbind(android.content.Intent i){ inst=null; return super.onUnbind(i); }
  @Override public void onDestroy(){ inst=null; super.onDestroy(); }
  @Override public void onAccessibilityEvent(AccessibilityEvent e){}
  @Override public void onInterrupt(){}
  static boolean lock(){
    if(inst==null||android.os.Build.VERSION.SDK_INT<28) return false;
    return inst.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
  }
}
