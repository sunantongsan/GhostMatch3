package com.sunantongsan.ghostmatch3;
import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Decorative backdrop uses the same decoded hero atlas as gameplay. */
final class WelcomeArtView extends View {
    private StoryArt art;
    private final Paint p=new Paint(3);
    WelcomeArtView(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void setArt(StoryArt value){art=value;invalidate();}
    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight(),d=getResources().getDisplayMetrics().density;
        float cy=Math.min(h*.43f,330*d),radius=Math.min(w*.37f,165*d);
        p.setShader(new RadialGradient(w*.5f,cy,radius*1.5f,new int[]{0xFFB985EF,0x008C56CB},null,Shader.TileMode.CLAMP));
        c.drawCircle(w*.5f,cy,radius*1.5f,p);p.setShader(null);
        p.setColor(0x33FFFFFF);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2*d);c.drawCircle(w*.5f,cy,radius,p);p.setStyle(Paint.Style.FILL);
        for(int i=0;i<14;i++){double angle=i*Math.PI*2/14;float x=w*.5f+(float)Math.cos(angle)*radius*1.2f,y=cy+(float)Math.sin(angle)*radius*1.04f;p.setColor(i%2==0?0xFFFFD56A:0xFFCFACF8);c.save();c.rotate(i*27,x,y);c.drawRoundRect(x-3*d,y-7*d,x+3*d,y+7*d,2*d,2*d,p);c.restore();}
        if(art!=null){p.setColor(Color.WHITE);art.actor(c,0,w*.5f,cy+radius*.94f,radius*1.9f,0,p);}
        p.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));p.setTextSize(30*d);p.setColor(0xFFFFDD6B);c.save();c.rotate(-14,w*.15f,cy);c.drawText("ฮ่า!",w*.08f,cy,p);c.restore();
        p.setTextSize(25*d);c.save();c.rotate(14,w*.84f,cy-radius*.6f);c.drawText("555",w*.77f,cy-radius*.6f,p);c.restore();
    }
}
