package com.sunantongsan.ghostmatch3;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.content.*;
import java.util.*;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(18,10,46));
        getWindow().setNavigationBarColor(Color.rgb(18,10,46));
        setContentView(new GhostGameView(this));
    }
}

class GhostGameView extends View {
    private static final int N=7, TYPES=3;
    private final int[][] board=new int[N][N];
    private final float[][] fallFrom=new float[N][N];
    private final Set<Integer> exploding=new HashSet<>();
    private int animationPhase=0,animationSerial=0,cascadeDepth=0;
    private long phaseStart=0;
    private int specialR=-1,specialC=-1;
    private final Random rng=new Random();
    private final Paint p=new Paint(3);
    private final Paint stroke=new Paint(3);
    private final Paint spritePaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private Bitmap ghostSheet,boosterSheet,hauntedBackground;
    private final int[] colors={Color.rgb(245,245,255),Color.rgb(188,236,172),Color.rgb(161,77,227)};
    private final String[] boosterNames={"SWAP","HAMMER","ROW","COLUMN","HELPER","WAND","+5"};
    private final int[] boosterCount={8,8,6,6,6,8,8};
    private final int[] collected=new int[TYPES];
    private final int[] goals={10,10,10};
    private int helperFirstR=-1,helperFirstC=-1;
    private boolean paused=false;
    private int level=1, moves=28, score=0, target=1800, selectedR=-1, selectedC=-1;
    private int mode=-1, combo=0;
    private float boardX,boardY,cell,boosterY;
    private float touchDownX, touchDownY;
    private int touchDownR=-1, touchDownC=-1;
    private boolean won=false,lost=false;
    private long gameStart=System.currentTimeMillis();
    private final ArrayList<Spark> sparks=new ArrayList<>();
    private String comboText="";
    private long comboUntil=0;
    private float swipeFX=-1, swipeFY=-1;
    private static class Spark {
        float x,y,vx,vy,life,size; int color;
        Spark(float x,float y,float vx,float vy,float life,float size,int color){
            this.x=x;this.y=y;this.vx=vx;this.vy=vy;this.life=life;this.size=size;this.color=color;
        }
    }
    private String toast="Match 3 ghosts to begin!";
    private long toastUntil=0;
    private final android.os.Handler handler=new android.os.Handler();

    GhostGameView(Context c){
        super(c);
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        stroke.setStyle(Paint.Style.STROKE);
        ghostSheet=BitmapFactory.decodeResource(getResources(),R.drawable.ghost_sprites);
        boosterSheet=BitmapFactory.decodeResource(getResources(),R.drawable.booster_sprites);
        hauntedBackground=BitmapFactory.decodeResource(getResources(),R.drawable.haunted_background);
        newLevel();
    }

    private void newLevel(){
        animationSerial++;animationPhase=0;exploding.clear();
        for(float[] row:fallFrom)Arrays.fill(row,0);
        moves=26+(level/8)*2;
        target=1500+level*200;
        for(int i=0;i<TYPES;i++){goals[i]=8+level/3;collected[i]=0;}
        score=0; combo=0; won=false; lost=false; paused=false; mode=-1;helperFirstR=-1;
        for(int r=0;r<N;r++) for(int c=0;c<N;c++){
            int t;
            do { t=rng.nextInt(TYPES); } while((c>=2&&board[r][c-1]==t&&board[r][c-2]==t)||(r>=2&&board[r-1][c]==t&&board[r-2][c]==t));
            board[r][c]=t;
        }
        ensureMove();
        message("Level "+level+" — easy goal!");
        invalidate();
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(), h=getHeight();
        Paint bg=new Paint();
        bg.setShader(new LinearGradient(0,0,w,h,Color.rgb(22,10,54),Color.rgb(49,19,84),Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,bg);
        if(hauntedBackground!=null&&!hauntedBackground.isRecycled()){
            c.drawBitmap(hauntedBackground,null,new RectF(0,0,w,h),spritePaint);
            p.setColor(Color.argb(28,12,4,36));c.drawRect(0,0,w,h,p);
        } else {
            drawStars(c,w,h);
            drawHauntedScene(c,w,h);
        }
        p.setTypeface(Typeface.create("sans",Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        float margin=w*.055f;
        float top=h*.018f;
        panel(c,margin,top,w*.20f,h*.13f,Color.rgb(65,34,113));
        p.setTextSize(w*.035f);p.setColor(Color.WHITE);
        c.drawText("ด่านที่",w*.135f,h*.057f,p);
        p.setTextSize(w*.072f);c.drawText(""+level,w*.135f,h*.113f,p);
        panel(c,w*.235f,top,w*.675f,h*.147f,Color.rgb(51,32,107));
        p.setTextSize(w*.037f);p.setColor(Color.WHITE);c.drawText("เป้าหมาย",w*.455f,h*.052f,p);
        for(int i=0;i<3;i++){
            float gx=w*(.316f+.148f*i);
            drawGhost(c,gx,h*.098f,w*.038f,colors[i],i,false);
            p.setColor(collected[i]>=goals[i]?Color.rgb(107,236,94):Color.WHITE);
            p.setTextSize(w*.024f);p.setTextAlign(Paint.Align.CENTER);
            c.drawText(collected[i]+"/"+goals[i],gx,h*.145f,p);
        }
        panel(c,w*.695f,top,w*.96f,h*.084f,Color.rgb(56,35,105));
        p.setColor(Color.WHITE);p.setTextSize(w*.034f);c.drawText("คะแนน",w*.827f,h*.045f,p);
        p.setColor(Color.rgb(255,214,91));p.setTextSize(w*.046f);c.drawText(""+score,w*.827f,h*.075f,p);
        panel(c,w*.695f,h*.095f,w*.96f,h*.174f,Color.rgb(45,42,116));
        p.setColor(Color.WHITE);p.setTextSize(w*.030f);c.drawText("เหลือการย้าย",w*.827f,h*.125f,p);
        p.setColor(Color.rgb(255,206,88));p.setTextSize(w*.061f);c.drawText(""+moves,w*.827f,h*.162f,p);
        drawRound(c,w*.91f,top,w*.985f,top+w*.075f,Color.rgb(130,63,198),w*.04f);
        p.setColor(Color.WHITE);p.setTextSize(w*.043f);c.drawText(paused?"▶":"Ⅱ",w*.947f,top+w*.053f,p);
        float progL=w*.24f,progR=w*.66f,progY=h*.177f;
        drawRound(c,progL,progY,progR,progY+w*.03f,Color.rgb(24,28,62),w*.02f);
        float ratio=0;for(int i=0;i<3;i++)ratio+=Math.min(1f,collected[i]/(float)goals[i])/3f;
        p.setShader(new LinearGradient(progL,0,progR,0,Color.rgb(75,193,66),Color.rgb(161,250,84),Shader.TileMode.CLAMP));
        c.drawRoundRect(progL,progY,progL+(progR-progL)*ratio,progY+w*.03f,w*.02f,w*.02f,p);p.setShader(null);
        for(int i=1;i<=3;i++){
            p.setColor(ratio>=i/3f?Color.rgb(255,214,72):Color.rgb(103,90,139));
            p.setTextSize(w*.037f);c.drawText("★",progL+(progR-progL)*i/3f,progY+w*.03f,p);
        }

        boardX=margin; boardY=h*.222f; cell=(w-2*margin)/N;
        panel(c,boardX-w*.017f,boardY-w*.017f,w-boardX+w*.017f,boardY+cell*N+w*.017f,Color.rgb(37,39,83));
        int boardClip=c.save();
        c.clipRect(boardX,boardY,boardX+N*cell,boardY+N*cell);
        for(int r=0;r<N;r++)for(int col=0;col<N;col++)drawCell(c,r,col);
        c.restoreToCount(boardClip);
        drawEffects(c,w,h);

        boosterY=boardY+cell*N+h*.025f;
        panel(c,margin,boosterY-h*.019f,w-margin,boosterY+h*.115f,Color.rgb(61,36,116));
        p.setTextAlign(Paint.Align.LEFT);p.setTextSize(w*.033f);p.setColor(Color.WHITE);
        c.drawText("ไอเท็มช่วยเหลือ",margin+w*.024f,boosterY+h*.004f,p);
        float gap=w*.008f, bw=(w-2*margin-w*.035f-gap*6)/7f, by=boosterY+h*.014f;
        for(int i=0;i<7;i++)drawBooster(c,i,margin+w*.018f+i*(bw+gap),by,bw,h*.087f,w);
        drawRound(c,margin+w*.11f,h*.881f,w-margin-w*.02f,h*.963f,Color.rgb(67,156,35),w*.09f);
        p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(w*.052f);
        c.drawText("ผ่านง่าย! สนุกได้ทุกคน ♥",w*.52f,h*.934f,p);
        drawGhost(c,w*.16f,h*.91f,w*.07f,colors[0],0,false);

        if(System.currentTimeMillis()<toastUntil){
            float ty=h*.925f;
            drawRound(c,margin,ty-h*.043f,w-margin,ty+h*.018f,Color.argb(230,70,36,112),30);
            p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.WHITE);p.setTextSize(w*.035f);
            c.drawText(toast,w/2,ty,p);
            postInvalidateDelayed(100);
        }
        if(comboUntil>System.currentTimeMillis()){
            float lift=(comboUntil-System.currentTimeMillis())/1200f;
            p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans",Typeface.BOLD));
            p.setTextSize(w*.078f);p.setColor(Color.WHITE);
            p.setShadowLayer(18,0,0,Color.rgb(255,87,203));
            c.drawText(comboText,w/2,boardY+cell*N*.48f-lift*w*.08f,p);p.clearShadowLayer();
        }
        if(won||lost||paused) drawOverlay(c,w,h);
        postInvalidateOnAnimation();
    }

    private void drawStars(Canvas c,float w,float h){
        p.setColor(Color.argb(120,255,255,255));
        for(int i=0;i<34;i++){
            float x=(i*83%997)/997f*w, y=(i*157%911)/911f*h;
            c.drawCircle(x,y,1+(i%3),p);
        }
        p.setColor(Color.argb(35,255,110,190));
        c.drawCircle(w*.1f,h*.25f,w*.22f,p);
        p.setColor(Color.argb(30,70,210,255));
        c.drawCircle(w*.9f,h*.72f,w*.25f,p);
    }

    private void drawHauntedScene(Canvas c,float w,float h){
        // Moon, distant castle and mist give the board a storybook Halloween atmosphere.
        p.setColor(Color.argb(35,170,105,255));
        c.drawCircle(w*.82f,h*.15f,w*.13f,p);
        p.setColor(Color.argb(95,255,241,176));
        c.drawCircle(w*.82f,h*.15f,w*.082f,p);
        p.setColor(Color.argb(120,8,8,31));
        Path castle=new Path();
        castle.moveTo(0,h*.21f);castle.lineTo(w*.08f,h*.15f);castle.lineTo(w*.12f,h*.21f);
        castle.lineTo(w*.18f,h*.12f);castle.lineTo(w*.24f,h*.21f);castle.lineTo(w*.31f,h*.17f);
        castle.lineTo(w*.38f,h*.21f);castle.close();c.drawPath(castle,p);
        p.setColor(Color.argb(18,170,225,255));
        for(int i=0;i<5;i++)c.drawOval(-w*.15f+i*w*.27f,h*(.72f+i*.025f),w*.35f+i*w*.27f,h*(.83f+i*.025f),p);
    }

    private void drawEffects(Canvas c,float w,float h){
        long now=System.currentTimeMillis();
        for(int i=sparks.size()-1;i>=0;i--){
            Spark s=sparks.get(i);s.life-=.035f;
            if(s.life<=0){sparks.remove(i);continue;}
            s.x+=s.vx;s.y+=s.vy;s.vy+=.12f;
            p.setColor((Math.max(0,Math.min(255,(int)(s.life*255)))<<24)|(s.color&0x00ffffff));
            p.setShadowLayer(10,0,0,s.color);
            c.drawCircle(s.x,s.y,s.size*(.55f+s.life),p);p.clearShadowLayer();
        }
        if(swipeFX>=0){
            p.setColor(Color.argb(90,255,255,255));
            c.drawCircle(swipeFX,swipeFY,cell*.18f,p);
            swipeFX=-1;
        }
    }

    private void burstAt(int r,int col,int color){
        float x=boardX+(col+.5f)*cell,y=boardY+(r+.5f)*cell;
        for(int i=0;i<14;i++){
            double a=Math.PI*2*i/14.0+rng.nextDouble()*.35;
            float speed=2.5f+rng.nextFloat()*6f;
            sparks.add(new Spark(x,y,(float)Math.cos(a)*speed,(float)Math.sin(a)*speed,
                .65f+rng.nextFloat()*.35f,3+rng.nextFloat()*6,color));
        }
    }

    private void stat(Canvas c,String title,String value,float x,float y,float w){
        p.setTextAlign(Paint.Align.CENTER);p.setTextSize(w*.025f);p.setColor(Color.rgb(188,167,235));
        c.drawText(title,x,y,p);
        p.setTextSize(w*.041f);p.setColor(Color.WHITE);
        c.drawText(value,x,y+w*.043f,p);
    }

    private void drawCell(Canvas c,int r,int col){
        float x=boardX+col*cell, y=boardY+r*cell, pad=cell*.075f;
        float bob=(float)Math.sin((System.currentTimeMillis()-gameStart)/420.0+r*.8+col*.65)*cell*.025f;
        int back=((r+col)&1)==0?Color.argb(75,118,79,173):Color.argb(55,78,52,132);
        drawRound(c,x+pad,y+pad,x+cell-pad,y+cell-pad,back,cell*.22f);
        boolean sel=r==selectedR&&col==selectedC;
        if(sel){
            stroke.setColor(Color.rgb(255,219,62));stroke.setStrokeWidth(cell*.055f);
            c.drawRoundRect(x+pad,y+pad,x+cell-pad,y+cell-pad,cell*.22f,cell*.22f,stroke);
        }
        if(board[r][col]<0)return;
        int value=board[r][col],kind=value/TYPES,type=value%TYPES;
        float cy=y+cell*.51f+bob;
        if(animationPhase==2){
            float t=Math.min(1f,(System.currentTimeMillis()-phaseStart)/340f);
            float eased=1f-(float)Math.pow(1f-t,3);
            cy+=fallFrom[r][col]*cell*(1f-eased);
        }
        if(animationPhase==1&&exploding.contains(r*N+col)){
            float t=Math.min(1f,(System.currentTimeMillis()-phaseStart)/240f);
            int save=c.save();c.scale(1f+.45f*t,1f+.45f*t,x+cell/2,cy);
            spritePaint.setAlpha(Math.max(0,(int)(255*(1f-t))));
            drawGhost(c,x+cell/2,cy,cell*.34f,colors[type],type,sel);
            c.restoreToCount(save);spritePaint.setAlpha(255);
        }else drawGhost(c,x+cell/2,cy,cell*.34f,colors[type],type,sel);
        if(kind>0){
            p.setShadowLayer(9,0,0,Color.rgb(255,213,93));
            p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create("sans",Typeface.BOLD));p.setTextSize(cell*.43f);
            c.drawText(kind==1?"↔":kind==2?"↕":"★",x+cell*.53f,cy+cell*.14f,p);
            p.clearShadowLayer();
        }
    }

    private void drawGhost(Canvas c,float cx,float cy,float rad,int color,int face,boolean selected){
        if(ghostSheet!=null&&!ghostSheet.isRecycled()){
            int type=Math.max(0,Math.min(2,face));
            float sheetCell=ghostSheet.getWidth()/3f;
            Rect source=new Rect((int)(type*sheetCell),0,(int)((type+1)*sheetCell),ghostSheet.getHeight());
            RectF dest=new RectF(cx-rad*1.24f,cy-rad*1.22f,cx+rad*1.24f,cy+rad*1.22f);
            c.drawBitmap(ghostSheet,source,dest,spritePaint);
            if(selected){
                stroke.setColor(Color.rgb(255,221,78));stroke.setStrokeWidth(rad*.10f);
                stroke.setShadowLayer(15,0,0,Color.rgb(255,232,122));
                c.drawRoundRect(dest,rad*.38f,rad*.38f,stroke);stroke.clearShadowLayer();
            }
            return;
        }
        // Fallback only if a device cannot decode the embedded graphic asset.
        // Layered glossy character: glow, shadow, soft 3D body, arms and expressive face.
        p.setShadowLayer(selected?22:12,0,rad*.12f,selected?Color.WHITE:color);
        p.setColor(Color.argb(90,0,0,0));
        c.drawOval(cx-rad*.78f,cy+rad*.65f,cx+rad*.78f,cy+rad*.94f,p);
        p.clearShadowLayer();

        Paint body=new Paint(Paint.ANTI_ALIAS_FLAG);
        body.setShader(new RadialGradient(cx-rad*.35f,cy-rad*.55f,rad*1.65f,
            new int[]{lighten(color,70),color,darken(color,45)},
            new float[]{0f,.58f,1f},Shader.TileMode.CLAMP));
        body.setShadowLayer(selected?24:10,0,0,selected?Color.rgb(255,225,90):color);

        Path g=new Path();
        g.moveTo(cx-rad*.82f,cy+rad*.63f);
        g.lineTo(cx-rad*.82f,cy-rad*.05f);
        g.cubicTo(cx-rad*.82f,cy-rad*.86f,cx-rad*.38f,cy-rad*1.05f,cx,cy-rad*1.05f);
        g.cubicTo(cx+rad*.50f,cy-rad*1.05f,cx+rad*.82f,cy-rad*.68f,cx+rad*.82f,cy-rad*.05f);
        g.lineTo(cx+rad*.82f,cy+rad*.63f);
        g.quadTo(cx+rad*.60f,cy+rad*.43f,cx+rad*.38f,cy+rad*.70f);
        g.quadTo(cx+rad*.15f,cy+rad*.43f,cx,cy+rad*.70f);
        g.quadTo(cx-rad*.18f,cy+rad*.43f,cx-rad*.40f,cy+rad*.70f);
        g.quadTo(cx-rad*.62f,cy+rad*.43f,cx-rad*.82f,cy+rad*.63f);
        g.close();
        c.drawPath(g,body); body.clearShadowLayer();

        // Raised little arms.
        p.setColor(lighten(color,20));
        c.drawOval(cx-rad*1.03f,cy-rad*.15f,cx-rad*.66f,cy+rad*.35f,p);
        c.drawOval(cx+rad*.66f,cy-rad*.15f,cx+rad*1.03f,cy+rad*.35f,p);

        // Gloss highlight.
        p.setColor(Color.argb(125,255,255,255));
        c.drawOval(cx-rad*.50f,cy-rad*.79f,cx-rad*.12f,cy-rad*.55f,p);

        // Eyes and personality.
        p.setColor(Color.rgb(28,15,38));
        if(face==2){
            stroke.setColor(Color.rgb(28,15,38));stroke.setStrokeWidth(rad*.11f);
            c.drawLine(cx-rad*.46f,cy-rad*.28f,cx-rad*.18f,cy-rad*.17f,stroke);
            c.drawLine(cx+rad*.46f,cy-rad*.28f,cx+rad*.18f,cy-rad*.17f,stroke);
        }
        c.drawOval(cx-rad*.45f,cy-rad*.28f,cx-rad*.17f,cy+rad*.10f,p);
        c.drawOval(cx+rad*.17f,cy-rad*.28f,cx+rad*.45f,cy+rad*.10f,p);
        p.setColor(Color.WHITE);
        c.drawCircle(cx-rad*.34f,cy-rad*.18f,rad*.055f,p);
        c.drawCircle(cx+rad*.28f,cy-rad*.18f,rad*.055f,p);

        p.setColor(Color.rgb(55,18,48));
        if(face==1){
            stroke.setColor(Color.rgb(55,18,48));stroke.setStrokeWidth(rad*.08f);
            c.drawArc(cx-rad*.20f,cy+rad*.12f,cx+rad*.20f,cy+rad*.38f,15,150,false,stroke);
        } else {
            c.drawOval(cx-rad*.23f,cy+rad*.10f,cx+rad*.23f,cy+rad*.43f,p);
            p.setColor(face==0?Color.rgb(255,92,137):Color.rgb(255,135,160));
            c.drawOval(cx-rad*.15f,cy+rad*.28f,cx+rad*.15f,cy+rad*.48f,p);
        }
        p.setColor(Color.argb(115,255,125,170));
        c.drawCircle(cx-rad*.57f,cy+rad*.12f,rad*.12f,p);
        c.drawCircle(cx+rad*.57f,cy+rad*.12f,rad*.12f,p);

        if(selected){
            stroke.setColor(Color.rgb(255,225,80));stroke.setStrokeWidth(rad*.08f);
            c.drawCircle(cx,cy-rad*.05f,rad*1.12f,stroke);
        }
    }

    private int lighten(int color,int amount){
        return Color.rgb(Math.min(255,Color.red(color)+amount),Math.min(255,Color.green(color)+amount),Math.min(255,Color.blue(color)+amount));
    }
    private int darken(int color,int amount){
        return Color.rgb(Math.max(0,Color.red(color)-amount),Math.max(0,Color.green(color)-amount),Math.max(0,Color.blue(color)-amount));
    }

    private void panel(Canvas c,float l,float t,float r,float b,int fill){
        p.setShadowLayer(13,0,7,Color.rgb(3,2,25));p.setColor(Color.rgb(128,75,184));
        c.drawRoundRect(l-3,t-3,r+3,b+3,18,18,p);p.clearShadowLayer();
        p.setShader(new LinearGradient(l,t,r,b,lighten(fill,12),darken(fill,25),Shader.TileMode.CLAMP));
        c.drawRoundRect(l,t,r,b,16,16,p);p.setShader(null);
    }

    private void drawBooster(Canvas c,int i,float x,float y,float w,float h,float screenW){
        boolean active=mode==i;
        panel(c,x,y,x+w,y+h*.76f,active?Color.rgb(243,191,76):Color.rgb(243,187,102));
        p.setTextAlign(Paint.Align.CENTER);
        if(boosterSheet!=null&&!boosterSheet.isRecycled()){
            float sheetCell=boosterSheet.getWidth()/7f;
            Rect source=new Rect((int)(i*sheetCell),0,(int)((i+1)*sheetCell),boosterSheet.getHeight());
            RectF dest=new RectF(x+w*.04f,y+h*.02f,x+w*.96f,y+h*.75f);
            c.drawBitmap(boosterSheet,source,dest,spritePaint);
        }else{
            p.setColor(Color.rgb(119,57,193));p.setTextSize(screenW*.060f);
            c.drawText(new String[]{"✋","H","★","◈","♧","★","+5"}[i],x+w/2,y+h*.54f,p);
        }
        p.setColor(Color.WHITE);p.setTextSize(screenW*.017f);
        c.drawText(boosterNames[i],x+w/2,y+h*.96f,p);
        p.setColor(Color.rgb(190,30,45));c.drawCircle(x+w*.86f,y+h*.08f,w*.22f,p);
        p.setColor(Color.WHITE);p.setTextSize(screenW*.025f);
        c.drawText(""+boosterCount[i],x+w*.86f,y+h*.13f,p);
    }

    private void drawOverlay(Canvas c,float w,float h){
        p.setColor(Color.argb(205,10,5,30));c.drawRect(0,0,w,h,p);
        float l=w*.10f,r=w*.90f,t=h*.30f,b=h*.68f;
        drawRound(c,l,t,r,b,Color.rgb(68,35,112),w*.06f);
        p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.WHITE);p.setTextSize(w*.078f);
        c.drawText(paused?"PAUSED":won?"LEVEL COMPLETE!":"SO CLOSE!",w/2,t+h*.09f,p);
        p.setTextSize(w*.12f);c.drawText(won?"★ ★ ★":"♥",w/2,t+h*.17f,p);
        p.setTextSize(w*.044f);p.setColor(Color.rgb(233,220,255));
        c.drawText(paused?"Tap continue to play":won?"Great ghost magic!":"Try this level again.",w/2,t+h*.23f,p);
        drawRound(c,w*.22f,t+h*.27f,w*.78f,t+h*.35f,Color.rgb(255,188,64),50);
        p.setColor(Color.rgb(55,25,70));p.setTextSize(w*.045f);
        c.drawText(paused?"CONTINUE":won?"NEXT LEVEL":"RETRY",w/2,t+h*.325f,p);
    }

    @Override public boolean onTouchEvent(android.view.MotionEvent e){
        float x=e.getX(),y=e.getY();
        if(e.getAction()==MotionEvent.ACTION_DOWN){
            touchDownX=x; touchDownY=y;
            if(!won&&!lost&&!paused&&y>=boardY&&y<boardY+N*cell&&x>=boardX&&x<boardX+N*cell){
                touchDownC=Math.min(N-1,(int)((x-boardX)/cell));
                touchDownR=Math.min(N-1,(int)((y-boardY)/cell));
                selectedR=touchDownR; selectedC=touchDownC; invalidate();
            } else {touchDownR=-1;touchDownC=-1;}
            return true;
        }
        if(e.getAction()!=MotionEvent.ACTION_UP)return true;
        if(animationPhase!=0)return true;
        if(won||lost||paused){
            if(y>getHeight()*.57f&&y<getHeight()*.68f){
                if(paused)paused=false;
                else {if(won)level++;newLevel();}
                invalidate();
            }
            return true;
        }
        if(x>getWidth()*.89f&&y<getHeight()*.09f){paused=true;invalidate();return true;}

        // Standard match-3 swipe: drag one ghost toward an adjacent cell.
        if(touchDownR>=0){
            float dx=x-touchDownX,dy=y-touchDownY;
            float threshold=cell*.24f;
            int tr=touchDownR,tc=touchDownC;
            if(Math.max(Math.abs(dx),Math.abs(dy))>=threshold){
                if(Math.abs(dx)>Math.abs(dy))tc+=dx>0?1:-1;
                else tr+=dy>0?1:-1;
                selectedR=-1;selectedC=-1;
                if(tr>=0&&tr<N&&tc>=0&&tc<N){
                    swipeFX=boardX+(tc+.5f)*cell;swipeFY=boardY+(tr+.5f)*cell;
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    if(mode==0){
                        if(boosterCount[0]>0){
                            swap(touchDownR,touchDownC,tr,tc);boosterCount[0]--;mode=-1;
                            resolveCascades();checkEnd();
                        }
                    }else if(mode>=1&&mode<=3||mode==5){cellTap(touchDownR,touchDownC);}
                    else attemptSwipe(touchDownR,touchDownC,tr,tc);
                }
                touchDownR=-1;touchDownC=-1;invalidate();return true;
            }
            int rr=touchDownR,cc=touchDownC;touchDownR=-1;touchDownC=-1;
            cellTap(rr,cc);return true;
        }

        float margin=getWidth()*.055f,gap=getWidth()*.008f,bw=(getWidth()-2*margin-getWidth()*.035f-gap*6)/7f;
        float by=boosterY+getHeight()*.014f;
        if(y>=by&&y<=by+getHeight()*.10f){
            int i=(int)((x-margin)/(bw+gap));
            if(i>=0&&i<7)boosterTap(i);
        }
        return true;
    }

    private void attemptSwipe(int r1,int c1,int r2,int c2){
        int a=board[r1][c1],b=board[r2][c2];
        swap(r1,c1,r2,c2);
        if(a>=TYPES||b>=TYPES){
            moves--;cascadeDepth=0;
            Set<Integer> hits=new HashSet<>();
            if(a>=TYPES)expandPower(r2,c2,a,hits);
            if(b>=TYPES)expandPower(r1,c1,b,hits);
            beginExplosion(hits,false,-1,-1);
        }else if(hasAnyMatch()){
            moves--;cascadeDepth=0;
            beginExplosion(findMatches(),true,r2,c2);
        } else {
            swap(r1,c1,r2,c2);
            message("That move makes no match — try another!");
        }
    }

    private void cellTap(int r,int c){
        if(mode>=1&&mode<=3){
            Set<Integer> hit=new HashSet<>();
            if(mode==1)hit.add(r*N+c);
            else if(mode==2)for(int j=0;j<N;j++)hit.add(r*N+j);
            else for(int i=0;i<N;i++)hit.add(i*N+c);
            useBooster(mode,"Magic power!");score+=350;cascadeDepth=0;
            beginExplosion(hit,false,-1,-1);invalidate();return;
        }
        if(mode==5){
            int type=board[r][c],chosen=(type+1)%TYPES;
            if(c>=2&&board[r][c-1]==board[r][c-2])chosen=board[r][c-1];
            else if(c<=N-3&&board[r][c+1]==board[r][c+2])chosen=board[r][c+1];
            else if(r>=2&&board[r-1][c]==board[r-2][c])chosen=board[r-1][c];
            else if(r<=N-3&&board[r+1][c]==board[r+2][c])chosen=board[r+1][c];
            board[r][c]=chosen;useBooster(5,"Magic wand!");
            cascadeDepth=0;resolveCascades();invalidate();return;
        }
        if(selectedR<0){selectedR=r;selectedC=c;invalidate();return;}
        if(selectedR==r&&selectedC==c){selectedR=-1;selectedC=-1;invalidate();return;}
        if(Math.abs(selectedR-r)+Math.abs(selectedC-c)==1){
            int sr=selectedR,sc=selectedC;selectedR=-1;selectedC=-1;
            if(mode==0){swap(sr,sc,r,c);useBooster(0,"Free swap!");resolveCascades();checkEnd();}
            else attemptSwipe(sr,sc,r,c);
        }else{selectedR=r;selectedC=c;}
        invalidate();
    }

    private void boosterTap(int i){
        if(boosterCount[i]<=0){message("Earn more boosters by passing levels!");return;}
        if(i==4){
            int[] move=findPossibleMove();
            if(move!=null){boosterCount[i]--;attemptSwipe(move[0],move[1],move[2],move[3]);
                message("Helpful ghost found a match!");}
        }else if(i==6){boosterCount[i]--;moves+=5;message("+5 moves added!");}
        else{mode=mode==i?-1:i;selectedR=-1;selectedC=-1;
            message(mode<0?"Booster cancelled":i==0?"Swipe any two neighbors":"Tap a ghost for "+boosterNames[i]);}
        invalidate();
    }

    private int[] findPossibleMove(){
        for(int r=0;r<N;r++)for(int c=0;c<N;c++){
            if(c+1<N){swap(r,c,r,c+1);boolean ok=hasAnyMatch();swap(r,c,r,c+1);if(ok)return new int[]{r,c,r,c+1};}
            if(r+1<N){swap(r,c,r+1,c);boolean ok=hasAnyMatch();swap(r,c,r+1,c);if(ok)return new int[]{r,c,r+1,c};}
        }return null;
    }

    private void useBooster(int i,String msg){boosterCount[i]--;mode=-1;message(msg);}
    private void clearAt(int r,int c){board[r][c]=-1;}
    private void swap(int r1,int c1,int r2,int c2){int t=board[r1][c1];board[r1][c1]=board[r2][c2];board[r2][c2]=t;}

    private boolean hasAnyMatch(){return !findMatches().isEmpty();}

    private Set<Integer> findMatches(){
        Set<Integer> out=new HashSet<>();
        for(int r=0;r<N;r++){
            int run=1;
            for(int c=1;c<=N;c++){
                if(c<N&&board[r][c]>=0&&base(board[r][c])==base(board[r][c-1])) run++;
                else {if(run>=3)for(int k=c-run;k<c;k++)out.add(r*N+k);run=1;}
            }
        }
        for(int c=0;c<N;c++){
            int run=1;
            for(int r=1;r<=N;r++){
                if(r<N&&board[r][c]>=0&&base(board[r][c])==base(board[r-1][c])) run++;
                else {if(run>=3)for(int k=r-run;k<r;k++)out.add(k*N+c);run=1;}
            }
        }
        return out;
    }

    private int base(int value){return value<0?-1:value%TYPES;}

    private void expandPower(int row,int col,int value,Set<Integer> hits){
        if(value<0)return;
        int kind=value/TYPES;
        if(kind==1)for(int j=0;j<N;j++)hits.add(row*N+j);
        else if(kind==2)for(int i=0;i<N;i++)hits.add(i*N+col);
        else if(kind==3){
            int type=value%TYPES;
            for(int i=0;i<N;i++)for(int j=0;j<N;j++)
                if(base(board[i][j])==type)hits.add(i*N+j);
        }
        hits.add(row*N+col);
    }

    private int[] powerReward(int preferredR,int preferredC){
        int best=0,kind=0,rr=-1,cc=-1;
        for(int r=0;r<N;r++){
            int run=1;
            for(int col=1;col<=N;col++){
                if(col<N&&base(board[r][col])>=0&&base(board[r][col])==base(board[r][col-1]))run++;
                else{
                    if(run>=4&&run>best){
                        cc=(r==preferredR&&preferredC>=col-run&&preferredC<col)?preferredC:col-run;
                        rr=r;kind=run>=5?3:1;best=run;
                    }
                    run=1;
                }
            }
        }
        for(int col=0;col<N;col++){
            int run=1;
            for(int r=1;r<=N;r++){
                if(r<N&&base(board[r][col])>=0&&base(board[r][col])==base(board[r-1][col]))run++;
                else{
                    if(run>=4&&run>best){
                        rr=(col==preferredC&&preferredR>=r-run&&preferredR<r)?preferredR:r-run;
                        cc=col;kind=run>=5?3:2;best=run;
                    }
                    run=1;
                }
            }
        }
        return kind==0?null:new int[]{rr,cc,kind};
    }

    private void resolveCascades(){
        if(animationPhase!=0)return;
        Set<Integer> matches=findMatches();
        if(!matches.isEmpty())beginExplosion(matches,false,-1,-1);
        else {ensureMove();checkEnd();}
    }

    private void beginExplosion(Set<Integer> hits,boolean player,int preferredR,int preferredC){
        if(hits.isEmpty()){ensureMove();checkEnd();return;}
        if(cascadeDepth++>=12){cascadeDepth=0;ensureMove();checkEnd();return;}
        int[] reward=player?powerReward(preferredR,preferredC):null;
        Set<Integer> expanded=new HashSet<>(hits),processed=new HashSet<>();
        boolean changed;
        do{
            changed=false;
            for(int pos:new HashSet<>(expanded)){
                if(!processed.add(pos))continue;
                int r=pos/N,c=pos%N;
                if(board[r][c]>=TYPES){
                    int size=expanded.size();
                    expandPower(r,c,board[r][c],expanded);
                    if(expanded.size()>size)changed=true;
                }
            }
        }while(changed);
        if(reward!=null){
            int pos=reward[0]*N+reward[1];
            if(expanded.remove(pos)){
                board[reward[0]][reward[1]]=base(board[reward[0]][reward[1]])+TYPES*reward[2];
                message(reward[2]==3?"Rainbow ghost unlocked!":reward[2]==1?"Row blast unlocked!":"Column blast unlocked!");
            }
        }
        if(expanded.isEmpty()){ensureMove();checkEnd();return;}
        exploding.clear();exploding.addAll(expanded);animationPhase=1;phaseStart=System.currentTimeMillis();
        combo=Math.min(12,cascadeDepth);score+=expanded.size()*90*combo;
        for(int pos:expanded){
            int r=pos/N,c=pos%N,v=board[r][c];
            if(v>=0){collected[base(v)]++;burstAt(r,c,colors[base(v)]);}
        }
        if(combo>=2){
            comboText="COMBO x"+combo+"!";comboUntil=System.currentTimeMillis()+1200;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
        final int serial=animationSerial;
        postDelayed(()->{
            if(serial!=animationSerial)return;
            for(int pos:exploding)board[pos/N][pos%N]=-1;
            exploding.clear();
            collapseAnimated();
            animationPhase=2;phaseStart=System.currentTimeMillis();invalidate();
            postDelayed(()->{
                if(serial!=animationSerial)return;
                animationPhase=0;invalidate();
                Set<Integer> next=findMatches();
                if(!next.isEmpty())beginExplosion(next,false,-1,-1);
                else{cascadeDepth=0;ensureMove();checkEnd();}
            },360);
        },240);
        invalidate();
    }

    private void collapseAnimated(){
        for(int c=0;c<N;c++){
            int write=N-1,spawn=-1;
            for(int r=N-1;r>=0;r--)if(board[r][c]>=0){
                board[write][c]=board[r][c];fallFrom[write][c]=r-write;write--;
            }
            while(write>=0){
                board[write][c]=rng.nextInt(TYPES);
                fallFrom[write][c]=spawn-write;
                write--;spawn--;
            }
        }
    }

    private void collapse(){
        for(int c=0;c<N;c++){
            int write=N-1;
            for(int r=N-1;r>=0;r--)if(board[r][c]>=0)board[write--][c]=board[r][c];
            while(write>=0)board[write--][c]=rng.nextInt(TYPES);
        }
    }

    private boolean possibleMove(){
        for(int r=0;r<N;r++)for(int c=0;c<N;c++){
            if(c+1<N){swap(r,c,r,c+1);boolean ok=hasAnyMatch();swap(r,c,r,c+1);if(ok)return true;}
            if(r+1<N){swap(r,c,r+1,c);boolean ok=hasAnyMatch();swap(r,c,r+1,c);if(ok)return true;}
        }return false;
    }

    private void ensureMove(){if(!possibleMove())shuffle();}
    private void shuffle(){
        ArrayList<Integer> list=new ArrayList<>();
        for(int[] row:board)for(int v:row)list.add(v<0?rng.nextInt(TYPES):v);
        do{
            Collections.shuffle(list,rng);int k=0;
            for(int r=0;r<N;r++)for(int c=0;c<N;c++)board[r][c]=list.get(k++);
        }while((hasAnyMatch()||!possibleMove()));
    }

    private void showHint(){
        for(int r=0;r<N;r++)for(int c=0;c<N;c++){
            if(c+1<N){swap(r,c,r,c+1);boolean ok=hasAnyMatch();swap(r,c,r,c+1);if(ok){selectedR=r;selectedC=c;message("Hint: select the glowing ghost");invalidate();return;}}
            if(r+1<N){swap(r,c,r+1,c);boolean ok=hasAnyMatch();swap(r,c,r+1,c);if(ok){selectedR=r;selectedC=c;message("Hint: select the glowing ghost");invalidate();return;}}
        }
    }

    private void checkEnd(){
        if(!won&&collected[0]>=goals[0]&&collected[1]>=goals[1]&&collected[2]>=goals[2]){
            won=true;boosterCount[rng.nextInt(7)]++;
            for(int i=0;i<100;i++)sparks.add(new Spark(rng.nextFloat()*getWidth(),getHeight()*.25f,
                (rng.nextFloat()-.5f)*5f,rng.nextFloat()*-5f,.7f+rng.nextFloat(),4+rng.nextFloat()*7f,colors[i%TYPES]));
            performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            message("Level complete! Free booster earned.");
        }
        else if(moves<=0)lost=true;
    }

    private void message(String s){toast=s;toastUntil=System.currentTimeMillis()+2300;}
    private void drawRound(Canvas c,float l,float t,float r,float b,int color,float rad){
        p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawRoundRect(l,t,r,b,rad,rad,p);
    }
}
