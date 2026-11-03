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
 void checkSceneDraws()throws Exception{
  StorySceneView story=scene();View surface=(View)field(story,"stage");
  check(story.getWidth()>0&&story.getHeight()>0,"scene has viewport");
  check(surface.getWidth()==story.getWidth()&&surface.getHeight()==story.getHeight(),"drawing surface fills scene after ad");
  check(surface.getMeasuredHeight()==story.getHeight(),"drawing surface measured after ad");
  Bitmap pixels=Bitmap.createBitmap(story.getWidth(),story.getHeight(),Bitmap.Config.ARGB_8888);
  runOnMainSync(()->story.draw(new Canvas(pixels)));
  java.util.HashSet<Integer> colors=new java.util.HashSet<>();
  for(int y=20;y<pixels.getHeight();y+=40)for(int x=20;x<pixels.getWidth();x+=40)colors.add(pixels.getPixel(x,y));
  pixels.recycle();check(colors.size()>30,"scene renders artwork, not solid dark background");
 }
 void adReturnRegression()throws Exception{
  // Exercise the actual result dispatcher in both lifecycle orders without faking an ad impression.
  for(int cycle=0;cycle<4;cycle++){
   selectQuestion(23);runOnMainSync(()->view.progress.answer(false));invoke("render",new Class[]{});
   runOnMainSync(()->callActivityOnPause(host));
   final int[] delivered={0};Runnable earned=()->{
    delivered[0]++;
    try{Method m=view.getClass().getDeclaredMethod("finishRewardAd");m.setAccessible(true);m.invoke(view);}catch(Exception e){throw new RuntimeException(e);}
   };
   if(cycle%2==0){runOnMainSync(()->host.deliverQuizAdResult(earned));waitForIdleSync();check(delivered[0]==0,"ad result waits for resume");runOnMainSync(()->callActivityOnResume(host));}
   else {runOnMainSync(()->callActivityOnResume(host));runOnMainSync(()->host.deliverQuizAdResult(earned));}
   waitForIdleSync();SystemClock.sleep(150);check(delivered[0]==1,"ad result consumed once");
   checkSceneDraws();check(view.progress.index==23&&view.progress.phase==QuizProgress.Phase.QUESTION,"earned ad retries same question");
   if(cycle==3)shot("09-after-ad-playable");
   tap(scene().choiceView(2));check(view.progress.phase==QuizProgress.Phase.CORRECT,"answer works after ad dismissal");
  }
  // Closed early / show failure must still dispatch on resume, without granting a reward.
  selectQuestion(23);runOnMainSync(()->view.progress.answer(false));invoke("render",new Class[]{});
  runOnMainSync(()->callActivityOnPause(host));final int[] closed={0};
  runOnMainSync(()->host.deliverQuizAdResult(()->{closed[0]++;}));
  runOnMainSync(()->callActivityOnResume(host));waitForIdleSync();
  check(closed[0]==1&&view.progress.phase==QuizProgress.Phase.AD_REQUIRED,"unearned dismissal keeps reward gate");checkSceneDraws();
 }
 void audioRegression()throws Exception{
  GameAudio audio=(GameAudio)field(host,"gameAudio");
  if(!audio.isEnabled())runOnMainSync(audio::toggle);
  long deadline=SystemClock.elapsedRealtime()+10000;
  java.util.Set<?> ready=(java.util.Set<?>)field(audio,"ready");
  int[] laughs=(int[])field(audio,"laughs"),cheers=(int[])field(audio,"celebrations");
  boolean loaded=false;
  while(SystemClock.elapsedRealtime()<deadline){
   synchronized(audio){loaded=true;for(int id:laughs)loaded&=ready.contains(id);for(int id:cheers)loaded&=ready.contains(id);}
   if(loaded)break;SystemClock.sleep(100);
  }
  check(loaded,"all user laugh and cheer clips decode");
  for(int kind:new int[]{GameAudio.GHOST,GameAudio.WIN}){
   SystemClock.sleep(150);runOnMainSync(()->audio.play(kind));
   check(!((java.util.Set<?>)field(audio,"streams")).isEmpty(),"random reaction starts");
   runOnMainSync(()->audio.setActive(false));
   check(((java.util.Set<?>)field(audio,"streams")).isEmpty(),"ad/background stops effects");
   check(!((android.media.MediaPlayer)field(audio,"music")).isPlaying(),"ad/background pauses music");
   runOnMainSync(()->audio.setActive(true));
  }
  android.media.MediaPlayer current=(android.media.MediaPlayer)field(audio,"music");
  int resource=(Integer)field(audio,"musicResource");
  check(current.isPlaying(),"music resumes after ad");
  runOnMainSync(()->current.seekTo(Math.max(0,current.getDuration()-150)));
  deadline=SystemClock.elapsedRealtime()+6000;
  while((Integer)field(audio,"musicResource")==resource&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100);
  check((Integer)field(audio,"musicResource")!=resource,"finished music advances to a different track");
  runOnMainSync(audio::toggle);
  check(!((android.media.MediaPlayer)field(audio,"music")).isPlaying(),"mute stops music");
  runOnMainSync(()->audio.play(GameAudio.GHOST));
  check(((java.util.Set<?>)field(audio,"streams")).isEmpty(),"mute blocks effects");
  runOnMainSync(audio::toggle);
  check(((android.media.MediaPlayer)field(audio,"music")).isPlaying(),"unmute resumes music");
 }
 public void onStart(){Bundle result=new Bundle();try{
  getTargetContext().getSharedPreferences("ad_age",0).edit().putString("band","teen").commit();
  getTargetContext().getSharedPreferences("ser_joy_v1",0).edit().clear().commit();
  byte[] bundled;try(InputStream in=getTargetContext().getAssets().open("ser-joy-content.json")){bundled=JokePatch.read(in);}
  check(JokePatch.verified(bundled,JokePatch.sha(bundled),8,156).jokes.length()==156,"expanded patch compatible");
  boolean rejected=false;try{JokePatch.verified(bundled,"bad",8,156);}catch(Exception expected){rejected=true;}check(rejected,"checksum rejection");
  File cache=new File(getTargetContext().getFilesDir(),"ser-joy-content.json");cache.delete();
  Intent market=MainActivity.storeUpdateIntent(true),web=MainActivity.storeUpdateIntent(false);
  check("com.android.vending".equals(market.getPackage())&&market.getDataString().equals("market://details?id=com.sunantongsan.ghostmatch3"),"store link opens exact game");
  check(web.getDataString().equals("https://play.google.com/store/apps/details?id=com.sunantongsan.ghostmatch3"),"browser fallback opens exact game");
  launch();check(view.progress.index==0,"initial question");shot("01-ant-story");
  View update=(View)field(host,"updateGame");
  runOnMainSync(()->{host.showStoreUpdate(true);check(host.updateAvailable,"store availability updates UI state");check(update.getVisibility()==View.VISIBLE,"update button shown for available update");});
  IntentFilter storeFilter=new IntentFilter(Intent.ACTION_VIEW);storeFilter.addDataScheme("market");ActivityMonitor storeMonitor=addMonitor(storeFilter,null,true);
  runOnMainSync(()->{host.showStoreUpdate(true);update.performClick();});check(storeMonitor.getHits()==1,"update tap launches store intent");removeMonitor(storeMonitor);
  runOnMainSync(()->{host.showStoreUpdate(false);check(update.getVisibility()==View.GONE,"no update prompt when current");});
  // Regression: repeated unconstrained/constrained measurements used when ScrollView replaces a scene.
  StorySceneView probe=scene();int pw=probe.getWidth(),ph=probe.getHeight();
  runOnMainSync(()->{
   probe.measure(View.MeasureSpec.makeMeasureSpec(pw,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(ph,View.MeasureSpec.AT_MOST));
   View surface;try{surface=(View)field(probe,"stage");}catch(Exception e){throw new RuntimeException(e);}
   check(surface.getMeasuredHeight()==probe.getMeasuredHeight(),"unconstrained pass does not leave stage zero-sized");
   probe.measure(View.MeasureSpec.makeMeasureSpec(pw,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(ph,View.MeasureSpec.EXACTLY));probe.layout(0,0,pw,ph);
  });checkSceneDraws();
  audioRegression();
  adReturnRegression();
  // History must not reveal the locked current question or reset its ad gate.
  selectQuestion(73);runOnMainSync(()->view.progress.answer(false));invoke("save",new Class[]{});
  runOnMainSync(view::showHistory);waitForIdleSync();
  AlertDialog history=(AlertDialog)field(view,"historyDialog");check(history!=null&&history.isShowing(),"history opens");
  check(QuizHistory.count(view.progress)==73,"history excludes locked current question");shot("history-review");
  runOnMainSync(history::dismiss);check(view.progress.index==73&&view.progress.phase==QuizProgress.Phase.AD_REQUIRED&&view.progress.streak==1,"history preserves gate and streak");
  selectQuestion(73);StorySceneView fxScene=scene();tap(fxScene.choiceView(host.content.jokes.getJSONObject(73).getInt("answer")));SystemClock.sleep(600);shot("celebration-fireworks");
  selectQuestion(73);int wrong=(host.content.jokes.getJSONObject(73).getInt("answer")+1)%3;tap(scene().choiceView(wrong));SystemClock.sleep(600);shot("animal-laughter-555");
  // Every question must instantiate the scene renderer with three usable nonoverlapping objects.
  for(int i=0;i<host.content.jokes.length();i++){
   selectQuestion(i);StorySceneView story=scene();check(story!=null,"story exists "+i);
   String[] captions=(String[])field(story,"tags");check(captions.length==3,"three visible captions");
   for(String caption:captions)check(!caption.trim().isEmpty(),"caption always available");
   check(story.questionText().equals(host.content.jokes.getJSONObject(i).getString("question")),"original question shown");
   if(i==50){check(captions[0].equals("เลย์")&&captions[1].equals("โก๋แก่")&&captions[2].equals("ฮานามิ"),"snack brands");check(!story.showsSetupSubject(),"no snack answer preview");}
   if(i==30){check(captions[0].equals("หนอน")&&captions[1].equals("งู")&&captions[2].equals("ปลวก"),"library animals");check(!story.showsSetupSubject(),"no answer preview in library");}
   if(i==26)check(captions[2].equals("ไฟแช็ก"),"naga object label has no punchline");
   if(i==27)check(captions[0].equals("เงินบาท"),"currency label does not reveal wordplay");
   for(int a=0;a<3;a++){View v=story.choiceView(a);check(v.isClickable()&&v.getContentDescription().length()>0,"accessible object "+i);Rect ar=new Rect();v.getHitRect(ar);
    check(ar.left>=0&&ar.right<=story.getWidth()&&ar.top>=0&&ar.bottom<=story.getHeight(),"object within screen "+i);
    for(int b=a+1;b<3;b++){Rect br=new Rect();story.choiceView(b).getHitRect(br);check(!Rect.intersects(ar,br),"separate choices "+i);}
   }
   if(i>=56){
    org.json.JSONObject q=host.content.jokes.getJSONObject(i);
    check((Boolean)field(story,"answerCards"),"sourced renderer selected");
    check(!story.showsSetupSubject(),"old target cannot leak into replacement");
    for(int a=0;a<3;a++)check(captions[a].equals(q.getJSONArray("choices").getJSONObject(a).getString("label")),"new captions use canonical choices");
    checkSceneDraws();
    if(i==56||i==59||i==88||i==125||i==155)shot("sourced-question-"+(i+1));
    int correct=q.getInt("answer");tap(story.choiceView(correct));
    check(view.progress.phase==QuizProgress.Phase.CORRECT,"correct sourced answer accepted "+i);
    runOnMainSync(story::showFinal);checkSceneDraws();
    if(i==56||i==59||i==88||i==125||i==155)shot("sourced-answer-"+(i+1));
   }
   shot("audit-before-"+(i+1));
   if(i>=42&&i<56){checkSceneDraws();runOnMainSync(story::showFinal);checkSceneDraws();}
   if(i==26||i==27||i==47||i==22||i==30||i==31||i==38||i==40||i==155||i==42||i==45||i==52||i==55)shot("scene-"+(i+1));
  }
  selectQuestion(30);tap(scene().choiceView(0));
  check(view.progress.phase==QuizProgress.Phase.CORRECT,"plain worm is accepted");
  SystemClock.sleep(5400);check(scene().mode()==3,"bookworm explanation follows resolution");shot("library-explanation");
  selectQuestion(34);StorySceneView toyota=scene();runOnMainSync(toyota::showFinal);shot("toyota-explanation");
  selectQuestion(31);StorySceneView basket=scene();runOnMainSync(basket::showFinal);shot("basket-explanation");
  selectQuestion(23);shot("02-door-choices");tap(scene().choiceView(0));
  check(view.progress.phase==QuizProgress.Phase.AD_REQUIRED&&view.progress.index==23,"hammer wrong stays at door");shot("03-door-laugh");
  SystemClock.sleep(3000);check(scene().mode()==1,"no punchline on wrong");
  runOnMainSync(()->host.finish());waitForIdleSync();SystemClock.sleep(300);launch();
  check(view.progress.index==23&&view.progress.phase==QuizProgress.Phase.AD_REQUIRED,"wrong gate survives restart");
  // Simulate state-machine reward, never claim an actual ad impression was watched.
  runOnMainSync(()->view.progress.reward());invoke("save",new Class[]{});invoke("render",new Class[]{});
  check(view.progress.index==23&&view.progress.phase==QuizProgress.Phase.QUESTION,"reward retries same question");
  tap(scene().choiceView(1));check(view.progress.phase==QuizProgress.Phase.AD_REQUIRED,"bomb also wrong");
  SystemClock.sleep(3000);runOnMainSync(()->view.progress.reward());invoke("render",new Class[]{});
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
  runOnMainSync(()->host.finish());waitForIdleSync();
  getTargetContext().getSharedPreferences("ser_joy_v1",0).edit().putInt("curatedContentVersion",4).putInt("index",42).putString("phase","QUESTION").commit();
  launch();check(view.progress.index==56,"old tail position migrates past inserted fourteen");checkSceneDraws();
  runOnMainSync(()->host.finish());waitForIdleSync();launch();check(view.progress.index==56,"position migration runs once");
  runOnMainSync(()->host.finish());waitForIdleSync();
  getTargetContext().getSharedPreferences("ser_joy_v1",0).edit().putInt("curatedContentVersion",5).putInt("index",50).putString("phase","CORRECT").commit();
  launch();check(view.progress.index==50&&view.progress.phase==QuizProgress.Phase.QUESTION,"replacement question clears old result");
  check(host.content.jokes.getJSONObject(50).getString("id").equals("joke-51"),"replacement keeps stable ID");
  result.putString("stream","PASS: randomized music and reactions, 100 sourced answers scored and rendered, mute and ad audio lifecycle; quiz screens, all 156 illustrated scenes, touch targets, ad resume drawing and retry gates, door fire cutscene, saved progress and match-three\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable t){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(t));finish(Activity.RESULT_CANCELED,result);}}
}
