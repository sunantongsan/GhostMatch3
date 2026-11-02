package com.sunantongsan.ghostmatch3;
public class QuizProgressTest {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args){
  QuizProgress q=new QuizProgress(22,0,0,"QUESTION");
  for(int i=0;i<2;i++){check(q.answer(false),"accept wrong");check(!q.answer(false),"double tap ignored");check(!q.next(),"wrong cannot bypass ad");q=new QuizProgress(22,q.index,q.streak,q.phase.name());check(q.phase==QuizProgress.Phase.AD_REQUIRED,"restore gate");check(q.reward(),"earned reward permits retry");check(!q.reward(),"reward once");check(q.streak==i+1,"streak survives ads");}
  q.answer(false);check(q.phase==QuizProgress.Phase.BREAK_REQUIRED,"third wrong break");check(!q.reward(),"ad cannot bypass break");q.finishBreak();check(q.index==0&&q.streak==0,"break returns to same puzzle and resets");check(!q.finishBreak(),"break cannot advance twice");
  q.answer(false);q.reward();q.answer(true);check(q.streak==0,"correct resets");q.next();q.answer(false);check(q.streak==1,"nonconsecutive errors do not accumulate");
  q=new QuizProgress(22,21,0,"QUESTION");q.answer(true);q.next();check(q.phase==QuizProgress.Phase.COMPLETE&&q.index==22,"final question completes");check(!q.answer(true)&&!q.reward()&&!q.next(),"complete locked");
  q=new QuizProgress(122,22,0,"COMPLETE");check(q.index==22&&q.phase==QuizProgress.Phase.QUESTION,"content expansion resumes at first new joke");
  q=new QuizProgress(122,121,0,"QUESTION");q.answer(true);q.next();check(q.index==122&&q.phase==QuizProgress.Phase.COMPLETE,"expanded final question");
  for(int seed=0;seed<100;seed++){AdventureEngine b=new AdventureEngine(1,seed,new int[]{10,500,0});for(int r=0;r<8;r++)for(int c=0;c<8;c++)b.hp[r][c]=0;b.remaining=0;b.refillFresh();b.ensureMove();for(int i=0;i<10&&!b.won;i++){int[] h=b.hint();check(h!=null,"break solvable");check(b.move(h[0],h[1]),"hint move");}check(b.moves==0||b.won,"break finishes without win requirement");}
  check(CharacterAnimation.frame(QuizProgress.Phase.AD_REQUIRED,550)==6,"laugh open");
  check(CharacterAnimation.frame(QuizProgress.Phase.AD_REQUIRED,700)==7,"laugh closed");
  check(CharacterAnimation.frame(QuizProgress.Phase.CORRECT,10)==8,"correct starts scratching");
  check(CharacterAnimation.frame(QuizProgress.Phase.CORRECT,800)==9,"correct realization");
  check(CharacterAnimation.frame(QuizProgress.Phase.CORRECT,1100)==10,"correct pointing");
  check(CharacterAnimation.frame(QuizProgress.Phase.CORRECT,1500)==11,"correct thumbs up");
  System.out.println("PASS: quiz gates, restored progress, duplicate callbacks, completion and 100 break boards");
 }
}
