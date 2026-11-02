package com.sunantongsan.ghostmatch3;
import android.content.Context;
import android.graphics.*;
import org.json.*;
import java.io.*;

/** Shared atlases are decoded once per activity, not once per question. */
final class StoryArt {
    final Bitmap rooms,special,hero,motion;
    final Bitmap[] objects=new Bitmap[8];
    final JSONObject[] scenes;
    StoryArt(Context c)throws Exception{
        motion=load(c,"story/motion.webp");
        rooms=load(c,"story/rooms.webp");special=load(c,"story/special.webp");hero=load(c,"ser-joy-fullbody.webp");
        for(int i=0;i<objects.length;i++)objects[i]=load(c,"story/objects-"+i+".webp");
        JSONObject doc;try(InputStream in=c.getAssets().open("story/scenes.json")){doc=new JSONObject(new String(JokePatch.read(in),"UTF-8"));}
        JSONArray all=doc.getJSONArray("scenes");scenes=new JSONObject[all.length()];
        for(int i=0;i<scenes.length;i++){scenes[i]=all.getJSONObject(i);if(!scenes[i].getString("id").equals("joke-"+(i+1)))throw new IOException("scene order");}
    }
    private static Bitmap load(Context c,String name)throws Exception{try(InputStream in=c.getAssets().open(name)){Bitmap b=BitmapFactory.decodeStream(in);if(b==null)throw new IOException(name);return b;}}
    void sprite(Canvas c,int id,float x,float y,float width,float height,Paint p){
        Bitmap b=id<0?special:objects[id/32];int cell=id<0?-id-1:id%32,cols=id<0?4:8;
        int cw=b.getWidth()/cols,ch=b.getHeight()/4;
        Rect src=new Rect(cell%cols*cw+2,cell/cols*ch+2,(cell%cols+1)*cw-2,(cell/cols+1)*ch-2);
        c.drawBitmap(b,src,new RectF(x-width/2,y-height,x+width/2,y),p);
    }
    void actor(Canvas c,int frame,float x,float bottom,float height,float shake,Paint p){
        if(frame>=12){
            int f=frame-12,row=f/4,cw=motion.getWidth()/4;int[] t={0,360,690,970},b={360,690,970,1254};
            float ww=height*cw/(b[row]-t[row]);c.save();c.rotate(shake,x,bottom-height*.6f);
            c.drawBitmap(motion,new Rect(f%4*cw,t[row],(f%4+1)*cw,b[row]),new RectF(x-ww/2,bottom-height,x+ww/2,bottom),p);c.restore();return;
        }
        int[] tops={0,420,825},ends={420,825,1254};int cw=hero.getWidth()/4,row=frame/4;
        float width=height*cw/(ends[row]-tops[row]);
        c.save();c.rotate(shake,x,bottom-height*.6f);
        c.drawBitmap(hero,new Rect(frame%4*cw,tops[row],(frame%4+1)*cw,ends[row]),new RectF(x-width/2,bottom-height,x+width/2,bottom),p);c.restore();
    }
}
