package com.sunantongsan.ghostmatch3;
import android.content.res.Resources;
import android.graphics.*;

/** Scene objects are driven by real objective and clock state, never a baked outcome. */
final class GuardianArt {
    private final Bitmap worlds,people;
    private final Rect[] poses=new Rect[8];
    private final Paint p=new Paint(3);
    GuardianArt(Resources r){
        worlds=BitmapFactory.decodeResource(r,R.drawable.guardian_worlds);
        people=BitmapFactory.decodeResource(r,R.drawable.guardian_people);
        int w=people.getWidth()/4,h=people.getHeight()/2;
        for(int i=0;i<8;i++){
            int x0=i%4*w,y0=i/4*h,l=x0+w,t=y0+h,right=x0,bottom=y0;
            for(int y=y0;y<y0+h;y++)for(int x=x0;x<x0+w;x++)if(Color.alpha(people.getPixel(x,y))>40){l=Math.min(l,x);t=Math.min(t,y);right=Math.max(right,x+1);bottom=Math.max(bottom,y+1);}
            poses[i]=l<right?new Rect(l,t,right,bottom):new Rect(x0,y0,x0+w,y0+h);
        }
    }
    private void line(Canvas c,float x,float y,float xx,float yy,int color,float width){p.setShader(null);p.setColor(color);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x,y,xx,yy,p);}
    private void rect(Canvas c,float l,float t,float r,float b,int color,float radius){p.setShader(null);p.setColor(color);c.drawRoundRect(l,t,r,b,radius,radius,p);}
    private void person(Canvas c,int pose,float x,float feet,float height){
        Rect src=poses[pose];float w=height*src.width()/src.height();p.setShader(null);p.setAlpha(255);c.drawBitmap(people,src,new RectF(x-w/2,feet-height,x+w/2,feet),p);
    }
    void portrait(Canvas c,float x,float y,float height){person(c,6,x,y+height/2,height);}
    void scene(Canvas c,GuardianStory story,AdventureEngine game,RectF box,float energy,float cleared,float danger,float outcome,float intro,RescueArt ghosts){
        c.save();c.clipRect(box);c.translate(box.left,box.top);float scale=box.width()/376f;c.scale(scale,scale);
        float h=box.height()/scale,floor=h*.84f;int family=story.mechanism%4;
        int sw=worlds.getWidth()/2,sh=worlds.getHeight()/2;
        int cropH=Math.min(sh,Math.round(sw*h/376f)),top=(sh-cropH)/2;
        Rect src=new Rect(family%2*sw,family/2*sh+top,(family%2+1)*sw,family/2*sh+top+cropH);
        p.setColor(Color.WHITE);p.setShader(null);c.drawBitmap(worlds,src,new RectF(0,0,376,h),p);
        float tick=android.os.SystemClock.uptimeMillis()/1000f;
        boolean movie=intro>=0,goodDeed=movie&&intro<2.3f,win=game.won;
        float safe=win?Math.min(1,Math.max(0,outcome)/2.2f):0;
        float charge=movie?0:Math.min(1,energy),open=movie?0:Math.min(1,cleared);
        if(movie)danger=intro<2.3f?0:Math.min(.5f,(intro-2.3f)/6);
        danger*=1-safe;
        // Position changes only after the physical intervention has completed.
        float actorX=86+safe*235;
        float ground=floor;
        if(family==0){
            float bridge=story.mechanism==4?Math.min(charge,open):charge;
            if(movie)bridge=intro<2.3f?1:Math.max(0,1-(intro-2.3f));
            for(int i=0;i<8;i++)if(i<bridge*8+.05f){float x=116+i*21;
                line(c,x,floor+3,x+18,floor+3,0xff5b4a33,7);line(c,x,floor,x+18,floor,0xffffdca0,3);
                if(i==Math.min(7,(int)(bridge*8)))line(c,x,floor-3,x+18,floor-3,0xffb6ffff,2);
            }
            if(story.mechanism==4&&charge>0){p.setColor(0x8060e7ff);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);c.drawArc(47,15,145,floor+12,-110,220,false,p);p.setStyle(Paint.Style.FILL);}
            // Rain and wind strengthen toward the real deadline, then settle after rescue.
            for(int i=0;i<28;i++){float x=(i*43+tick*(35+danger*90))%400-20,y=(i*29+tick*80)%h;
                line(c,x,y,x-6-danger*10,y+13,0x77b5e2f7,1);}
            rect(c,294,floor-55,309,floor,0xff684f35,3);
            if(open<1){for(int i=0;i<3;i++)if(open<(i+1)/3f)line(c,281,floor-12-i*14,323,floor-12-i*14,0xffa77c43,5);}
            if(danger>.65f&&!win){c.save();c.rotate((float)Math.sin(tick*8)*danger*4,70,floor);line(c,40,floor+4,107,floor+4,0xff706157,5);c.restore();}
        }else if(family==1){
            float drainage=story.mechanism==5?Math.min(charge,open):charge;
            float waterTop=floor+14-danger*h*.47f+drainage*h*.42f;
            waterTop=Math.min(h+4,waterTop+safe*h*.3f);
            Path water=new Path();water.moveTo(0,h);water.lineTo(0,waterTop);
            for(int x=0;x<=376;x+=4)water.lineTo(x,waterTop+2*(float)Math.sin(x*.09+tick*3));water.lineTo(376,h);water.close();
            p.setShader(new LinearGradient(0,waterTop,0,h,0xaa61dfff,0xee154b84,Shader.TileMode.CLAMP));c.drawPath(water,p);p.setShader(null);
            if(charge>0)for(int i=0;i<18;i++){float u=(tick*.6f+i/18f)%1,x=195+(1-u)*95,y=waterTop+u*(h-waterTop);line(c,x,y,x-7,y+4,0xaaddffff,2);}
            // Drain grille and valve visibly open with the collected ghost power.
            for(int i=0;i<5;i++)line(c,182+i*7,h-22-charge*30,182+i*7,h-charge*30,0xffbdc9c7,3);
            c.save();c.rotate(charge*270,274,floor-44);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);p.setColor(0xffffce7d);c.drawCircle(274,floor-44,15,p);p.setStyle(Paint.Style.FILL);line(c,259,floor-44,289,floor-44,0xffeec17a,3);c.restore();
            if(open<1)for(int i=0;i<3;i++)if(open<(i+1)/3f)rect(c,303,floor-18-i*17,331,floor-i*17,0xff856446,3);
        }else if(family==2){
            if(story.mechanism==2){
                float y=18+danger*(floor-65)-charge*65;y=Math.max(-55,y);
                line(c,179,0,179,y,0xffd5c5a8,3);rect(c,146,y,214,y+48,0xff62401f,4);rect(c,150,y+4,210,y+44,0xffbb8c4d,2);
                line(c,151,y+5,209,y+43,0xff694a2f,5);line(c,151,y+43,209,y+5,0xff694a2f,5);
                if(charge>0){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(0xffa5fff3);c.drawRoundRect(143,y-3,217,y+51,7,7,p);p.setStyle(Paint.Style.FILL);}
            }else{
                float x=302-danger*190+charge*90;
                rect(c,x-32,floor-38,x+32,floor-6,0xffa77c43,4);line(c,x-36,floor-5,x+36,floor-5,0xff54687d,5);
                p.setColor(0xff17293a);c.drawCircle(x-23,floor,7,p);c.drawCircle(x+23,floor,7,p);
                if(charge>0){line(c,135,floor-50,135,floor+3,0xff84ffec,7);line(c,127,floor-50,143,floor-50,0xffe8ffff,3);}
            }
            if(open<1)for(int i=0;i<3;i++)if(open<(i+1)/3f)line(c,286,floor-14-i*15,330,floor-14-i*15,0xff95704d,6);
        }else{
            float approach=danger*(1-charge*.9f),size=23+approach*105;
            float x=story.mechanism==7?192+charge*100:192;
            float y=20+approach*(floor-70);
            rect(c,x-size*.38f,y,x+size*.38f,y+size*.8f,0xff254a68,6);
            rect(c,x-size*.3f,y+4,x+size*.3f,y+size*.3f,0xffaadbe7,3);
            rect(c,x-size*.38f,y+size*.48f,x+size*.38f,y+size*.7f,0xffd6a947,2);
            for(int sign:new int[]{-1,1}){p.setColor(0xfffff2b2);c.drawCircle(x+sign*size*.22f,y+size*.43f,size*.06f,p);}
            c.save();c.rotate(-open*85,293,floor-10);line(c,293,floor-10,349,floor-10,0xffefebe2,6);line(c,310,floor-10,321,floor-10,0xffb44343,6);c.restore();
            if(charge>0){p.setColor(0x668ef8ff);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);c.drawOval(x-size*.5f,y-5,x+size*.5f,y+size*.9f,p);p.setStyle(Paint.Style.FILL);}
        }
        int pose=goodDeed?(family==0?0:5):win?(safe<.9f?3:6):danger>.72f?2:1;
        if(movie&&intro<2.3f&&family!=0)actorX=70+intro*9;
        if(win&&safe<.9f)ground+=(float)Math.sin(tick*18)*2;
        person(c,pose,actorX,ground,goodDeed&&family==0?h*.55f:h*.68f);
        if(!movie||intro>4.3f){float fade=movie?Math.min(1,(intro-4.3f)/1.2f):1;
            c.save();c.scale(fade,fade,40,h*.3f);ghosts.ghost(c,0,win?1:0,40,h*.3f+(float)Math.sin(tick*3)*3,h*.28f);c.restore();}
        if(charge>0&&!win)for(int i=0;i<8;i++){float u=(tick*.4f+i/8f)%1,x=40+u*150,y=h*.3f+(floor-h*.3f)*u;
            p.setColor(Color.argb((int)(170*(1-u)),154,255,238));c.drawCircle(x,y,1.5f+(1-u)*2,p);}
        if(danger>.7f&&!win){p.setColor(Color.argb((int)(18+16*Math.sin(tick*5)),255,55,43));c.drawRect(0,0,376,h,p);}
        if(game.lost){rect(c,0,0,376,h,0x99472d3e,0);ghosts.ghost(c,0,0,135,h*.4f,h*.3f);}
        c.restore();p.setAlpha(255);
    }
}
