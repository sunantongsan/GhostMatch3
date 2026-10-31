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
    private static final String TEST_BANNER_ID="ca-app-pub-3940256099942544/6300978111";
    private AdventureView gameView;
    private GameAudio gameAudio;
    private ConsentInformation consentInformation;
    private InterstitialAd interstitial;
    private RewardedAd rewardedAd;
    private boolean adsInitialized=false, adLoading=false, rewardLoading=false, privacyOptionsRequired=false;
    private android.widget.FrameLayout rootLayout;
    private android.widget.LinearLayout bannerSlot;
    private com.google.android.gms.ads.AdView bannerAd;
    private boolean bannerLoaded=false, activityResumed=false, interstitialDue=false;
    private int completedSinceAd=0;
    private boolean underAgeOfConsent=true;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(18,10,46));
        getWindow().setNavigationBarColor(Color.rgb(18,10,46));
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        showWelcome();
    }

    private void startGame(){
        BoardPatch.load(this);
        gameAudio=new GameAudio(this);
        gameAudio.setActive(activityResumed);
        gameView=new AdventureView(this);
        rootLayout=new android.widget.FrameLayout(this);
        rootLayout.addView(gameView,new android.widget.FrameLayout.LayoutParams(-1,-1));
        setContentView(rootLayout);
        completedSinceAd=getSharedPreferences("ad_frequency",MODE_PRIVATE).getInt("completed",0)%2;
        requestPlayerAge();
    }

    private void showWelcome(){
        android.widget.FrameLayout screen=new android.widget.FrameLayout(this);
        android.widget.ImageView background=new android.widget.ImageView(this);
        background.setImageResource(R.drawable.ghost_wai_welcome);
        background.setContentDescription("ผีน้อยยกมือไหว้ต้อนรับสู่อาณาจักรผี");
        background.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        screen.addView(background,new android.widget.FrameLayout.LayoutParams(-1,-1));
        android.widget.TextView title=new android.widget.TextView(this);
        title.setText("GHOST\nMATCH 3");title.setTextSize(38);title.setTextColor(0xFFFFD775);
        title.setGravity(Gravity.CENTER);title.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));
        title.setShadowLayer(dp(8),0,dp(3),0xFF24083F);
        android.widget.FrameLayout.LayoutParams titleLayout=new android.widget.FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        titleLayout.topMargin=dp(36);screen.addView(title,titleLayout);
        android.widget.LinearLayout panel=new android.widget.LinearLayout(this);
        panel.setOrientation(android.widget.LinearLayout.VERTICAL);panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(20),dp(12),dp(20),dp(8));
        android.graphics.drawable.GradientDrawable panelBackground=new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xD928123F,0xEF120826});
        panelBackground.setCornerRadius(dp(24));panelBackground.setStroke(dp(1),0xFFBA8CDA);panel.setBackground(panelBackground);
        android.widget.FrameLayout.LayoutParams panelLayout=new android.widget.FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        panelLayout.setMargins(dp(24),0,dp(24),dp(24));screen.addView(panel,panelLayout);
        screen.setOnApplyWindowInsetsListener((v,insets)->{
            titleLayout.topMargin=dp(24)+insets.getSystemWindowInsetTop();title.setLayoutParams(titleLayout);
            panelLayout.bottomMargin=dp(16)+insets.getSystemWindowInsetBottom();panel.setLayoutParams(panelLayout);
            return insets;
        });
        android.widget.TextView greeting=new android.widget.TextView(this);
        greeting.setText("ยินดีต้อนรับสู่อาณาจักรผี");greeting.setTextSize(18);greeting.setTextColor(0xFFFFD775);
        greeting.setGravity(Gravity.CENTER);greeting.setTypeface(null,Typeface.BOLD);panel.addView(greeting);
        android.widget.TextView status=new android.widget.TextView(this);
        status.setTextSize(13);status.setPadding(0,dp(8),0,dp(2));
        status.setTextColor(Color.WHITE);status.setGravity(Gravity.CENTER);panel.addView(status);
        android.widget.ProgressBar bar=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFCA7BFF));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF503167));
        panel.addView(bar,new android.widget.LinearLayout.LayoutParams(-1,dp(20)));
        android.widget.Button play=new android.widget.Button(this);play.setText("เข้าเกม");play.setTextSize(22);play.setTextColor(0xFF35154E);
        play.setTypeface(null,Typeface.BOLD);play.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFCD69));play.setEnabled(false);
        panel.addView(play,new android.widget.LinearLayout.LayoutParams(-1,dp(56)));
        android.widget.Button retry=new android.widget.Button(this);retry.setText("ตรวจสอบอีกครั้ง");retry.setTextColor(0xFFE8D1FF);retry.setBackgroundColor(Color.TRANSPARENT);retry.setVisibility(View.GONE);panel.addView(retry);
        Runnable check=()->{
            play.setEnabled(false);retry.setVisibility(View.GONE);bar.setIndeterminate(true);
            status.setText("กำลังตรวจสอบแพตช์…");
            new Thread(()->{
                String result=ContentPatch.update(this,percent->runOnUiThread(()->{
                    if(isDestroyed()||isFinishing())return;
                    bar.setIndeterminate(false);bar.setProgress(percent);status.setText("กำลังโหลดแพตช์ "+percent+"%");
                }));
                GuardianStory.update(this);
                BoardPatch.update(this);
                runOnUiThread(()->{if(isDestroyed()||isFinishing())return;
                    bar.setIndeterminate(false);bar.setProgress(result.startsWith("ตรวจสอบแพตช์ไม่ได้")?0:100);status.setText(result);
                    play.setEnabled(true);retry.setVisibility(View.VISIBLE);
                });
            },"ghost-content-update").start();
        };
        play.setOnClickListener(v->{play.setEnabled(false);startGame();});
        retry.setOnClickListener(v->check.run());setContentView(screen);check.run();
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
            else {interstitial=null;rewardedAd=null;destroyBanner();}
        });
    }

    private void initializeAds(){
        if(adsInitialized){loadBanner();return;}
        adsInitialized=true;
        MobileAds.initialize(this,status->runOnUiThread(()->{if(isDestroyed()||isFinishing())return;loadNextAd();loadRewardedAd();loadBanner();}));
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

    void guardianMood(float danger,boolean scene){if(gameAudio!=null)gameAudio.guardianMood(danger,scene);}
    void playSound(int kind){if(gameAudio!=null)gameAudio.play(kind);}
    boolean soundEnabled(){return gameAudio!=null&&gameAudio.isEnabled();}
    void toggleSound(){if(gameAudio!=null){gameAudio.toggle();gameAudio.play(GameAudio.GHOST);}}

    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}

    private void loadBanner(){
        if(bannerAd!=null||!adsInitialized||consentInformation==null||!consentInformation.canRequestAds())return;
        bannerSlot=new android.widget.LinearLayout(this);
        bannerSlot.setOrientation(android.widget.LinearLayout.VERTICAL);
        bannerSlot.setGravity(Gravity.CENTER);
        bannerSlot.setBackgroundColor(Color.rgb(22,10,46));
        bannerSlot.setVisibility(View.GONE);
        android.widget.TextView label=new android.widget.TextView(this);
        label.setText("โฆษณา");label.setTextSize(10);label.setTextColor(Color.LTGRAY);
        label.setGravity(Gravity.CENTER);
        bannerSlot.addView(label,new android.widget.LinearLayout.LayoutParams(-1,dp(16)));
        bannerAd=new com.google.android.gms.ads.AdView(this);
        bannerAd.setAdSize(com.google.android.gms.ads.AdSize.BANNER);
        bannerAd.setAdUnitId(TEST_BANNER_ID);
        bannerSlot.addView(bannerAd,new android.widget.LinearLayout.LayoutParams(dp(320),dp(50)));
        android.widget.FrameLayout.LayoutParams params=new android.widget.FrameLayout.LayoutParams(-1,dp(66),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        params.bottomMargin=dp(8);
        rootLayout.addView(bannerSlot,params);
        bannerAd.setAdListener(new com.google.android.gms.ads.AdListener(){
            @Override public void onAdLoaded(){bannerLoaded=true;gameView.invalidate();}
            @Override public void onAdFailedToLoad(LoadAdError error){
                bannerLoaded=false;if(bannerSlot!=null)bannerSlot.setVisibility(View.GONE);
            }
        });
        bannerAd.loadAd(new AdRequest.Builder().build());
    }

    void updateBannerVisibility(boolean gameplay,float controlsBottom){
        if(bannerSlot==null)return;
        // Keep 16dp clear of controls; omit the banner on screens too short or narrow.
        boolean fits=gameView.getWidth()>=dp(320)&&controlsBottom+dp(16)<=gameView.getHeight()-dp(74);
        boolean allowed=consentInformation!=null&&consentInformation.canRequestAds();
        int visibility=gameplay&&fits&&allowed&&bannerLoaded&&activityResumed?View.VISIBLE:View.GONE;
        if(bannerSlot.getVisibility()!=visibility)bannerSlot.post(()->{
            if(bannerSlot!=null)bannerSlot.setVisibility(visibility);
        });
    }

    private void destroyBanner(){
        bannerLoaded=false;
        if(bannerAd!=null){bannerAd.destroy();bannerAd=null;}
        if(bannerSlot!=null){rootLayout.removeView(bannerSlot);bannerSlot=null;}
    }

    @Override protected void onResume(){super.onResume();activityResumed=true;if(gameAudio!=null)gameAudio.setActive(true);if(bannerAd!=null)bannerAd.resume();if(gameView!=null){gameView.setActive(true);gameView.invalidate();}}
    @Override protected void onPause(){activityResumed=false;if(gameView!=null)gameView.setActive(false);if(gameAudio!=null)gameAudio.setActive(false);if(bannerAd!=null)bannerAd.pause();if(bannerSlot!=null)bannerSlot.setVisibility(View.GONE);super.onPause();}
    @Override protected void onDestroy(){if(gameAudio!=null)gameAudio.release();destroyBanner();super.onDestroy();}

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
        // Count successful clears, not level numbers or failed attempts. Persist across sessions.
        completedSinceAd=(completedSinceAd+1)%2;
        getSharedPreferences("ad_frequency",MODE_PRIVATE).edit().putInt("completed",completedSinceAd).apply();
        interstitialDue=completedSinceAd==0;
        // Leave the celebration uninterrupted; show only on the next-level transition.
        loadNextAd();
    }

    void showAdBeforeNextLevel(Runnable continueToLevel){
        if(!interstitialDue){continueToLevel.run();return;}
        interstitialDue=false; // Never queue a backlog if an ad is unavailable.
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
