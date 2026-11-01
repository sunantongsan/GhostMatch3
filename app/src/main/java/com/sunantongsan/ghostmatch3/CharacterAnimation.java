package com.sunantongsan.ghostmatch3;
/** Hand-drawn keyframe timing, independent from rendering and lifecycle. */
final class CharacterAnimation {
 static int frame(QuizProgress.Phase phase,long elapsed){
  elapsed=Math.max(0,elapsed);
  if(phase==QuizProgress.Phase.CORRECT||phase==QuizProgress.Phase.COMPLETE){
   if(elapsed<650)return 8; if(elapsed<1000)return 9;if(elapsed<1250)return 10;
   return elapsed%2400<180?10:11;
  }
  if(phase==QuizProgress.Phase.AD_REQUIRED||phase==QuizProgress.Phase.BREAK_REQUIRED){
   long t=elapsed%3000;if(t<300)return 4;if(t<500)return 5;
   if(t<1800)return ((t-500)/160)%2==0?6:7;
   return t<2200?5:4;
  }
  long t=elapsed%4200;if(t<1500)return 0;if(t<1800)return 3;if(t<2700)return 1;if(t<2830)return 2;if(t<3550)return 1;return 3;
 }
}
