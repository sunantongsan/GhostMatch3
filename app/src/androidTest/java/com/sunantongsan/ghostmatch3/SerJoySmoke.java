package com.sunantongsan.ghostmatch3;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
public class SerJoySmoke extends Instrumentation {
 MainActivity host;SerJoyView view;
 public void onCreate(Bundle b){super.onCreate(b);start();}
 void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 void invoke(String name,Class<?>[] types,Object...args){final Throwable[] error={null};runOnMainSync(()->{try{Method m=view.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(view,args);}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new RuntimeException(error[0]);waitForIdleSync();}
 void shot(String name)throws Exception{SystemClock.sleep(1000);Bitmap b=getUiAutomation().takeScreenshot();File dir=new File(getTargetContext().getExternalFilesDir(null),"screenshots");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
 void launch()throws Exception{host=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));view=(SerJoyView)field(host,"gameView");waitForIdleSync();}
 public void onStart(){Bundle result=new Bundle();try{
  getTargetContext().getSharedPreferences("ad_age",0).edit().putString("band","teen").commit();getTargetContext().getSharedPreferences("ser_joy_v1",0).edit().clear().commit();
  launch();check(view.progress.index==0,"initial question");shot("01-question");
  invoke("answer",new Class[]{int.class},1);check(view.progress.phase==QuizProgress.Phase.AD_REQUIRED,"wrong gate");shot("02-wrong-reaction");
  runOnMainSync(()->host.finish());waitForIdleSync();launch();check(view.progress.phase==QuizProgress.Phase.AD_REQUIRED&&view.progress.streak==1,"restart retains gate");
  // State-machine reward is tested without pretending an actual network ad was watched.
  runOnMainSync(()->view.progress.reward());invoke("render",new Class[]{});
  invoke("answer",new Class[]{int.class},1);check(view.progress.phase==QuizProgress.Phase.CORRECT,"correct answer");shot("03-correct-reaction");
  runOnMainSync(()->{view.progress.next();view.progress.answer(false);view.progress.reward();view.progress.answer(false);view.progress.reward();view.progress.answer(false);});
  invoke("render",new Class[]{});check(view.progress.phase==QuizProgress.Phase.BREAK_REQUIRED,"three wrong");shot("04-break-gate");invoke("startBreak",new Class[]{});shot("05-match-three");
  AdventureEngine board=(AdventureEngine)field(view,"board");int before=board.moves;View bv=(View)field(view,"boardView");int[] pair=board.hint();
  for(int cell:pair){runOnMainSync(()->{float side=bv.getWidth()/8f;long t=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(t,t,MotionEvent.ACTION_UP,(cell%8+.5f)*side,(cell/8+.5f)*side,0);bv.dispatchTouchEvent(e);e.recycle();});}
  check(board.moves==before-1,"actual board touch consumes one turn");runOnMainSync(()->host.finish());waitForIdleSync();launch();invoke("startBreak",new Class[]{});board=(AdventureEngine)field(view,"board");check(board.moves==before-1,"break board restored");
  final AdventureEngine end=board;runOnMainSync(()->{while(end.moves>0&&!end.won){int[] h=end.hint();end.move(h[0],h[1]);}});invoke("render",new Class[]{});shot("06-break-complete");
  result.putString("stream","PASS: quiz screens, reactions, restart gates, real board touch and persisted break\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable t){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(t));finish(Activity.RESULT_CANCELED,result);}}
}
