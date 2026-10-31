package com.sunantongsan.ghostmatch3;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Data-only board layouts and goals (schema 4, app 0.42.0+). Never downloads code.
 * Payload: {"schema":4,"version":N,
 *   "layouts":[[8 rows of 8 chars], ...],            // optional, replaces the cycle used from level 4
 *   "levels":{"12":{"layout":[8 rows],"color":0,"color2":3,"goal2":6,"giantHp":4}}}   // all optional
 * Cells: '.' piece, '#' hole, r stone, R hard stone, i ice, w web, l lock, G g / g g giant ghost.
 */
final class BoardPatch {
    private static final String BASE="https://sunantongsan.github.io/GhostMatch3/patches/";
    static final int SCHEMA=4;
    private static AtomicFile file(Context c){return new AtomicFile(new File(c.getFilesDir(),"board-patch.json"));}

    private static String[] rows(JSONArray a)throws JSONException{
        String[] rows=new String[a.length()];
        for(int i=0;i<a.length();i++)rows[i]=a.getString(i);
        return rows;
    }

    /** Parses and validates; on success the engine uses the new boards. Throws without changing anything otherwise. */
    static int apply(byte[] bytes)throws Exception{
        JSONObject data=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        if(data.getInt("schema")!=SCHEMA||data.getInt("version")<1)throw new IOException("schema");
        String[][] cycle=null;
        JSONArray layouts=data.optJSONArray("layouts");
        if(layouts!=null){cycle=new String[layouts.length()][];for(int i=0;i<layouts.length();i++)cycle[i]=rows(layouts.getJSONArray(i));}
        Map<Integer,String[]> exact=new HashMap<>();Map<Integer,int[]> extras=new HashMap<>();
        JSONObject levels=data.optJSONObject("levels");
        if(levels!=null){
            Iterator<String> keys=levels.keys();
            while(keys.hasNext()){
                String key=keys.next();int n=Integer.parseInt(key);JSONObject level=levels.getJSONObject(key);
                if(level.has("layout"))exact.put(n,rows(level.getJSONArray("layout")));
                int[] x={level.optInt("color",-1),level.optInt("color2",-1),level.optInt("goal2",-1),level.optInt("giantHp",-1)};
                if(x[0]!=-1||x[1]!=-1||x[2]!=-1||x[3]!=-1)extras.put(n,x);
            }
        }
        AdventureEngine.applyBoardPatch(cycle,exact,extras);
        return data.getInt("version");
    }

    /** Loads the last validated patch from storage; falls back to bundled boards. Call before the game view is built. */
    static synchronized void load(Context c){
        try{apply(file(c).readFully());}
        catch(FileNotFoundException none){AdventureEngine.resetBoardPatch();}
        catch(Exception bad){AdventureEngine.resetBoardPatch();}
    }

    private static byte[] read(InputStream in,int limit)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;
        while((n=in.read(b))!=-1){if(out.size()+n>limit)throw new IOException("size");out.write(b,0,n);}
        return out.toByteArray();
    }
    private static byte[] fetch(String name,int limit)throws Exception{
        if(!name.matches("board-[a-zA-Z0-9-]+\\.json"))throw new IOException("filename");
        HttpURLConnection c=(HttpURLConnection)new URL(BASE+name).openConnection();
        c.setConnectTimeout(4000);c.setReadTimeout(4000);c.setInstanceFollowRedirects(false);c.setUseCaches(false);
        try{if(c.getResponseCode()!=200)throw new IOException("status");try(InputStream in=c.getInputStream()){return read(in,limit);}}
        finally{c.disconnect();}
    }

    /** Checks the manifest, downloads, verifies SHA-256 and atomically stores a newer patch. Offline keeps the last good one. */
    static synchronized void update(Context c){
        try{
            JSONObject m=new JSONObject(new String(fetch("board-manifest.json",16384),StandardCharsets.UTF_8));
            if(m.getInt("schema")!=SCHEMA||m.getInt("minAppVersion")>c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionCode)return;
            int installed=0;
            try{installed=new JSONObject(new String(file(c).readFully(),StandardCharsets.UTF_8)).getInt("version");}catch(Exception ignored){}
            if(m.getInt("version")<=installed)return;
            byte[] b=fetch(m.getString("file"),262144);StringBuilder sha=new StringBuilder();
            for(byte v:MessageDigest.getInstance("SHA-256").digest(b))sha.append(String.format(Locale.ROOT,"%02x",v&255));
            if(!sha.toString().equalsIgnoreCase(m.getString("sha256")))throw new IOException("digest");
            if(apply(b)!=m.getInt("version"))throw new IOException("version");
            AtomicFile f=file(c);FileOutputStream out=f.startWrite();
            try{out.write(b);f.finishWrite(out);}catch(Exception e){f.failWrite(out);throw e;}
        }catch(Exception ignored){/* bundled or last valid boards stay playable */}
        load(c);
    }
}
