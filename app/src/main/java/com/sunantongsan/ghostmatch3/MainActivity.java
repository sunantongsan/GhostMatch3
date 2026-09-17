package com.sunantongsan.ghostmatch3;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.content.*;
import java.util.*;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

public class MainActivity extends Activity {
    // Google's official SAMPLE identifiers: test impressions never earn money.
    private static final String TEST_INTERSTITIAL_ID="ca-app-pub-3940256099942544/1033173712";
    private static final long MIN_AD_INTERVAL_MS=90000L;
    private GhostGameView gameView;
    private ConsentInformation consentInformation;
    private InterstitialAd interstitial;
    private boolean adsInitialized=false, adLoading=false, privacyOptionsRequired=false;
    private long lastAdShownAt=0;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(18,10,46));
        getWindow().setNavigationBarColor(Color.rgb(18,10,46));
        gameView=new GhostGameView(this);
        setContentView(gameView);
        requestAdConsent();
    }

    private void requestAdConsent(){
        consentInformation=UserMessagingPlatform.getConsentInformation(this);
        consentInformation.requestConsentInfoUpdate(this,
            new ConsentRequestParameters.Builder().build(),
            ()->{
                refreshPrivacyOption();
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this,error->{
                    refreshPrivacyOption();
                    if(consentInformation.canRequestAds())initializeAds();
                });
                if(consentInformation.canRequestAds())initializeAds();
            },
            error->{
                refreshPrivacyOption();
                // A previous valid consent may still permit ads; otherwise stay ad-free.
                if(consentInformation.canRequestAds())initializeAds();
            });
    }

    private void refreshPrivacyOption(){
        privacyOptionsRequired=consentInformation.getPrivacyOptionsRequirementStatus()
            ==ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
        if(gameView!=null)gameView.invalidate();
    }

    boolean needsPrivacyOptions(){return privacyOptionsRequired;}

    void openPrivacyOptions(){
        if(!privacyOptionsRequired)return;
        UserMessagingPlatform.showPrivacyOptionsForm(this,error->{
            refreshPrivacyOption();
            if(consentInformation.canRequestAds())initializeAds();
            else interstitial=null;
        });
    }

    private void initializeAds(){
        if(adsInitialized)return;
        adsInitialized=true;
        MobileAds.initialize(this,status->runOnUiThread(this::loadNextAd));
    }

    private void loadNextAd(){
        if(!adsInitialized||!consentInformation.canRequestAds()||adLoading||interstitial!=null)return;
        adLoading=true;
        InterstitialAd.load(this,TEST_INTERSTITIAL_ID,new AdRequest.Builder().build(),
            new InterstitialAdLoadCallback(){
                @Override public void onAdLoaded(InterstitialAd ad){
                    adLoading=false;interstitial=ad;
                }
                @Override public void onAdFailedToLoad(LoadAdError error){
                    adLoading=false;interstitial=null;
                }
            });
    }

    void onLevelCompleted(int completedLevel){
        // Let the victory animation play first. Never block the NEXT LEVEL button.
        if(completedLevel<4||(completedLevel-4)%3!=0)return;
        gameView.postDelayed(()->{
            if(!gameView.isShowingVictory(completedLevel)||interstitial==null
               ||!consentInformation.canRequestAds())return;
            long now=android.os.SystemClock.elapsedRealtime();
            if(lastAdShownAt!=0&&now-lastAdShownAt<MIN_AD_INTERVAL_MS)return;
            InterstitialAd ad=interstitial;interstitial=null;
            ad.setFullScreenContentCallback(new FullScreenContentCallback(){
                @Override public void onAdDismissedFullScreenContent(){loadNextAd();}
                @Override public void onAdFailedToShowFullScreenContent(AdError error){loadNextAd();}
            });
            lastAdShownAt=now;
            ad.show(this);
        },3200);
    }
}

class GhostGameView extends View {
    private static final int N=7, TYPES=3;
    private final int[][] board=new int[N][N];
    private final int[][] ice=new int[N][N];
    private final boolean[][] blocked=new boolean[N][N];
    private static final int WALL=-99;
    private int iceLeft=0,iceInitial=0,highestLevel=1;
    private final android.content.SharedPreferences progress;
    private final float[][] fallFrom=new float[N][N];
    private final Set<Integer> exploding=new HashSet<>();
    private final ArrayList<int[]> castPoints=new ArrayList<>();
    private int powerMultiplier=1, rainbowTarget=-1, activeMultiplier=1;
    private int animationPhase=0,animationSerial=0,cascadeDepth=0;
    private long phaseStart=0;
    private int specialR=-1,specialC=-1;
    private int swapR1=-1,swapC1=-1,swapR2=-1,swapC2=-1;
    private final Random rng=new Random();
    private final Paint p=new Paint(3);
    private final Paint stroke=new Paint(3);
    private final Paint spritePaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private Bitmap ghostSheet,ghostReactions,boosterSheet,hauntedBackground,magicItems,dancingSkeleton;
    private final int[] colors={Color.rgb(245,245,255),Color.rgb(188,236,172),Color.rgb(161,77,227)};
    private final String[] boosterNames={"SWAP","HAMMER","ROW","COLUMN","BURST","RAINBOW","+5"};
    private final int[] boosterCount={8,8,6,6,6,8,8};
    private final int[] collected=new int[TYPES];
    private final int[] goals={10,10,10};
    private int helperFirstR=-1,helperFirstC=-1;
    private boolean paused=false,missionBrief=false;
    private long missionBriefStart=0;
    private int level=1, moves=28, score=0, target=1800, selectedR=-1, selectedC=-1;
    private int mode=-1, combo=0;
    private int tutorialStage=-1;
    private int[] tutorialMove;
    private float boardX,boardY,cell,boosterY;
    private float touchDownX, touchDownY;
    private int touchDownR=-1, touchDownC=-1;
    private boolean won=false,lost=false;
    private long gameStart=System.currentTimeMillis(),victoryStart=0;
    private final ArrayList<Spark> sparks=new ArrayList<>();
    private String comboText="";
    private long comboUntil=0;
    private float swipeFX=-1, swipeFY=-1;
    private int reactionR=-1,reactionC=-1;
    private long reactionStart=0,reactionUntil=0;
    private static final long REACTION_MS=1250L;
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
        ghostReactions=BitmapFactory.decodeResource(getResources(),R.drawable.ghost_reactions);
        boosterSheet=BitmapFactory.decodeResource(getResources(),R.drawable.booster_sprites);
        magicItems=BitmapFactory.decodeResource(getResources(),R.drawable.magic_items);
        dancingSkeleton=BitmapFactory.decodeResource(getResources(),R.drawable.anatomical_skeleton_dance);
        hauntedBackground=BitmapFactory.decodeResource(getResources(),R.drawable.haunted_background);
        progress=c.getSharedPreferences("ghostmatch_progress",Context.MODE_PRIVATE);
        highestLevel=Math.max(1,progress.getInt("highest_level",1));
        level=highestLevel;
        tutorialStage=level==1&&!progress.getBoolean("tutorial_complete",false)?0:-1;
        newLevel();
    }

    private void newLevel(){
        animationSerial++;animationPhase=0;exploding.clear();castPoints.clear();powerMultiplier=1;
        for(float[] row:fallFrom)Arrays.fill(row,0);
        configureLayout();
        if(level==1){moves=8;target=700;}
        else if(level==2){moves=10;target=950;}
        else if(level==3){moves=12;target=1250;}
        else if(level<=5){moves=14;target=1500+level*120;}
        else moves=Math.min(31,15+level/3);
        for(int i=0;i<TYPES;i++){
            goals[i]=level<=3?2+level:level<=7?5+level/2:Math.min(24,7+level/2);
            if(level>=12&&i==(level-1)%TYPES)goals[i]+=Math.min(5,level/8);
            collected[i]=0;
        }
        for(int[] row:ice)Arrays.fill(row,0);
        iceLeft=0;
        int iceCount=level<4?0:Math.min(22,2+(level-4)/2);
        int strength=level>=18?2:1;
        ArrayList<Integer> open=new ArrayList<>();
        for(int r=0;r<N;r++)for(int c=0;c<N;c++)if(!blocked[r][c])open.add(r*N+c);
        Collections.shuffle(open,rng);
        for(int k=0;k<Math.min(iceCount,open.size());k++){
            int pos=open.get(k),rr=pos/N,cc=pos%N;
            ice[rr][cc]=strength;iceLeft+=strength;
        }
        iceInitial=iceLeft;
        score=0;combo=0;won=false;lost=false;paused=false;mode=-1;helperFirstR=-1;
        for(int r=0;r<N;r++)for(int c=0;c<N;c++){
            if(blocked[r][c]){board[r][c]=WALL;continue;}
            int t,guard=0;
            do{
                t=rng.nextInt(TYPES);guard++;
            }while(guard<20&&((c>=2&&!blocked[r][c-1]&&!blocked[r][c-2]&&board[r][c-1]==t&&board[r][c-2]==t)
                ||(r>=2&&!blocked[r-1][c]&&!blocked[r-2][c]&&board[r-1][c]==t&&board[r-2][c]==t)));
            board[r][c]=t;
        }
        ensureMove();
        if(tutorialStage==1)tutorialMove=findPossibleMove();
        String shape=level<=2?"Small garden":level<=5?"Training hall":level<=9?"Haunted manor":
            level<=14?"Broken corners":level<=19?"Moon cross":level<=24?"Split crypt":"Cursed hourglass";
        message(level<=3?"Easy start — "+shape:"Level "+level+" — "+shape);
        missionBrief=level>1;missionBriefStart=System.currentTimeMillis();
        invalidate();
    }

    private void configureLayout(){
        for(boolean[] row:blocked)Arrays.fill(row,false);
        for(int r=0;r<N;r++)for(int c=0;c<N;c++){
            boolean wall=false;
            if(level<=2)wall=r==0||r==N-1||c==0||c==N-1;
            else if(level<=5)wall=r==0||c==N-1;
            else if(level<=9)wall=false;
            else if(level<=14)wall=(r==0||r==N-1)&&(c==0||c==N-1);
            else if(level<=19)wall=(r<2||r>N-3)&&(c<2||c>N-3);
            else if(level<=24)wall=c==N/2&&r>=2&&r<=4;
            else wall=((r==0||r==N-1)&&(c<2||c>N-3))||(Math.abs(r-N/2)<=1&&(c==0||c==N-1));
            blocked[r][c]=wall;
        }
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
        if(iceInitial>0){
            p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.rgb(178,234,255));p.setTextSize(w*.029f);
            c.drawText("❄ น้ำแข็ง "+iceLeft+"/"+iceInitial,w*.46f,h*.214f,p);
        }

        boardX=w*.025f; boardY=h*.208f; cell=(w-2*boardX)/N;
        panel(c,boardX-w*.017f,boardY-w*.017f,w-boardX+w*.017f,boardY+cell*N+w*.017f,Color.rgb(37,39,83));
        int boardClip=c.save();
        c.clipRect(boardX,boardY,boardX+N*cell,boardY+N*cell);
        for(int r=0;r<N;r++)for(int col=0;col<N;col++)drawCell(c,r,col);
        c.restoreToCount(boardClip);
        drawEffects(c,w,h);
        if(tutorialStage==1&&tutorialMove!=null)drawTutorialCue(c,w,h);

        boosterY=boardY+cell*N+h*.019f;
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
            float lift=(comboUntil-System.currentTimeMillis())/1800f;
            p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans",Typeface.BOLD));
            p.setTextSize(w*.078f);p.setColor(Color.WHITE);
            p.setShadowLayer(18,0,0,Color.rgb(255,87,203));
            c.drawText(comboText,w/2,boardY+cell*N*.48f-lift*w*.08f,p);p.clearShadowLayer();
        }
        if(won||lost||paused) drawOverlay(c,w,h);
        if(missionBrief&&tutorialStage<0&&!won&&!lost&&!paused)drawMissionBrief(c,w,h);
        if(tutorialStage==0||tutorialStage==2&&animationPhase==0)drawTutorialPage(c,w,h);
        postInvalidateOnAnimation();
    }

    private void drawMissionBrief(Canvas c,float w,float h){
        long elapsed=System.currentTimeMillis()-missionBriefStart;
        float pulse=.96f+.04f*(float)Math.sin(elapsed/180f);
        p.setColor(Color.argb(225,10,5,35));c.drawRect(0,0,w,h,p);
        float top=h*.22f;
        panel(c,w*.06f,top,w*.94f,h*.76f,Color.rgb(65,34,116));
        p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans",Typeface.BOLD));
        p.setColor(Color.rgb(255,216,79));p.setTextSize(w*.042f);
        c.drawText("ภารกิจด่าน "+level,w/2,top+h*.058f,p);
        p.setColor(Color.WHITE);p.setTextSize(w*.056f);
        c.drawText("ทำให้ครบเพื่อผ่านด่าน",w/2,top+h*.118f,p);
        for(int i=0;i<TYPES;i++){
            float gx=w*(.27f+.23f*i),gy=top+h*.215f;
            int save=c.save();c.scale(pulse,pulse,gx,gy);
            drawGhost(c,gx,gy,w*.063f,colors[i],i,false);c.restoreToCount(save);
            p.setColor(Color.WHITE);p.setTextSize(w*.037f);
            c.drawText("เก็บ "+goals[i]+" ตัว",gx,gy+h*.075f,p);
        }
        drawRound(c,w*.15f,top+h*.32f,w*.85f,top+h*.385f,Color.rgb(43,31,91),w*.025f);
        p.setColor(Color.rgb(255,209,84));p.setTextSize(w*.041f);
        c.drawText("ย้ายได้ "+moves+" ครั้ง",w/2,top+h*.363f,p);
        p.setColor(iceInitial>0?Color.rgb(177,235,255):Color.rgb(205,192,235));p.setTextSize(w*.031f);
        String obstacle=iceInitial>0?"ทำลายน้ำแข็ง "+iceInitial+" ชั้นด้วย":"ด่านนี้ยังไม่มีน้ำแข็ง";
        c.drawText(obstacle,w/2,top+h*.428f,p);
        drawRound(c,w*.20f,top+h*.47f,w*.80f,top+h*.54f,Color.rgb(83,199,48),w*.05f);
        p.setColor(Color.WHITE);p.setTextSize(w*.047f);
        c.drawText("เริ่มด่าน",w/2,top+h*.518f,p);
    }

    private void finishTutorial(){
        tutorialStage=-1;tutorialMove=null;selectedR=-1;selectedC=-1;
        progress.edit().putBoolean("tutorial_complete",true).apply();
        message("You're ready! Match ghosts and use magic items.");
    }

    private void drawTutorialCue(Canvas c,float w,float h){
        int r1=tutorialMove[0],c1=tutorialMove[1],r2=tutorialMove[2],c2=tutorialMove[3];
        float x1=boardX+(c1+.5f)*cell,y1=boardY+(r1+.5f)*cell;
        float x2=boardX+(c2+.5f)*cell,y2=boardY+(r2+.5f)*cell;
        float wave=(float)Math.sin((System.currentTimeMillis()-gameStart)/180f);
        stroke.setColor(Color.rgb(255,222,81));stroke.setStrokeWidth(cell*.07f);
        stroke.setShadowLayer(18,0,0,Color.rgb(255,210,64));
        c.drawCircle(x1,y1,cell*(.43f+.04f*wave),stroke);
        c.drawCircle(x2,y2,cell*(.43f+.04f*wave),stroke);
        c.drawLine(x1,y1,x2,y2,stroke);stroke.clearShadowLayer();
        float top=h*.795f;
        drawRound(c,w*.045f,top,w*.955f,top+h*.065f,Color.argb(240,60,33,110),w*.03f);
        p.setColor(Color.WHITE);p.setTypeface(Typeface.create("sans",Typeface.BOLD));
        p.setTextAlign(Paint.Align.LEFT);p.setTextSize(w*.039f);
        c.drawText("ลองเลื่อนผีคู่ที่เรืองแสง",w*.075f,top+h*.042f,p);
        p.setTextAlign(Paint.Align.RIGHT);p.setTextSize(w*.031f);
        c.drawText("ข้าม",w*.91f,top+h*.041f,p);
    }

    private void drawTutorialPage(Canvas c,float w,float h){
        p.setColor(Color.argb(225,12,7,39));c.drawRect(0,0,w,h,p);
        if(tutorialStage==0)drawTutorialVideo(c,w,h);
        float t=tutorialStage==0?h*.325f:h*.285f;
        panel(c,w*.075f,t,w*.925f,h*.71f,Color.rgb(72,39,125));
        drawGhost(c,w/2,t+h*.095f,w*.11f,colors[0],0,false);
        p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.WHITE);
        p.setTypeface(Typeface.create("sans",Typeface.BOLD));p.setTextSize(w*.061f);
        c.drawText(tutorialStage==0?"ยินดีต้อนรับ!":"เก่งมาก!",w/2,t+h*.19f,p);
        p.setTextSize(w*.035f);p.setColor(Color.rgb(240,230,255));
        if(tutorialStage==0){
            c.drawText("เลื่อนผีให้เรียงกัน 3 ตัวขึ้นไป",w/2,t+h*.245f,p);
            c.drawText("เก็บผีตามเป้าหมายเพื่อผ่านด่าน",w/2,t+h*.282f,p);
            c.drawText("เริ่มด้วยผีคู่ที่เรืองแสงบนกระดาน",w/2,t+h*.319f,p);
        }else{
            c.drawText("ผีที่จับคู่จะหาย แล้วตัวใหม่ตกลงมา",w/2,t+h*.245f,p);
            c.drawText("จับ 4 หรือ 5 ตัว จะได้ไอเท็มเวทมนตร์",w/2,t+h*.282f,p);
            c.drawText("แตะตัวช่วยด้านล่างเมื่ออยากให้ช่วย",w/2,t+h*.319f,p);
        }
        drawRound(c,w*.20f,h*.555f,w*.80f,h*.615f,Color.rgb(255,191,78),w*.035f);
        p.setColor(Color.rgb(65,29,72));p.setTextSize(w*.043f);
        c.drawText(tutorialStage==0?"เริ่มเรียนรู้":"เข้าใจแล้ว",w/2,h*.596f,p);
        if(tutorialStage==0){
            p.setColor(Color.rgb(215,200,237));p.setTextSize(w*.032f);
            c.drawText("ข้ามคำแนะนำ",w/2,h*.675f,p);
        }
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

    private void drawTutorialVideo(Canvas c,float w,float h){
        long loop=(System.currentTimeMillis()-gameStart)%3600L;
        float top=h*.155f,left=w*.16f,size=w*.17f;
        drawRound(c,left-w*.025f,top-w*.025f,left+size*4+w*.025f,top+size+w*.025f,
            Color.argb(230,32,28,78),w*.035f);
        int moving=loop<1900?1:2;
        for(int i=0;i<4;i++){
            float cx=left+(i+.5f)*size,cy=top+size*.5f;
            drawRound(c,left+i*size+size*.05f,top+size*.05f,left+(i+1)*size-size*.05f,
                top+size-size*.05f,Color.argb(90,121,86,181),size*.20f);
            int type=i==0||i>=2?0:1;
            if(loop>2300&&type==0){
                float burst=Math.min(1f,(loop-2300)/650f);
                int save=c.save();c.scale(1f+.38f*burst,1f+.38f*burst,cx,cy);
                spritePaint.setAlpha((int)(255*(1f-burst)));
                drawGhost(c,cx,cy,size*.30f,colors[type],type,false);
                spritePaint.setAlpha(255);c.restoreToCount(save);
            }else drawGhost(c,cx,cy,size*.30f,colors[type],type,false);
        }
        if(loop<2300){
            float t=Math.min(1f,loop/1500f);
            float fx=left+size*(1.5f+t),fy=top+size*.62f;
            p.setColor(Color.argb(235,255,226,177));p.setShadowLayer(12,0,0,Color.WHITE);
            c.drawCircle(fx,fy,size*.13f,p);p.clearShadowLayer();
            stroke.setColor(Color.rgb(255,221,74));stroke.setStrokeWidth(size*.045f);
            c.drawLine(left+size*1.5f,top+size*.78f,fx,fy,stroke);
        }else{
            p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.rgb(255,224,83));
            p.setTextSize(w*.047f);c.drawText("จับคู่ 3!",w/2,top+size*.68f,p);
        }
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
        if(animationPhase==1&&!castPoints.isEmpty())drawSpellEffects(c);
        if(System.currentTimeMillis()<reactionUntil)postInvalidateOnAnimation();
        if(swipeFX>=0){
            p.setColor(Color.argb(90,255,255,255));
            c.drawCircle(swipeFX,swipeFY,cell*.18f,p);
            swipeFX=-1;
        }
    }

    private void drawSpellEffects(Canvas c){
        float progress=Math.min(1f,(System.currentTimeMillis()-phaseStart)/1100f);
        float pulse=(float)Math.sin(progress*Math.PI);
        int saved=c.save();
        c.clipRect(boardX,boardY,boardX+N*cell,boardY+N*cell);
        Paint fx=new Paint(Paint.ANTI_ALIAS_FLAG);
        fx.setStyle(Paint.Style.STROKE);fx.setStrokeCap(Paint.Cap.ROUND);
        for(int[] power:castPoints){
            int row=power[0],col=power[1],kind=power[2],boost=power[3];
            float x=boardX+(col+.5f)*cell,y=boardY+(row+.5f)*cell;
            int color=kind==1?Color.rgb(65,201,255):kind==2?Color.rgb(250,103,255):
                      kind==3?Color.rgb(255,213,96):Color.rgb(208,128,255);
            fx.setColor((Math.max(0,(int)(185*pulse))<<24)|(color&0xffffff));
            fx.setStrokeWidth(cell*(boost>1?.28f:.17f)*pulse);
            fx.setShadowLayer(24,0,0,color);
            if(kind==1)c.drawLine(boardX,y,boardX+N*cell,y,fx);
            else if(kind==2)c.drawLine(x,boardY,x,boardY+N*cell,fx);
            else if(kind==3){
                for(int i=0;i<10;i++){
                    double angle=(Math.PI*2*i/10)+progress*4;
                    c.drawLine(x,y,x+(float)Math.cos(angle)*cell*4,
                              y+(float)Math.sin(angle)*cell*4,fx);
                }
            }else{
                fx.setStrokeWidth(cell*.11f);
                c.drawCircle(x,y,cell*(.35f+progress*(boost>1?3.6f:1.9f)),fx);
            }
            fx.setShadowLayer(28,0,0,color);
            fx.setStrokeWidth(cell*.08f);
            c.drawCircle(x,y,cell*(.40f+progress*(boost>1?2.2f:1.25f)),fx);
            fx.clearShadowLayer();
            // Each spell has its own sigil and moving glints, not just a tinted blast.
            Paint rune=new Paint(Paint.ANTI_ALIAS_FLAG);
            rune.setStyle(Paint.Style.STROKE);
            rune.setStrokeWidth(cell*.035f);
            rune.setColor(Color.argb((int)(220*pulse),255,255,245));
            float radius=cell*(.40f+progress*(boost>1?2.2f:1.25f));
            int glyphs=kind==3?12:kind==4?8:6;
            for(int g=0;g<glyphs;g++){
                double angle=2*Math.PI*g/glyphs+progress*(kind==2?-2.8:2.8);
                float gx=x+(float)Math.cos(angle)*radius,gy=y+(float)Math.sin(angle)*radius;
                float tip=cell*(kind==3?.16f:.11f)*(1f-progress*.5f);
                c.drawLine(gx-tip,gy,gx+tip,gy,rune);
                c.drawLine(gx,gy-tip,gx,gy+tip,rune);
            }
            if(kind==1||kind==2){
                // Racing light along the full beam distinguishes row and column spells.
                rune.setStyle(Paint.Style.FILL);
                for(int trail=0;trail<5;trail++){
                    float along=(progress*1.7f+trail*.22f)%1f;
                    float lx=kind==1?boardX+along*N*cell:x;
                    float ly=kind==2?boardY+along*N*cell:y;
                    rune.setColor(Color.argb((int)(210*pulse),255,255,255));
                    c.drawCircle(lx,ly,cell*(trail==0?.16f:.075f),rune);
                }
            }else if(kind==4){
                rune.setStyle(Paint.Style.STROKE);
                rune.setStrokeWidth(cell*.06f);
                c.drawCircle(x,y,radius*.65f,rune);
            }
        }
        c.restoreToCount(saved);
        if(activeMultiplier>1){
            p.setColor(Color.argb((int)(52*pulse),255,250,217));
            c.drawRect(boardX,boardY,boardX+N*cell,boardY+N*cell,p);
            p.setShadowLayer(20,0,0,Color.rgb(255,210,70));
            p.setColor(Color.WHITE);p.setTextSize(cell*.72f);p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create("sans",Typeface.BOLD));
            c.drawText("MAGIC ×2!",getWidth()/2f,boardY+N*cell*.49f,p);p.clearShadowLayer();
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
        if(blocked[r][col]){
            drawRound(c,x+pad,y+pad,x+cell-pad,y+cell-pad,Color.argb(135,19,13,43),cell*.22f);
            stroke.setColor(Color.argb(120,123,94,165));stroke.setStrokeWidth(cell*.025f);
            c.drawLine(x+cell*.28f,y+cell*.30f,x+cell*.72f,y+cell*.70f,stroke);
            c.drawLine(x+cell*.72f,y+cell*.30f,x+cell*.28f,y+cell*.70f,stroke);
            return;
        }
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
        float cx=x+cell/2,cy=y+cell*.51f+bob;
        if(animationPhase==3){
            float t=Math.min(1f,(System.currentTimeMillis()-phaseStart)/150f);
            float ease=1f-(1f-t)*(1f-t);
            if(r==swapR1&&col==swapC1){
                cx+=(swapC2-swapC1)*cell*(1f-ease);
                cy+=(swapR2-swapR1)*cell*(1f-ease);
            }else if(r==swapR2&&col==swapC2){
                cx+=(swapC1-swapC2)*cell*(1f-ease);
                cy+=(swapR1-swapR2)*cell*(1f-ease);
            }
        }
        if(animationPhase==2){
            float t=Math.min(1f,(System.currentTimeMillis()-phaseStart)/320f);
            float eased=1f-(float)Math.pow(1f-t,3);
            // A quick overshoot gives each falling piece a soft landing.
            float landing=t>.72f?(float)Math.sin((t-.72f)/.28f*Math.PI)*.065f:0f;
            cy+=fallFrom[r][col]*cell*(1f-eased)-landing*cell;
        }
        if(animationPhase==1&&exploding.contains(r*N+col)){
            float t=Math.min(1f,(System.currentTimeMillis()-phaseStart)/1100f);
            float charge=Math.min(1f,t/.65f),blast=Math.max(0f,(t-.65f)/.35f);
            float pulse=(float)Math.sin(charge*13f+col*.65f+r*.42f);
            float radius=cell*(.31f+.28f*blast);
            int aura=colors[type];
            p.setColor((Math.max(0,(int)((1f-blast)*125))<<24)|(aura&0x00ffffff));
            p.setShadowLayer(cell*.17f,0,0,aura);
            c.drawCircle(cx,cy,radius,p);p.clearShadowLayer();
            stroke.setStrokeWidth(cell*(.025f+.045f*blast));
            stroke.setColor(Color.argb(Math.max(0,(int)((1f-blast)*225)),255,247,211));
            c.drawCircle(cx,cy,radius+cell*.12f*blast,stroke);
            int save=c.save();
            float scale=1f+.075f*pulse*charge+.78f*blast;
            c.scale(scale,scale,cx,cy);
            spritePaint.setAlpha(Math.max(0,(int)(255*(1f-blast))));
            drawPiece(c,cx,cy,kind,type,sel,r,col);
            c.restoreToCount(save);spritePaint.setAlpha(255);
            if(blast>0f){
                for(int k=0;k<6;k++){
                    float angle=(float)(k*Math.PI/3+r*.7f+col*.4f);
                    float dist=cell*(.2f+.57f*blast);
                    p.setColor(Color.argb(Math.max(0,(int)(230*(1f-blast))),255,235,164));
                    c.drawCircle(cx+(float)Math.cos(angle)*dist,cy+(float)Math.sin(angle)*dist,
                        cell*.043f*(1f-blast)+1f,p);
                }
            }
        }else drawPiece(c,cx,cy,kind,type,sel,r,col);
        if(ice[r][col]>0){
            p.setColor(ice[r][col]>1?Color.argb(155,160,223,255):Color.argb(100,176,235,255));
            c.drawRoundRect(x+pad,y+pad,x+cell-pad,y+cell-pad,cell*.18f,cell*.18f,p);
            stroke.setColor(Color.argb(210,231,249,255));stroke.setStrokeWidth(cell*.028f);
            c.drawRoundRect(x+pad,y+pad,x+cell-pad,y+cell-pad,cell*.18f,cell*.18f,stroke);
        }
    }

    private void drawPiece(Canvas c,float cx,float cy,int kind,int type,boolean selected,int row,int col){
        if(kind==0){
            drawGhostAlive(c,cx,cy,cell*.34f,type,selected,row,col);
            return;
        }
        float t=(System.currentTimeMillis()-gameStart)/280f;
        float radius=cell*(.43f+.055f*(float)Math.sin(t));
        p.setColor(kind==3?Color.argb(105,255,118,227):Color.argb(105,255,204,87));
        p.setShadowLayer(20,0,0,kind==3?Color.rgb(228,107,255):Color.rgb(255,213,91));
        c.drawCircle(cx,cy,radius,p);p.clearShadowLayer();
        stroke.setColor(Color.argb(170,255,246,204));stroke.setStrokeWidth(cell*.033f);
        c.drawCircle(cx,cy,cell*(.43f+.055f*(float)Math.sin(t)),stroke);
        if(magicItems!=null&&!magicItems.isRecycled()){
            int index=kind==4?2:kind==3?3:kind-1;
            float slice=magicItems.getWidth()/4f;
            Rect source=new Rect((int)(index*slice),0,(int)((index+1)*slice),magicItems.getHeight());
            float size=cell*.91f;
            c.drawBitmap(magicItems,source,new RectF(cx-size/2,cy-size/2,cx+size/2,cy+size/2),spritePaint);
        }else{
            p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(cell*.45f);
            c.drawText(kind==1?"↔":kind==2?"↕":kind==3?"★":"✦",cx,cy+cell*.14f,p);
        }
    }

    private void drawGhostAlive(Canvas c,float cx,float cy,float rad,int type,boolean selected,int row,int col){
        long now=System.currentTimeMillis();
        boolean reacting=row==reactionR&&col==reactionC&&now<reactionUntil;
        if(ghostReactions!=null&&!ghostReactions.isRecycled()){
            int frame=reacting?Math.min(3,1+(int)((now-reactionStart)/320L)):0;
            int sw=ghostReactions.getWidth()/3,sh=ghostReactions.getHeight()/4;
            Rect source=new Rect(type*sw,frame*sh,(type+1)*sw,(frame+1)*sh);
            float wiggle=reacting?(float)Math.sin((now-reactionStart)/55f)*9f:0f;
            float squash=reacting?1f+.07f*(float)Math.sin((now-reactionStart)/70f):1f;
            int save=c.save();
            c.rotate(wiggle,cx,cy);
            c.scale(2f-squash,squash,cx,cy);
            RectF dest=new RectF(cx-rad*1.27f,cy-rad*1.29f,cx+rad*1.27f,cy+rad*1.29f);
            spritePaint.setAlpha(255);
            c.drawBitmap(ghostReactions,source,dest,spritePaint);
            c.restoreToCount(save);
            if(selected){
                stroke.setColor(Color.rgb(255,221,78));stroke.setStrokeWidth(rad*.10f);
                stroke.setShadowLayer(18,0,0,Color.rgb(255,232,122));
                c.drawCircle(cx,cy,rad*1.18f,stroke);stroke.clearShadowLayer();
            }
            if(reacting){
                p.setColor(Color.argb(150,255,240,130));
                for(int i=0;i<3;i++){
                    float a=(now-reactionStart)/130f+i*2.09f;
                    c.drawCircle(cx+(float)Math.cos(a)*rad*1.35f,cy+(float)Math.sin(a)*rad*1.18f,rad*.09f,p);
                }
            }
            return;
        }
        drawGhost(c,cx,cy,rad,colors[type],type,selected);
    }

    private void drawGhost(Canvas c,float cx,float cy,float rad,int color,int face,boolean selected){
        if(ghostReactions!=null&&!ghostReactions.isRecycled()){
            int type=Math.max(0,Math.min(2,face));
            int sw=ghostReactions.getWidth()/3,sh=ghostReactions.getHeight()/4;
            Rect source=new Rect(type*sw,0,(type+1)*sw,sh);
            RectF dest=new RectF(cx-rad*1.24f,cy-rad*1.25f,cx+rad*1.24f,cy+rad*1.25f);
            c.drawBitmap(ghostReactions,source,dest,spritePaint);
            return;
        }
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
        if(i>=2&&i<=5&&magicItems!=null&&!magicItems.isRecycled()){
            int icon=i==2?0:i==3?1:i==4?2:3;
            float slice=magicItems.getWidth()/4f;
            Rect source=new Rect((int)(icon*slice),0,(int)((icon+1)*slice),magicItems.getHeight());
            c.drawBitmap(magicItems,source,new RectF(x+w*.035f,y+h*.005f,x+w*.965f,y+h*.76f),spritePaint);
        }else if(boosterSheet!=null&&!boosterSheet.isRecycled()){
            float sheetCell=boosterSheet.getWidth()/7f;
            Rect source=new Rect((int)(i*sheetCell),0,(int)((i+1)*sheetCell),boosterSheet.getHeight());
            c.drawBitmap(boosterSheet,source,new RectF(x+w*.04f,y+h*.02f,x+w*.96f,y+h*.75f),spritePaint);
        }else{
            p.setColor(Color.rgb(119,57,193));p.setTextSize(screenW*.060f);
            c.drawText(new String[]{"✋","H","↔","↕","✦","★","+5"}[i],x+w/2,y+h*.54f,p);
        }
        p.setColor(Color.WHITE);p.setTextSize(screenW*.017f);
        c.drawText(boosterNames[i],x+w/2,y+h*.96f,p);
        p.setColor(Color.rgb(190,30,45));c.drawCircle(x+w*.86f,y+h*.08f,w*.22f,p);
        p.setColor(Color.WHITE);p.setTextSize(screenW*.025f);
        c.drawText(""+boosterCount[i],x+w*.86f,y+h*.13f,p);
    }

    private void drawOverlay(Canvas c,float w,float h){
        p.setColor(Color.argb(218,10,5,30));c.drawRect(0,0,w,h,p);
        float l=w*.08f,r=w*.92f,t=won?h*.19f:h*.30f,b=won?h*.76f:
            paused&&getContext() instanceof MainActivity&&((MainActivity)getContext()).needsPrivacyOptions()?h*.79f:h*.68f;
        panel(c,l,t,r,b,Color.rgb(68,35,112));
        p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.WHITE);p.setTextSize(w*.075f);
        c.drawText(paused?"PAUSED":won?"LEVEL COMPLETE!":"SO CLOSE!",w/2,t+h*.068f,p);
        if(won){
            p.setColor(Color.rgb(255,216,91));p.setTextSize(w*.083f);
            c.drawText("★ ★ ★",w/2,t+h*.118f,p);
            long elapsed=System.currentTimeMillis()-victoryStart;
            float bob=(float)Math.sin(elapsed/170f)*h*.005f;
            // Each routine lasts long enough to recognize: two moonwalk poses, then two pop poses.
            int routine=(int)((elapsed/1680)%2);
            int frame=routine*2+(int)((elapsed/280)%2);
            if(dancingSkeleton!=null&&!dancingSkeleton.isRecycled()){
                float sw=dancingSkeleton.getWidth()/4f;
                Rect source=new Rect((int)(frame*sw),0,(int)((frame+1)*sw),dancingSkeleton.getHeight());
                float size=Math.min(w*.43f,h*.32f);
                int saved=c.save();
                c.rotate((float)Math.sin(elapsed/360f)*3f,w/2,t+h*.305f);
                c.drawBitmap(dancingSkeleton,source,
                    new RectF(w/2-size*.50f,t+h*.137f+bob,w/2+size*.50f,t+h*.472f+bob),spritePaint);
                c.restoreToCount(saved);
            }else drawGhost(c,w/2,t+h*.29f,w*.12f,colors[0],0,false);
            p.setColor(Color.rgb(233,220,255));p.setTextSize(w*.04f);
            c.drawText(routine==0?"MOONWALK!":"POP DANCE!",w/2,t+h*.494f,p);
            drawRound(c,w*.20f,t+h*.515f,w*.80f,t+h*.565f,Color.rgb(255,188,64),50);
            p.setColor(Color.rgb(55,25,70));p.setTextSize(w*.045f);
            c.drawText("NEXT LEVEL",w/2,t+h*.549f,p);
        }else{
            p.setTextSize(w*.12f);c.drawText("♥",w/2,t+h*.17f,p);
            p.setTextSize(w*.044f);p.setColor(Color.rgb(233,220,255));
            c.drawText(paused?"Tap continue to play":"Try this level again.",w/2,t+h*.23f,p);
            drawRound(c,w*.22f,t+h*.27f,w*.78f,t+h*.35f,Color.rgb(255,188,64),50);
            p.setColor(Color.rgb(55,25,70));p.setTextSize(w*.045f);
            c.drawText(paused?"CONTINUE":"RETRY",w/2,t+h*.325f,p);
            if(paused&&getContext() instanceof MainActivity&&((MainActivity)getContext()).needsPrivacyOptions()){
                drawRound(c,w*.22f,h*.70f,w*.78f,h*.765f,Color.rgb(105,73,162),40);
                p.setColor(Color.WHITE);p.setTextSize(w*.036f);
                c.drawText("PRIVACY OPTIONS",w/2,h*.745f,p);
            }
        }
    }

    @Override public boolean onTouchEvent(android.view.MotionEvent e){
        float x=e.getX(),y=e.getY();
        if(e.getAction()==MotionEvent.ACTION_DOWN){
            if(missionBrief)return true;
            if(tutorialStage==0||tutorialStage==2&&animationPhase==0)return true;
            touchDownX=x; touchDownY=y;
            if(!won&&!lost&&!paused&&y>=boardY&&y<boardY+N*cell&&x>=boardX&&x<boardX+N*cell){
                touchDownC=Math.min(N-1,(int)((x-boardX)/cell));
                touchDownR=Math.min(N-1,(int)((y-boardY)/cell));
                if(blocked[touchDownR][touchDownC]){touchDownR=-1;touchDownC=-1;return true;}
                selectedR=touchDownR; selectedC=touchDownC;
                reactionR=touchDownR;reactionC=touchDownC;
                reactionStart=System.currentTimeMillis();reactionUntil=reactionStart+REACTION_MS;
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                invalidate();
            } else {touchDownR=-1;touchDownC=-1;}
            return true;
        }
        if(e.getAction()!=MotionEvent.ACTION_UP)return true;
        if(missionBrief){missionBrief=false;message("ทำภารกิจให้ครบ แล้วไปด่านต่อไป!");invalidate();return true;}
        if(tutorialStage==0){
            if(y>getHeight()*.53f&&y<getHeight()*.65f){
                tutorialStage=1;tutorialMove=findPossibleMove();
                message("Swipe the two glowing ghosts!");
            }else if(y>getHeight()*.65f&&y<getHeight()*.76f)finishTutorial();
            invalidate();return true;
        }
        if(tutorialStage==2&&animationPhase==0){
            if(y>getHeight()*.55f&&y<getHeight()*.72f)finishTutorial();
            invalidate();return true;
        }
        if(animationPhase!=0)return true;
        if(tutorialStage==1&&touchDownR<0){
            if(y>getHeight()*.78f&&y<getHeight()*.87f&&x>getWidth()*.72f)finishTutorial();
            invalidate();return true;
        }
        if(won||lost||paused){
            if(paused&&y>getHeight()*.70f&&y<getHeight()*.77f
               &&getContext() instanceof MainActivity){
                ((MainActivity)getContext()).openPrivacyOptions();return true;
            }
            if(y>(won?getHeight()*.69f:getHeight()*.57f)&&y<(won?getHeight()*.76f:getHeight()*.68f)){
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
                if(tr>=0&&tr<N&&tc>=0&&tc<N&&!blocked[tr][tc]){
                    swipeFX=boardX+(tc+.5f)*cell;swipeFY=boardY+(tr+.5f)*cell;
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    if(mode==0){
                        if(boosterCount[0]>0){
                            freeSwap(touchDownR,touchDownC,tr,tc);
                        }
                    }else if(mode>=1&&mode<=5){cellTap(touchDownR,touchDownC);}
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
        if(tutorialStage==1&&tutorialMove!=null){
            boolean forward=r1==tutorialMove[0]&&c1==tutorialMove[1]&&r2==tutorialMove[2]&&c2==tutorialMove[3];
            boolean reverse=r2==tutorialMove[0]&&c2==tutorialMove[1]&&r1==tutorialMove[2]&&c1==tutorialMove[3];
            if(!forward&&!reverse){message("Swipe the two glowing ghosts!");return;}
            tutorialStage=2;tutorialMove=null;
        }
        int a=board[r1][c1],b=board[r2][c2];
        swap(r1,c1,r2,c2);
        if(a<TYPES&&b<TYPES&&!hasAnyMatch()){
            swap(r1,c1,r2,c2);
            message("Try another pair!");
            return;
        }
        moves--;cascadeDepth=0;
        swapR1=r1;swapC1=c1;swapR2=r2;swapC2=c2;
        animationPhase=3;phaseStart=System.currentTimeMillis();
        final int serial=animationSerial;
        postDelayed(()->{
            if(serial!=animationSerial)return;
            animationPhase=0;
            if(a>=TYPES||b>=TYPES){
                // Two magic items amplify each other: both casts gain a wider radius.
                powerMultiplier=(a>=TYPES&&b>=TYPES)?2:1;
                Set<Integer> hits=new HashSet<>();
                if(a>=TYPES)expandPower(r2,c2,a,hits,powerMultiplier,b<TYPES?b:-1);
                if(b>=TYPES)expandPower(r1,c1,b,hits,powerMultiplier,a<TYPES?a:-1);
                beginExplosion(hits,false,-1,-1);
            }else beginExplosion(findMatches(),true,r2,c2);
            invalidate();
        },150);
        invalidate();
    }

    private void freeSwap(int r1,int c1,int r2,int c2){
        int a=board[r1][c1],b=board[r2][c2];
        swap(r1,c1,r2,c2);
        useBooster(0,"Free magic swap!");
        cascadeDepth=0;
        if(a>=TYPES||b>=TYPES){
            powerMultiplier=a>=TYPES&&b>=TYPES?2:1;
            Set<Integer> hits=new HashSet<>();
            if(a>=TYPES)expandPower(r2,c2,a,hits,powerMultiplier,b<TYPES?b:-1);
            if(b>=TYPES)expandPower(r1,c1,b,hits,powerMultiplier,a<TYPES?a:-1);
            beginExplosion(hits,false,-1,-1);
        }else if(hasAnyMatch())beginExplosion(findMatches(),true,r2,c2);
        else{ensureMove();message("Free swap complete!");}
        invalidate();
    }

    private int targetTypeForPower(int r,int c){return Math.max(0,colorOf(board[r][c]));}

    private void cellTap(int r,int c){
        if(mode>=1&&mode<=5){
            int power=mode;
            Set<Integer> hit=new HashSet<>();
            if(power==1)hit.add(r*N+c);
            else if(power==2||power==3||power==4||power==5){
                // Inventory powers share the same in-board artwork and spell rules.
                int kind=power==2?1:power==3?2:power==4?4:3;
                int targetType=power==5?colorOf(board[r][c]):-1;
                expandPower(r,c,kind*TYPES+targetTypeForPower(r,c),hit,1,targetType);
            }
            useBooster(power,"Magic power!");
            score+=350;cascadeDepth=0;
            beginExplosion(hit,false,-1,-1);invalidate();return;
        }
        if(selectedR<0){selectedR=r;selectedC=c;invalidate();return;}
        if(selectedR==r&&selectedC==c){selectedR=-1;selectedC=-1;invalidate();return;}
        if(Math.abs(selectedR-r)+Math.abs(selectedC-c)==1){
            int sr=selectedR,sc=selectedC;selectedR=-1;selectedC=-1;
            if(mode==0){freeSwap(sr,sc,r,c);}
            else attemptSwipe(sr,sc,r,c);
        }else{selectedR=r;selectedC=c;}
        invalidate();
    }

    private void boosterTap(int i){
        if(boosterCount[i]<=0){message("Earn more boosters by passing levels!");return;}
        if(i==6){boosterCount[i]--;moves+=5;message("+5 moves added!");}
        else{mode=mode==i?-1:i;selectedR=-1;selectedC=-1;
            message(mode<0?"Booster cancelled":i==0?"Swipe any two neighbors":"Tap a ghost for "+boosterNames[i]);}
        invalidate();
    }

    private int[] findPossibleMove(){
        for(int r=0;r<N;r++)for(int c=0;c<N;c++){
            if(c+1<N&&!blocked[r][c]&&!blocked[r][c+1]){swap(r,c,r,c+1);boolean ok=hasAnyMatch();swap(r,c,r,c+1);if(ok)return new int[]{r,c,r,c+1};}
            if(r+1<N&&!blocked[r][c]&&!blocked[r+1][c]){swap(r,c,r+1,c);boolean ok=hasAnyMatch();swap(r,c,r+1,c);if(ok)return new int[]{r,c,r+1,c};}
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
                if(c<N&&base(board[r][c])>=0&&base(board[r][c])==base(board[r][c-1])) run++;
                else {if(run>=3)for(int k=c-run;k<c;k++)out.add(r*N+k);run=1;}
            }
        }
        for(int c=0;c<N;c++){
            int run=1;
            for(int r=1;r<=N;r++){
                if(r<N&&base(board[r][c])>=0&&base(board[r][c])==base(board[r-1][c])) run++;
                else {if(run>=3)for(int k=r-run;k<r;k++)out.add(k*N+c);run=1;}
            }
        }
        return out;
    }

    private int base(int value){return value<0||value>=TYPES?-1:value;}
    private int colorOf(int value){return value<0?-1:value%TYPES;}

    private void expandPower(int row,int col,int value,Set<Integer> hits){
        expandPower(row,col,value,hits,1,-1);
    }

    private void expandPower(int row,int col,int value,Set<Integer> hits,int multiplier,int targetType){
        if(value<TYPES)return;
        int kind=value/TYPES;
        castPoints.add(new int[]{row,col,kind,multiplier});
        if(kind==1){
            for(int r=Math.max(0,row-multiplier+1);r<=Math.min(N-1,row+multiplier-1);r++)
                for(int j=0;j<N;j++)hits.add(r*N+j);
        }else if(kind==2){
            for(int c=Math.max(0,col-multiplier+1);c<=Math.min(N-1,col+multiplier-1);c++)
                for(int i=0;i<N;i++)hits.add(i*N+c);
        }else if(kind==3){
            if(multiplier>1){
                for(int i=0;i<N;i++)for(int j=0;j<N;j++)hits.add(i*N+j);
            }else{
                int type=targetType>=0?targetType:colorOf(value);
                for(int i=0;i<N;i++)for(int j=0;j<N;j++)
                    if(base(board[i][j])==type)hits.add(i*N+j);
            }
        }else if(kind==4){
            for(int i=Math.max(0,row-multiplier);i<=Math.min(N-1,row+multiplier);i++)
                for(int j=Math.max(0,col-multiplier);j<=Math.min(N-1,col+multiplier);j++)hits.add(i*N+j);
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
        // An L/T crossing creates a 3×3 burst, distinct from a straight four.
        for(int r=0;r<N;r++)for(int c=0;c<N;c++){
            int type=base(board[r][c]);if(type<0)continue;
            int horiz=1,vert=1;
            for(int j=c-1;j>=0&&base(board[r][j])==type;j--)horiz++;
            for(int j=c+1;j<N&&base(board[r][j])==type;j++)horiz++;
            for(int i=r-1;i>=0&&base(board[i][c])==type;i--)vert++;
            for(int i=r+1;i<N&&base(board[i][c])==type;i++)vert++;
            if(horiz>=3&&vert>=3&&best<5)return new int[]{r,c,4};
        }
        return kind==0?null:new int[]{rr,cc,kind};
    }

    private void resolveCascades(){
        if(animationPhase!=0)return;
        Set<Integer> matches=findMatches();
        if(!matches.isEmpty())beginExplosion(matches,true,-1,-1);
        else {ensureMove();checkEnd();}
    }

    private void beginExplosion(Set<Integer> hits,boolean player,int preferredR,int preferredC){
        if(hits.isEmpty()){ensureMove();checkEnd();return;}
        if(cascadeDepth++>=12){cascadeDepth=0;ensureMove();checkEnd();return;}
        int[] reward=player?powerReward(preferredR,preferredC):null;
        Set<Integer> expanded=new HashSet<>(hits),processed=new HashSet<>();
        expanded.removeIf(pos->pos<0||pos>=N*N||blocked[pos/N][pos%N]);
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
                board[reward[0]][reward[1]]=colorOf(board[reward[0]][reward[1]])+TYPES*reward[2];
                message(reward[2]==4?"Ghost burst unlocked!":reward[2]==3?"Rainbow ghost unlocked!":reward[2]==1?"Row blast unlocked!":"Column blast unlocked!");
            }
        }
        if(expanded.isEmpty()){ensureMove();checkEnd();return;}
        exploding.clear();exploding.addAll(expanded);animationPhase=1;phaseStart=System.currentTimeMillis();
        activeMultiplier=powerMultiplier;powerMultiplier=1;
        combo=Math.min(12,cascadeDepth);score+=expanded.size()*90*combo*activeMultiplier;
        for(int pos:expanded){
            int r=pos/N,c=pos%N,v=board[r][c];
            if(v>=0){
                if(v<TYPES)collected[v]++;
                if(ice[r][c]>0){ice[r][c]--;iceLeft--;score+=100;}
                burstAt(r,c,colors[colorOf(v)]);
            }
        }
        if(combo>=2){
            comboText="COMBO x"+combo+"!";comboUntil=System.currentTimeMillis()+1800;
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
                castPoints.clear();
                if(!next.isEmpty())beginExplosion(next,true,-1,-1);
                else{cascadeDepth=0;ensureMove();checkEnd();}
            },320);
        },1100);
        invalidate();
    }

    private void collapseAnimated(){
        for(int c=0;c<N;c++){
            int end=N-1;
            while(end>=0){
                while(end>=0&&blocked[end][c]){board[end][c]=WALL;fallFrom[end][c]=0;end--;}
                if(end<0)break;
                int start=end;while(start>0&&!blocked[start-1][c])start--;
                collapseSegment(c,start,end,true);
                end=start-1;
            }
        }
    }

    private void collapse(){
        for(int c=0;c<N;c++){
            int end=N-1;
            while(end>=0){
                while(end>=0&&blocked[end][c]){board[end][c]=WALL;end--;}
                if(end<0)break;
                int start=end;while(start>0&&!blocked[start-1][c])start--;
                collapseSegment(c,start,end,false);
                end=start-1;
            }
        }
    }

    private void collapseSegment(int c,int start,int end,boolean animated){
        int write=end;
        for(int r=end;r>=start;r--)if(board[r][c]>=0){
            board[write][c]=board[r][c];
            if(animated)fallFrom[write][c]=r-write;
            write--;
        }
        int spawn=start-1;
        while(write>=start){
            board[write][c]=rng.nextInt(TYPES);
            if(animated)fallFrom[write][c]=spawn-write;
            write--;spawn--;
        }
    }

    private boolean possibleMove(){
        for(int r=0;r<N;r++)for(int c=0;c<N;c++){
            if(c+1<N&&!blocked[r][c]&&!blocked[r][c+1]){swap(r,c,r,c+1);boolean ok=hasAnyMatch();swap(r,c,r,c+1);if(ok)return true;}
            if(r+1<N&&!blocked[r][c]&&!blocked[r+1][c]){swap(r,c,r+1,c);boolean ok=hasAnyMatch();swap(r,c,r+1,c);if(ok)return true;}
        }return false;
    }

    private void ensureMove(){
        for(int[] row:board)for(int v:row)if(v>=TYPES)return;
        if(!possibleMove())shuffle();
    }
    private void shuffle(){
        ArrayList<Integer> list=new ArrayList<>();
        for(int r=0;r<N;r++)for(int c=0;c<N;c++)if(!blocked[r][c])list.add(board[r][c]<0?rng.nextInt(TYPES):board[r][c]);
        do{
            Collections.shuffle(list,rng);int k=0;
            for(int r=0;r<N;r++)for(int c=0;c<N;c++)board[r][c]=blocked[r][c]?WALL:list.get(k++);
        }while((hasAnyMatch()||!possibleMove()));
    }

    private void showHint(){
        for(int r=0;r<N;r++)for(int c=0;c<N;c++){
            if(c+1<N&&!blocked[r][c]&&!blocked[r][c+1]){swap(r,c,r,c+1);boolean ok=hasAnyMatch();swap(r,c,r,c+1);if(ok){selectedR=r;selectedC=c;message("Hint: select the glowing ghost");invalidate();return;}}
            if(r+1<N&&!blocked[r][c]&&!blocked[r+1][c]){swap(r,c,r+1,c);boolean ok=hasAnyMatch();swap(r,c,r+1,c);if(ok){selectedR=r;selectedC=c;message("Hint: select the glowing ghost");invalidate();return;}}
        }
    }

    boolean isShowingVictory(int completedLevel){return won&&level==completedLevel;}

    private void checkEnd(){
        if(!won&&iceLeft==0&&collected[0]>=goals[0]&&collected[1]>=goals[1]&&collected[2]>=goals[2]){
            won=true;victoryStart=System.currentTimeMillis();boosterCount[rng.nextInt(7)]++;
            if(getContext() instanceof MainActivity)((MainActivity)getContext()).onLevelCompleted(level);
            highestLevel=Math.max(highestLevel,level+1);
            progress.edit().putInt("highest_level",highestLevel).apply();
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
