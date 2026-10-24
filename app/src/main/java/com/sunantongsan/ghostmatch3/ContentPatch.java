package com.sunantongsan.ghostmatch3;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Data only: never downloads executable code. Keeps the last validated patch. */
final class ContentPatch {
    private static final String BASE="https://sunantongsan.github.io/GhostMatch3/patches/";
    private static JSONObject cached;
    interface Progress { void update(int percent); }
    private static AtomicFile file(Context context){return new AtomicFile(new File(context.getFilesDir(),"content-patch.json"));}
    private static JSONObject validate(byte[] bytes) throws Exception {
        JSONObject data=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        if(data.getInt("schema")!=1||data.getInt("version")<1)throw new IOException("Unsupported data");
        JSONObject levels=data.getJSONObject("levels");
        java.util.Iterator<String> keys=levels.keys();
        while(keys.hasNext()){
            String key=keys.next();int id=Integer.parseInt(key);
            if(id<1||id>120)throw new IOException("Invalid level");
            JSONObject level=levels.getJSONObject(key);
            int moves=level.getInt("moves"),target=level.getInt("target");
            if(moves<1||moves>100||target<100||target>100000)throw new IOException("Invalid balance");
        }
        return data;
    }
    private static synchronized JSONObject local(Context context){
        if(cached==null)try{cached=validate(file(context).readFully());}catch(Exception ignored){}
        return cached;
    }
    static JSONObject level(Context context,int number){
        JSONObject data=local(context);
        return data==null?null:data.optJSONObject("levels").optJSONObject(Integer.toString(number));
    }
    private static byte[] fetch(String name,int limit,Progress progress) throws Exception {
        if(!name.matches("[a-zA-Z0-9._-]+\\.json"))throw new IOException("Invalid filename");
        HttpURLConnection connection=(HttpURLConnection)new URL(BASE+name).openConnection();
        connection.setConnectTimeout(5000);connection.setReadTimeout(5000);
        connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);
        long deadline=android.os.SystemClock.elapsedRealtime()+12000;
        try{
            if(connection.getResponseCode()!=200)throw new IOException("Unavailable");
            int length=connection.getContentLength();if(length>limit)throw new IOException("Too large");
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int n;
                while((n=input.read(buffer))!=-1){
                    if(output.size()+n>limit||android.os.SystemClock.elapsedRealtime()>deadline)throw new IOException("Limit exceeded");
                    output.write(buffer,0,n);
                    if(progress!=null)progress.update(length>0?Math.min(99,output.size()*100/length):0);
                }
                return output.toByteArray();
            }
        }finally{connection.disconnect();}
    }
    static String update(Context context,Progress progress){
        JSONObject previous=local(context);
        try{
            JSONObject manifest=new JSONObject(new String(fetch("manifest.json",16384,null),StandardCharsets.UTF_8));
            if(manifest.getInt("schema")!=1)throw new IOException("Unsupported schema");
            if(manifest.getInt("minAppVersion")>35)return "มีเวอร์ชันใหม่ในสโตร์ • ยังเล่นเวอร์ชันปัจจุบันได้";
            int version=manifest.getInt("version");
            if(previous!=null&&previous.getInt("version")>=version)return "พร้อมเล่น • แพตช์ "+previous.getInt("version");
            byte[] bytes=fetch(manifest.getString("file"),262144,progress);
            StringBuilder digest=new StringBuilder();
            for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))digest.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            if(!digest.toString().equalsIgnoreCase(manifest.getString("sha256")))throw new IOException("Checksum mismatch");
            JSONObject data=validate(bytes);if(data.getInt("version")!=version)throw new IOException("Version mismatch");
            AtomicFile destination=file(context);FileOutputStream stream=null;
            try{stream=destination.startWrite();stream.write(bytes);destination.finishWrite(stream);}
            catch(Exception error){if(stream!=null)destination.failWrite(stream);throw error;}
            synchronized(ContentPatch.class){cached=data;}
            return "อัปเดตสำเร็จ • แพตช์ "+version;
        }catch(Exception error){return "ตรวจสอบแพตช์ไม่ได้ • เล่นด้วยข้อมูลเดิมได้";}
    }
}
