package kr.newniverse.phone;

import android.Manifest;
import android.content.*;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.*;
import org.json.*;
import java.util.ArrayList;

/** v6 명함 → 주소록 저장 (이 폰 연락처, 계정 없이). 사진은 저장하지 않아요. */
final class ContactSaver {
  private ContactSaver(){}
  /** 반환: ok · perm(연락처 쓰기 허용 없음) · empty(이름도 번호도 없음) · fail */
  static String save(Context c,JSONObject f){
    String name=t(f,"name"), mobile=t(f,"mobile"), tel=t(f,"tel"), company=t(f,"company"), title=t(f,"title"), email=t(f,"email");
    if(name.isEmpty()&&mobile.isEmpty()&&tel.isEmpty()) return "empty";
    if(!Nv.has(c,Manifest.permission.WRITE_CONTACTS)) return "perm";
    if(name.isEmpty()) name=!company.isEmpty()?company:(mobile.isEmpty()?tel:mobile);
    ArrayList<ContentProviderOperation> ops=new ArrayList<>();
    ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
      .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE,null).withValue(ContactsContract.RawContacts.ACCOUNT_NAME,null).build());
    ops.add(data(StructuredName.CONTENT_ITEM_TYPE).withValue(StructuredName.DISPLAY_NAME,name).build());
    if(!mobile.isEmpty()) ops.add(data(Phone.CONTENT_ITEM_TYPE).withValue(Phone.NUMBER,mobile).withValue(Phone.TYPE,Phone.TYPE_MOBILE).build());
    if(!tel.isEmpty()) ops.add(data(Phone.CONTENT_ITEM_TYPE).withValue(Phone.NUMBER,tel).withValue(Phone.TYPE,Phone.TYPE_WORK).build());
    if(!email.isEmpty()) ops.add(data(Email.CONTENT_ITEM_TYPE).withValue(Email.ADDRESS,email).withValue(Email.TYPE,Email.TYPE_WORK).build());
    if(!company.isEmpty()||!title.isEmpty()) ops.add(data(Organization.CONTENT_ITEM_TYPE).withValue(Organization.COMPANY,company).withValue(Organization.TITLE,title).withValue(Organization.TYPE,Organization.TYPE_WORK).build());
    try{ c.getContentResolver().applyBatch(ContactsContract.AUTHORITY,ops); return "ok"; }catch(Exception e){ return "fail"; }
  }
  private static ContentProviderOperation.Builder data(String mime){
    return ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,0).withValue(ContactsContract.Data.MIMETYPE,mime);
  }
  static String t(JSONObject f,String k){ String s=f==null?"":f.optString(k,""); s=s.replace('\n',' ').trim(); return s.length()>120?s.substring(0,120):s; }
}
