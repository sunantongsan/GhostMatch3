package com.sunantongsan.ghostmatch3;
import java.util.*;
public class BreakBoardTest {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args){int max=0;
  for(int seed=0;seed<200;seed++){
   AdventureEngine b=new AdventureEngine(1,seed,new int[]{10,500,0},3);
   for(int[] row:b.hp)Arrays.fill(row,0);b.remaining=0;b.refillFresh();b.ensureMove();b.recordAnimation=true;
   check(b.matches().isEmpty(),"initial board settled");
   boolean invalidChecked=false;
   for(int a=0;a<64&&!invalidChecked;a++)for(int dest:new int[]{a+1,a+8})if(dest<64&&Math.abs(a/8-dest/8)+Math.abs(a%8-dest%8)==1){
    b.swap(a,dest);boolean invalid=b.matches().isEmpty();b.swap(a,dest);
    if(invalid){AdventureEngine.Step snapshot=new AdventureEngine.Step("test",b,null);int turns=b.moves;check(!b.move(a,dest)&&b.moves==turns&&Arrays.deepEquals(snapshot.tiles,b.tile),"invalid swap returns unchanged without consuming turn");invalidChecked=true;break;}
   }
   while(b.moves>0&&!b.won){
    int[] h=b.hint();check(h!=null,"always playable");int n=b.moves;
    check(b.move(h[0],h[1])&&b.moves==n-1,"valid move costs one turn");
    check(b.animation.get(0).kind.equals("swap"),"swap shown first");
    check(b.animation.stream().anyMatch(s->s.kind.equals("clear")),"matched ghosts disappear");
    check(b.animation.stream().anyMatch(s->s.kind.equals("fall")),"gravity recorded");
    check(b.matches().isEmpty(),"cascade fully resolves");
    for(int[] row:b.tile)for(int t:row)check(t>=0&&t<3,"three visible types only");
    AdventureEngine.Step last=b.animation.get(b.animation.size()-1);check(Arrays.deepEquals(last.tiles,b.tile),"animation ends on actual board");max=Math.max(max,b.animation.size());
   }
   check(b.moves==0||b.won,"short relief round completes");
  }
  System.out.println("PASS: 200 relief rounds, visible types, gravity, cascades, playable boards; max animation steps="+max);
 }
}
