package com.sunantongsan.ghostmatch3;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;
import java.lang.reflect.*;
import java.util.function.BooleanSupplier;

/** Device-only regression: starts the actual Activity and sends real touch input. */
public class AdventureSmoke extends Instrumentation {
 private volatile MainActivity host; private AdventureView view; private String phase; private Throwable mainError;
 @Override public void callActivityOnResume(Activity activity){super.callActivityOnResume(activity);if(activity instanceof MainActivity)host=(MainActivity)activity;}
 @Override public void onCreate(Bundle args){super.onCreate(args);phase=args==null?"fresh":args.getString("phase","fresh");start();}
 private void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 private Object field(Object o,String n){try{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new RuntimeException(e);}}
 private void main(Runnable r){mainError=null;runOnMainSync(()->{try{r.run();}catch(Throwable t){mainError=t;}});if(mainError!=null)throw new RuntimeException(mainError);}
 private void settle(){waitForIdleSync();SystemClock.sleep(180);}
 private void until(BooleanSupplier f,long ms,String label){long end=SystemClock.uptimeMillis()+ms;while(SystemClock.uptimeMillis()<end){if(f.getAsBoolean())return;SystemClock.sleep(150);}throw new AssertionError("timeout: "+label);}
 private AccessibilityNodeInfo find(AccessibilityNodeInfo n,String text,String cls){
  if(n==null)return null;
  if((text==null||text.contentEquals(n.getText()==null?"":n.getText()))&&(cls==null||cls.contentEquals(n.getClassName()==null?"":n.getClassName())))return n;
  for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo got=find(n.getChild(i),text,cls);if(got!=null)return got;}return null;
 }
 private AccessibilityNodeInfo node(String text,String cls){return find(getUiAutomation().getRootInActiveWindow(),text,cls);}
 private void clickText(String text){until(()->{AccessibilityNodeInfo n=node(text,null);return n!=null&&n.isEnabled();},45000,text);AccessibilityNodeInfo n=node(text,null);check(n.performAction(AccessibilityNodeInfo.ACTION_CLICK),"click "+text);settle();}
 private void tap(float x,float y){
  final float[] xy=new float[2];main(()->{int[] loc=new int[2];view.getLocationOnScreen(loc);float s=(Float)field(view,"scale");xy[0]=loc[0]+(Float)field(view,"ox")+x*s;xy[1]=loc[1]+(Float)field(view,"oy")+y*s;});
  long t=SystemClock.uptimeMillis();MotionEvent d=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,xy[0],xy[1],0),u=MotionEvent.obtain(t,t+40,MotionEvent.ACTION_UP,xy[0],xy[1],0);
  sendPointerSync(d);sendPointerSync(u);d.recycle();u.recycle();settle();
 }
 private int coins(){return (Integer)field(view,"coins");}
 private AdventureEngine game(){return (AdventureEngine)field(view,"game");}
 private void shot(String name)throws Exception{Bitmap b=getUiAutomation().takeScreenshot();check(b!=null,"screenshot");File dir=new File(getTargetContext().getExternalFilesDir(null),"screenshots");dir.mkdirs();try(FileOutputStream o=new FileOutputStream(new File(dir,phase+"-"+name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,o);}b.recycle();}
 private void launch(){
  Intent i=new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);MainActivity started=(MainActivity)startActivitySync(i);if(host==null)host=started;
  try{shot("welcome");}catch(Exception e){throw new RuntimeException(e);}
  clickText("เข้าเกม");
  if(!phase.equals("resume")){
   until(()->node(null,"android.widget.EditText")!=null,10000,"age field");
   Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"17");
   check(node(null,"android.widget.EditText").performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args),"age input");clickText("ต่อไป");
  }
  until(()->field(host,"gameView")!=null,10000,"real game view");view=(AdventureView)field(host,"gameView");settle();
  check(field(host,"gameAudio")!=null,"audio initialized");check(view.isShown(),"game visible");
 }
 private void move(){
  SystemClock.sleep(550);int[] h=game().hint();check(h!=null,"hint");
  int before=game().moves;
  float x=12+h[0]%8*47+23.5f,y=280+h[0]/8*47+23.5f;
  final float[] xy=new float[3];main(()->{int[] loc=new int[2];view.getLocationOnScreen(loc);xy[2]=(Float)field(view,"scale");xy[0]=loc[0]+(Float)field(view,"ox")+x*xy[2];xy[1]=loc[1]+(Float)field(view,"oy")+y*xy[2];});
  float dx=(h[1]%8-h[0]%8)*15*xy[2],dy=(h[1]/8-h[0]/8)*15*xy[2];long at=SystemClock.uptimeMillis();
  for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(at,SystemClock.uptimeMillis(),action,xy[0]+(action==0?0:dx),xy[1]+(action==0?0:dy),0);sendPointerSync(e);e.recycle();}
  settle();
  check(game().moves==before-1,"short swipe consumed exactly one turn");
  check(!((java.util.List)field(view,"sparks")).isEmpty(),"match particles emitted");
  SystemClock.sleep(550); // Allow the visible move animation to finish before the next control.
 }
 private void startLevel(int n){main(()->{try{Method method=view.getClass().getDeclaredMethod("start",int.class);method.setAccessible(true);method.invoke(view,n);}catch(Exception e){throw new RuntimeException(e);}});settle();tap(200,578);}
 private void verifyGuardian()throws Exception{
  java.util.Map<Integer,GuardianStory> stories=GuardianStory.load(getTargetContext());check(stories.size()==32,"32 authored rescues");
  java.util.Set<String> titles=new java.util.HashSet<>();for(GuardianStory s:stories.values()){check(titles.add(s.title),"unique episode title");}
  main(()->{try{Method m=view.getClass().getDeclaredMethod("start",int.class);m.setAccessible(true);m.invoke(view,2);}catch(Exception e){throw new RuntimeException(e);}});
  SystemClock.sleep(1000);GuardianClock clock=(GuardianClock)field(view,"guardianClock");long full=clock.remainingMs;
  shot("guardian-kindness");SystemClock.sleep(2000);shot("guardian-incident");
  check(clock.remainingMs==full,"intro does not consume rescue time");
  SystemClock.sleep(2200);shot("guardian-arrival");
  tap(200,578);SystemClock.sleep(600);check(clock.remainingMs<full,"timer starts after skip");
  tap(40,35);long paused=clock.remainingMs;SystemClock.sleep(900);check(clock.remainingMs==paused,"pause freezes clock");tap(200,304);
  int[] inv=(int[])field(view,"inventory");main(()->{inv[3]=5;inv[4]=5;inv[9]=5;});
  tap(194,767);long frozen=clock.remainingMs;SystemClock.sleep(1000);check(clock.remainingMs==frozen,"freeze booster stops real clock");
  main(()->{clock.frozenMs=0;game().freeze=0;clock.remainingMs=5000;});
  tap(118,767);check(clock.remainingMs>18000,"extra turns extends time");
  SystemClock.sleep(600);tap(270,767);check(game().shield==1,"shield activated through touch");main(()->{clock.remainingMs=1;});SystemClock.sleep(250);check(!game().lost&&clock.remainingMs>8000&&game().shield==0,"shield protects deadline once");
  main(()->{clock.remainingMs=1;});SystemClock.sleep(900);check(game().lost,"deadline ends rescue");shot("guardian-timeout");
  for(int n:new int[]{2,4,8,14,16,22,26,28}){startLevel(n);main(()->{GuardianClock cc=(GuardianClock)field(view,"guardianClock");cc.remainingMs=cc.limitMs/4;});shot("guardian-mechanism-"+n);}
 }
 private void verifyRescue()throws Exception{
  int[] inv=(int[])field(view,"inventory");
  for(int level:new int[]{2,4,8,26}){startLevel(level);shot("rescue-"+level);}
  startLevel(2);
  main(()->{for(int i=0;i<inv.length;i++)inv[i]=20;});
  int[] order={6,0,7,8,1,2,9,3,4,5};
  for(int id:new int[]{6,0,7,8,1,2,9,3,4,5}){
   if(game().won||game().lost)startLevel(2);
   int slot=0;while(order[slot]!=id)slot++;
   int before=inv[id],moves=game().moves,danger=game().danger;
   SystemClock.sleep(600);tap(42+(slot%5)*76,700+(slot/5)*67);
   if(id==6){int[] pair=game().hint();tap(12+pair[0]%8*47+23.5f,280+pair[0]/8*47+23.5f);tap(12+pair[1]%8*47+23.5f,280+pair[1]/8*47+23.5f);}
   else if(id==0||id==1||id==2||id==7||id==8)tap(12+3*47+23.5f,280+3*47+23.5f);
   check(inv[id]==before-1+(game().won&&(Integer)field(view,"rewardItem")==id?1:0),"inventory decrement and earned reward "+id);check(game().moves==moves+(id==9?5:0),"booster turn cost "+id);
   check(game().danger==(id==9?Math.max(0,danger-5):danger),"booster danger cost "+id);
   shot("booster-"+id);
  }
  startLevel(2);main(()->{inv[1]=100;});shot("rescue-before");
  for(int i=0;i<80&&!game().won;i++){
   SystemClock.sleep(600);tap(346,700);int pos=(i*17)%64;
   if(!game().inside(pos/8,pos%8))pos=27;
   tap(12+pos%8*47+23.5f,280+pos/8*47+23.5f);
   if(i==2)shot("rescue-progress");
  }
  check(game().won,"rescue completed with real booster touches");SystemClock.sleep(3200);shot("rescue-celebration");SystemClock.sleep(3000);shot("rescue-victory");
  int rewarded=(Integer)field(view,"rewardItem");check(rewarded>=0,"win grants booster");int count=inv[rewarded];
  SystemClock.sleep(300);check(inv[rewarded]==count,"win reward credited once");
  startLevel(2);move();tap(40,35);tap(200,528);
 }
 @Override public void onStart(){
  Bundle result=new Bundle();
  try{
   ContentPatch.validate("{\"schema\":2,\"version\":1,\"levels\":{\"1\":{\"moves\":42,\"goal\":17,\"reward\":95}}}".getBytes("UTF-8"));
   boolean rejected=false;try{ContentPatch.validate("{\"schema\":2,\"version\":1,\"levels\":{\"1\":{\"moves\":0,\"goal\":17,\"reward\":95}}}".getBytes("UTF-8"));}catch(Exception expected){rejected=true;}check(rejected,"invalid patch rejected");
   if(!phase.equals("resume")){
    try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"adventure-patch.json"))){out.write("{\"schema\":2,\"version\":999,\"levels\":{\"1\":{\"moves\":42,\"goal\":17,\"reward\":95}}}".getBytes("UTF-8"));}
   }
   launch();shot("home");
   if(phase.equals("showcase")){
    for(int level:new int[]{2,4}){
     main(()->{try{Method m=view.getClass().getDeclaredMethod("start",int.class);m.setAccessible(true);m.invoke(view,level);}catch(Exception e){throw new RuntimeException(e);}});
     SystemClock.sleep(7800);for(int j=0;j<4&&!game().won&&!game().lost;j++)move();
     int[] inventory=(int[])field(view,"inventory");main(()->{inventory[1]=20;});
     for(int i=0;i<20&&!game().won&&!game().lost;i++){SystemClock.sleep(650);tap(346,700);int target=-1;for(int r=0;r<8;r++)for(int c=0;c<8;c++)if(game().hp[r][c]>0)target=r*8+c;if(target<0)target=(i*17)%64;if(!game().inside(target/8,target%8))target=27;tap(12+target%8*47+23.5f,280+target/8*47+23.5f);}
     SystemClock.sleep(7000);shot("showcase-"+level);
    }
    result.putString("stream","PASS: showcase\n");finish(Activity.RESULT_OK,result);return;
   }
   if(phase.equals("resume")){
    SharedPreferences exp=getTargetContext().getSharedPreferences("qa_expected",0);
    check(coins()==exp.getInt("coins",-1),"coins after process death");
    check(game()!=null&&game().moves==exp.getInt("moves",-1),"moves after process death");
    check(game().level.number==exp.getInt("level",-1),"level after process death");
    check(getTargetContext().getSharedPreferences("ghost_adventure_v1",0).getString("run","").equals(exp.getString("run","missing")),"exact saved board restored");
    tap(200,540);check(field(view,"screen").equals("play"),"resume button");move();shot("resumed-board");
   }else{
    check(coins()==150,"new wallet");
    tap(290,650);check(field(view,"screen").equals("shop"),"open shop");
    tap(100,240);clickText("ซื้อ 1 ชิ้น");check(coins()==50&&((int[])field(view,"inventory"))[0]==3,"purchase hammer");
    tap(290,240);clickText("ซื้อ 1 ชิ้น");check(coins()==50&&((int[])field(view,"inventory"))[1]==1,"reject insufficient funds");shot("shop");
    tap(40,40);check(field(view,"screen").equals("home"),"shop back");
    tap(200,540);check(field(view,"screen").equals("play")&&(Boolean)field(view,"brief"),"start level");
    tap(200,578);check(!(Boolean)field(view,"brief"),"dismiss instructions");
    check(game().moves==42&&game().level.goal==17&&game().level.reward==95,"downloaded balance wired into new game");
    move();
    SystemClock.sleep(550);tap(118,700);
    int p=-1;for(int r=0;r<8;r++)for(int c=0;c<8;c++)if(game().hp[r][c]>0)p=r*8+c;
    check(p>=0,"obstacle for hammer");int hp=game().hp[p/8][p%8];
    tap(12+p%8*47+23.5f,280+p/8*47+23.5f);
    check(((int[])field(view,"inventory"))[0]==2&&game().hp[p/8][p%8]<hp,"hammer touch consumed and damaged");shot("hammer-effect");
    for(int n=0;n<60&&!game().won&&!game().lost;n++)move();
    check(game().won||game().lost,"full real-touch game ended");SystemClock.sleep(6000);shot(game().won?"victory":"loss");
    tap(200,530);check(!game().won&&!game().lost,"next or retry");tap(200,578);move();
    tap(40,35);check((Boolean)field(view,"paused"),"pause");
    tap(200,375);check(field(view,"screen").equals("shop"),"shop while paused");
    tap(40,40);check(field(view,"screen").equals("play")&&(Boolean)field(view,"paused"),"return from shop");
    tap(200,304);check(!(Boolean)field(view,"paused"),"unpause");
    getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);until(()->!view.hasWindowFocus(),10000,"background focus lost");settle();
    Intent i=new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);getTargetContext().startActivity(i);until(()->view.isShown()&&view.hasWindowFocus(),10000,"foreground visible and focused");settle();
    check(view.isShown(),"foreground return");shot("board");
    tap(40,35);tap(200,528);check(field(view,"screen").equals("home"),"home from pause");
    verifyGuardian();
    verifyRescue();
    getTargetContext().getSharedPreferences("qa_expected",0).edit().putInt("coins",coins()).putInt("moves",game().moves).putInt("level",game().level.number).putString("run",getTargetContext().getSharedPreferences("ghost_adventure_v1",0).getString("run","")).commit();
   }
   result.putString("stream","PASS: real-entry "+phase+"; actual welcome, touch gameplay, shop, boosters, pause and persistence checks\n");finish(Activity.RESULT_OK,result);
  }catch(Throwable t){try{shot("failure");}catch(Exception ignored){}StringWriter s=new StringWriter();t.printStackTrace(new PrintWriter(s));result.putString("stream","FAIL: "+s+"\n");finish(Activity.RESULT_CANCELED,result);}
 }
}
