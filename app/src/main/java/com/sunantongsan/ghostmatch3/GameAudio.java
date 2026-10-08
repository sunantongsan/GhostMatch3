package com.sunantongsan.ghostmatch3;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Small original synthesized effects, decoded off the UI thread. No network or microphone. */
final class GameAudio {
    static final int GHOST=0, SWAP=1, MATCH=2, MAGIC=3, WIN=4, LOSE=5, INVALID=6;
    private final SoundPool pool;
    private final int[] samples=new int[7];
    private final long[] lastPlayed=new long[7];
    private final Set<Integer> ready=new HashSet<>();
    private final Set<Integer> streams=new HashSet<>();
    private boolean released=false, active=false;
    private volatile boolean enabled;
    private final android.content.SharedPreferences preferences;

    GameAudio(Context context){
        preferences=context.getSharedPreferences("game_audio",Context.MODE_PRIVATE);
        enabled=preferences.getBoolean("enabled",true);
        pool=new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        pool.setOnLoadCompleteListener((p,id,status)->{synchronized(this){if(!released&&status==0)ready.add(id);}});
        File directory=context.getCacheDir();
        new Thread(()->{
            for(int kind=0;kind<samples.length;kind++){
                synchronized(this){if(released)return;}
                File file=new File(directory,"ghost_sfx_v1_"+kind+".wav");
                try{
                    if(!file.exists())writeWave(file,kind);
                    synchronized(this){if(released)return;samples[kind]=pool.load(file.getAbsolutePath(),1);}
                }catch(IOException ignored){/* A sound failure must never interrupt play. */}
            }
        },"ghost-sound-loader").start();
    }

    boolean isEnabled(){return enabled;}
    synchronized void toggle(){enabled=!enabled;preferences.edit().putBoolean("enabled",enabled).apply();if(!enabled)stop();}
    synchronized void setActive(boolean value){active=value;if(!value)stop();}
    private void stop(){for(int stream:streams)pool.stop(stream);streams.clear();}
    synchronized void play(int kind){
        if(released||!active||!enabled||kind<0||kind>=samples.length||!ready.contains(samples[kind]))return;
        long now=SystemClock.elapsedRealtime();
        if(now-lastPlayed[kind]<(kind==GHOST?130:80))return;
        lastPlayed[kind]=now;
        if(kind==WIN||kind==LOSE)stop();
        float volume=kind==GHOST?.32f:kind==MAGIC?.48f:.42f;
        float rate=kind==GHOST||kind==MATCH?.94f+(now%13)*.01f:1f;
        int stream=pool.play(samples[kind],volume,volume,1,0,rate);
        if(stream!=0){if(streams.size()>32)streams.clear();streams.add(stream);}
    }
    synchronized void release(){if(released)return;stop();released=true;pool.release();ready.clear();}

    private static void le(FileOutputStream out,int value,int bytes)throws IOException{
        for(int n=0;n<bytes;n++)out.write((value>>>(8*n))&255);
    }
    static void writeWave(File file,int kind)throws IOException{
        int rate=22050;
        double duration=kind==WIN?1.15:kind==LOSE?.95:kind==MAGIC?.60:kind==GHOST?.28:.20;
        int count=(int)(rate*duration);
        try(FileOutputStream out=new FileOutputStream(file)){
            out.write(new byte[]{'R','I','F','F'});le(out,36+count*2,4);
            out.write(new byte[]{'W','A','V','E','f','m','t',' '});le(out,16,4);le(out,1,2);le(out,1,2);
            le(out,rate,4);le(out,rate*2,4);le(out,2,2);le(out,16,2);
            out.write(new byte[]{'d','a','t','a'});le(out,count*2,4);
            double phase=0;
            double[] celebration={523.25,659.25,783.99,1046.50,1318.51};
            for(int i=0;i<count;i++){
                double t=i/(double)rate,u=t/duration,f;
                if(kind==GHOST)f=760+430*Math.sin(Math.PI*u)+55*Math.sin(2*Math.PI*24*t);
                else if(kind==SWAP)f=420+950*u;
                else if(kind==MATCH)f=950+600*u;
                else if(kind==MAGIC)f=300+1500*u+110*Math.sin(t*65);
                else if(kind==WIN)f=celebration[Math.min(4,(int)(u*5))];
                else if(kind==LOSE)f=620-330*u+45*Math.sin(t*45);
                else f=260-100*u;
                phase+=2*Math.PI*f/rate;
                double envelope=Math.min(1,t/.012)*Math.min(1,(duration-t)/.045);
                double sample;
                if(kind==GHOST||kind==LOSE){
                    double syllable=.35+.65*Math.pow(Math.sin(Math.PI*u*(kind==LOSE?4:2)),2);
                    sample=(Math.sin(phase)+.3*Math.sin(2*phase)+.12*Math.sin(3*phase))*syllable;
                }else if(kind==MAGIC)sample=.7*Math.sin(phase)+.2*Math.sin(phase*1.501)+.1*Math.sin(phase*2.01);
                else sample=Math.sin(phase)+.18*Math.sin(2*phase);
                if(kind==WIN)envelope*=.5+.5*Math.sin(Math.PI*((u*5)%1));
                le(out,(int)(Math.max(-1,Math.min(1,sample*.55*envelope))*32767),2);
            }
        }
    }
}
