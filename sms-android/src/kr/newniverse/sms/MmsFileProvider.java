package kr.newniverse.sms;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * v4: 통신사에서 내려받은 장문·사진(MMS) PDU 를 잠깐 담는 파일 통로 (androidx FileProvider 대신 아주 작은 판).
 * 밖으로 열려 있지 않고(exported=false), 내려받기 때 전화 앱(MmsService)에만 쓰기/읽기 허락을 잠깐 줌.
 * content://kr.newniverse.sms.mmsfile/<이름>.pdu  →  cache/mms/<이름>.pdu
 */
public class MmsFileProvider extends ContentProvider {
  static final String AUTH="kr.newniverse.sms.mmsfile";
  static File dir(Context c){ File d=new File(c.getCacheDir(),"mms"); if(!d.exists()) d.mkdirs(); return d; }
  static File file(Context c,String name){ return MmsLogic.safeFileName(name)?new File(dir(c),name):null; }
  static Uri uri(String name){ return new Uri.Builder().scheme("content").authority(AUTH).appendPath(name).build(); }

  @Override public boolean onCreate(){ return true; }
  @Override public ParcelFileDescriptor openFile(Uri u,String mode) throws FileNotFoundException {
    Context c=getContext(); String name=u==null?null:u.getLastPathSegment();
    File f=c==null?null:file(c,name); if(f==null) throw new FileNotFoundException("bad");
    int m;
    if(mode!=null&&mode.contains("w")) m=ParcelFileDescriptor.MODE_WRITE_ONLY|ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE;
    else m=ParcelFileDescriptor.MODE_READ_ONLY;
    return ParcelFileDescriptor.open(f,m);
  }
  @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){ return null; }
  @Override public String getType(Uri u){ return "application/vnd.wap.mms-message"; }
  @Override public Uri insert(Uri u,ContentValues v){ return null; }
  @Override public int delete(Uri u,String s,String[] a){ Context c=getContext(); File f=c==null?null:file(c,u.getLastPathSegment()); return f!=null&&f.delete()?1:0; }
  @Override public int update(Uri u,ContentValues v,String s,String[] a){ return 0; }
}
