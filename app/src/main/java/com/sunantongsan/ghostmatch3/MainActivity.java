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
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

public class MainActivity extends Activity {
    // Build variants keep development traffic on official Google sample units.
    private static final boolean TEST_ADS=BuildConfig.TEST_ADS;
    private static final String REWARDED_ID=BuildConfig.REWARDED_AD_ID;
    private SerJoyView gameView;
    private GameAudio gameAudio;
    private StorySpeech storySpeech;
    private ConsentInformation consentInformation;
    private RewardedAd rewardedAd;
    private boolean adsInitialized=false, rewardLoading=false, privacyOptionsRequired=false;
    private android.widget.FrameLayout rootLayout;
    private boolean activityResumed=false;
    private Runnable pendingQuizAdResult;
    private boolean underAgeOfConsent=true;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFFFFFBF0);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        getWindow().setNavigationBarColor(0xFFFFFBF0);
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        showWelcome();
    }

    JokePatch.Content content;
    GameAssets gameAssets;
    private Thread patchWorker;
    private android.widget.Button enterGame,updateGame;
    boolean updateAvailable=false;
    private long lastUpdateCheck=-60000;
    static Intent storeUpdateIntent(boolean playStore){
        String id="com.sunantongsan.ghostmatch3";
        Intent intent=new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(playStore?"market://details?id="+id:"https://play.google.com/store/apps/details?id="+id));
        if(playStore)intent.setPackage("com.android.vending");return intent;
    }
    void openStoreUpdate(){
        try{startActivity(storeUpdateIntent(true));}
        catch(ActivityNotFoundException e){try{startActivity(storeUpdateIntent(false));}catch(ActivityNotFoundException missing){android.widget.Toast.makeText(this,"ไม่พบ Google Play หรือเบราว์เซอร์บนเครื่อง",android.widget.Toast.LENGTH_LONG).show();}}
    }
    void showStoreUpdate(boolean available){
        if(isDestroyed()||isFinishing())return;updateAvailable=available;
        if(updateGame!=null)updateGame.setVisibility(available?View.VISIBLE:View.GONE);
    }
    private void checkStoreUpdate(){
        long now=android.os.SystemClock.elapsedRealtime();if(now-lastUpdateCheck<60000)return;lastUpdateCheck=now;
        com.google.android.play.core.appupdate.AppUpdateManagerFactory.create(this).getAppUpdateInfo()
            .addOnSuccessListener(info->showStoreUpdate(info.updateAvailability()==com.google.android.play.core.install.model.UpdateAvailability.UPDATE_AVAILABLE))
            .addOnFailureListener(error->{/* Offline or sideloaded: keep playing; store link remains in menu. */});
    }
    private android.graphics.drawable.GradientDrawable welcomeShape(int top,int bottom,int radius){
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{top,bottom});
        d.setCornerRadius(dp(radius));return d;
    }
    private void showWelcome(){
        android.widget.FrameLayout stage=new android.widget.FrameLayout(this);
        stage.setBackground(welcomeShape(0xFF291448,0xFF7840B4,0));
        WelcomeArtView hero=new WelcomeArtView(this);
        stage.addView(hero,new android.widget.FrameLayout.LayoutParams(-1,-1));
        android.widget.LinearLayout welcome=new android.widget.LinearLayout(this);
        welcome.setOrientation(android.widget.LinearLayout.VERTICAL);welcome.setGravity(Gravity.CENTER_HORIZONTAL);
        welcome.setPadding(dp(24),dp(28),dp(24),dp(20));
        android.widget.TextView eyebrow=new android.widget.TextView(this);eyebrow.setText("ยินดีต้อนรับสู่โลกของมุกกวน");eyebrow.setTextSize(14);eyebrow.setTextColor(0xFFE8D7FF);eyebrow.setGravity(Gravity.CENTER);welcome.addView(eyebrow);
        android.widget.TextView title=new android.widget.TextView(this);title.setText("Serจ่อย");title.setTextSize(46);title.setTextColor(0xFFFFD65B);title.setGravity(Gravity.CENTER);title.setTypeface(null,Typeface.BOLD);title.setShadowLayer(dp(4),0,dp(3),0xFF281044);welcome.addView(title);
        android.widget.TextView sub=new android.widget.TextView(this);sub.setText("คิดให้ไว… ระวังโดนจ่อยหลอก!");sub.setTextSize(16);sub.setTextColor(Color.WHITE);sub.setGravity(Gravity.CENTER);welcome.addView(sub);
        View spacer=new View(this);welcome.addView(spacer,new android.widget.LinearLayout.LayoutParams(1,dp(230),1));
        android.widget.LinearLayout card=new android.widget.LinearLayout(this);card.setOrientation(android.widget.LinearLayout.VERTICAL);card.setPadding(dp(20),dp(18),dp(20),dp(18));card.setBackground(welcomeShape(0xFFFFFCF5,0xFFF0E5FF,28));card.setElevation(dp(8));
        welcome.addView(card,new android.widget.LinearLayout.LayoutParams(-1,-2));
        android.widget.TextView label=new android.widget.TextView(this);label.setText("มุกกวน • ปริศนาหักมุม • เสียงหัวเราะ");label.setTextSize(14);label.setTextColor(0xFF623997);label.setGravity(Gravity.CENTER);card.addView(label);
        android.widget.TextView status=new android.widget.TextView(this);status.setText("กำลังเตรียมความสนุก…");status.setTextSize(13);status.setTextColor(0xFF665675);status.setGravity(Gravity.CENTER);status.setPadding(0,dp(12),0,dp(8));card.addView(status);
        android.widget.ProgressBar bar=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF8D50D8));card.addView(bar,new android.widget.LinearLayout.LayoutParams(-1,dp(4)));
        enterGame=new android.widget.Button(this);enterGame.setText("กำลังเตรียมเกม…");enterGame.setEnabled(false);enterGame.setAllCaps(false);enterGame.setTextSize(22);enterGame.setTypeface(null,Typeface.BOLD);enterGame.setTextColor(0xFF38204F);enterGame.setMinHeight(dp(62));
        android.graphics.drawable.GradientDrawable gold=welcomeShape(0xFFFFE582,0xFFFFBE35,22);gold.setStroke(dp(2),0xFFFFEEAC);
        enterGame.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x55FFFFFF),gold,null));enterGame.setElevation(dp(4));
        android.widget.LinearLayout.LayoutParams playParams=new android.widget.LinearLayout.LayoutParams(-1,dp(64));playParams.topMargin=dp(16);card.addView(enterGame,playParams);
        enterGame.setOnClickListener(v->{if(content!=null&&gameAssets!=null&&gameView==null){enterGame.setEnabled(false);enterGame.setText("ไปสนุกกันเลย!");enterGame.postOnAnimation(()->enterGame.post(this::startGame));}});
        updateGame=new android.widget.Button(this);updateGame.setText("มีเวอร์ชันใหม่ • อัปเดต");updateGame.setAllCaps(false);updateGame.setTextColor(0xFF623997);updateGame.setVisibility(View.GONE);updateGame.setOnClickListener(v->openStoreUpdate());card.addView(updateGame);
        android.widget.TextView footer=new android.widget.TextView(this);footer.setText("เล่นต่อได้จากข้อเดิมทุกครั้ง");footer.setTextColor(0xFFDFCCF3);footer.setTextSize(12);footer.setGravity(Gravity.CENTER);footer.setPadding(0,dp(18),0,0);welcome.addView(footer);
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.setFillViewport(true);scroll.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});scroll.addView(welcome);stage.addView(scroll,new android.widget.FrameLayout.LayoutParams(-1,-1));setContentView(stage);
        patchWorker=new Thread(()->{
            GameAudio preparedAudio=null;
            try{
                GameAssets prepared=new GameAssets(getApplicationContext());
                runOnUiThread(()->{if(!isDestroyed()&&!isFinishing())hero.setArt(prepared.story);});
                JokePatch.Content loaded=JokePatch.check(getApplicationContext(),(message,percent)->runOnUiThread(()->{if(isDestroyed()||isFinishing())return;status.setText(message);bar.setProgress(percent);}));
                preparedAudio=new GameAudio(getApplicationContext());
                GameAudio readyAudio=preparedAudio;
                runOnUiThread(()->{if(isDestroyed()||isFinishing()){readyAudio.release();return;}gameAssets=prepared;gameAudio=readyAudio;content=loaded;status.setText("พร้อมแล้ว! วันนี้จะรอดกี่มุก?");bar.setVisibility(View.GONE);enterGame.setText("เข้าเกม  ▶");enterGame.setEnabled(true);});
            }catch(Exception e){if(preparedAudio!=null)preparedAudio.release();runOnUiThread(()->{if(isDestroyed()||isFinishing())return;status.setText("เตรียมเกมไม่สำเร็จ กรุณาลองอีกครั้ง");enterGame.setText("ลองอีกครั้ง");enterGame.setEnabled(true);enterGame.setOnClickListener(v->recreate());});}
        },"ser-joy-prepare");patchWorker.start();
    }

    private void startGame(){
        if(isDestroyed()||isFinishing()||gameView!=null)return;
        gameAudio.setActive(activityResumed);
        storySpeech=new StorySpeech(this);
        gameView=new SerJoyView(this);
        gameView.setActive(activityResumed);
        rootLayout=new android.widget.FrameLayout(this);
        rootLayout.addView(gameView,new android.widget.FrameLayout.LayoutParams(-1,-1));
        setContentView(rootLayout);
        requestPlayerAge();
    }

    private void requestPlayerAge(){
        // Store only an age band locally, never a birth date or exact age.
        android.content.SharedPreferences prefs=getSharedPreferences("ad_age",MODE_PRIVATE);
        String band=prefs.getString("band","");
        if("teen".equals(band)||"adult".equals(band)){
            underAgeOfConsent=!"adult".equals(band);
            requestAdConsent();
            return;
        }
        android.widget.EditText input=new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("อายุ (ปี)");
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(3)});
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this)
            .setTitle("อายุของคุณ")
            .setMessage("กรุณาระบุอายุตามจริง เพื่อจัดการความเป็นส่วนตัวและโฆษณาให้เหมาะสม เก็บเฉพาะกลุ่มอายุไว้บนเครื่อง")
            .setView(input).setCancelable(false)
            .setNegativeButton("ออก",(d,w)->finish())
            .setNeutralButton("ความเป็นส่วนตัว",null)
            .setPositiveButton("ต่อไป",null).create();
        dialog.setOnShowListener(d->{
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->openPrivacyPolicy());
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            int age;
            try{age=Integer.parseInt(input.getText().toString().trim());}
            catch(NumberFormatException e){input.setError("กรุณาระบุอายุ");return;}
            if(age<1||age>120){input.setError("กรุณาตรวจสอบอายุ");return;}
            if(age<13){
                dialog.dismiss();
                new android.app.AlertDialog.Builder(this).setTitle("ยังไม่สามารถเข้าเล่นได้")
                    .setMessage("เกมนี้เปิดให้ผู้เล่นอายุ 13 ปีขึ้นไป")
                    .setCancelable(false).setPositiveButton("ปิดเกม",(a,b)->finish()).show();
                return;
            }
            underAgeOfConsent=age<18;
            prefs.edit().putString("band",underAgeOfConsent?"teen":"adult").apply();
            dialog.dismiss();
            requestAdConsent();
        });});
        dialog.show();
    }

    private void requestAdConsent(){
        // Target audience is 13+. Treat all minors conservatively; adults use UMP consent.
        MobileAds.setRequestConfiguration(new com.google.android.gms.ads.RequestConfiguration.Builder()
            .setTagForChildDirectedTreatment(com.google.android.gms.ads.RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE)
            .setTagForUnderAgeOfConsent(underAgeOfConsent
                ?com.google.android.gms.ads.RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE
                :com.google.android.gms.ads.RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE)
            .setMaxAdContentRating(com.google.android.gms.ads.RequestConfiguration.MAX_AD_CONTENT_RATING_G)
            .build());
        // Official demo units only: no publisher UMP configuration is needed for test impressions.
        if(TEST_ADS){initializeAds();return;}
        consentInformation=UserMessagingPlatform.getConsentInformation(this);
        consentInformation.requestConsentInfoUpdate(this,
            new ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(underAgeOfConsent).build(),
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
            else {rewardedAd=null;}
        });
    }

    private void initializeAds(){
        if(adsInitialized){loadRewardedAd();return;}
        adsInitialized=true;
        MobileAds.initialize(this,status->runOnUiThread(()->{if(isDestroyed()||isFinishing())return;loadRewardedAd();}));
    }

    void openPrivacyPolicy(){
        String text;
        try(java.io.InputStream input=getAssets().open("privacy_policy.txt")){
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            byte[] buffer=new byte[4096];int count;
            while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count);
            text=new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
        }catch(java.io.IOException e){text="นโยบายความเป็นส่วนตัว: https://raw.githubusercontent.com/sunantongsan/GhostMatch3/main/PRIVACY.txt\nติดต่อ sunan.tongsan@gmail.com";}
        android.widget.TextView content=new android.widget.TextView(this);
        content.setText(text);content.setTextSize(15);content.setPadding(dp(20),dp(12),dp(20),dp(12));
        android.text.util.Linkify.addLinks(content,android.text.util.Linkify.WEB_URLS|android.text.util.Linkify.EMAIL_ADDRESSES);
        content.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.addView(content);
        new android.app.AlertDialog.Builder(this).setTitle("ความเป็นส่วนตัว").setView(scroll).setPositiveButton("ปิด",null).show();
    }

    void sayStory(String text){if(storySpeech!=null)storySpeech.say(text);}
    void stopStorySpeech(){if(storySpeech!=null)storySpeech.stop();}
    void setBreakMusic(boolean value){if(gameAudio!=null)gameAudio.setBreakMusic(value);}
    void playSound(int kind){if(gameAudio!=null)gameAudio.play(kind);}
    boolean soundEnabled(){return gameAudio!=null&&gameAudio.isEnabled();}
    void toggleSound(){if(gameAudio!=null){gameAudio.toggle();if(!gameAudio.isEnabled())stopStorySpeech();gameAudio.play(GameAudio.GHOST);}}

    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}

    @Override protected void onResume(){super.onResume();activityResumed=true;checkStoreUpdate();if(storySpeech!=null)storySpeech.setActive(true);if(gameAudio!=null)gameAudio.setActive(true);if(gameView!=null)gameView.setActive(true);flushQuizAdResult();}
    @Override protected void onPause(){activityResumed=false;if(storySpeech!=null)storySpeech.setActive(false);if(gameView!=null)gameView.setActive(false);if(gameAudio!=null)gameAudio.setActive(false);super.onPause();}
    @Override protected void onDestroy(){pendingQuizAdResult=null;if(storySpeech!=null)storySpeech.release();if(gameView!=null)gameView.release();if(gameAudio!=null)gameAudio.release();super.onDestroy();}

    private boolean canRequestAds(){return TEST_ADS||(consentInformation!=null&&consentInformation.canRequestAds());}

    private void loadRewardedAd(){
        if(!adsInitialized||!canRequestAds()
            ||rewardLoading||rewardedAd!=null)return;
        rewardLoading=true;
        RewardedAd.load(this,REWARDED_ID,new AdRequest.Builder().build(),
            new RewardedAdLoadCallback(){
                @Override public void onAdLoaded(RewardedAd ad){rewardLoading=false;rewardedAd=ad;}
                @Override public void onAdFailedToLoad(LoadAdError error){rewardLoading=false;rewardedAd=null;}
            });
    }

    // Full-screen ad dismissal may arrive before or after Activity.onResume.
    // Consume the result once, on the resumed activity's next UI turn.
    void deliverQuizAdResult(Runnable result){
        if(isFinishing()||isDestroyed())return;
        pendingQuizAdResult=result;flushQuizAdResult();
    }
    private void flushQuizAdResult(){
        if(!activityResumed||pendingQuizAdResult==null)return;
        getWindow().getDecorView().post(()->{
            if(!activityResumed||isFinishing()||isDestroyed())return;
            Runnable result=pendingQuizAdResult;pendingQuizAdResult=null;
            if(result!=null)result.run();
        });
    }

    void showQuizReward(Runnable earned,Runnable closed,Runnable unavailable){
        if(rewardedAd==null||!canRequestAds()){
            loadRewardedAd();deliverQuizAdResult(unavailable);return;
        }
        RewardedAd ad=rewardedAd;rewardedAd=null;
        // Keep the reward token separate from dismissal; do not move screens under the ad.
        final boolean[] token={false};
        ad.setFullScreenContentCallback(new FullScreenContentCallback(){
            private boolean finished=false;
            @Override public void onAdDismissedFullScreenContent(){
                if(finished)return;finished=true;loadRewardedAd();
                deliverQuizAdResult(token[0]?earned:closed);
            }
            @Override public void onAdFailedToShowFullScreenContent(AdError error){
                if(finished)return;finished=true;loadRewardedAd();deliverQuizAdResult(unavailable);
            }
        });
        ad.show(this,rewardItem->token[0]=true);
    }
}
