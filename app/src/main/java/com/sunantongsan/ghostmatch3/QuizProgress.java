package com.sunantongsan.ghostmatch3;

/** Durable quiz gates. An ad dismissal alone never grants progression. */
final class QuizProgress {
    enum Phase { QUESTION, CORRECT, AD_REQUIRED, BREAK_REQUIRED, COMPLETE }
    int index, streak; Phase phase;
    final int total;
    QuizProgress(int total,int index,int streak,String phase) {
        this.total=total; this.index=Math.max(0,Math.min(total,index));
        this.streak=Math.max(0,Math.min(3,streak));
        try { this.phase=Phase.valueOf(phase); } catch(Exception e) { this.phase=Phase.QUESTION; }
        if(this.index==total)this.phase=Phase.COMPLETE;
        else if(this.phase==Phase.COMPLETE)this.phase=Phase.QUESTION;
    }
    boolean answer(boolean correct) {
        if(phase!=Phase.QUESTION)return false;
        if(correct){streak=0;phase=Phase.CORRECT;}
        else {streak++;phase=streak>=3?Phase.BREAK_REQUIRED:Phase.AD_REQUIRED;}
        return true;
    }
    boolean next(){if(phase!=Phase.CORRECT)return false;advance();return true;}
    boolean reward(){if(phase!=Phase.AD_REQUIRED)return false;phase=Phase.QUESTION;return true;}
    boolean finishBreak(){if(phase!=Phase.BREAK_REQUIRED)return false;streak=0;phase=Phase.QUESTION;return true;}
    private void advance(){index++;phase=index>=total?Phase.COMPLETE:Phase.QUESTION;}
}
