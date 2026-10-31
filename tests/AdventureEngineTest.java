package com.sunantongsan.ghostmatch3;
import java.util.*;
public class AdventureEngineTest {
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 static void invariant(AdventureEngine e){
  int remaining=0;
  for(int r=0;r<8;r++)for(int c=0;c<8;c++)if(e.active[r][c]){check(e.tile[r][c]>=0&&e.tile[r][c]<6,"tile range");check(e.hp[r][c]>=0,"negative obstacle");if(e.obstacle[r][c]!=AdventureEngine.BIG_PART)remaining+=e.hp[r][c];}
  check(remaining==e.remaining,"obstacle accounting");check(e.matches().isEmpty(),"unresolved cascade");
  if(!e.won&&!e.lost)check(e.hint()!=null,"no legal move");
 }
 public static void main(String[] args){
  AdventureEngine patched=new AdventureEngine(1,123,new int[]{42,17,95});
  check(patched.moves==42&&patched.level.goal==17&&patched.level.reward==95,"patch not applied");
  boolean rejected=false;try{new AdventureEngine(1,123,new int[]{0,17,95});}catch(IllegalArgumentException expected){rejected=true;}
  check(rejected,"invalid patch accepted");
  int boss=0,bonus=0,overlap=0,wins=0;
  for(int n=1;n<=120;n++){
   AdventureEngine.Level l=new AdventureEngine.Level(n);
   check((l.kind==3)==(n%6==0),"boss schedule");check(l.bonus==(n%10==0),"bonus schedule");
   if(l.kind==3)boss++;if(l.bonus)bonus++;if(l.kind==3&&l.bonus)overlap++;
   for(int seed=0;seed<5;seed++){
    AdventureEngine e=new AdventureEngine(n,seed);invariant(e);
    int m=e.moves;check(!e.move(0,63)&&m==e.moves,"invalid move charged");
    for(int turn=0;turn<60&&!e.won&&!e.lost;turn++){
     int[] h=e.hint();check(h!=null,"hint missing");check(e.move(h[0],h[1]),"hint not playable");invariant(e);
    }
    check(e.won||e.lost,"run never ends");if(e.won)wins++;
    check(!e.use(0,0),"booster after end");
   }
   // Objective can be completed with available mechanics, including layered blockers.
   AdventureEngine e=new AdventureEngine(n,7);
   for(int i=0;i<300&&!e.won;i++){int p=i%64;if(e.active[p/8][p%8])e.use(1,p);}
   check(e.won,"unreachable objective "+n);check(e.reward()>=l.reward,"reward below advertised");
  }
  check(boss==20&&bonus==12&&overlap==4,"schedule counts");
  AdventureEngine e=new AdventureEngine(2,1);check(e.use(3,-1),"freeze rescue");
  for(int i=0;i<3;i++){int[] h=e.hint();e.move(h[0],h[1]);check(e.danger==0,"freeze failed");}
  check(e.freeze==0,"freeze duration");int[] h=e.hint();e.move(h[0],h[1]);check(e.danger==1,"danger not resumed");
  e=new AdventureEngine(6,4);check(e.use(4,-1),"shield boss");for(int i=0;i<4;i++){h=e.hint();e.move(h[0],h[1]);}check(e.shield==0,"shield did not block boss");
  for(int id:new int[]{0,1,2,5,7,8,9}){
   e=new AdventureEngine(2,23);int moves=e.moves,turns=e.turns,danger=e.danger;
   check(e.use(id,id==5||id==9?-1:27),"restored booster "+id);invariant(e);
   check(e.moves==moves+(id==9?5:0)&&e.turns==turns&&e.danger==danger,"booster charged a turn "+id);
   if(id==7||id==8)check(e.casts.stream().anyMatch(c->c[1]==(id==7?1:2)),"missing directional cast");
  }
  e=new AdventureEngine(2,42);h=e.hint();int m=e.moves,d=e.danger;
  check(e.freeSwap(h[0],h[1]),"free swap rejected");check(e.moves==m&&e.danger==d&&e.turns==0,"free swap charged");invariant(e);
  check(!e.freeSwap(7,8),"free swap wrapped row");
  // Giant 2x2 ghost: one shared health pool, any covered cell can be hit, all four cells open together.
  e=new AdventureEngine(12,3);int anchor=-1;for(int p=0;p<64;p++)if(e.obstacle[p/8][p%8]==AdventureEngine.BIG&&e.hp[p/8][p%8]>0){anchor=p;break;}
  check(anchor>=0,"giant ghost layout");int big=e.hp[anchor/8][anchor%8],rem=e.remaining;
  check(e.use(0,anchor+9),"hammer giant ghost part");check(e.remaining==rem-1,"giant ghost counted once");
  for(int p:new int[]{anchor,anchor+1,anchor+8,anchor+9})check(e.hp[p/8][p%8]==big-1,"giant ghost shared health");
  for(int i=1;i<big;i++)e.use(0,anchor+1);check(e.movable(anchor/8,anchor%8)&&e.movable(anchor/8+1,anchor%8+1),"giant ghost opens 2x2");invariant(e);
  AdventureEngine.Level two=new AdventureEngine.Level(13);check(two.goal2>0&&two.color2!=two.color,"second goal from level 11");
  e=new AdventureEngine(13,5);e.progress=99;e.remaining=0;e.finish();check(!e.won,"second goal required");e.progress2=two.goal2;e.finish();check(e.won,"both goals complete");
  e=new AdventureEngine(2,1);e.danger=9;check(e.use(9,-1)&&e.danger==4,"extra moves must extend rescue clock");
  System.out.println("PASS: 120 levels; 600 full games; 20 bosses; 12 bonuses; 4 overlaps; obstacles, cascades, hints, freeze, shield and completion. First-hint wins="+wins+"/600 (not a difficulty acceptance test)");
 }
}
