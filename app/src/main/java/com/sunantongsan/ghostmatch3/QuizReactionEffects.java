package com.sunantongsan.ghostmatch3;

import android.graphics.*;

/** Bounded, time-based effects. Shared artwork; no particle allocation per frame. */
final class QuizReactionEffects {
    private final Bitmap animals;
    private final Paint p=new Paint(3);
    private final Rect src=new Rect();
    private final RectF dst=new RectF();
    private final int animal;
    private final int[] colors={0xFFFFD35C,0xFFFF6DB6,0xFF65E5FF,0xFFA9FF91,0xFFC6A1FF};
    QuizReactionEffects(Bitmap animals){this.animals=animals;animal=new java.util.Random().nextInt(4);}
    void draw(Canvas c,boolean correct,float seconds,float w,float h){
        if(seconds<0||seconds>4.8f)return;
        float fade=Math.min(1,Math.max(0,(4.8f-seconds)/.8f));
        c.save();c.clipRect(0,h*.32f,w,h*.88f);
        if(correct){
            // Three staggered fireworks, radial sparks, then falling ribbons.
            for(int burst=0;burst<3;burst++){
                float t=seconds-burst*.42f;if(t<0||t>2.2f)continue;
                float cx=w*(.20f+.30f*burst),cy=h*(.42f+.07f*(burst%2));
                for(int i=0;i<24;i++){
                    double angle=i*Math.PI/12;float radius=w*.30f*t;
                    float x=cx+(float)Math.cos(angle)*radius,y=cy+(float)Math.sin(angle)*radius+h*.045f*t*t;
                    p.setColor(colors[(i+burst)%colors.length]);p.setAlpha((int)(255*(1-t/2.2f)*fade));p.setStrokeWidth(w*.006f);
                    c.drawLine(x,y,x-(float)Math.cos(angle)*w*.045f,y-(float)Math.sin(angle)*w*.045f,p);
                    c.drawCircle(x,y,w*.006f,p);
                }
            }
            for(int i=0;i<72;i++){
                float x=w*((i*.618034f)%1),y=h*(.28f+((seconds*.13f+i*.071f)%.65f));
                p.setColor(colors[i%colors.length]);p.setAlpha((int)(235*fade));
                c.save();c.translate(x+(float)Math.sin(seconds*2+i)*w*.04f,y);c.rotate(seconds*130+i*31);
                c.drawRoundRect(-w*.007f,-w*.018f,w*.007f,w*.018f,w*.003f,w*.003f,p);c.restore();
            }
            badge(c,"สุดยอด!",w*.5f,h*.43f,w*.105f,0xFFFFD35C,fade);
            badge(c,"★  ★  ★",w*.5f,h*.51f,w*.085f,Color.WHITE,fade);
        }else{
            // Overshoot entrance, squash-and-stretch laughter and orbiting Thai 555.
            float enter=Math.min(1,seconds/.35f),bounce=1+(float)Math.sin(seconds*17)*.035f;
            float size=Math.min(w*.65f,h*.33f)*(enter==1?1:(1+(float)Math.sin(enter*Math.PI)*.14f)*enter);
            float cx=w*.5f,cy=h*.57f;
            p.setColor(0xFF8525BA);p.setAlpha((int)(110*fade));c.drawCircle(cx,cy,size*.58f,p);
            int cw=animals.getWidth()/2,ch=animals.getHeight()/2;
            src.set(animal%2*cw,animal/2*ch,(animal%2+1)*cw,(animal/2+1)*ch);
            dst.set(cx-size/2,cy-size/2,cx+size/2,cy+size/2);
            c.save();c.rotate((float)Math.sin(seconds*11)*6,cx,cy);c.scale(bounce,2-bounce,cx,cy);
            p.setColor(Color.WHITE);p.setAlpha((int)(255*fade));c.drawBitmap(animals,src,dst,p);c.restore();
            for(int i=0;i<9;i++){
                double angle=i*Math.PI*2/9+Math.sin(seconds*2)*.12;
                float x=cx+(float)Math.cos(angle)*size*.66f,y=cy+(float)Math.sin(angle)*size*.61f;
                c.save();c.rotate((float)Math.sin(seconds*5+i)*17,x,y);
                badge(c,i%3==0?"555":"5",x,y,w*(.07f+.015f*(i%2)),colors[i%colors.length],fade);c.restore();
            }
        }
        p.setAlpha(255);c.restore();
    }
    private void badge(Canvas c,String s,float x,float y,float size,int color,float alpha){
        p.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));p.setTextAlign(Paint.Align.CENTER);p.setTextSize(size);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(size*.13f);p.setColor(0xFF382151);p.setAlpha((int)(255*alpha));c.drawText(s,x,y,p);
        p.setStyle(Paint.Style.FILL);p.setColor(color);p.setAlpha((int)(255*alpha));c.drawText(s,x,y,p);
    }
}
