package com.sunantongsan.ghostmatch3;
import java.util.*;
public class AudioShuffleTest {
 public static void main(String[] args){
  for(int n:new int[]{1,3,4})for(int seed=0;seed<30;seed++){
   int[] values=new int[n];for(int i=0;i<n;i++)values[i]=i+10;
   AudioShuffle bag=new AudioShuffle(values,new Random(seed));int previous=-1;
   for(int round=0;round<30;round++){
    Set<Integer> seen=new HashSet<>();
    for(int i=0;i<n;i++){int next=bag.next();if(next<10||next>=10+n||!seen.add(next)||(n>1&&next==previous))throw new AssertionError("Audio repetition or missing track");previous=next;}
   }
  }
  System.out.println("PASS: shuffled audio covers every clip without adjacent repeats");
 }
}
