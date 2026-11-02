package com.sunantongsan.ghostmatch3;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;

/** Data-only, bounded, checksum-verified updates. Never downloads executable code. */
final class JokePatch {
    static final String BASE="https://sunantongsan.github.io/GhostMatch3/patches/";
    static final int BUNDLED_VERSION=5, LIMIT=262144;
    interface Status { void update(String text,int percent); }
    static final class Content {
        final int version; final JSONArray jokes;
        Content(int v,JSONArray j){version=v;jokes=j;}
    }
    static Content validate(byte[] bytes)throws Exception {
        JSONObject doc=new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
        if(doc.getInt("schema")!=1||doc.getInt("version")<1)throw new IOException("schema");
        JSONArray jokes=doc.getJSONArray("jokes");
        if(jokes.length()<22||jokes.length()>500)throw new IOException("count");
        Set<String> ids=new HashSet<>();
        for(int i=0;i<jokes.length();i++){
            JSONObject j=jokes.getJSONObject(i);
            String id=j.getString("id");
            if(!id.equals("joke-"+(i+1))||!ids.add(id))throw new IOException("stable order");
            bounded(j,"question",300);bounded(j,"punchline",500);
            JSONArray c=j.getJSONArray("choices");
            if(c.length()!=3||j.getInt("answer")<0||j.getInt("answer")>2)throw new IOException("choices");
            for(int n=0;n<3;n++){bounded(c.getJSONObject(n),"label",100);bounded(c.getJSONObject(n),"icon",24);}
        }
        return new Content(doc.getInt("version"),jokes);
    }
    private static void bounded(JSONObject o,String key,int max)throws Exception{
        String s=o.getString(key);if(s.trim().isEmpty()||s.length()>max)throw new IOException(key);
    }
    static Content verified(byte[] data,String hash,int version,int previousCount)throws Exception{
        if(!sha(data).equals(hash))throw new IOException("checksum");
        Content next=validate(data);if(next.version!=version||next.jokes.length()<previousCount)throw new IOException("version/count");
        return next;
    }
    static Content install(Context c,byte[] data,String hash,int version,int previousCount)throws Exception{
        Content next=verified(data,hash,version,previousCount);
        AtomicFile target=new AtomicFile(new File(c.getFilesDir(),"ser-joy-content.json"));FileOutputStream out=null;
        try{out=target.startWrite();out.write(data);target.finishWrite(out);}catch(Exception e){if(out!=null)target.failWrite(out);throw e;}
        return next;
    }
    static String sha(byte[] b)throws Exception{
        StringBuilder out=new StringBuilder();for(byte v:MessageDigest.getInstance("SHA-256").digest(b))out.append(String.format(Locale.ROOT,"%02x",v&255));return out.toString();
    }
    static byte[] read(InputStream in)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;
        while((n=in.read(b))!=-1){if(out.size()+n>LIMIT)throw new IOException("size");out.write(b,0,n);}return out.toByteArray();
    }
    static Content local(Context c)throws Exception{
        Content bundled;try(InputStream in=c.getAssets().open("ser-joy-content.json")){bundled=validate(read(in));}
        try(InputStream in=new AtomicFile(new File(c.getFilesDir(),"ser-joy-content.json")).openRead()){
            Content cached=validate(read(in));if(cached.version>=bundled.version)return cached;
        }catch(Exception ignored){}return bundled;
    }
    private static byte[] fetch(String file,Status status,int start)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(BASE+file).openConnection();
        c.setConnectTimeout(4000);c.setReadTimeout(4000);c.setInstanceFollowRedirects(false);
        try{
            if(c.getResponseCode()!=200||c.getContentLengthLong()>LIMIT)throw new IOException("response");
            long deadline=android.os.SystemClock.elapsedRealtime()+6000;
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[4096];int n;long total=c.getContentLengthLong();
                while((n=in.read(b))!=-1){if(out.size()+n>LIMIT||android.os.SystemClock.elapsedRealtime()>deadline)throw new IOException("limit");out.write(b,0,n);status.update(start==10?"กำลังตรวจสอบข้อมูลล่าสุด…":"กำลังโหลดมุกใหม่…",total>0?start+(int)((90-start)*out.size()/total):start);}
                return out.toByteArray();
            }
        }finally{c.disconnect();}
    }
    static synchronized Content check(Context c,Status status)throws Exception{
        Content current=local(c);
        try{
            status.update("กำลังตรวจสอบข้อมูลล่าสุด…",10);
            JSONObject m=new JSONObject(new String(fetch("ser-joy-manifest.json",status,10),"UTF-8"));
            if(m.getInt("schema")!=1)throw new IOException("manifest");
            int version=m.getInt("version");
            if(m.getInt("minAppVersion")>c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionCode){status.update("มีเวอร์ชันแอปใหม่ • อัปเดตผ่าน Google Play ได้",100);return current;}
            if(version>current.version){
                String file=m.getString("file"),hash=m.getString("sha256");
                if(!file.matches("ser-joy-v[0-9]+\\.json")||!hash.matches("[a-f0-9]{64}"))throw new IOException("manifest fields");
                byte[] data=fetch(file,status,30);
                current=install(c,data,hash,version,current.jokes.length());
            }
            status.update("พร้อมเล่นแล้ว • ข้อมูลเวอร์ชัน "+current.version,100);
        }catch(Exception e){status.update("เชื่อมต่อไม่ได้ • เล่นด้วยข้อมูลที่มีได้เลย",100);}
        return current;
    }
}
