package kr.newniverse.memo;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** 짧은 진동(설정에서 끌 수 있음). 주머니 속에서도 ‘시작됐다·저장됐다’를 알 수 있게 */
final class Vib {
  static void once(Context c,Prefs p,int ms){
    if(p!=null&&!p.vibrate()) return;
    try{ Vibrator v=c.getSystemService(Vibrator.class); if(v!=null&&v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(Math.max(5,Math.min(ms,400)),VibrationEffect.DEFAULT_AMPLITUDE)); }catch(Throwable e){}
  }
  /** 저장됨: 짧게 두 번 */
  static void saved(Context c,Prefs p){
    if(p!=null&&!p.vibrate()) return;
    try{ Vibrator v=c.getSystemService(Vibrator.class); if(v!=null&&v.hasVibrator()) v.vibrate(VibrationEffect.createWaveform(new long[]{0,35,70,35},-1)); }catch(Throwable e){}
  }
}
