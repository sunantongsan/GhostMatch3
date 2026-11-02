package com.sunantongsan.ghostmatch3;

import android.speech.tts.TextToSpeech;
import java.util.Locale;

/** Device Thai voice; text remains visible if a Thai voice is not installed. */
final class StorySpeech {
    private TextToSpeech engine;
    private boolean ready, released, active=true;
    private String pending;
    private final MainActivity host;
    StorySpeech(MainActivity host) {
        this.host=host;
        engine=new TextToSpeech(host,status->host.runOnUiThread(()->{
            if(released||engine==null||status!=TextToSpeech.SUCCESS)return;
            int language=engine.setLanguage(Locale.forLanguageTag("th-TH"));
            ready=language>=TextToSpeech.LANG_AVAILABLE;
            engine.setPitch(1.12f);engine.setSpeechRate(.94f);
            if(pending!=null){String text=pending;pending=null;say(text);}
        }));
    }
    void say(String text){
        if(released||!active||!host.soundEnabled())return;
        if(!ready){pending=text;return;}
        engine.speak(text,TextToSpeech.QUEUE_FLUSH,null,"serjoy-story");
    }
    void setActive(boolean value){active=value;if(!value){pending=null;if(engine!=null)engine.stop();}}
    void stop(){pending=null;if(engine!=null)engine.stop();}
    void release(){released=true;pending=null;if(engine!=null){engine.stop();engine.shutdown();}}
}
