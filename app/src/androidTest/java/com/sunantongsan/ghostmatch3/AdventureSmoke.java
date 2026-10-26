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
  float x=12+h[0]%8*47+23.5f,y=237+h[0]/8*47+23.5f;
  final float[] xy=new float[3];main(()->{int[] loc=new int[2];view.getLocationOnScreen(loc);xy[2]=(Float)field(view,"scale");xy[0]=loc[0]+(Float)field(view,"ox")+x*xy[2];xy[1]=loc[1]+(Float)field(view,"oy")+y*xy[2];});
  float dx=(h[1]%8-h[0]%8)*15*xy[2],dy=(h[1]/8-h[0]/8)*15*xy[2];long at=SystemClock.uptimeMillis();
  for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(at,SystemClock.uptimeMillis(),action,xy[0]+(action==0?0:dx),xy[1]+(action==0?0:dy),0);sendPointerSync(e);e.recycle();}
  settle();
  check(game().moves==before-1,"short swipe consumed exactly one turn");
  check(!((java.util.List)field(view,"sparks")).isEmpty(),"match particles emitted");
  SystemClock.sleep(550); // Allow the visible move animation to finish before the next control.
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
    tap(100,315);clickText("ซื้อ 1 ชิ้น");check(coins()==50&&((int[])field(view,"inventory"))[0]==1,"purchase hammer");
    tap(290,315);clickText("ซื้อ 1 ชิ้น");check(coins()==50&&((int[])field(view,"inventory"))[1]==0,"reject insufficient funds");shot("shop");
    tap(40,40);check(field(view,"screen").equals("home"),"shop back");
    tap(200,540);check(field(view,"screen").equals("play")&&(Boolean)field(view,"brief"),"start level");
    tap(200,578);check(!(Boolean)field(view,"brief"),"dismiss instructions");
    check(game().moves==42&&game().level.goal==17&&game().level.reward==95,"downloaded balance wired into new game");
    move();
    SystemClock.sleep(550);tap(43,665);
    int p=-1;for(int r=0;r<8;r++)for(int c=0;c<8;c++)if(game().hp[r][c]>0)p=r*8+c;
    check(p>=0,"obstacle for hammer");int hp=game().hp[p/8][p%8];
    tap(12+p%8*47+23.5f,237+p/8*47+23.5f);
    check(((int[])field(view,"inventory"))[0]==0&&game().hp[p/8][p%8]<hp,"hammer touch consumed and damaged");shot("hammer-effect");
    for(int n=0;n<60&&!game().won&&!game().lost;n++)move();
    check(game().won||game().lost,"full real-touch game ended");SystemClock.sleep(1100);shot(game().won?"victory":"loss");
    tap(200,530);check(!game().won&&!game().lost,"next or retry");tap(200,578);move();
    tap(40,35);check((Boolean)field(view,"paused"),"pause");
    tap(200,375);check(field(view,"screen").equals("shop"),"shop while paused");
    tap(40,40);check(field(view,"screen").equals("play")&&(Boolean)field(view,"paused"),"return from shop");
    tap(200,304);check(!(Boolean)field(view,"paused"),"unpause");
    getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);settle();
    Intent i=new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);getTargetContext().startActivity(i);until(()->view.isShown(),10000,"foreground visible");settle();
    check(view.isShown(),"foreground return");shot("board");
    tap(40,35);tap(200,528);check(field(view,"screen").equals("home"),"home from pause");
    getTargetContext().getSharedPreferences("qa_expected",0).edit().putInt("coins",coins()).putInt("moves",game().moves).putInt("level",game().level.number).putString("run",getTargetContext().getSharedPreferences("ghost_adventure_v1",0).getString("run","")).commit();
   }
   result.putString("stream","PASS: real-entry "+phase+"; actual welcome, touch gameplay, shop, boosters, pause and persistence checks\n");finish(Activity.RESULT_OK,result);
  }catch(Throwable t){try{shot("failure");}catch(Exception ignored){}StringWriter s=new StringWriter();t.printStackTrace(new PrintWriter(s));result.putString("stream","FAIL: "+s+"\n");finish(Activity.RESULT_CANCELED,result);}
 }
}
