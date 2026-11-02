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
 void shot(String name)throws Exception{SystemClock.sleep(150);Bitmap b=getUiAutomation().takeScreenshot();File dir=new File(getTargetContext().getExternalFilesDir(null),"screenshots");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
 void launch()throws Exception{
  host=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
  long until=SystemClock.elapsedRealtime()+30000;while(field(host,"content")==null&&SystemClock.elapsedRealtime()<until){SystemClock.sleep(100);waitForIdleSync();}
  check(field(host,"content")!=null,"welcome loader completes");
  runOnMainSync(()->{try{((android.widget.Button)field(host,"enterGame")).performClick();}catch(Exception e){throw new RuntimeException(e);}});view=(SerJoyView)field(host,"gameView");waitForIdleSync();
 }
 StorySceneView scene()throws Exception{return (StorySceneView)field(view,"story");}
 void selectQuestion(int index){runOnMainSync(()->{view.progress.index=index;view.progress.phase=QuizProgress.Phase.QUESTION;view.progress.streak=0;});invoke("render",new Class[]{});}
 void tap(View v)throws Exception{
  int[] xy=new int[2];runOnMainSync(()->v.getLocationOnScreen(xy));float x=xy[0]+v.getWidth()/2f,y=xy[1]+v.getHeight()/2f;
  check(v.getWidth()>=48*getTargetContext().getResources().getDisplayMetrics().density,"minimum touch target");
  long at=SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(at,SystemClock.uptimeMillis(),action,x,y,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);check(getUiAutomation().injectInputEvent(e,true),"touch inject");e.recycle();}waitForIdleSync();
 }
 public void onStart(){Bundle result=new Bundle();try{
  getTargetContext().getSharedPreferences("ad_age",0).edit().putString("band","teen").commit();
  getTargetContext().getSharedPreferences("ser_joy_v1",0).edit().clear().commit();
  byte[] bundled;try(InputStream in=getTargetContext().getAssets().open("ser-joy-content.json")){bundled=JokePatch.read(in);}
  check(JokePatch.verified(bundled,JokePatch.sha(bundled),4,122).jokes.length()==142,"expanded patch compatible");
  boolean rejected=false;try{JokePatch.verified(bundled,"bad",4,142);}catch(Exception expected){rejected=true;}check(rejected,"checksum rejection");
  File cache=new File(getTargetContext().getFilesDir(),"ser-joy-content.json");cache.delete();
  launch();check(view.progress.index==0,"initial question");shot("01-ant-story");
  // Every question must instantiate the scene renderer with three usable nonoverlapping objects.
  for(int i=0;i<142;i++){
   selectQuestion(i);StorySceneView story=scene();check(story!=null,"story exists "+i);
   for(int a=0;a<3;a++){View v=story.choiceView(a);check(v.isClickable()&&v.getContentDescription().length()>0,"accessible object "+i);Rect ar=new Rect();v.getHitRect(ar);
    check(ar.left>=0&&ar.right<=story.getWidth()&&ar.top>=0&&ar.bottom<=story.getHeight(),"object within screen "+i);
    for(int b=a+1;b<3;b++){Rect br=new Rect();story.choiceView(b).getHitRect(br);check(!Rect.intersects(ar,br),"separate choices "+i);}
   }
   if(i==22||i==30||i==31||i==38||i==40||i==141)shot("scene-"+(i+1));
  }
  selectQuestion(23);shot("02-door-choices");tap(scene().choiceView(0));
  check(view.progress.phase==QuizProgress.Phase.AD_REQUIRED&&view.progress.index==23,"hammer wrong stays at door");shot("03-door-laugh");
  SystemClock.sleep(1700);check(scene().mode()==1,"no punchline on wrong");
  runOnMainSync(()->host.finish());waitForIdleSync();SystemClock.sleep(300);launch();
  check(view.progress.index==23&&view.progress.phase==QuizProgress.Phase.AD_REQUIRED,"wrong gate survives restart");
  // Simulate state-machine reward, never claim an actual ad impression was watched.
  runOnMainSync(()->view.progress.reward());invoke("save",new Class[]{});invoke("render",new Class[]{});
  check(view.progress.index==23&&view.progress.phase==QuizProgress.Phase.QUESTION,"reward retries same question");
  tap(scene().choiceView(1));check(view.progress.phase==QuizProgress.Phase.AD_REQUIRED,"bomb also wrong");
  SystemClock.sleep(1700);runOnMainSync(()->view.progress.reward());invoke("render",new Class[]{});
  tap(scene().choiceView(2));check(view.progress.phase==QuizProgress.Phase.CORRECT,"fire correct");
  StorySceneView correct=scene();check(correct.mode()==2&&!correct.choiceView(0).isEnabled(),"cutscene locks input");
  SystemClock.sleep(2200);shot("04-fire-carried-to-door");SystemClock.sleep(1100);shot("05-door-running");
  runOnMainSync(()->view.setActive(false));long paused=(Long)field(correct,"elapsed");SystemClock.sleep(500);check((Long)field(correct,"elapsed")==paused,"background pauses cutscene");runOnMainSync(()->view.setActive(true));
  SystemClock.sleep(2500);check(correct.mode()==3,"cutscene completes");shot("06-door-punchline");
  runOnMainSync(()->view.progress.next());invoke("save",new Class[]{});invoke("render",new Class[]{});check(view.progress.index==24,"next advances once");
  selectQuestion(38);tap(scene().choiceView(2));SystemClock.sleep(5400);shot("07-egg-cracks");
  selectQuestion(40);tap(scene().choiceView(1));SystemClock.sleep(5400);shot("08-toad-on-icecream");
  // Existing match-three remains playable and returns to the same unsolved question.
  selectQuestion(2);runOnMainSync(()->{view.progress.answer(false);view.progress.reward();view.progress.answer(false);view.progress.reward();view.progress.answer(false);});
  check(view.progress.phase==QuizProgress.Phase.BREAK_REQUIRED,"three wrong still opens break gate");invoke("startBreak",new Class[]{});
  AdventureEngine board=(AdventureEngine)field(view,"board");int before=board.moves;int[] pair=board.hint();
  runOnMainSync(()->board.move(pair[0],pair[1]));check(board.moves==before-1,"match-three valid move");
  runOnMainSync(()->view.progress.finishBreak());check(view.progress.index==2&&view.progress.phase==QuizProgress.Phase.QUESTION,"break retries same question");
  result.putString("stream","PASS: quiz screens, all 142 illustrated scenes, touch targets, retry gates, door fire cutscene, saved progress and match-three\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable t){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(t));finish(Activity.RESULT_CANCELED,result);}}
}
