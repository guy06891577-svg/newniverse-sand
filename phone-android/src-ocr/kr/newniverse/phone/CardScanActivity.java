package kr.newniverse.phone;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.*;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.sdkinternal.MlKitContext;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import org.json.*;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/** v6 명함 사진 → 글자 줄. 따로 떨어진 프로세스(:ocr)에서 돌아서, 글자 읽기가 잘못돼도 전화 앱 본체는 멀쩡해요.
 *  글자 읽기는 앱 안에 들어 있는 한국어 모델로 폰 안에서만 해요(인터넷 권한 없음). 사진은 읽고 나면 바로 지워요.
 *  결과: RESULT_OK + "lines" = [{t,h,y,x}] (글자 높이 h 로 이름·가게명 고름) / "err" = cancel·nophoto·decode·ocr·timeout·nocam */
public class CardScanActivity extends Activity {
  static final String EXTRA_SRC="src";
  static final int RQ_CAM=1, RQ_PICK=2;
  boolean launched; TextView msg;

  @Override protected void onCreate(Bundle b){
    super.onCreate(b);
    launched=b!=null&&b.getBoolean("launched");
    FrameLayout root=new FrameLayout(this);
    msg=new TextView(this); msg.setText("명함 읽는 중…"); msg.setTextColor(0xFFEFF3FF); msg.setTextSize(17); msg.setPadding(56,36,56,36);
    GradientDrawable g=new GradientDrawable(); g.setCornerRadius(48); g.setColor(0xCC1A2033); g.setStroke(2,0x55A9C8FF); msg.setBackground(g);
    msg.setVisibility(android.view.View.INVISIBLE);
    root.addView(msg,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
    setContentView(root);
    if(!launched) launch();
  }
  @Override protected void onSaveInstanceState(Bundle o){ super.onSaveInstanceState(o); o.putBoolean("launched",launched); }

  void launch(){
    launched=true;
    String src=getIntent().getStringExtra(EXTRA_SRC);
    try{
      if("camera".equals(src)){
        CardPhotoProvider.wipe(this);
        Uri out=CardPhotoProvider.URI;
        Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(MediaStore.EXTRA_OUTPUT,out);
        i.setClipData(ClipData.newRawUri("card",out));
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i,RQ_CAM);
      }else{
        Intent i;
        if(Build.VERSION.SDK_INT>=33){ i=new Intent(MediaStore.ACTION_PICK_IMAGES); }
        else{ i=new Intent(Intent.ACTION_GET_CONTENT); i.addCategory(Intent.CATEGORY_OPENABLE); }
        i.setType("image/*");
        startActivityForResult(i,RQ_PICK);
      }
    }catch(Exception e){ done(null,"camera".equals(src)?"nocam":"nogallery"); }
  }

  @Override protected void onActivityResult(int rq,int rs,Intent data){
    if(rs!=RESULT_OK){ done(null,"cancel"); return; }
    final Uri u=rq==RQ_CAM?CardPhotoProvider.URI:(data==null?null:data.getData());
    if(u==null){ done(null,"nophoto"); return; }
    msg.setVisibility(android.view.View.VISIBLE);
    new Thread(()->{
      String err=null; JSONArray lines=null;
      try{
        Bitmap bm=load(this,u,2000);
        if(bm==null) err="decode";
        else{
          lines=ocr(this,bm,20000);
          bm.recycle();
        }
      }catch(java.util.concurrent.TimeoutException e){ err="timeout"; }
      catch(Throwable e){ err="ocr"; }
      final JSONArray L=lines; final String E=err;
      runOnUiThread(()->done(L,E));
    },"nv-ocr").start();
  }

  void done(JSONArray lines,String err){
    CardPhotoProvider.wipe(this);   // 기본: 사진은 남기지 않음
    Intent r=new Intent();
    if(lines!=null) r.putExtra("lines",lines.toString()); else r.putExtra("err",err==null?"ocr":err);
    setResult(lines!=null?RESULT_OK:RESULT_CANCELED,r);
    finish();
  }

  /** 큰 사진은 긴 변 max 픽셀 안쪽으로 줄이고, 사진에 적힌 방향(EXIF)대로 돌림 */
  static Bitmap load(Context c,Uri u,int max) throws Exception{
    ContentResolver cr=c.getContentResolver();
    BitmapFactory.Options o=new BitmapFactory.Options(); o.inJustDecodeBounds=true;
    try(InputStream in=cr.openInputStream(u)){ BitmapFactory.decodeStream(in,null,o); }
    if(o.outWidth<=0||o.outHeight<=0) return null;
    int s=1; while(Math.max(o.outWidth,o.outHeight)/(s*2)>=max) s*=2;
    BitmapFactory.Options d=new BitmapFactory.Options(); d.inSampleSize=s;
    Bitmap bm; try(InputStream in=cr.openInputStream(u)){ bm=BitmapFactory.decodeStream(in,null,d); }
    if(bm==null) return null;
    int deg=0;
    try(InputStream in=cr.openInputStream(u)){
      int or=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
      deg=or==ExifInterface.ORIENTATION_ROTATE_90?90:or==ExifInterface.ORIENTATION_ROTATE_180?180:or==ExifInterface.ORIENTATION_ROTATE_270?270:0;
    }catch(Exception e){}
    float k=Math.min(1f,(float)max/Math.max(bm.getWidth(),bm.getHeight()));
    if(deg!=0||k<1f){
      Matrix m=new Matrix(); if(k<1f) m.postScale(k,k); if(deg!=0) m.postRotate(deg);
      Bitmap r=Bitmap.createBitmap(bm,0,0,bm.getWidth(),bm.getHeight(),m,true);
      if(r!=bm) bm.recycle(); bm=r;
    }
    return bm;
  }

  static JSONArray ocr(Context c,Bitmap bm,long timeoutMs) throws Exception{
    MlKitContext.initializeIfNeeded(c.getApplicationContext());
    TextRecognizer tr=TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
    try{
      Text t=Tasks.await(tr.process(InputImage.fromBitmap(bm,0)),timeoutMs,TimeUnit.MILLISECONDS);
      JSONArray a=new JSONArray();
      for(Text.TextBlock b:t.getTextBlocks()) for(Text.Line l:b.getLines()){
        Rect r=l.getBoundingBox(); String s=l.getText(); if(s==null||s.trim().isEmpty()) continue;
        JSONObject o=new JSONObject(); o.put("t",s.trim()); o.put("h",r==null?0:r.height()); o.put("y",r==null?0:r.top); o.put("x",r==null?0:r.left); a.put(o);
      }
      return a;
    }finally{ try{ tr.close(); }catch(Exception e){} }
  }
}
