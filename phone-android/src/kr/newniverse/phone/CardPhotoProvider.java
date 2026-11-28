package kr.newniverse.phone;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;

/** v6 명함 사진: 카메라 앱이 사진을 써 넣을 임시 파일 하나만 내줌 (앱 밖에서는 열 수 없고, 카메라에 그때만 쓰기 허락). 읽고 나면 바로 지움 */
public class CardPhotoProvider extends ContentProvider {
  static final String AUTH="kr.newniverse.phone.cardphoto";
  static final Uri URI=Uri.parse("content://"+AUTH+"/shot.jpg");
  static File file(Context c){ File d=new File(c.getCacheDir(),"card"); d.mkdirs(); return new File(d,"shot.jpg"); }
  static void wipe(Context c){ try{ File d=new File(c.getCacheDir(),"card"); File[] fs=d.listFiles(); if(fs!=null) for(File f:fs) f.delete(); }catch(Exception e){} }
  @Override public boolean onCreate(){ return true; }
  @Override public ParcelFileDescriptor openFile(Uri u,String mode) throws FileNotFoundException {
    if(!"shot.jpg".equals(u.getLastPathSegment())) throw new FileNotFoundException();
    File f=file(getContext());
    int m=ParcelFileDescriptor.parseMode(mode);
    if((m&ParcelFileDescriptor.MODE_WRITE_ONLY)!=0||(m&ParcelFileDescriptor.MODE_READ_WRITE)!=0) m|=ParcelFileDescriptor.MODE_CREATE;
    return ParcelFileDescriptor.open(f,m);
  }
  @Override public String getType(Uri u){ return "image/jpeg"; }
  @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){
    android.database.MatrixCursor c=new android.database.MatrixCursor(new String[]{android.provider.OpenableColumns.DISPLAY_NAME,android.provider.OpenableColumns.SIZE});
    c.addRow(new Object[]{"shot.jpg",file(getContext()).length()}); return c; }
  @Override public Uri insert(Uri u,ContentValues v){ return null; }
  @Override public int delete(Uri u,String s,String[] a){ return file(getContext()).delete()?1:0; }
  @Override public int update(Uri u,ContentValues v,String s,String[] a){ return 0; }
}
