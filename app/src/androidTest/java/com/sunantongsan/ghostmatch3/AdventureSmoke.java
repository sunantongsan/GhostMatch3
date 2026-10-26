package com.sunantongsan.ghostmatch3;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;

/** Runs on a disposable emulator only. Produces real Android Canvas screenshots. */
public class AdventureSmoke extends Instrumentation {
 private MainActivity host;private AdventureView view;private Throwable failure;
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 private void set(String name,Object value)throws Exception{Field f=AdventureView.class.getDeclaredField(name);f.setAccessible(true);f.set(view,value);}
 private Object get(String name)throws Exception{Field f=AdventureView.class.getDeclaredField(name);f.setAccessible(true);return f.get(view);}
 private void invoke(String name,Class<?>[] types,Object...args)throws Exception{Method m=AdventureView.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(view,args);}
 private void shot(String name)throws Exception{
  Bitmap b=Bitmap.createBitmap(400,820,Bitmap.Config.ARGB_8888);view.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(820,View.MeasureSpec.EXACTLY));view.layout(0,0,400,820);view.draw(new Canvas(b));
  File dir=new File(getTargetContext().getExternalFilesDir(null),"screenshots");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
 }
 @Override public void onStart(){
  Bundle result=new Bundle();
  try{
   getTargetContext().getSharedPreferences("ghost_adventure_v1",0).edit().clear().commit();
   Intent intent=new Intent(getTargetContext(),MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);host=(MainActivity)startActivitySync(intent);
   runOnMainSync(()->{try{
    view=new AdventureView(host);host.setContentView(view);view.setActive(false);
    shot("01-home");set("screen","shop");shot("02-shop");
    invoke("start",new Class<?>[]{int.class},1);set("brief",false);shot("03-board");
    invoke("start",new Class<?>[]{int.class},2);set("brief",false);shot("04-rescue");
    AdventureEngine game=(AdventureEngine)get("game");int[] move=game.hint();if(!game.move(move[0],move[1]))throw new AssertionError("move");invoke("afterAction",new Class<?>[]{});
    invoke("start",new Class<?>[]{int.class},6);set("brief",false);shot("05-boss");
    invoke("start",new Class<?>[]{int.class},10);set("brief",false);shot("06-bonus");
    invoke("start",new Class<?>[]{int.class},30);set("brief",false);shot("07-boss-bonus");
    game=(AdventureEngine)get("game");game.progress=game.level.goal;game.finish();int before=(Integer)get("coins");invoke("afterAction",new Class<?>[]{});int after=(Integer)get("coins");if(after<=before)throw new AssertionError("gold not awarded");invoke("afterAction",new Class<?>[]{});if((Integer)get("coins")!=after)throw new AssertionError("double reward");shot("08-victory");
    AdventureView restored=new AdventureView(host);Field cf=AdventureView.class.getDeclaredField("coins");cf.setAccessible(true);if(cf.getInt(restored)!=after)throw new AssertionError("wallet restore");
    set("screen","map");shot("09-map");
   }catch(Throwable t){failure=t;}});
   if(failure!=null)throw new RuntimeException(failure);
   result.putString("stream","PASS: screens, move, gold award, duplicate protection, wallet persistence\n");finish(Activity.RESULT_OK,result);
  }catch(Throwable t){result.putString("stream","FAIL: "+t+"\n");finish(Activity.RESULT_CANCELED,result);}
 }
}
