package com.sunantongsan.ghostmatch3;
public final class GuardianClockTest {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args){
  GuardianClock c=new GuardianClock(180);c.advance(1234);check(c.remainingMs==178766,"active time");
  c.frozenMs=8000;c.advance(9000);check(c.remainingMs==177766&&c.frozenMs==0,"freeze consumes only excess time");
  c.advance(-99);check(c.remainingMs==177766,"monotonic guard");
  c.advance(500000);check(c.remainingMs==0&&c.danger()==1,"deadline");
  c.extend(10);check(c.remainingMs==10000,"shield rescue");
  c.extend(10000);check(c.remainingMs==300000,"bounded extensions");
  System.out.println("PASS: foreground clock, freeze, timeout, shield and bounded extension");
 }
}
