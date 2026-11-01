package com.sunantongsan.ghostmatch3;

import android.graphics.*;
import java.util.ArrayList;

/** Short, bounded effects driven by actual resolved clears, never by touch guesses. */
final class MatchEffects {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path star=new Path();
    private final ArrayList<Burst> bursts=new ArrayList<>();
    private final int[] palette={0xFFB67AFF,0xFF67DCAC,0xFF8FDFFF};
    private long labelAt; private int combo,cleared;
    private static final class Burst {
        final int position,type,power; final long at;
        Burst(int p,int t,int s,long time){position=p;type=t;power=s;at=time;}
    }
    void reset(){bursts.clear();combo=0;cleared=0;labelAt=0;}
    void clear(AdventureEngine.Step before,AdventureEngine.Step after,long now){
        int count=0;
        for(int r=0;r<8;r++)for(int c=0;c<8;c++)if(before.tiles[r][c]>=0&&after.tiles[r][c]<0){
            if(bursts.size()>=80)bursts.remove(0);
            bursts.add(new Burst(r*8+c,before.tiles[r][c],before.powers[r][c],now));count++;
        }
        if(count>0){combo++;cleared+=count;labelAt=now;}
    }
    boolean alive(long now){return !bursts.isEmpty()||labelAt>0&&now-labelAt<750;}
    private void star(Canvas c,float x,float y,float radius,float rotation){
        star.reset();
        for(int n=0;n<10;n++){double a=rotation+n*Math.PI/5;float r=n%2==0?radius:radius*.42f;float sx=x+(float)Math.cos(a)*r,sy=y+(float)Math.sin(a)*r;if(n==0)star.moveTo(sx,sy);else star.lineTo(sx,sy);}star.close();c.drawPath(star,paint);
    }
    void draw(Canvas c,float cell,long now){
        for(int i=bursts.size()-1;i>=0;i--){Burst b=bursts.get(i);float t=(now-b.at)/650f;if(t>=1){bursts.remove(i);continue;}
            float x=(b.position%8+.5f)*cell,y=(b.position/8+.5f)*cell;
            int color=palette[b.type%3];paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(cell*.055f*(1-t)+1);paint.setColor(color);paint.setAlpha((int)(210*(1-t)));
            c.drawCircle(x,y,cell*(.16f+.68f*t),paint);
            if(b.power>0){paint.setColor(0xFFFFD057);paint.setStrokeWidth(cell*.12f*(1-t));
                if(b.power==1||b.power==4)c.drawLine(0,y,cell*8,y,paint);
                if(b.power==2||b.power==4)c.drawLine(x,0,x,cell*8,paint);
                if(b.power>=3)c.drawCircle(x,y,cell*(.3f+2*t),paint);
            }
            paint.setStyle(Paint.Style.FILL);
            for(int n=0;n<7;n++){double angle=n*Math.PI*2/7+b.position*.53;float speed=.42f+(n%3)*.17f;float distance=cell*(.12f+speed*t);
                float px=x+(float)Math.cos(angle)*distance,py=y+(float)Math.sin(angle)*distance+cell*.38f*t*t;
                paint.setColor(n%2==0?0xFFFFD35C:color);paint.setAlpha((int)(255*(1-t)));
                star(c,px,py,cell*.10f*(1-t)+1,(float)(angle+t*3));
            }
            paint.setColor(Color.WHITE);paint.setAlpha((int)(120*(1-t)*(1-t)));c.drawCircle(x,y,cell*.28f*(1-t),paint);
        }
        if(labelAt>0&&now-labelAt<750){float t=(now-labelAt)/750f;float y=cell*(.85f-.22f*t);String label=combo>1?"คอมโบ "+combo+"!":"ปิ๊ง! +"+(cleared*10);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(cell*.43f);
            paint.setAlpha(255);paint.setColor(0xFF342844);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(cell*.075f);c.drawText(label,cell*4,y,paint);
            paint.setStyle(Paint.Style.FILL);paint.setColor(combo>1?0xFFFFDE69:Color.WHITE);paint.setAlpha((int)(255*Math.min(1,(1-t)*3)));c.drawText(label,cell*4,y,paint);
        }
        paint.setAlpha(255);paint.setStyle(Paint.Style.FILL);
    }
}
