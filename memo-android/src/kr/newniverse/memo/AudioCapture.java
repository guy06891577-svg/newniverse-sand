package kr.newniverse.memo;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;
import java.io.File;

/** 말소리를 m4a(AAC)로 저장. 받아쓰기를 못 쓸 때의 대안이고, 설정에서 켜면 평소에도 같이 보관해요. */
final class AudioCapture {
  /** 시험용: 소리 크기를 정해 주고(>=0), 파일 검사를 느슨하게 */
  static int testAmp=-1; static boolean lenient=false;
  private MediaRecorder mr; File file; long startedAt; boolean running;

  /** 시작. 실패(마이크를 다른 앱이 쓰는 중 등)하면 false */
  boolean start(Context c,File f){
    try{
      f.getParentFile().mkdirs();
      mr=Build.VERSION.SDK_INT>=31?new MediaRecorder(c):new MediaRecorder();
      mr.setAudioSource(MediaRecorder.AudioSource.MIC);
      mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
      mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
      mr.setAudioChannels(1); mr.setAudioSamplingRate(16000); mr.setAudioEncodingBitRate(48000);
      mr.setOutputFile(f.getAbsolutePath());
      mr.prepare(); mr.start();
      file=f; startedAt=System.currentTimeMillis(); running=true;
      return true;
    }catch(Throwable e){ release(); f.delete(); return false; }
  }
  /** 지금 소리 크기(0~32767). 목소리 감지에 써요 */
  int amp(){ if(testAmp>=0) return testAmp; try{ return running&&mr!=null?mr.getMaxAmplitude():0; }catch(Throwable e){ return 0; } }
  /** 멈추고 파일을 돌려줘요. 너무 짧거나 실패하면 지우고 null */
  File stop(){
    File f=file; boolean ok=false;
    if(mr!=null&&running){ try{ mr.stop(); ok=true; }catch(Throwable e){ ok=false; } }
    release();
    if(lenient&&f!=null){ try{ if(!f.exists()) f.createNewFile(); }catch(Throwable e){} release(); return f; }
    if(f!=null&&(!ok||!f.exists()||f.length()<800)){ if(f!=null) f.delete(); return null; }
    return f;
  }
  void release(){ running=false; try{ if(mr!=null) mr.release(); }catch(Throwable e){} mr=null; }
}
