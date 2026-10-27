package com.sunantongsan.ghostmatch3;
import android.content.res.Resources;
import android.graphics.*;

/** Scene objects are driven by real objective and clock state, never a baked outcome. */
final class GuardianArt {
    private final Bitmap worlds,people,props;
    private final Rect[] poses=new Rect[8],propRects=new Rect[8];
    private final Paint p=new Paint(3);
    GuardianArt(Resources r){
        worlds=BitmapFactory.decodeResource(r,R.drawable.guardian_worlds);
        people=BitmapFactory.decodeResource(r,R.drawable.guardian_people);
        props=BitmapFactory.decodeResource(r,R.drawable.guardian_props);
        int[][] areas={{0,0,412,470},{420,0,768,490},{776,0,1152,490},{1152,0,1536,490},{0,470,384,1024},{390,512,768,1024},{768,512,1152,1024},{1152,512,1536,1024}};
        for(int i=0;i<8;i++){
            int[] a=areas[i];int x0=a[0]*props.getWidth()/1536,y0=a[1]*props.getHeight()/1024,x1=a[2]*props.getWidth()/1536,y1=a[3]*props.getHeight()/1024;
            int l=x1,t=y1,right=x0,bottom=y0;
            for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++)if(Color.alpha(props.getPixel(x,y))>40){l=Math.min(l,x);t=Math.min(t,y);right=Math.max(right,x+1);bottom=Math.max(bottom,y+1);}
            propRects[i]=new Rect(l,t,right,bottom);
        }
        int w=people.getWidth()/4,h=people.getHeight()/2;
        for(int i=0;i<8;i++){
            int x0=i%4*w,y0=i/4*h,l=x0+w,t=y0+h,right=x0,bottom=y0;
            for(int y=y0;y<y0+h;y++)for(int x=x0;x<x0+w;x++)if(Color.alpha(people.getPixel(x,y))>40){l=Math.min(l,x);t=Math.min(t,y);right=Math.max(right,x+1);bottom=Math.max(bottom,y+1);}
            poses[i]=l<right?new Rect(l,t,right,bottom):new Rect(x0,y0,x0+w,y0+h);
        }
    }
    private void line(Canvas c,float x,float y,float xx,float yy,int color,float width){p.setShader(null);p.setColor(color);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x,y,xx,yy,p);}
    private void rect(Canvas c,float l,float t,float r,float b,int color,float radius){p.setShader(null);p.setColor(color);c.drawRoundRect(l,t,r,b,radius,radius,p);}
    private void prop(Canvas c,int index,float x,float bottom,float width,float height,float alpha){
        Rect src=propRects[index];float ratio=Math.min(width/src.width(),height/src.height()),w=src.width()*ratio,h=src.height()*ratio;
        p.setShader(null);p.setAlpha((int)(255*Math.max(0,Math.min(1,alpha))));c.drawBitmap(props,src,new RectF(x-w/2,bottom-h,x+w/2,bottom),p);p.setAlpha(255);
    }
    private void person(Canvas c,int pose,float x,float feet,float height){
        Rect src=poses[pose];float w=height*src.width()/src.height();p.setShader(null);p.setAlpha(255);c.drawBitmap(people,src,new RectF(x-w/2,feet-height,x+w/2,feet),p);
    }
    void portrait(Canvas c,float x,float y,float height){person(c,6,x,y+height/2,height);}
    void scene(Canvas c,GuardianStory story,AdventureEngine game,RectF box,float energy,float cleared,float danger,float outcome,float intro,RescueArt ghosts){
        c.save();c.clipRect(box);c.translate(box.left,box.top);float scale=box.width()/376f;c.scale(scale,scale);
        float h=box.height()/scale;int family=story.mechanism%4;
        int sw=worlds.getWidth()/2,sh=worlds.getHeight()/2;
        int cropH=Math.min(sh,Math.round(sw*h/376f)),top=(sh-cropH)/2;
        float floor=Math.min(h-10,(new int[]{330,330,400,405}[family]-top)*h/cropH);
        Rect src=new Rect(family%2*sw,family/2*sh+top,(family%2+1)*sw,family/2*sh+top+cropH);
        p.setColor(Color.WHITE);p.setShader(null);c.drawBitmap(worlds,src,new RectF(0,0,376,h),p);
        float tick=android.os.SystemClock.uptimeMillis()/1000f;
        boolean movie=intro>=0,goodDeed=movie&&intro<2.3f,win=game.won;
        float safe=win?Math.min(1,Math.max(0,outcome)/2.2f):0;
        float charge=movie?0:Math.min(1,energy),open=movie?0:Math.min(1,cleared);
        if(movie)danger=intro<2.3f?0:Math.min(.25f,(intro-2.3f)/12);
        else danger=.25f+.75f*danger;
        float trainDanger=danger;
        danger*=1-safe;
        // Position changes only after the physical intervention has completed.
        float actorStart=story.mechanism==2?179:family==3?173:86;
        float actorX=actorStart+safe*(321-actorStart);
        float ground=floor;Path floodFront=null;float floodTop=0;
        if(family==0){
            float bridge=story.mechanism==4?Math.min(charge,open):charge;
            if(movie)bridge=intro<2.3f?1:Math.max(0,1-(intro-2.3f));
            c.save();c.clipRect(113,floor-45,113+173*bridge,floor+28);prop(c,0,199,floor+24,176,75,1);c.restore();
            if(bridge>0&&bridge<1)line(c,113+173*bridge,floor-5,113+173*bridge,floor+6,0xffc9fff4,3);
            if(story.mechanism==4&&charge>0){p.setColor(0x8060e7ff);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);c.drawArc(47,15,145,floor+12,-110,220,false,p);p.setStyle(Paint.Style.FILL);}
            // Rain and wind strengthen toward the real deadline, then settle after rescue.
            for(int i=0;i<28;i++){float x=(i*43+tick*(35+danger*90))%400-20,y=(i*29+tick*80)%h;
                line(c,x,y,x-6-danger*10,y+13,0x77b5e2f7,1);}
            if(open<1)prop(c,1,305,floor+2,45,67,1-open);
            if(danger>.65f&&!win){c.save();c.rotate((float)Math.sin(tick*8)*danger*4,70,floor);line(c,40,floor+4,107,floor+4,0xff706157,5);c.restore();}
        }else if(family==1){
            float drainage=story.mechanism==5?Math.min(charge,open):charge;
            float waterTop=floor+14-danger*h*.72f+drainage*h*.24f;
            waterTop=Math.min(h+4,waterTop+safe*h*.8f);
            Path water=new Path();water.moveTo(0,h);water.lineTo(0,waterTop);
            for(int x=0;x<=376;x+=4)water.lineTo(x,waterTop+2*(float)Math.sin(x*.09+tick*3));water.lineTo(376,h);water.close();
            p.setShader(new LinearGradient(0,waterTop,0,h,0xaa61dfff,0xee154b84,Shader.TileMode.CLAMP));c.drawPath(water,p);p.setShader(null);floodFront=water;floodTop=waterTop;
            if(charge>0)for(int i=0;i<18;i++){float u=(tick*.6f+i/18f)%1,x=195+(1-u)*95,y=waterTop+u*(h-waterTop);line(c,x,y,x-7,y+4,0xaaddffff,2);}
            // Drain grille and valve visibly open with the collected ghost power.
            prop(c,7,199,h+10-charge*28,57,55,1);
            c.save();c.rotate(charge*270,274,floor-44);prop(c,5,274,floor-27,34,34,1);c.restore();
            if(open<1)prop(c,1,319,floor+2,40,62,1-open);
        }else if(family==2){
            if(story.mechanism==2){
                float y=-30+danger*(floor-80)-charge*75;y=Math.max(-100,y);
                line(c,179,0,179,y,0xffd5c5a8,3);prop(c,2,179,y+55,80,92,1);
                if(charge>0){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(0xffa5fff3);c.drawRoundRect(143,y-3,217,y+51,7,7,p);p.setStyle(Paint.Style.FILL);}
            }else{
                float x=302-danger*190+charge*90;
                prop(c,3,x,floor+5,78,72,1);
                if(charge>0){line(c,135,floor-50,135,floor+3,0xff84ffec,7);line(c,127,floor-50,143,floor-50,0xffe8ffff,3);}
            }
            if(open<1)prop(c,1,309,floor+2,45,67,1-open);
        }else{
            float approach=trainDanger*(1-charge*.35f),size=23+approach*105;
            float x=166+approach*10+(story.mechanism==7?charge*100:0);
            float y=h*.62f+approach*(floor+8-h*.62f)-size;
            prop(c,4,x,y+size,Math.max(23,size),Math.max(30,size*1.3f),1);
            c.save();c.rotate(-open*70,293,floor-10);prop(c,6,320,floor+3,70,65,1);c.restore();
            if(charge>0){p.setColor(0x668ef8ff);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);c.drawOval(x-size*.5f,y-5,x+size*.5f,y+size*.9f,p);p.setStyle(Paint.Style.FILL);}
        }
        int pose=goodDeed?(family==0?0:5):win?(safe<.9f?3:6):danger>.72f?2:1;
        if(movie&&intro<2.3f&&family!=0)actorX=70+(actorStart-70)*Math.min(1,intro/2.3f);
        if(win&&safe<.9f)ground+=(float)Math.sin(tick*18)*2;
        person(c,pose,actorX,ground,Math.min(floor-10,goodDeed&&family==0?h*.55f:h*.68f));
        if(floodFront!=null){p.setShader(new LinearGradient(0,floodTop,0,h,0x6657cde8,0xb3154b84,Shader.TileMode.CLAMP));c.drawPath(floodFront,p);p.setShader(null);}
        if(!movie||intro>4.3f){float fade=movie?Math.min(1,(intro-4.3f)/1.2f):1;
            c.save();c.scale(fade,fade,40,h*.3f);ghosts.ghost(c,0,win?1:0,40,h*.3f+(float)Math.sin(tick*3)*3,h*.28f);c.restore();}
        if(charge>0&&!win)for(int i=0;i<8;i++){float u=(tick*.4f+i/8f)%1,x=40+u*150,y=h*.3f+(floor-h*.3f)*u;
            p.setColor(Color.argb((int)(170*(1-u)),154,255,238));c.drawCircle(x,y,1.5f+(1-u)*2,p);}
        if(danger>.7f&&!win){p.setColor(Color.argb((int)(18+16*Math.sin(tick*5)),255,55,43));c.drawRect(0,0,376,h,p);}
        if(game.lost){rect(c,0,0,376,h,0x99472d3e,0);ghosts.ghost(c,0,0,135,h*.4f,h*.3f);}
        c.restore();p.setAlpha(255);
    }
}
