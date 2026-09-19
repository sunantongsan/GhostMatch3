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
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

public class MainActivity extends Activity {
    // Google's official SAMPLE identifiers: test impressions never earn money.
    private static final String TEST_INTERSTITIAL_ID="ca-app-pub-3940256099942544/1033173712";
    private static final String TEST_REWARDED_ID="ca-app-pub-3940256099942544/5224354917";
    private static final long MIN_AD_INTERVAL_MS=90000L;
    private GhostGameView gameView;
    private ConsentInformation consentInformation;
    private InterstitialAd interstitial;
    private RewardedAd rewardedAd;
    private boolean adsInitialized=false, adLoading=false, rewardLoading=false, privacyOptionsRequired=false;
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
            else {interstitial=null;rewardedAd=null;}
        });
    }

    private void initializeAds(){
        if(adsInitialized)return;
        adsInitialized=true;
        MobileAds.initialize(this,status->runOnUiThread(()->{loadNextAd();loadRewardedAd();}));
    }

    private void loadRewardedAd(){
        if(!adsInitialized||consentInformation==null||!consentInformation.canRequestAds()
            ||rewardLoading||rewardedAd!=null)return;
        rewardLoading=true;
        RewardedAd.load(this,TEST_REWARDED_ID,new AdRequest.Builder().build(),
            new RewardedAdLoadCallback(){
                @Override public void onAdLoaded(RewardedAd ad){rewardLoading=false;rewardedAd=ad;}
                @Override public void onAdFailedToLoad(LoadAdError error){rewardLoading=false;rewardedAd=null;}
            });
    }

    void showRewardedMoves(Runnable earned,Runnable unavailable){
        if(rewardedAd==null||consentInformation==null||!consentInformation.canRequestAds()){
            loadRewardedAd();unavailable.run();return;
        }
        RewardedAd ad=rewardedAd;rewardedAd=null;
        ad.setFullScreenContentCallback(new FullScreenContentCallback(){
            @Override public void onAdDismissedFullScreenContent(){loadRewardedAd();}
            @Override public void onAdFailedToShowFullScreenContent(AdError error){loadRewardedAd();unavailable.run();}
        });
        ad.show(this,rewardItem->earned.run());
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
        // Keep the celebration uninterrupted; the ad belongs to the NEXT LEVEL transition.
        loadNextAd();
    }

    void showAdBeforeNextLevel(Runnable continueToLevel){
        if(interstitial==null||consentInformation==null||!consentInformation.canRequestAds()){
            loadNextAd();continueToLevel.run();return;
        }
        InterstitialAd ad=interstitial;interstitial=null;
        ad.setFullScreenContentCallback(new FullScreenContentCallback(){
            private boolean continued=false;
            private void finish(){if(continued)return;continued=true;loadNextAd();continueToLevel.run();}
            @Override public void onAdDismissedFullScreenContent(){finish();}
            @Override public void onAdFailedToShowFullScreenContent(AdError error){finish();}
        });
        ad.show(this);
    }
}

class GhostGameView extends View {
    private static final int N=7, TYPES=4;
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
    private Bitmap ghostSheet,ghostReactions,boosterSheet,hauntedBackground,magicItems,dancingSkeleton,laughingSkull;
    private final int[] colors={Color.rgb(245,245,255),Color.rgb(188,236,172),Color.rgb(161,77,227),Color.rgb(255,143,55)};
    private final String[] boosterNames={"SWAP","HAMMER","ROW","COLUMN","BURST","RAINBOW","+5"};
    private final int[] boosterCount={2,2,1,1,1,1,2};
    private final int[] collected=new int[TYPES];
    private final int[] goals={10,10,10,0};
    private int helperFirstR=-1,helperFirstC=-1;
    private boolean paused=false,missionBrief=false,worldMap=true;
    private int mapWorld=0;
    private long missionBriefStart=0;
    private int level=1, moves=28, score=0, target=1800, selectedR=-1, selectedC=-1;
    private int mode=-1, combo=0;
    private int tutorialStage=-1;
    private int[] tutorialMove;
    private float boardX,boardY,cell,boosterY;
    private float touchDownX, touchDownY;
    private int touchDownR=-1, touchDownC=-1;
    private boolean won=false,lost=false,victoryAdvancing=false,rewardAdPending=false;
    private long gameStart=System.currentTimeMillis(),victoryStart=0,lostStart=0;
    private int victoryReward=-1,victoryBonus=-1,victoryBonusCount=0;
    private boolean worldClearReward=false;
    private static final long VICTORY_DANCE_MS=4800L;
    private static final long FAILURE_LAUGH_MS=4800L;
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
        dancingSkeleton=BitmapFactory.decodeResource(getResources(),R.drawable.skeleton_dance_v3);
        laughingSkull=BitmapFactory.decodeResource(getResources(),R.drawable.laughing_skull);
        hauntedBackground=BitmapFactory.decodeResource(getResources(),R.drawable.haunted_background);
        progress=c.getSharedPreferences("ghostmatch_progress",Context.MODE_PRIVATE);
        highestLevel=Math.max(1,progress.getInt("highest_level",1));
        highestLevel=Math.min(120,highestLevel);
        level=highestLevel;
        mapWorld=(highestLevel-1)/12;
        tutorialStage=level==1&&!progress.getBoolean("tutorial_complete",false)?0:-1;
        newLevel();
    }

    private void newLevel(){
        animationSerial++;animationPhase=0;exploding.clear();castPoints.clear();powerMultiplier=1;
        for(float[] row:fallFrom)Arrays.fill(row,0);
        configureLayout();
        if(level==1){moves=8;target=850;}
        else if(level==2){moves=9;target=1200;}
        else if(level==3){moves=10;target=1550;}
        else if(level<=5){moves=11;target=1900+level*150;}
        else {moves=Math.min(26,12+level/8);target=Math.min(18000,2300+level*275);}
        int excluded=level<=3?3:(level-1)%TYPES;
        for(int i=0;i<TYPES;i++){
            goals[i]=i==excluded?0:(level<=3?4+level:level<=7?9+level/2:Math.min(38,11+(level*4)/5));
            if(goals[i]>0&&level>=12&&i==(level+1)%TYPES)goals[i]+=Math.min(8,level/12);
            collected[i]=0;
        }
        for(int[] row:ice)Arrays.fill(row,0);
        iceLeft=0;
        int iceCount=level<4?0:Math.min(36,4+(level-4)/3+(level/16)*2);
        int strength=level>=70?3:level>=18?2:1;
        ArrayList<Integer> open=new ArrayList<>();
        for(int r=0;r<N;r++)for(int c=0;c<N;c++)if(!blocked[r][c])open.add(r*N+c);
        Collections.shuffle(open,rng);
        for(int k=0;k<Math.min(iceCount,open.size());k++){
            int pos=open.get(k),rr=pos/N,cc=pos%N;
            ice[rr][cc]=strength;iceLeft+=strength;
        }
        iceInitial=iceLeft;
        score=0;combo=0;won=false;lost=false;victoryAdvancing=false;paused=false;mode=-1;helperFirstR=-1;
        for(int r=0;r<N;r++)for(int c=0;c<N;c++){
            if(blocked[r][c]){board[r][c]=WALL;continue;}
            int t,guard=0;
            do{
                t=rng.nextInt(availableTypes());guard++;
            }while(guard<20&&((c>=2&&!blocked[r][c-1]&&!blocked[r][c-2]&&board[r][c-1]==t&&board[r][c-2]==t)
                ||(r>=2&&!blocked[r-1][c]&&!blocked[r-2][c]&&board[r-1][c]==t&&board[r-2][c]==t)
                ||(r>=1&&c>=1&&!blocked[r-1][c]&&!blocked[r][c-1]&&!blocked[r-1][c-1]
                    &&board[r-1][c]==t&&board[r][c-1]==t&&board[r-1][c-1]==t)));
            board[r][c]=t;
        }
        ensureMove();
        if(tutorialStage==1)tutorialMove=findPossibleMove();
        String[] shapeNames={"สวนผี","ประตูโค้ง","ลานเวท","เพชรต้องสาป","ป้อมค้างคาว","นาฬิกาทราย",
            "ปราสาท","ห้องแฝด","จันทร์เสี้ยว","มงกุฎ","ประตูมิติ","ลานบอส"};
        String shape=shapeNames[(level-1)%shapeNames.length];
        message(level<=3?"เริ่มต้นฝึกฝน — "+shape:"ด่านท้าทาย "+level+" — "+shape);
        missionBrief=level>1;missionBriefStart=System.currentTimeMillis();
        invalidate();
    }

    private int availableTypes(){return level<4?3:TYPES;}

    private int[] activeGoalTypes(){
        int[] active=new int[3];int n=0;
        for(int i=0;i<TYPES&&n<active.length;i++)if(goals[i]>0)active[n++]=i;
        return active;
    }

    private void configureLayout(){
        for(boolean[] row:blocked)Arrays.fill(row,false);
        int shape=(level-1)%12;
        for(int r=0;r<N;r++)for(int c=0;c<N;c++){
            boolean wall=false;
            if(shape==0)wall=r==0||r==N-1||c==0||c==N-1;
            else if(shape==1)wall=(r==0&&(c<2||c>4))||(r==6&&(c==0||c==6));
            else if(shape==2)wall=false;
            else if(shape==3)wall=(r==0||r==6)&&(c<2||c>4)||(r==1||r==5)&&(c==0||c==6);
            else if(shape==4)wall=(r<2||r>4)&&(c<2||c>4);
            else if(shape==5)wall=(r==0||r==6)&&(c<2||c>4)||(r==1||r==5)&&(c==0||c==6);
            else if(shape==6)wall=(r==0&&(c==1||c==5))||(r==6&&(c==0||c==3||c==6));
            else if(shape==7)wall=c==3&&r>=2&&r<=4;
            else if(shape==8)wall=(c==0&&r>0&&r<6)||(c==1&&r>=2&&r<=4);
            else if(shape==9)wall=(r==0&&(c==1||c==3||c==5))||(r==6&&(c<2||c>4));
            else if(shape==10)wall=(r==1||r==5)&&(c==1||c==5)||(r==3&&c==3);
            else wall=(r==0||r==6)&&(c==0||c==6)||(r==3&&(c==0||c==6));
            blocked[r][c]=wall;
        }
    }

    private float mapNodeX(int index,float w){
        int row=index/3,col=index%3;
        if((row&1)==1)col=2-col;
        return w*(.20f+.30f*col);
    }

    private float mapNodeY(int index,float h){return h*(.245f+.165f*(index/3));}

    private void drawWorldMap(Canvas c,float w,float h){
        p.setTypeface(Typeface.create("sans",Typeface.BOLD));p.setTextAlign(Paint.Align.CENTER);
        p.setColor(Color.argb(105,7,3,25));c.drawRect(0,0,w,h,p);
        Paint mist=new Paint(Paint.ANTI_ALIAS_FLAG);
        mist.setShader(new RadialGradient(w*.5f,h*.50f,w*.72f,Color.argb(95,118,54,191),Color.TRANSPARENT,Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,mist);
        panel(c,w*.055f,h*.025f,w*.945f,h*.17f,Color.rgb(61,30,111));
        p.setColor(Color.rgb(255,217,80));p.setTextSize(w*.055f);
        c.drawText("เส้นทางอาณาจักรผี",w/2,h*.082f,p);
        p.setColor(Color.WHITE);p.setTextSize(w*.034f);
        c.drawText("โลก "+(mapWorld+1)+" / 10  •  ด่าน "+(mapWorld*12+1)+"–"+(mapWorld*12+12),w/2,h*.13f,p);
        stroke.setStrokeWidth(w*.024f);stroke.setStrokeCap(Paint.Cap.ROUND);
        for(int i=0;i<11;i++){
            float x1=mapNodeX(i,w),y1=mapNodeY(i,h),x2=mapNodeX(i+1,w),y2=mapNodeY(i+1,h);
            int stage=mapWorld*12+i+1;
            stroke.setColor(stage<highestLevel?Color.rgb(122,225,81):Color.rgb(91,70,124));
            stroke.setShadowLayer(12,0,0,stroke.getColor());c.drawLine(x1,y1,x2,y2,stroke);stroke.clearShadowLayer();
        }
        for(int i=0;i<12;i++){
            int stage=mapWorld*12+i+1;boolean unlocked=stage<=highestLevel;boolean cleared=stage<highestLevel;
            float x=mapNodeX(i,w),y=mapNodeY(i,h),rad=w*.067f;
            p.setColor(unlocked?Color.rgb(111,55,178):Color.rgb(48,40,69));
            if(stage%12==0)p.setColor(unlocked?Color.rgb(196,63,105):Color.rgb(63,40,58));
            else if(stage%3==0)p.setColor(unlocked?Color.rgb(157,94,38):Color.rgb(58,47,38));
            p.setShadowLayer(unlocked?18:5,0,0,unlocked?Color.rgb(177,100,255):Color.BLACK);
            c.drawCircle(x,y,rad,p);p.clearShadowLayer();
            stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(w*.009f);
            stroke.setColor(cleared?Color.rgb(116,242,95):unlocked?Color.rgb(255,213,79):Color.rgb(105,91,124));
            c.drawCircle(x,y,rad,stroke);stroke.setStyle(Paint.Style.STROKE);
            if(!unlocked)drawLockedStage(c,x,y,rad);
            else if(stage%12==0)drawMapGhost(c,x,y-rad*.08f,rad*.82f,stage,true);
            else if(stage%3==0)drawTreasureChest(c,x,y-rad*.04f,rad*.78f,stage);
            else drawMapGhost(c,x,y-rad*.08f,rad*.78f,stage,false);
            p.setColor(unlocked?Color.WHITE:Color.rgb(150,140,166));
            p.setTextSize(w*.027f);c.drawText(""+stage,x,y+w*.043f,p);
            if(cleared){p.setColor(Color.rgb(104,239,87));p.setTextSize(w*.030f);c.drawText("✓",x+rad*.78f,y-rad*.62f,p);}
            if(stage%12==0){p.setColor(Color.rgb(255,205,72));p.setTextSize(w*.016f);c.drawText("ประตูใหญ่",x,y+rad*1.35f,p);}
            else if(stage%3==0){p.setColor(Color.rgb(255,218,112));p.setTextSize(w*.016f);c.drawText("หีบลับ",x,y+rad*1.35f,p);}
        }
        drawRound(c,w*.06f,h*.91f,w*.30f,h*.965f,mapWorld>0?Color.rgb(91,58,145):Color.rgb(53,44,71),w*.03f);
        drawRound(c,w*.70f,h*.91f,w*.94f,h*.965f,mapWorld<9?Color.rgb(91,58,145):Color.rgb(53,44,71),w*.03f);
        p.setColor(Color.WHITE);p.setTextSize(w*.031f);c.drawText("‹ โลกก่อน",w*.18f,h*.948f,p);c.drawText("โลกถัดไป ›",w*.82f,h*.948f,p);
        p.setColor(Color.rgb(225,211,243));p.setTextSize(w*.027f);
        c.drawText("แตะประตูที่เปิดเพื่อเริ่มด่าน • รวมทั้งหมด 120 ด่าน",w/2,h*.885f,p);
    }

    private void drawMapGhost(Canvas c,float x,float y,float rad,int stage,boolean guardian){
        int type=(stage*7+stage/3)%TYPES;
        int aura=new int[]{Color.rgb(100,225,255),Color.rgb(176,255,104),Color.rgb(234,105,255),
            Color.rgb(255,167,72),Color.rgb(98,142,255),Color.rgb(255,91,154)}[stage%6];
        p.setColor(Color.argb(80,Color.red(aura),Color.green(aura),Color.blue(aura)));p.setShadowLayer(rad*.45f,0,0,aura);
        c.drawCircle(x,y,rad*.88f,p);p.clearShadowLayer();
        drawGhost(c,x,y,rad*(guardian?.62f:.57f),colors[type],type,false);
        Paint deco=new Paint(Paint.ANTI_ALIAS_FLAG);deco.setColor(Color.rgb(255,215,75));deco.setStyle(Paint.Style.FILL);
        int style=guardian?0:stage%6;
        if(style==0){
            Path crown=new Path();crown.moveTo(x-rad*.43f,y-rad*.52f);crown.lineTo(x-rad*.30f,y-rad*.88f);
            crown.lineTo(x-rad*.08f,y-rad*.60f);crown.lineTo(x+rad*.10f,y-rad*.91f);
            crown.lineTo(x+rad*.30f,y-rad*.60f);crown.lineTo(x+rad*.46f,y-rad*.86f);
            crown.lineTo(x+rad*.39f,y-rad*.47f);crown.close();c.drawPath(crown,deco);
        }else if(style==1){
            Path horns=new Path();horns.moveTo(x-rad*.30f,y-rad*.50f);horns.quadTo(x-rad*.75f,y-rad*.88f,x-rad*.63f,y-rad*.25f);
            horns.lineTo(x-rad*.40f,y-rad*.38f);horns.close();c.drawPath(horns,deco);
            int save=c.save();c.scale(-1,1,x,y);c.drawPath(horns,deco);c.restoreToCount(save);
        }else if(style==2){
            deco.setColor(Color.rgb(105,48,175));Path hat=new Path();hat.moveTo(x-rad*.52f,y-rad*.48f);hat.lineTo(x+rad*.52f,y-rad*.48f);
            hat.lineTo(x+rad*.08f,y-rad*1.10f);hat.close();c.drawPath(hat,deco);deco.setColor(Color.rgb(255,214,69));c.drawCircle(x+rad*.04f,y-rad*.76f,rad*.09f,deco);
        }else if(style==3){
            deco.setStyle(Paint.Style.STROKE);deco.setStrokeWidth(rad*.10f);deco.setColor(Color.rgb(255,230,112));
            c.drawOval(x-rad*.42f,y-rad*.82f,x+rad*.42f,y-rad*.65f,deco);deco.setStyle(Paint.Style.FILL);
        }else if(style==4){
            deco.setColor(Color.rgb(63,27,91));Path wing=new Path();wing.moveTo(x-rad*.40f,y-rad*.05f);wing.lineTo(x-rad*.92f,y-rad*.42f);
            wing.lineTo(x-rad*.80f,y+rad*.12f);wing.lineTo(x-rad*.48f,y+rad*.30f);wing.close();c.drawPath(wing,deco);
            int save=c.save();c.scale(-1,1,x,y);c.drawPath(wing,deco);c.restoreToCount(save);
        }else{
            deco.setColor(aura);deco.setShadowLayer(rad*.25f,0,0,aura);c.drawCircle(x,y-rad*.55f,rad*.13f,deco);deco.clearShadowLayer();
        }
    }

    private void drawTreasureChest(Canvas c,float x,float y,float rad,int stage){
        int glow=new int[]{Color.rgb(255,208,66),Color.rgb(83,231,255),Color.rgb(239,100,255)}[(stage/3)%3];
        p.setColor(Color.argb(95,Color.red(glow),Color.green(glow),Color.blue(glow)));p.setShadowLayer(rad*.55f,0,0,glow);c.drawCircle(x,y,rad,p);p.clearShadowLayer();
        drawRound(c,x-rad*.63f,y-rad*.10f,x+rad*.63f,y+rad*.55f,Color.rgb(132,62,31),rad*.14f);
        p.setColor(Color.rgb(236,150,53));c.drawArc(x-rad*.63f,y-rad*.58f,x+rad*.63f,y+rad*.27f,180,180,true,p);
        p.setColor(Color.rgb(255,217,78));c.drawRect(x-rad*.09f,y-rad*.28f,x+rad*.09f,y+rad*.55f,p);
        c.drawCircle(x,y+rad*.10f,rad*.15f,p);p.setColor(Color.rgb(90,43,36));c.drawCircle(x,y+rad*.10f,rad*.055f,p);
    }

    private void drawLockedStage(Canvas c,float x,float y,float rad){
        p.setColor(Color.argb(185,22,18,35));c.drawCircle(x,y,rad*.73f,p);
        p.setColor(Color.rgb(118,105,139));p.setTextSize(rad*.75f);p.setTextAlign(Paint.Align.CENTER);c.drawText("?",x,y+rad*.25f,p);
        Paint lock=new Paint(Paint.ANTI_ALIAS_FLAG);lock.setStyle(Paint.Style.STROKE);lock.setStrokeWidth(rad*.10f);lock.setColor(Color.rgb(92,80,111));
        c.drawArc(x-rad*.20f,y-rad*.75f,x+rad*.20f,y-rad*.25f,180,-180,false,lock);
    }

    private void handleWorldMapTap(float x,float y){
        float w=getWidth(),h=getHeight();
        if(y>h*.89f){
            if(x<w*.38f&&mapWorld>0)mapWorld--;
            else if(x>w*.62f&&mapWorld<9)mapWorld++;
            invalidate();return;
        }
        for(int i=0;i<12;i++){
            float dx=x-mapNodeX(i,w),dy=y-mapNodeY(i,h);
            int stage=mapWorld*12+i+1;
            if(dx*dx+dy*dy<w*w*.008f){
                if(stage>highestLevel){message("ประตูนี้ยังล็อกอยู่ ผ่านด่านก่อนหน้าให้สำเร็จก่อน");return;}
                level=stage;tutorialStage=level==1&&!progress.getBoolean("tutorial_complete",false)?0:-1;
                worldMap=false;newLevel();missionBrief=level>1;invalidate();return;
            }
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
        if(worldMap){drawWorldMap(c,w,h);postInvalidateOnAnimation();return;}
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
        boolean fourGoals=iceInitial>0;
        int[] activeGoals=activeGoalTypes();
        for(int i=0;i<(fourGoals?4:3);i++){
            float gx=fourGoals?w*(.285f+.11f*i):w*(.316f+.148f*i);
            int ghostType=i<3?activeGoals[i]:-1;
            if(i<3)drawGhost(c,gx,h*.098f,w*(fourGoals?.033f:.038f),colors[ghostType],ghostType,false);
            else{
                p.setTextSize(w*.050f);p.setColor(Color.rgb(179,235,255));
                c.drawText("❄",gx,h*.108f,p);
            }
            boolean complete=i<3?collected[ghostType]>=goals[ghostType]:iceLeft==0;
            p.setColor(complete?Color.rgb(107,236,94):Color.WHITE);
            p.setTextSize(w*(fourGoals?.020f:.024f));p.setTextAlign(Paint.Align.CENTER);
            String count=i<3?(complete?"✓ "+goals[ghostType]+"/"+goals[ghostType]:collected[ghostType]+"/"+goals[ghostType]):
                (complete?"✓ 0/"+iceInitial:iceLeft+"/"+iceInitial);
            c.drawText(count,gx,h*.145f,p);
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
        float ratio=Math.min(1f,score/(float)Math.max(1,target));
        p.setShader(new LinearGradient(progL,0,progR,0,Color.rgb(75,193,66),Color.rgb(161,250,84),Shader.TileMode.CLAMP));
        c.drawRoundRect(progL,progY,progL+(progR-progL)*ratio,progY+w*.03f,w*.02f,w*.02f,p);p.setShader(null);
        for(int i=1;i<=3;i++){
            p.setColor(ratio>=i/3f?Color.rgb(255,214,72):Color.rgb(103,90,139));
            p.setTextSize(w*.037f);c.drawText("★",progL+(progR-progL)*i/3f,progY+w*.03f,p);
        }
        boardX=w*.025f; boardY=h*.208f; cell=(w-2*boardX)/N;
        drawThaiBoardFrame(c);
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
        c.drawText("ท้าทายขึ้น! วางแผนให้ดี ♥",w*.52f,h*.934f,p);
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
        int[] activeGoals=activeGoalTypes();
        for(int i=0;i<activeGoals.length;i++){
            int type=activeGoals[i];
            float gx=w*(.27f+.23f*i),gy=top+h*.215f;
            int save=c.save();c.scale(pulse,pulse,gx,gy);
            drawGhost(c,gx,gy,w*.063f,colors[type],type,false);c.restoreToCount(save);
            p.setColor(Color.WHITE);p.setTextSize(w*.037f);
            c.drawText("เก็บ "+goals[type]+" ตัว",gx,gy+h*.075f,p);
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
            c.drawText("จับ 4, 5 หรือสี่เหลี่ยม 2×2 รับไอเท็ม",w/2,t+h*.282f,p);
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
                      kind==3?Color.rgb(255,213,96):kind==5?Color.rgb(75,232,255):Color.rgb(208,128,255);
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
            }else if(kind==5){
                fx.setStrokeWidth(cell*(boost>1?.16f:.10f));
                for(int d=0;d<4;d++){
                    double a=d*Math.PI/2;
                    float travel=cell*(.55f+progress*(boost>1?4.8f:3.3f));
                    float tx=x+(float)Math.cos(a)*travel,ty=y+(float)Math.sin(a)*travel;
                    c.drawLine(x,y,tx,ty,fx);
                    p.setColor(Color.argb((int)(235*pulse),255,245,177));
                    c.drawCircle(tx,ty,cell*(boost>1?.23f:.17f),p);
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
            int glyphs=kind==3?12:kind==4?8:kind==5?4:6;
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

    private void drawThaiBoardFrame(Canvas c){
        int world=((level-1)/12)%5;
        int[] inner={Color.rgb(39,27,79),Color.rgb(22,55,76),Color.rgb(72,27,62),Color.rgb(28,66,55),Color.rgb(64,44,25)};
        int[] jewel={Color.rgb(190,89,255),Color.rgb(53,205,255),Color.rgb(255,83,180),Color.rgb(67,230,160),Color.rgb(255,153,54)};
        // Build the backdrop from active cells, so every board has its own true silhouette.
        for(int r=0;r<N;r++)for(int col=0;col<N;col++)if(!blocked[r][col]){
            float x=boardX+col*cell,y=boardY+r*cell;
            p.setColor(inner[world]);p.setShadowLayer(cell*.20f,0,cell*.05f,Color.argb(210,8,3,25));
            c.drawRoundRect(x-cell*.015f,y-cell*.015f,x+cell*1.015f,y+cell*1.015f,cell*.12f,cell*.12f,p);p.clearShadowLayer();
        }
        Paint gold=new Paint(Paint.ANTI_ALIAS_FLAG);gold.setStyle(Paint.Style.STROKE);gold.setStrokeCap(Paint.Cap.ROUND);
        gold.setStrokeWidth(cell*.065f);gold.setShader(new LinearGradient(boardX,boardY,boardX+N*cell,boardY+N*cell,
            Color.rgb(255,239,145),Color.rgb(177,83,255),Shader.TileMode.MIRROR));
        gold.setShadowLayer(cell*.15f,0,0,jewel[world]);
        for(int r=0;r<N;r++)for(int col=0;col<N;col++)if(!blocked[r][col]){
            float x=boardX+col*cell,y=boardY+r*cell,in=cell*.015f;
            if(r==0||blocked[r-1][col])c.drawLine(x+in,y+in,x+cell-in,y+in,gold);
            if(r==N-1||blocked[r+1][col])c.drawLine(x+in,y+cell-in,x+cell-in,y+cell-in,gold);
            if(col==0||blocked[r][col-1])c.drawLine(x+in,y+in,x+in,y+cell-in,gold);
            if(col==N-1||blocked[r][col+1])c.drawLine(x+cell-in,y+in,x+cell-in,y+cell-in,gold);
        }
        gold.clearShadowLayer();gold.setShader(null);
        // Thai kanok flames crown the exposed upper rim instead of marking unused cells.
        for(int col=0;col<N;col++){
            int first=-1;for(int r=0;r<N;r++)if(!blocked[r][col]){first=r;break;}
            if(first>=0&&(col%2==0||col==N-1))drawKanok(c,boardX+(col+.5f)*cell,boardY+first*cell,cell*.25f,jewel[world]);
        }
    }

    private void drawKanok(Canvas c,float cx,float base,float size,int glow){
        Path flame=new Path();
        flame.moveTo(cx,base+size*.18f);flame.cubicTo(cx-size*.62f,base-size*.10f,cx-size*.42f,base-size*.74f,cx,base-size);
        flame.cubicTo(cx+size*.06f,base-size*.52f,cx+size*.58f,base-size*.38f,cx+size*.36f,base+size*.12f);
        flame.cubicTo(cx+size*.18f,base-size*.04f,cx+size*.02f,base-size*.10f,cx,base+size*.18f);flame.close();
        p.setColor(Color.rgb(255,214,89));p.setShadowLayer(size*.42f,0,0,glow);c.drawPath(flame,p);p.clearShadowLayer();
        Path inner=new Path();inner.moveTo(cx,base-size*.02f);inner.cubicTo(cx-size*.18f,base-size*.28f,cx-size*.05f,base-size*.55f,cx,base-size*.70f);
        inner.cubicTo(cx+size*.22f,base-size*.38f,cx+size*.16f,base-size*.17f,cx,base-size*.02f);inner.close();
        p.setColor(Color.rgb(101,35,142));c.drawPath(inner,p);
    }

    private void drawCell(Canvas c,int r,int col){
        float x=boardX+col*cell, y=boardY+r*cell, pad=cell*.075f;
        if(blocked[r][col])return;
        float bob=(float)Math.sin((System.currentTimeMillis()-gameStart)/420.0+r*.8+col*.65)*cell*.025f;
        int theme=((level-1)/12)%4;
        int[] light={Color.argb(82,118,79,173),Color.argb(82,52,125,165),Color.argb(82,145,69,126),Color.argb(82,66,133,104)};
        int[] dark={Color.argb(58,78,52,132),Color.argb(58,30,75,126),Color.argb(58,91,39,105),Color.argb(58,38,82,74)};
        int back=((r+col)&1)==0?light[theme]:dark[theme];
        float corner=theme==1?cell*.12f:theme==2?cell*.30f:cell*.22f;
        drawRound(c,x+pad,y+pad,x+cell-pad,y+cell-pad,back,corner);
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
            drawGhostAlive(c,cx,cy,cell*.405f,type,selected,row,col);
            return;
        }
        if(kind==5){drawFourWayRocket(c,cx,cy,cell*.43f);return;}
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

    private void drawFourWayRocket(Canvas c,float cx,float cy,float rad){
        float pulse=1f+.06f*(float)Math.sin((System.currentTimeMillis()-gameStart)/150f);
        int saved=c.save();c.scale(pulse,pulse,cx,cy);
        p.setColor(Color.argb(115,64,224,255));p.setShadowLayer(rad*.55f,0,0,Color.CYAN);
        c.drawCircle(cx,cy,rad*.72f,p);p.clearShadowLayer();
        for(int i=0;i<4;i++){
            int arm=c.save();c.rotate(i*90,cx,cy);
            Path rocket=new Path();
            rocket.moveTo(cx,cy-rad*.98f);rocket.lineTo(cx-rad*.24f,cy-rad*.42f);
            rocket.lineTo(cx-rad*.18f,cy-rad*.03f);rocket.lineTo(cx+rad*.18f,cy-rad*.03f);
            rocket.lineTo(cx+rad*.24f,cy-rad*.42f);rocket.close();
            p.setShader(new LinearGradient(cx,cy-rad,cx,cy,Color.WHITE,Color.rgb(255,116,63),Shader.TileMode.CLAMP));
            c.drawPath(rocket,p);p.setShader(null);
            p.setColor(Color.rgb(255,224,76));c.drawCircle(cx,cy-rad*.48f,rad*.10f,p);
            p.setColor(Color.argb(220,94,238,255));
            Path flame=new Path();flame.moveTo(cx-rad*.13f,cy-rad*.02f);flame.lineTo(cx,cy+rad*.27f);flame.lineTo(cx+rad*.13f,cy-rad*.02f);flame.close();c.drawPath(flame,p);
            c.restoreToCount(arm);
        }
        p.setColor(Color.rgb(104,45,190));p.setShadowLayer(rad*.22f,0,0,Color.MAGENTA);c.drawCircle(cx,cy,rad*.25f,p);p.clearShadowLayer();
        p.setColor(Color.WHITE);c.drawCircle(cx-rad*.07f,cy-rad*.07f,rad*.065f,p);
        c.restoreToCount(saved);
    }

    private void drawGhostAlive(Canvas c,float cx,float cy,float rad,int type,boolean selected,int row,int col){
        long now=System.currentTimeMillis();
        boolean reacting=row==reactionR&&col==reactionC&&now<reactionUntil;
        if(ghostReactions!=null&&!ghostReactions.isRecycled()){
            int frame=reacting?Math.min(3,1+(int)((now-reactionStart)/320L)):0;
            int sw=ghostReactions.getWidth()/3,sh=ghostReactions.getHeight()/4;
            int spriteType=type%3;
            Rect source=new Rect(spriteType*sw,frame*sh,(spriteType+1)*sw,(frame+1)*sh);
            float wiggle=reacting?(float)Math.sin((now-reactionStart)/55f)*9f:0f;
            float squash=reacting?1f+.07f*(float)Math.sin((now-reactionStart)/70f):1f;
            int save=c.save();
            c.rotate(wiggle,cx,cy);
            c.scale(2f-squash,squash,cx,cy);
            RectF dest=new RectF(cx-rad*1.27f,cy-rad*1.29f,cx+rad*1.27f,cy+rad*1.29f);
            spritePaint.setAlpha(255);
            if(type==3)spritePaint.setColorFilter(new PorterDuffColorFilter(Color.rgb(255,146,63),PorterDuff.Mode.MULTIPLY));
            c.drawBitmap(ghostReactions,source,dest,spritePaint);
            spritePaint.setColorFilter(null);
            c.restoreToCount(save);
            if(type==3)drawFireGhostAccents(c,cx,cy,rad);
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
            int type=Math.max(0,face)%3;
            int sw=ghostReactions.getWidth()/3,sh=ghostReactions.getHeight()/4;
            Rect source=new Rect(type*sw,0,(type+1)*sw,sh);
            RectF dest=new RectF(cx-rad*1.24f,cy-rad*1.25f,cx+rad*1.24f,cy+rad*1.25f);
            if(face==3)spritePaint.setColorFilter(new PorterDuffColorFilter(Color.rgb(255,146,63),PorterDuff.Mode.MULTIPLY));
            c.drawBitmap(ghostReactions,source,dest,spritePaint);spritePaint.setColorFilter(null);
            if(face==3)drawFireGhostAccents(c,cx,cy,rad);
            return;
        }
        if(ghostSheet!=null&&!ghostSheet.isRecycled()){
            int type=Math.max(0,face)%3;
            float sheetCell=ghostSheet.getWidth()/3f;
            Rect source=new Rect((int)(type*sheetCell),0,(int)((type+1)*sheetCell),ghostSheet.getHeight());
            RectF dest=new RectF(cx-rad*1.24f,cy-rad*1.22f,cx+rad*1.24f,cy+rad*1.22f);
            if(face==3)spritePaint.setColorFilter(new PorterDuffColorFilter(Color.rgb(255,146,63),PorterDuff.Mode.MULTIPLY));
            c.drawBitmap(ghostSheet,source,dest,spritePaint);spritePaint.setColorFilter(null);
            if(face==3)drawFireGhostAccents(c,cx,cy,rad);
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

    private void drawFireGhostAccents(Canvas c,float cx,float cy,float rad){
        p.setShader(new LinearGradient(cx,cy-rad*1.45f,cx,cy-rad*.78f,
            Color.rgb(255,238,92),Color.rgb(255,64,35),Shader.TileMode.CLAMP));
        p.setShadowLayer(rad*.24f,0,0,Color.rgb(255,92,24));
        Path flame=new Path();
        flame.moveTo(cx-rad*.58f,cy-rad*.72f);flame.quadTo(cx-rad*.48f,cy-rad*1.35f,cx-rad*.16f,cy-rad*.94f);
        flame.quadTo(cx,cy-rad*1.58f,cx+rad*.18f,cy-rad*.94f);
        flame.quadTo(cx+rad*.50f,cy-rad*1.35f,cx+rad*.58f,cy-rad*.72f);flame.close();
        c.drawPath(flame,p);p.clearShadowLayer();p.setShader(null);
        p.setColor(Color.rgb(91,245,255));p.setShadowLayer(rad*.18f,0,0,Color.CYAN);
        c.drawCircle(cx,cy-rad*.92f,rad*.10f,p);p.clearShadowLayer();
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
        if(won&&System.currentTimeMillis()-victoryStart<VICTORY_DANCE_MS){
            drawVictoryDance(c,w,h,System.currentTimeMillis()-victoryStart);return;
        }
        if(lost&&System.currentTimeMillis()-lostStart<FAILURE_LAUGH_MS){
            drawFailureLaugh(c,w,h,System.currentTimeMillis()-lostStart);return;
        }
        p.setColor(Color.argb(218,10,5,30));c.drawRect(0,0,w,h,p);
        float l=w*.08f,r=w*.92f,t=won?h*.20f:lost?h*.13f:h*.30f,b=won?h*.79f:lost?h*.93f:
            paused&&getContext() instanceof MainActivity&&((MainActivity)getContext()).needsPrivacyOptions()?h*.79f:h*.68f;
        panel(c,l,t,r,b,Color.rgb(68,35,112));
        p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.WHITE);p.setTextSize(w*.075f);
        c.drawText(paused?"PAUSED":won?"รางวัลผ่านด่าน":"ยังไม่ผ่านด่าน",w/2,t+h*.068f,p);
        if(won){
            p.setColor(Color.rgb(255,216,91));p.setTextSize(w*.083f);
            int stars=starsEarned();
            c.drawText((stars>=1?"★":"☆")+" "+(stars>=2?"★":"☆")+" "+(stars>=3?"★":"☆"),w/2,t+h*.118f,p);
            long elapsed=System.currentTimeMillis()-victoryStart;
            drawVictoryConfetti(c,w,h,elapsed);
            p.setColor(Color.rgb(231,219,251));p.setTextSize(w*.034f);
            c.drawText("ด่าน "+level+" สำเร็จ • คะแนน "+score,w/2,t+h*.165f,p);
            drawRewardItem(c,victoryReward,1,w*.16f,t+h*.205f,w*.68f,h*.105f);
            if(worldClearReward)drawWorldBundle(c,w*.16f,t+h*.325f,w*.68f,h*.105f);
            else if(victoryBonusCount>0)drawRewardItem(c,victoryBonus,victoryBonusCount,w*.16f,t+h*.325f,w*.68f,h*.105f);
            else{
                drawRound(c,w*.16f,t+h*.325f,w*.84f,t+h*.43f,Color.argb(100,44,29,83),w*.025f);
                p.setColor(Color.rgb(203,188,229));p.setTextSize(w*.030f);
                c.drawText("ทำคะแนน 3 ดาว รับไอเท็มเวทมนตร์เพิ่ม",w/2,t+h*.387f,p);
            }
            float pulse=.97f+.03f*(float)Math.sin(elapsed/150f);
            int save=c.save();c.scale(pulse,pulse,w/2,t+h*.50f);
            drawRound(c,w*.20f,t+h*.465f,w*.80f,t+h*.535f,Color.rgb(255,188,64),50);
            p.setColor(Color.rgb(55,25,70));p.setTextSize(w*.043f);
            c.drawText(victoryAdvancing?"กำลังโหลด...":"ไปด่านต่อไป",w/2,t+h*.512f,p);
            c.restoreToCount(save);
        }else if(paused){
            p.setTextSize(w*.12f);c.drawText("♥",w/2,t+h*.17f,p);
            p.setTextSize(w*.044f);p.setColor(Color.rgb(233,220,255));
            c.drawText("Tap continue to play",w/2,t+h*.23f,p);
            drawRound(c,w*.22f,t+h*.27f,w*.78f,t+h*.35f,Color.rgb(255,188,64),50);
            p.setColor(Color.rgb(55,25,70));p.setTextSize(w*.045f);
            c.drawText("CONTINUE",w/2,t+h*.325f,p);
            if(getContext() instanceof MainActivity&&((MainActivity)getContext()).needsPrivacyOptions()){
                drawRound(c,w*.22f,h*.70f,w*.78f,h*.765f,Color.rgb(105,73,162),40);
                p.setColor(Color.WHITE);p.setTextSize(w*.036f);
                c.drawText("PRIVACY OPTIONS",w/2,h*.745f,p);
            }
        }else{
            p.setColor(Color.rgb(233,220,255));p.setTextSize(w*.038f);
            c.drawText("เลือกเล่นต่อ หรือเริ่มด่านใหม่",w/2,t+h*.125f,p);
            drawLaughingSkullSprite(c,w/2,h*.355f,h*.205f,0f);
            drawRound(c,w*.18f,h*.47f,w*.82f,h*.55f,Color.rgb(255,188,64),45);
            p.setColor(Color.rgb(55,25,70));p.setTextSize(w*.043f);c.drawText("เริ่มด่านใหม่",w/2,h*.523f,p);
            drawRound(c,w*.18f,h*.59f,w*.82f,h*.68f,boosterCount[6]>0?Color.rgb(112,202,57):Color.rgb(92,78,112),45);
            p.setColor(Color.WHITE);p.setTextSize(w*.038f);c.drawText("ใช้ไอเท็ม +5 การย้าย  (เหลือ "+boosterCount[6]+")",w/2,h*.647f,p);
            drawRound(c,w*.18f,h*.72f,w*.82f,h*.81f,Color.rgb(96,79,214),45);
            p.setColor(Color.WHITE);p.setTextSize(w*.038f);
            c.drawText(rewardAdPending?"กำลังเตรียมโฆษณา...":"ดูโฆษณา รับ +5 การย้าย",w/2,h*.777f,p);
            p.setColor(Color.rgb(205,192,235));p.setTextSize(w*.026f);
            c.drawText("โฆษณาทดสอบ • รับรางวัลเมื่อดูจบ",w/2,h*.865f,p);
        }
    }

    private void drawFailureLaugh(Canvas c,float w,float h,long elapsed){
        float entrance=Math.min(1f,elapsed/360f);
        float bounce=1f+.055f*(float)Math.sin(elapsed/72f);
        float tilt=(float)Math.sin(elapsed/105f)*5.5f;
        float centerX=w/2+(float)Math.sin(elapsed/90f)*w*.018f;
        float centerY=boardY+cell*N*.48f;
        float size=Math.min(w*1.28f,h*.68f)*entrance*bounce;
        drawLaughingSkullSprite(c,centerX,centerY,size,tilt);
        float textPulse=1f+.06f*(float)Math.sin(elapsed/115f);
        int save=c.save();c.scale(textPulse,textPulse,w/2,h*.79f);
        p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans",Typeface.BOLD));
        p.setColor(Color.rgb(255,222,83));p.setTextSize(w*.058f);p.setShadowLayer(16,0,0,Color.rgb(104,34,183));
        c.drawText(elapsed<1250?"ฮ่า ฮ่า ฮ่า!":"เกือบผ่านแล้ว!",w/2,h*.79f,p);p.clearShadowLayer();
        c.restoreToCount(save);
    }

    private void drawLaughingSkullSprite(Canvas c,float centerX,float centerY,float size,float tilt){
        int save=c.save();c.rotate(tilt,centerX,centerY);
        if(laughingSkull!=null&&!laughingSkull.isRecycled()){
            spritePaint.setAlpha(255);
            float aspect=laughingSkull.getWidth()/(float)laughingSkull.getHeight();
            c.drawBitmap(laughingSkull,null,new RectF(centerX-size*.50f*aspect,centerY-size*.50f,
                centerX+size*.50f*aspect,centerY+size*.50f),spritePaint);
        }else{
            p.setColor(Color.rgb(246,239,220));p.setShadowLayer(size*.10f,0,0,Color.rgb(142,55,225));
            c.drawOval(centerX-size*.34f,centerY-size*.43f,centerX+size*.34f,centerY+size*.31f,p);p.clearShadowLayer();
            p.setColor(Color.rgb(55,18,65));
            c.drawOval(centerX-size*.23f,centerY-size*.18f,centerX-size*.04f,centerY+size*.02f,p);
            c.drawOval(centerX+size*.04f,centerY-size*.18f,centerX+size*.23f,centerY+size*.02f,p);
            c.drawOval(centerX-size*.22f,centerY+size*.04f,centerX+size*.22f,centerY+size*.32f,p);
            p.setColor(Color.rgb(130,247,66));p.setTextSize(size*.28f);p.setTextAlign(Paint.Align.CENTER);
            c.drawText("5",centerX-size*.43f,centerY-size*.05f,p);c.drawText("5",centerX+size*.43f,centerY+size*.08f,p);
        }
        c.restoreToCount(save);
    }

    private void drawVictoryDance(Canvas c,float w,float h,long elapsed){
        drawVictoryConfetti(c,w,h,elapsed);
        p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans",Typeface.BOLD));
        p.setColor(Color.WHITE);p.setTextSize(w*.071f);p.setShadowLayer(18,0,0,Color.rgb(126,49,211));
        c.drawText("ยินดีด้วย",w/2,h*.125f,p);
        p.setColor(Color.rgb(255,220,79));p.setTextSize(w*.048f);
        c.drawText("คุณผ่านด่าน "+level+" แล้ว!",w/2,h*.172f,p);p.clearShadowLayer();
        if(dancingSkeleton!=null&&!dancingSkeleton.isRecycled()){
            float frameProgress=(elapsed%5200L)/650f;
            int frame=(int)frameProgress%8,next=(frame+1)%8;
            float blend=frameProgress-(int)frameProgress;
            blend=blend*blend*(3f-2f*blend);
            float sw=dancingSkeleton.getWidth()/8f;
            Rect source=new Rect((int)(frame*sw),0,(int)((frame+1)*sw),dancingSkeleton.getHeight());
            Rect sourceNext=new Rect((int)(next*sw),0,(int)((next+1)*sw),dancingSkeleton.getHeight());
            float size=Math.min(w*1.25f,h*.62f),phase=elapsed/420f;
            float centerY=boardY+cell*N*.50f;
            int saved=c.save();
            float sway=(float)Math.sin(phase*.72f),settle=(float)Math.sin(phase*1.45f);
            c.translate(sway*w*.10f,Math.abs(settle)*h*.008f);
            c.rotate(sway*5.5f,w/2,centerY);
            c.skew(sway*.035f,0f);
            c.scale(1f-settle*.035f,1f+settle*.055f,w/2,centerY);
            RectF dest=new RectF(w/2-size*.42f,centerY-size*.52f,w/2+size*.42f,centerY+size*.52f);
            spritePaint.setAlpha((int)(255*(1f-blend)));
            c.drawBitmap(dancingSkeleton,source,dest,spritePaint);
            spritePaint.setAlpha((int)(255*blend));
            c.drawBitmap(dancingSkeleton,sourceNext,dest,spritePaint);
            spritePaint.setAlpha(255);
            c.restoreToCount(saved);
        }
        p.setColor(Color.WHITE);p.setTextSize(w*.035f);p.setShadowLayer(10,0,0,Color.rgb(91,36,161));
        String victoryLine=worldClearReward?(elapsed<2600?"พิชิตประตูใหญ่!":"โลกใหม่กำลังเปิด..."):
            elapsed<1700?"ฉลองชัยชนะ!":elapsed<3300?"เต้นบนกระดานเลย!":"กำลังเตรียมรางวัล...";
        c.drawText(victoryLine,w/2,h*.80f,p);p.clearShadowLayer();
    }

    private void drawRewardItem(Canvas c,int item,int amount,float x,float y,float width,float height){
        drawRound(c,x,y,x+width,y+height,Color.rgb(255,190,85),wSafe(width*.06f));
        drawRound(c,x+width*.018f,y+height*.08f,x+height*.92f,y+height*.92f,Color.rgb(104,52,166),height*.20f);
        if(boosterSheet!=null&&!boosterSheet.isRecycled()&&item>=0){
            float slice=boosterSheet.getWidth()/7f;
            Rect source=new Rect((int)(item*slice),0,(int)((item+1)*slice),boosterSheet.getHeight());
            c.drawBitmap(boosterSheet,source,new RectF(x+height*.08f,y+height*.10f,x+height*.90f,y+height*.90f),spritePaint);
        }
        String[] thai={"สลับตำแหน่ง","ค้อนทุบ","ระเบิดทั้งแถว","ระเบิดทั้งคอลัมน์","ระเบิดวิญญาณ","สายรุ้งเวทมนตร์","เพิ่ม 5 การย้าย"};
        p.setTextAlign(Paint.Align.LEFT);p.setColor(Color.rgb(62,30,75));p.setTextSize(getWidth()*.034f);
        c.drawText(item>=0?thai[item]:"ไอเท็มช่วยเหลือ",x+height*1.02f,y+height*.48f,p);
        p.setColor(Color.rgb(148,31,47));p.setTextSize(getWidth()*.040f);
        c.drawText("ได้รับ  +"+amount,x+height*1.02f,y+height*.79f,p);p.setTextAlign(Paint.Align.CENTER);
    }

    private void drawWorldBundle(Canvas c,float x,float y,float width,float height){
        drawRound(c,x,y,x+width,y+height,Color.rgb(255,213,91),wSafe(width*.06f));
        p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.rgb(91,35,118));p.setTextSize(getWidth()*.043f);
        c.drawText("♛  สมบัติโลกใหม่  ♛",x+width/2,y+height*.43f,p);
        p.setColor(Color.rgb(137,38,65));p.setTextSize(getWidth()*.029f);
        String bundle="ระเบิดแถว • คอลัมน์ • วิญญาณ • สายรุ้ง อย่างละ 1";
        if(victoryBonusCount>0)bundle+="  + โบนัส 3 ดาว";
        c.drawText(bundle,x+width/2,y+height*.76f,p);
    }

    private float wSafe(float value){return Math.max(8f,value);}

    private void drawVictoryConfetti(Canvas c,float w,float h,long elapsed){
        float fall=(elapsed%4200L)/4200f;
        int[] festive={Color.rgb(255,210,55),Color.rgb(255,75,165),Color.rgb(91,229,255),
            Color.rgb(133,238,78),Color.rgb(181,103,255)};
        for(int i=0;i<64;i++){
            float seed=(i*37%101)/101f;
            float x=(i*83%113)/113f*w+(float)Math.sin(elapsed/260f+i)*w*.018f;
            float y=((seed+fall*1.45f)%1f)*h*.70f+h*.02f;
            float turn=(float)Math.sin(elapsed/90f+i*.8f);
            p.setColor(festive[i%festive.length]);
            int save=c.save();c.rotate(turn*55f,x,y);
            c.drawRoundRect(x-w*.007f,y-h*.006f,x+w*.007f,y+h*.006f,w*.004f,w*.004f,p);
            c.restoreToCount(save);
        }
    }

    private void goToNextLevel(){
        if(victoryAdvancing)return;
        victoryAdvancing=true;invalidate();
        Runnable advance=()->{
            if(level<120)level++;
            mapWorld=(level-1)/12;worldMap=true;missionBrief=false;won=false;victoryAdvancing=false;
            newLevel();worldMap=true;
        };
        if(getContext() instanceof MainActivity)((MainActivity)getContext()).showAdBeforeNextLevel(advance);
        else advance.run();
    }

    @Override public boolean onTouchEvent(android.view.MotionEvent e){
        float x=e.getX(),y=e.getY();
        if(e.getAction()==MotionEvent.ACTION_DOWN){
            if(worldMap){touchDownX=x;touchDownY=y;return true;}
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
        if(worldMap){handleWorldMapTap(x,y);return true;}
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
            if(lost&&System.currentTimeMillis()-lostStart<FAILURE_LAUGH_MS)return true;
            if(paused&&y>getHeight()*.70f&&y<getHeight()*.77f
               &&getContext() instanceof MainActivity){
                ((MainActivity)getContext()).openPrivacyOptions();return true;
            }
            if(lost){
                if(y>getHeight()*.47f&&y<getHeight()*.55f)newLevel();
                else if(y>getHeight()*.59f&&y<getHeight()*.68f){
                    if(boosterCount[6]>0){boosterCount[6]--;continueWithMoves(5);}
                    else message("ไอเท็มเพิ่มการย้ายหมดแล้ว");
                }else if(y>getHeight()*.72f&&y<getHeight()*.81f&&!rewardAdPending){
                    rewardAdPending=true;invalidate();
                    if(getContext() instanceof MainActivity){
                        ((MainActivity)getContext()).showRewardedMoves(
                            ()->post(()->{rewardAdPending=false;continueWithMoves(5);}),
                            ()->post(()->{rewardAdPending=false;message("โฆษณายังไม่พร้อม กรุณาลองใหม่");invalidate();}));
                    }else{rewardAdPending=false;message("โฆษณายังไม่พร้อม");}
                }
                invalidate();return true;
            }
            boolean danceFinished=!won||System.currentTimeMillis()-victoryStart>=VICTORY_DANCE_MS;
            if(danceFinished&&y>(won?getHeight()*.64f:getHeight()*.57f)&&y<(won?getHeight()*.76f:getHeight()*.68f)){
                if(paused)paused=false; else if(won)goToNextLevel();
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

    private void continueWithMoves(int amount){
        lost=false;moves+=amount;rewardAdPending=false;
        message("ได้รับ +"+amount+" การย้าย สู้ต่อได้เลย!");invalidate();
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
        // A compact 2×2 block of four matching ghosts is also a valid match.
        for(int r=0;r<N-1;r++)for(int c=0;c<N-1;c++){
            int type=base(board[r][c]);
            if(type>=0&&base(board[r][c+1])==type&&base(board[r+1][c])==type&&base(board[r+1][c+1])==type){
                out.add(r*N+c);out.add(r*N+c+1);out.add((r+1)*N+c);out.add((r+1)*N+c+1);
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
        }else if(kind==5){
            int[][] dirs={{-1,0},{1,0},{0,-1},{0,1}};
            for(int[] dir:dirs){
                int hitR=-1,hitC=-1;
                for(int step=1;step<N;step++){
                    int rr=row+dir[0]*step,cc=col+dir[1]*step;
                    if(rr<0||rr>=N||cc<0||cc>=N||blocked[rr][cc])break;
                    if(board[rr][cc]>=0){hitR=rr;hitC=cc;if(step>=2)break;}
                }
                if(hitR>=0){
                    hits.add(hitR*N+hitC);
                    if(multiplier>1)for(int rr=Math.max(0,hitR-1);rr<=Math.min(N-1,hitR+1);rr++)
                        for(int cc=Math.max(0,hitC-1);cc<=Math.min(N-1,hitC+1);cc++)if(!blocked[rr][cc])hits.add(rr*N+cc);
                }
            }
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
        // Four ghosts in a square forge the exclusive four-direction rocket.
        if(best<5)for(int r=0;r<N-1;r++)for(int c=0;c<N-1;c++){
            int type=base(board[r][c]);
            if(type>=0&&base(board[r][c+1])==type&&base(board[r+1][c])==type&&base(board[r+1][c+1])==type){
                int pickR=(preferredR>=r&&preferredR<=r+1&&preferredC>=c&&preferredC<=c+1)?preferredR:r;
                int pickC=(preferredR>=r&&preferredR<=r+1&&preferredC>=c&&preferredC<=c+1)?preferredC:c;
                return new int[]{pickR,pickC,5};
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
                message(reward[2]==5?"Four-way rocket unlocked!":reward[2]==4?"Ghost burst unlocked!":reward[2]==3?"Rainbow ghost unlocked!":reward[2]==1?"Row blast unlocked!":"Column blast unlocked!");
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
            board[write][c]=rng.nextInt(availableTypes());
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
        for(int r=0;r<N;r++)for(int c=0;c<N;c++)if(!blocked[r][c])list.add(board[r][c]<0?rng.nextInt(availableTypes()):board[r][c]);
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

    private int starsEarned(){
        if(score>=target)return 3;
        if(score>=target*2/3)return 2;
        if(score>=target/3)return 1;
        return 0;
    }

    private void checkEnd(){
        boolean objectivesMet=iceLeft==0;
        for(int i=0;i<TYPES;i++)if(goals[i]>0&&collected[i]<goals[i])objectivesMet=false;
        if(!won&&objectivesMet){
            won=true;victoryStart=System.currentTimeMillis();
            victoryReward=rng.nextInt(7);victoryBonus=-1;victoryBonusCount=0;worldClearReward=level%12==0;
            boosterCount[victoryReward]++;
            int earnedStars=starsEarned();
            boolean firstThreeStar=earnedStars==3&&!progress.getBoolean("three_star_"+level,false);
            if(firstThreeStar){
                victoryBonus=2+rng.nextInt(4);victoryBonusCount=2;
                boosterCount[victoryBonus]+=victoryBonusCount;
                progress.edit().putBoolean("three_star_"+level,true).apply();
            }
            if(worldClearReward){
                for(int item=2;item<=5;item++)boosterCount[item]++;
            }else if(level%3==0){
                if(victoryBonus<0)victoryBonus=2+rng.nextInt(4);
                boosterCount[victoryBonus]++;victoryBonusCount++;
            }
            if(getContext() instanceof MainActivity)((MainActivity)getContext()).onLevelCompleted(level);
            highestLevel=Math.min(120,Math.max(highestLevel,level+1));
            progress.edit().putInt("highest_level",highestLevel).apply();
            for(int i=0;i<100;i++)sparks.add(new Spark(rng.nextFloat()*getWidth(),getHeight()*.25f,
                (rng.nextFloat()-.5f)*5f,rng.nextFloat()*-5f,.7f+rng.nextFloat(),4+rng.nextFloat()*7f,colors[i%TYPES]));
            performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            message(firstThreeStar?"3 STARS! +2 magic boosters!":"Level complete! Free booster earned.");
        }
        else if(moves<=0&&!lost){lost=true;lostStart=System.currentTimeMillis();performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);}
    }

    private void message(String s){toast=s;toastUntil=System.currentTimeMillis()+2300;}
    private void drawRound(Canvas c,float l,float t,float r,float b,int color,float rad){
        p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawRoundRect(l,t,r,b,rad,rad,p);
    }
}
