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
    // Google's official SAMPLE identifiers: test impressions never earn money.
    private static final boolean TEST_ADS=true;
    private static final String TEST_REWARDED_ID="ca-app-pub-3940256099942544/5224354917";
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
    private Thread patchWorker;
    private android.widget.Button enterGame;
    private void showWelcome(){
        android.widget.LinearLayout welcome=new android.widget.LinearLayout(this);
        welcome.setOrientation(android.widget.LinearLayout.VERTICAL);welcome.setGravity(Gravity.CENTER);welcome.setPadding(32,40,32,40);welcome.setBackgroundColor(0xFFFFFBF0);
        android.widget.ImageView hero=new android.widget.ImageView(this);hero.setAdjustViewBounds(true);hero.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        try(java.io.InputStream scene=getAssets().open("ser-joy-neighborhood.webp");java.io.InputStream actor=getAssets().open("ser-joy-fullbody.webp")){
            android.graphics.Bitmap bg=android.graphics.BitmapFactory.decodeStream(scene),sheet=android.graphics.BitmapFactory.decodeStream(actor);
            android.graphics.drawable.BitmapDrawable backdrop=new android.graphics.drawable.BitmapDrawable(getResources(),bg);backdrop.setGravity(Gravity.FILL);welcome.setBackground(backdrop);
            hero.setImageBitmap(android.graphics.Bitmap.createBitmap(sheet,0,0,sheet.getWidth()/4,420));sheet.recycle();
        }catch(java.io.IOException e){hero.setImageResource(R.drawable.app_icon);}
        int heroSize=Math.round(210*getResources().getDisplayMetrics().density);
        welcome.addView(hero,new android.widget.LinearLayout.LayoutParams(heroSize,Math.round(heroSize*1.33f)));
        android.widget.TextView title=new android.widget.TextView(this);title.setText("ท่านเซ่อจ่อย");title.setTextSize(34);title.setBackgroundColor(0xDFFFF8E8);title.setTextColor(0xFF24212B);title.setGravity(Gravity.CENTER);title.setTypeface(null,Typeface.BOLD);welcome.addView(title);
        android.widget.TextView sub=new android.widget.TextView(this);sub.setText("พักเรื่องเครียด…แล้วมาโดนมุกหลอกกัน!");sub.setTextSize(17);sub.setBackgroundColor(0xDFFFF8E8);sub.setGravity(Gravity.CENTER);sub.setPadding(0,20,0,30);welcome.addView(sub);
        android.widget.ProgressBar bar=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);welcome.addView(bar,new android.widget.LinearLayout.LayoutParams(-1,20));
        android.widget.TextView status=new android.widget.TextView(this);status.setText("เตรียมเสียงหัวเราะ…");status.setTextSize(16);status.setBackgroundColor(0xDFFFF8E8);status.setGravity(Gravity.CENTER);status.setPadding(0,24,0,24);welcome.addView(status);
        enterGame=new android.widget.Button(this);enterGame.setText("กำลังเตรียมเกม…");enterGame.setEnabled(false);welcome.addView(enterGame,new android.widget.LinearLayout.LayoutParams(-1,-2));
        enterGame.setOnClickListener(v->{if(content!=null&&gameView==null)startGame();});
        setContentView(welcome);
        patchWorker=new Thread(()->{
            try{
                JokePatch.Content loaded=JokePatch.check(getApplicationContext(),(message,percent)->runOnUiThread(()->{if(isDestroyed()||isFinishing())return;status.setText(message);bar.setProgress(percent);}));
                runOnUiThread(()->{if(isDestroyed()||isFinishing())return;content=loaded;enterGame.setText("เข้าเกม  →");enterGame.setEnabled(true);});
            }catch(Exception e){runOnUiThread(()->{if(isDestroyed()||isFinishing())return;status.setText("ข้อมูลเกมไม่สมบูรณ์ กรุณาเปิดเกมใหม่");enterGame.setText("ลองอีกครั้ง");enterGame.setEnabled(true);enterGame.setOnClickListener(v->recreate());});}
        },"ser-joy-patch");patchWorker.start();
    }

    private void startGame(){
        gameAudio=new GameAudio(this);
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

    @Override protected void onResume(){super.onResume();activityResumed=true;if(storySpeech!=null)storySpeech.setActive(true);if(gameAudio!=null)gameAudio.setActive(true);if(gameView!=null)gameView.setActive(true);flushQuizAdResult();}
    @Override protected void onPause(){activityResumed=false;if(storySpeech!=null)storySpeech.setActive(false);if(gameView!=null)gameView.setActive(false);if(gameAudio!=null)gameAudio.setActive(false);super.onPause();}
    @Override protected void onDestroy(){pendingQuizAdResult=null;if(storySpeech!=null)storySpeech.release();if(gameView!=null)gameView.release();if(gameAudio!=null)gameAudio.release();super.onDestroy();}

    private boolean canRequestAds(){return TEST_ADS||(consentInformation!=null&&consentInformation.canRequestAds());}

    private void loadRewardedAd(){
        if(!adsInitialized||!canRequestAds()
            ||rewardLoading||rewardedAd!=null)return;
        rewardLoading=true;
        RewardedAd.load(this,TEST_REWARDED_ID,new AdRequest.Builder().build(),
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
