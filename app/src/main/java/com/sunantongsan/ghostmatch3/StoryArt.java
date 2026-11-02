package com.sunantongsan.ghostmatch3;
import android.content.Context;
import android.graphics.*;
import org.json.*;
import java.io.*;

/** Shared atlases are decoded once per activity, not once per question. */
final class StoryArt {
    final Bitmap special,hero,motion,lighter;
    final Bitmap[] rooms=new Bitmap[4];
    final JSONObject bounds;
    final Bitmap[] objects=new Bitmap[9];
    final JSONObject[] scenes;
    StoryArt(Context c)throws Exception{
        lighter=load(c,"story/lighter.webp");
        motion=load(c,"story/motion.webp");
        for(int i=0;i<rooms.length;i++)rooms[i]=load(c,"story/rooms-"+i+".webp");
        try(InputStream in=c.getAssets().open("story/bounds.json")){bounds=new JSONObject(new String(JokePatch.read(in),"UTF-8"));}
        special=load(c,"story/special.webp");hero=load(c,"ser-joy-fullbody.webp");
        for(int i=0;i<objects.length;i++)objects[i]=load(c,"story/objects-"+i+".webp");
        JSONObject doc;try(InputStream in=c.getAssets().open("story/scenes.json")){doc=new JSONObject(new String(JokePatch.read(in),"UTF-8"));}
        JSONArray all=doc.getJSONArray("scenes");scenes=new JSONObject[all.length()];
        for(int i=0;i<scenes.length;i++){scenes[i]=all.getJSONObject(i);if(!scenes[i].getString("id").equals("joke-"+(i+1)))throw new IOException("scene order");}
    }
    private static Bitmap load(Context c,String name)throws Exception{try(InputStream in=c.getAssets().open(name)){Bitmap b=BitmapFactory.decodeStream(in);if(b==null)throw new IOException(name);return b;}}
    private Rect bounds(String name,int cell){
        JSONArray r=bounds.optJSONArray(name).optJSONArray(cell);
        return new Rect(r.optInt(0),r.optInt(1),r.optInt(2),r.optInt(3));
    }
    void background(Canvas c,int room,float width,float height,Paint p){
        Bitmap b=rooms[room/4];int cell=room%4,cw=b.getWidth()/2,ch=b.getHeight()/2;
        Rect src=new Rect(cell%2*cw+2,cell/2*ch+2,(cell%2+1)*cw-2,(cell/2+1)*ch-2);
        float scale=Math.max(width/src.width(),height/src.height()),w=src.width()*scale,h=src.height()*scale;
        c.drawBitmap(b,src,new RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2),p);
    }
    void sprite(Canvas c,int id,float x,float y,float width,float height,Paint p){
        if(id==-17){float scale=Math.min(width/lighter.getWidth(),height/lighter.getHeight()),w=lighter.getWidth()*scale,h=lighter.getHeight()*scale;c.drawBitmap(lighter,null,new RectF(x-w/2,y-h,x+w/2,y),p);return;}
        Bitmap b=id<0?special:objects[id/32];int cell=id<0?-id-1:id%32;
        Rect src=bounds(id<0?"special.webp":"objects-"+(id/32)+".webp",cell);
        float scale=Math.min(width/src.width(),height/src.height()),w=src.width()*scale,h=src.height()*scale;
        c.drawBitmap(b,src,new RectF(x-w/2,y-h,x+w/2,y),p);
    }
    void actor(Canvas c,int frame,float x,float bottom,float height,float shake,Paint p){
        if(frame>=12){
            Rect src=bounds("motion.webp",frame-12);float ww=height*src.width()/src.height();
            c.save();c.rotate(shake,x,bottom-height*.6f);
            c.drawBitmap(motion,src,new RectF(x-ww/2,bottom-height,x+ww/2,bottom),p);c.restore();return;
        }
        int[] tops={0,420,825},ends={420,825,1254};int cw=hero.getWidth()/4,row=frame/4;
        float width=height*cw/(ends[row]-tops[row]);
        c.save();c.rotate(shake,x,bottom-height*.6f);
        c.drawBitmap(hero,new Rect(frame%4*cw,tops[row],(frame%4+1)*cw,ends[row]),new RectF(x-width/2,bottom-height,x+width/2,bottom),p);c.restore();
    }
}
