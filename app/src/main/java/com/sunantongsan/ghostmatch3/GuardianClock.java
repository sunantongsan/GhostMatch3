package com.sunantongsan.ghostmatch3;

/** Counts foreground play only. The View owns whether a frame is playable. */
final class GuardianClock {
    long remainingMs, frozenMs;
    final long limitMs;
    GuardianClock(int seconds){limitMs=seconds*1000L;remainingMs=limitMs;}
    void advance(long delta){
        if(delta<=0)return;
        long frozen=Math.min(delta,frozenMs);frozenMs-=frozen;
        remainingMs=Math.max(0,remainingMs-(delta-frozen));
    }
    void extend(int seconds){remainingMs=Math.min(limitMs+120000,remainingMs+seconds*1000L);}
    float danger(){return Math.max(0,Math.min(1,1-remainingMs/(float)limitMs));}
}
