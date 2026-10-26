package com.sunantongsan.ghostmatch3;

import android.content.res.Resources;
import android.graphics.*;

/** Original artwork with live, rules-driven characters and hazards; no baked-in outcome. */
final class RescueArt {
    private final Paint p=new Paint(3);
    private final Bitmap castle,ghosts,boosters,magic;
    private final Rect[] ghostRects,boosterRects,magicRects;
    RescueArt(Resources r){
        castle=BitmapFactory.decodeResource(r,R.drawable.rescue_castle);
        ghosts=BitmapFactory.decodeResource(r,R.drawable.ghost_reactions);
        boosters=BitmapFactory.decodeResource(r,R.drawable.booster_sprites);
        magic=BitmapFactory.decodeResource(r,R.drawable.magic_items);
        ghostRects=slices(ghosts,3,4);boosterRects=slices(boosters,7,1);magicRects=slices(magic,4,1);
    }
    // Crop transparent padding at load time, keeping the legacy artwork's proportions.
    private Rect[] slices(Bitmap b,int cols,int rows){
        Rect[] out=new Rect[cols*rows];int w=b.getWidth()/cols,h=b.getHeight()/rows;
        for(int i=0;i<out.length;i++){int x0=i%cols*w,y0=i/cols*h,l=x0+w,t=y0+h,r=x0,bottom=y0;
            for(int y=y0;y<y0+h;y++)for(int x=x0;x<x0+w;x++)if(Color.alpha(b.getPixel(x,y))>24){l=Math.min(l,x);t=Math.min(t,y);r=Math.max(r,x+1);bottom=Math.max(bottom,y+1);}
            out[i]=l<r?new Rect(l,t,r,bottom):new Rect(x0,y0,x0+w,y0+h);
        }return out;
    }
    private void sprite(Canvas c,Bitmap b,Rect src,float x,float y,float size){
        float scale=size/Math.max(src.width(),src.height()),w=src.width()*scale,h=src.height()*scale;
        p.setShader(null);p.setAlpha(255);c.drawBitmap(b,src,new RectF(x-w/2,y-h/2,x+w/2,y+h/2),p);
    }
    void ghost(Canvas c,int color,int mood,float x,float y,float size){sprite(c,ghosts,ghostRects[mood*3+color%3],x,y,size);}
    void booster(Canvas c,int id,float x,float y,float size){
        if(id==7||id==8||id==1||id==2){int n=id==7?0:id==8?1:id==1?2:3;sprite(c,magic,magicRects[n],x,y,size);return;}
        int n=id==0?1:id==6?0:id==5?6:id==3?4:5;
        sprite(c,boosters,boosterRects[n],x,y,size);
        if(id==9)label(c,"+5",x,y+8,20,0xffffffff);
        if(id==4){p.setColor(0xff9effef);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawOval(x-size*.4f,y-size*.48f,x+size*.4f,y+size*.48f,p);p.setStyle(Paint.Style.FILL);}
    }
    private void label(Canvas c,String text,float x,float y,float size,int color){p.setShader(null);p.setColor(color);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextAlign(Paint.Align.CENTER);p.setTextSize(size);c.drawText(text,x,y,p);}
    private void line(Canvas c,float x,float y,float xx,float yy,int color,float width){p.setShader(null);p.setColor(color);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x,y,xx,yy,p);}
    void scene(Canvas c,AdventureEngine g,RectF box,float rescue,float danger,float winTime){
        c.save();c.clipRect(box);c.translate(box.left,box.top);c.scale(box.width()/376f,box.height()/168f);
        p.setAlpha(255);c.drawBitmap(castle,null,new RectF(0,-10,376,178),p);
        double tick=System.currentTimeMillis()/280.0;float bob=(float)Math.sin(tick)*3;
        float exit=g.won?Math.min(1,Math.max(0,winTime)/1.8f):0;
        float urgency=Math.max(0,(danger-.55f)/.45f);
        float gx=92+rescue*52+exit*180,gy=106+bob-exit*8;
        if(!g.won&&!g.lost)gx+=(float)Math.sin(tick*2)*urgency*4;
        if(g.level.kind==AdventureEngine.RESCUE&&!g.won){
            p.setColor(Color.argb((int)(urgency*(28+18*Math.sin(tick))),255,58,65));c.drawRect(0,0,376,168,p);
            line(c,18,157,358,157,0xff263349,6);
            line(c,18,157,18+340*Math.min(1,danger),157,danger>.7f?0xffff7070:0xffffcb70,6);
        }
        // The same danger counter that can end the level controls visible hazard position.
        if(g.level.kind==AdventureEngine.RESCUE&&(!g.won||exit<1)){
            danger*=1-exit;
            if(g.level.variant==0){
                float top=149-danger*65;Path water=new Path();water.moveTo(0,168);water.lineTo(0,top);
                for(int x=0;x<=376;x+=4)water.lineTo(x,top+3*(float)Math.sin(x*.07+tick));water.lineTo(376,168);water.close();
                p.setColor(0xb53acfe3);c.drawPath(water,p);
                for(int i=0;i<12;i++){p.setColor(0xaaffffff);c.drawCircle((i*37)%376,top+8+(i%3)*7,1.5f,p);}
            }else if(g.level.variant==1){
                float x=10+danger*100; p.setShader(new RadialGradient(x-8,95,42,new int[]{0xffb5c1d2,0xff596276,0xff293145},null,Shader.TileMode.CLAMP));c.drawCircle(x,108,35,p);p.setShader(null);
                line(c,x-19,92,x+5,110,0xff374252,4);line(c,x+5,110,x-8,128,0xff374252,3);
            }else if(g.level.variant==2){
                for(int i=0;i<7;i++){float y=16+i*18;line(c,0,y,43+danger*65,95,0xbbdce5ff,1.5f);}
                for(int i=0;i<4;i++){p.setColor(0x99e1eaff);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);c.drawOval(-30-i*17,17-i*8,27+i*24,145+i*8,p);p.setStyle(Paint.Style.FILL);}
            }else{
                for(int i=0;i<8;i++){float x=6+i*11,top=133-(20+danger*48)*(1+(float)Math.sin(tick+i)*.15f);Path flame=new Path();flame.moveTo(x-8,151);flame.quadTo(x-13,top+12,x+4,top);flame.quadTo(x-2,top+17,x+10,151);flame.close();p.setColor(i%2==0?0xffff973c:0xffffd45a);c.drawPath(flame,p);}
            }
        }
        if(g.level.kind==AdventureEngine.BOSS&&!g.won){
            ghost(c,2,2,291,84-bob,102);float health=Math.max(0,1-g.progress/(float)g.level.goal);
            p.setColor(0xff251735);c.drawRoundRect(236,139,345,147,4,4,p);p.setColor(0xfffb77ac);c.drawRoundRect(236,139,236+109*health,147,4,4,p);
        }
        // Remaining blockers physically raise the gate. Progress and obstacles both gate escape.
        if(g.level.kind!=AdventureEngine.BOSS&&g.level.kind!=AdventureEngine.BONUS){
            float open=g.won?exit:rescue,top=57-open*100;
            c.save();c.clipRect(190,22,261,145);
            for(int i=0;i<5;i++){float x=198+i*14;line(c,x,top,x,top+88,0xff172431,7);line(c,x-1,top,x-1,top+88,0xffbacbd0,3);}
            line(c,194,top+8,259,top+8,0xffd3b77a,7);line(c,194,top+82,259,top+82,0xffd3b77a,7);c.restore();
            if(g.remaining>0)label(c,"อาคม "+g.remaining,227,158,10,0xffffdf8a);
        }
        int mood=g.won?1:g.lost?3:g.freeze>0?0:danger>.65f?3:0;
        ghost(c,g.level.world%3,mood,gx,gy,91);
        if(g.shield>0||g.freeze>0){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(0xbb9cfff6);c.drawOval(gx-44,gy-52,gx+44,gy+50,p);p.setStyle(Paint.Style.FILL);}
        if(g.freeze>0)label(c,"หยุดภัย "+g.freeze+" ตา",86,26,12,0xffa6fff9);
        else if(g.level.kind==AdventureEngine.RESCUE&&!g.won)label(c,"ภัยใน "+Math.max(0,g.level.moves-g.danger)+" ตา",86,26,12,0xffffe3a4);
        if(g.won&&exit<1){
            for(int i=0;i<22;i++){float x=155+((i*41)%210),y=142-exit*120+(float)Math.sin(tick+i)*6;
                p.setColor(Color.argb((int)(220*(1-exit)),130,255,239));c.drawCircle(x,y,2+i%3,p);}
        }
        if(g.won){ghost(c,(g.level.world+1)%3,1,310,103-bob,65);label(c,"ขอบคุณที่ช่วยเรา!",190,29,16,0xffffdf8a);}
        else if(g.lost)label(c,"ลองช่วยเราอีกครั้งนะ",190,29,15,0xffffdf8a);
        c.restore();
    }
    void spell(Canvas c,int kind,int pos,float t,float bx,float by,float cell){
        if(t<0||t>1.15f||pos<0)return;
        float x=bx+(pos%8+.5f)*cell,y=by+(pos/8+.5f)*cell,pulse=(float)Math.sin(Math.min(1,t/1.15f)*Math.PI);
        int color=kind==1?0xff71e5ff:kind==2?0xffff87e4:0xffffd77c;
        c.save();c.clipRect(bx,by,bx+8*cell,by+8*cell);
        if(kind==1||kind==2){
            for(int layer=3;layer>=1;layer--){int a=(int)(pulse*(layer==1?245:60));int col=(a<<24)|(color&0xffffff);
                line(c,kind==1?bx:x,kind==1?y:by,kind==1?bx+8*cell:x,kind==1?y:by+8*cell,col,cell*.18f*layer*pulse);}
            for(int i=0;i<7;i++){float travel=(t*1.7f+i*.14f)%1*cell*8;p.setColor(0xffffffff);c.drawCircle(kind==1?bx+travel:x,kind==1?y:by+travel,3*pulse,p);}
        }else{p.setColor(((int)(210*pulse)<<24)|(color&0xffffff));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5*pulse);c.drawCircle(x,y,cell*(.3f+t*2.3f),p);p.setStyle(Paint.Style.FILL);}
        for(int i=0;i<12;i++){double a=Math.PI*2*i/12+t*2;float rad=cell*(.35f+t*2.2f),xx=x+(float)Math.cos(a)*rad,yy=y+(float)Math.sin(a)*rad;line(c,xx-3,yy,xx+3,yy,((int)(230*pulse)<<24)|0xffffff,2);line(c,xx,yy-3,xx,yy+3,((int)(230*pulse)<<24)|0xffffff,2);}
        c.restore();p.setAlpha(255);
    }
}
