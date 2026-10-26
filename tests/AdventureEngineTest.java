package com.sunantongsan.ghostmatch3;
import java.util.*;
public class AdventureEngineTest {
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 static void invariant(AdventureEngine e){
  int remaining=0;
  for(int r=0;r<8;r++)for(int c=0;c<8;c++)if(e.active[r][c]){check(e.tile[r][c]>=0&&e.tile[r][c]<6,"tile range");check(e.hp[r][c]>=0,"negative obstacle");remaining+=e.hp[r][c];}
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
  System.out.println("PASS: 120 levels; 600 full games; 20 bosses; 12 bonuses; 4 overlaps; obstacles, cascades, hints, freeze, shield and completion. First-hint wins="+wins+"/600 (not a difficulty acceptance test)");
 }
}
