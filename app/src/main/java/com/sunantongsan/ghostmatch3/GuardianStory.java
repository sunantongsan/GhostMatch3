package com.sunantongsan.ghostmatch3;
import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Data-only authored rescue episodes. Older clients never consume this manifest. */
final class GuardianStory {
    final int level,mechanism,seconds;
    final String title,kindness,incident,action1,action2,ending;
    GuardianStory(JSONObject j)throws JSONException{
        level=j.getInt("level");mechanism=j.getInt("mechanism");seconds=j.getInt("seconds");
        title=j.getString("title");kindness=j.getString("kindness");incident=j.getString("incident");
        action1=j.getString("action1");action2=j.getString("action2");ending=j.getString("ending");
    }
    static boolean rescue(int n){return n%2==0&&n%6!=0&&n%10!=0;}
    static JSONObject validate(byte[] bytes)throws Exception{
        JSONObject data=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        if(data.getInt("schema")!=3||data.getInt("version")<1)throw new IOException("schema");
        JSONArray a=data.getJSONArray("episodes");if(a.length()!=32)throw new IOException("episodes");
        Set<Integer> seen=new HashSet<>();
        for(int i=0;i<a.length();i++){
            GuardianStory s=new GuardianStory(a.getJSONObject(i));
            if(s.level<1||s.level>120||!rescue(s.level)||!seen.add(s.level)||s.mechanism<0||s.mechanism>7||s.seconds<60||s.seconds>300)throw new IOException("episode");
            for(String value:new String[]{s.title,s.kindness,s.incident,s.action1,s.action2,s.ending})if(value.isEmpty()||value.length()>100)throw new IOException("text");
        }return data;
    }
    static AtomicFile file(Context c){return new AtomicFile(new File(c.getFilesDir(),"guardian-episodes.json"));}
    static Map<Integer,GuardianStory> load(Context c){
        try{
            JSONObject d;
            try{d=validate(file(c).readFully());}catch(Exception ignored){try(InputStream in=c.getAssets().open("guardian-episodes.json")){d=validate(read(in,262144));}}
            Map<Integer,GuardianStory> stories=new HashMap<>();JSONArray a=d.getJSONArray("episodes");
            for(int i=0;i<a.length();i++){GuardianStory s=new GuardianStory(a.getJSONObject(i));stories.put(s.level,s);}return stories;
        }catch(Exception e){throw new IllegalStateException("Bundled guardian stories invalid",e);}
    }
    private static byte[] read(InputStream in,int limit)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;
        while((n=in.read(b))!=-1){if(out.size()+n>limit)throw new IOException("size");out.write(b,0,n);}return out.toByteArray();
    }
    private static byte[] fetch(String name,int limit)throws Exception{
        if(!name.matches("guardian-[a-zA-Z0-9-]+\\.json"))throw new IOException("filename");
        HttpURLConnection c=(HttpURLConnection)new URL("https://sunantongsan.github.io/GhostMatch3/patches/"+name).openConnection();
        c.setConnectTimeout(3000);c.setReadTimeout(3000);c.setInstanceFollowRedirects(false);c.setUseCaches(false);
        try{if(c.getResponseCode()!=200)throw new IOException("status");try(InputStream in=c.getInputStream()){return read(in,limit);}}finally{c.disconnect();}
    }
    static void update(Context c){
        try{
            JSONObject m=new JSONObject(new String(fetch("guardian-manifest.json",16384),StandardCharsets.UTF_8));
            if(m.getInt("schema")!=3||m.getInt("minAppVersion")>c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionCode)return;
            int installed=1;try{installed=validate(file(c).readFully()).getInt("version");}catch(Exception ignored){}
            if(m.getInt("version")<=installed)return;
            byte[] b=fetch(m.getString("file"),262144);StringBuilder sha=new StringBuilder();
            for(byte v:MessageDigest.getInstance("SHA-256").digest(b))sha.append(String.format(Locale.ROOT,"%02x",v&255));
            if(!sha.toString().equals(m.getString("sha256")))throw new IOException("digest");
            if(validate(b).getInt("version")!=m.getInt("version"))throw new IOException("version");
            AtomicFile f=file(c);FileOutputStream out=f.startWrite();try{out.write(b);f.finishWrite(out);}catch(Exception e){f.failWrite(out);throw e;}
        }catch(Exception ignored){/* Bundled/last valid stories remain playable offline. */}
    }
}
