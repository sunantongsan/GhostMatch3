package com.sunantongsan.ghostmatch3;

/** History only exposes completed questions and never mutates the live quiz. */
final class QuizHistory {
    static int count(QuizProgress p){
        return Math.min(p.total,p.index+(p.phase==QuizProgress.Phase.CORRECT?1:0));
    }
    static int clamp(int requested,int count){return Math.max(0,Math.min(requested,count-1));}
}
