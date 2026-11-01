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
 void launch()throws Exception{host=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
  check(field(host,"gameView")==null,"welcome gates game");
  long until=SystemClock.elapsedRealtime()+30000;
  while(field(host,"content")==null&&SystemClock.elapsedRealtime()<until){SystemClock.sleep(100);waitForIdleSync();}
  check(field(host,"content")!=null,"patch loader completes or falls back");
  if(view==null)shot("00-welcome");
  runOnMainSync(()->{try{((android.widget.Button)field(host,"enterGame")).performClick();}catch(Exception e){throw new RuntimeException(e);}});
  view=(SerJoyView)field(host,"gameView");waitForIdleSync();}
 public void onStart(){Bundle result=new Bundle();try{
  getTargetContext().getSharedPreferences("ad_age",0).edit().putString("band","teen").commit();getTargetContext().getSharedPreferences("ser_joy_v1",0).edit().clear().commit();
  byte[] bundled;try(InputStream in=getTargetContext().getAssets().open("ser-joy-content.json")){bundled=JokePatch.read(in);}
  check(JokePatch.verified(bundled,JokePatch.sha(bundled),1,22).jokes.length()==22,"valid patch");
  boolean rejected=false;try{JokePatch.verified(bundled,"bad",1,22);}catch(Exception expected){rejected=true;}check(rejected,"checksum rejection");
  rejected=false;try{JokePatch.verified(bundled,JokePatch.sha(bundled),2,22);}catch(Exception expected){rejected=true;}check(rejected,"version rejection");
  org.json.JSONObject invalid=new org.json.JSONObject(new String(bundled,"UTF-8"));invalid.getJSONArray("jokes").getJSONObject(0).put("answer",9);
  rejected=false;try{JokePatch.validate(invalid.toString().getBytes("UTF-8"));}catch(Exception expected){rejected=true;}check(rejected,"invalid answer rejection");
  File cache=new File(getTargetContext().getFilesDir(),"ser-joy-content.json");try(FileOutputStream out=new FileOutputStream(cache)){out.write("broken".getBytes("UTF-8"));}
  check(JokePatch.local(getTargetContext()).jokes.length()==22,"corrupt cache fallback");cache.delete();
  org.json.JSONObject updated=new org.json.JSONObject(new String(bundled,"UTF-8"));updated.put("version",2);byte[] patch=updated.toString().getBytes("UTF-8");
  JokePatch.install(getTargetContext(),patch,JokePatch.sha(patch),2,22);
  check(JokePatch.local(getTargetContext()).version==2,"downloaded content persists for offline launch");
  try{JokePatch.install(getTargetContext(),bundled,"bad",3,22);}catch(Exception expected){}
  check(JokePatch.local(getTargetContext()).version==2,"bad update preserves last good cache");cache.delete();
  getTargetContext().getSharedPreferences("game_audio",0).edit().putBoolean("enabled",true).commit();
  launch();check(view.progress.index==0,"initial question");
  GameAudio sounds=(GameAudio)field(host,"gameAudio");android.media.MediaPlayer music=(android.media.MediaPlayer)field(sounds,"music");
  check(music!=null&&music.isPlaying(),"supplied quiz music starts");
  runOnMainSync(sounds::toggle);check(!music.isPlaying(),"mute pauses music");
  runOnMainSync(sounds::toggle);check(music.isPlaying(),"unmute resumes music");
  runOnMainSync(()->sounds.setActive(false));check(!music.isPlaying(),"background pauses music");runOnMainSync(()->sounds.setActive(true));shot("01-question");
  invoke("answer",new Class[]{int.class},1);check(view.progress.phase==QuizProgress.Phase.AD_REQUIRED,"wrong gate");shot("02-wrong-reaction");SystemClock.sleep(1300);
  runOnMainSync(()->host.finish());waitForIdleSync();SystemClock.sleep(500);launch();check(view.progress.phase==QuizProgress.Phase.AD_REQUIRED&&view.progress.streak==1,"restart retains gate: phase="+view.progress.phase+", streak="+view.progress.streak+", prefs="+getTargetContext().getSharedPreferences("ser_joy_v1",0).getAll());
  // State-machine reward is tested without pretending an actual network ad was watched.
  runOnMainSync(()->view.progress.reward());invoke("render",new Class[]{});
  invoke("answer",new Class[]{int.class},1);check(view.progress.phase==QuizProgress.Phase.CORRECT,"correct answer");shot("03-correct-reaction");SystemClock.sleep(1600);shot("03b-correct-thumbs-up");
  runOnMainSync(()->{view.progress.next();view.progress.answer(false);view.progress.reward();view.progress.answer(false);view.progress.reward();view.progress.answer(false);});
  invoke("save",new Class[]{});invoke("render",new Class[]{});check(view.progress.phase==QuizProgress.Phase.BREAK_REQUIRED,"three wrong");shot("04-break-gate");
  org.json.JSONObject legacy=new org.json.JSONObject();legacy.put("index",view.progress.index);legacy.put("moves",8);legacy.put("score",60);org.json.JSONArray legacyCells=new org.json.JSONArray();for(int n=0;n<64;n++){legacyCells.put(n%6);legacyCells.put(0);}legacy.put("cells",legacyCells);
  getTargetContext().getSharedPreferences("ser_joy_v1",0).edit().putString("board",legacy.toString()).commit();invoke("startBreak",new Class[]{});
  AdventureEngine migrated=(AdventureEngine)field(view,"board");check(migrated.moves==8&&migrated.score==60,"legacy round progress preserved");for(int[] row:migrated.tile)for(int type:row)check(type>=0&&type<3,"one visible ghost per match type");
  GameAudio breakAudio=(GameAudio)field(host,"gameAudio");check((Boolean)field(breakAudio,"breakMusic"),"break selects second supplied music track");
  check(((android.media.MediaPlayer)field(breakAudio,"music")).isPlaying(),"break music plays");shot("05-match-three");
  AdventureEngine board=(AdventureEngine)field(view,"board");int before=board.moves;View bv=(View)field(view,"boardView");int[] pair=board.hint();
  int[] screen=new int[2];runOnMainSync(()->bv.getLocationOnScreen(screen));float side=bv.getWidth()/8f;
  long touch=SystemClock.uptimeMillis();float x=screen[0]+(pair[0]%8+.5f)*side,y=screen[1]+(pair[0]/8+.5f)*side,tx=screen[0]+(pair[1]%8+.5f)*side,ty=screen[1]+(pair[1]/8+.5f)*side;
  for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP}){
   MotionEvent e=MotionEvent.obtain(touch,SystemClock.uptimeMillis(),action,action==MotionEvent.ACTION_DOWN?x:tx,action==MotionEvent.ACTION_DOWN?y:ty,0);e.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);check(getUiAutomation().injectInputEvent(e,true),"real touchscreen injection");e.recycle();SystemClock.sleep(60);
  }
  SystemClock.sleep(1500);shot("05b-cascade");
  check(board.moves==before-1,"drag and repeated MOVE consume exactly one turn");runOnMainSync(()->host.finish());waitForIdleSync();SystemClock.sleep(500);launch();invoke("startBreak",new Class[]{});board=(AdventureEngine)field(view,"board");check(board.moves==before-1,"break board restored");
  View restored=(View)field(view,"boardView");final int remaining=board.moves;
  runOnMainSync(()->{long t=SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_CANCEL,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(t,t,action,10,10,0);restored.dispatchTouchEvent(e);e.recycle();}});
  check(board.moves==remaining,"cancelled drag never swaps");
  int[] tap=board.hint();runOnMainSync(()->{float tapSide=restored.getWidth()/8f;long t=SystemClock.uptimeMillis();for(int cell:tap)for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(t,t,action,(cell%8+.5f)*tapSide,(cell/8+.5f)*tapSide,0);restored.dispatchTouchEvent(e);e.recycle();}});
  check(board.moves==remaining-1,"tap-to-swap still works");
  final AdventureEngine end=board;runOnMainSync(()->{while(end.moves>0&&!end.won){int[] h=end.hint();end.move(h[0],h[1]);}});invoke("render",new Class[]{});shot("06-break-complete");
  result.putString("stream","PASS: quiz screens, reactions, restart gates, real board touch and persisted break\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable t){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(t));finish(Activity.RESULT_CANCELED,result);}}
}
