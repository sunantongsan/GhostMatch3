package com.sunantongsan.ghostmatch3;

import android.content.*;
import android.graphics.*;
import android.view.*;
import org.json.*;
import java.util.*;

/** New portrait adventure presentation; classic board renderer is deliberately not used. */
final class AdventureView extends View {
    private final MainActivity host;
    private final SharedPreferences prefs;
    private final Paint paint=new Paint(3);
    private final Bitmap atlas,background;
    private final ArrayList<Hit> hits=new ArrayList<>();
    private final int[] inventory=new int[6];
    private static final String[] ITEMS={"ค้อนเวท","ระเบิด","ลูกแก้วสี","หยุดภัย","โล่ผี","สลับใหม่"};
    private static final String[] DETAILS={"ทุบอุปสรรค 1 ชั้นในช่องที่เลือก","ระเบิดพื้นที่ 3 × 3 ช่อง","ล้างชิ้นที่เป็นสีเดียวกับช่องที่เลือก","หยุดอันตราย 3 ตา • ใช้ในด่านช่วยเหลือและบอส","กันบอส 1 ครั้ง หรือถอยภัยช่วยเหลือ 3 ตา","เรียงกระดานใหม่โดยไม่เสียตา"};
    private static final int[] PRICES={100,180,240,120,120,80};
    private static final int GOLD=0xffffd56a,WHITE=0xfffff5e2,INK=0xff231738,TEAL=0xff78e0d7;
    private String screen="home",returnFromShop="home",notice="";
    private int coins,highest,mapPage=0,selected=-1,item=-1,down=-1;
    private int insetTop,insetBottom,insetLeft,insetRight;
    private float scale=1,ox,oy;
    private long noticeUntil,animationUntil,lastInteraction=System.currentTimeMillis();
    private boolean credited,brief=false,paused=false,active=true;
    private AdventureEngine game;
    private int lastReward;
    private static class Hit {RectF box;Runnable action;Hit(RectF r,Runnable a){box=r;action=a;}}
    AdventureView(MainActivity activity){
        super(activity);host=activity;prefs=activity.getSharedPreferences("ghost_adventure_v1",Context.MODE_PRIVATE);
        coins=Math.max(0,prefs.getInt("gold",150));highest=Math.max(1,Math.min(120,prefs.getInt("highest",activity.getSharedPreferences("ghostmatch_progress",Context.MODE_PRIVATE).getInt("highest_level",1))));
        for(int i=0;i<6;i++)inventory[i]=Math.max(0,prefs.getInt("item_"+i,0));
        atlas=BitmapFactory.decodeResource(getResources(),R.drawable.adventure_atlas);
        background=BitmapFactory.decodeResource(getResources(),R.drawable.adventure_home);
        setFocusable(true);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("Ghost Match 3 ผจญภัย • แตะเล่น เลือกด่าน หรือร้านค้า");
        setOnApplyWindowInsetsListener((v,insets)->{insetTop=insets.getSystemWindowInsetTop();insetBottom=insets.getSystemWindowInsetBottom();insetLeft=insets.getSystemWindowInsetLeft();insetRight=insets.getSystemWindowInsetRight();invalidate();return insets;});
        restore();
    }
    void setActive(boolean value){active=value;if(value)invalidate();else save();}
    private void sound(int kind){host.playSound(kind);}
    private void message(String text){notice=text;noticeUntil=System.currentTimeMillis()+2600;invalidate();}
    private void save(){
        SharedPreferences.Editor e=prefs.edit().putInt("gold",coins).putInt("highest",highest);
        for(int i=0;i<6;i++)e.putInt("item_"+i,inventory[i]);
        if(game!=null){try{
            JSONObject j=new JSONObject();j.put("level",game.level.number);j.put("moves",game.moves);j.put("progress",game.progress);j.put("remaining",game.remaining);j.put("initial",game.initialObstacles);j.put("turns",game.turns);j.put("danger",game.danger);j.put("freeze",game.freeze);j.put("shield",game.shield);j.put("score",game.score);j.put("won",game.won);j.put("lost",game.lost);j.put("credited",credited);j.put("reward",lastReward);
            j.put("balanceMoves",game.level.moves);j.put("balanceGoal",game.level.goal);j.put("balanceReward",game.level.reward);
            JSONArray cells=new JSONArray();for(int r=0;r<8;r++)for(int c=0;c<8;c++){cells.put(game.tile[r][c]);cells.put(game.power[r][c]);cells.put(game.hp[r][c]);cells.put(game.obstacle[r][c]);}j.put("cells",cells);e.putString("run",j.toString());
        }catch(JSONException ignored){}}
        e.apply();
    }
    private void restore(){try{
        String raw=prefs.getString("run","");if(raw.isEmpty())return;
        JSONObject j=new JSONObject(raw);AdventureEngine restored=new AdventureEngine(j.getInt("level"),1,j.has("balanceMoves")?new int[]{j.getInt("balanceMoves"),j.getInt("balanceGoal"),j.getInt("balanceReward")}:null);
        JSONArray a=j.getJSONArray("cells");if(a.length()!=256)return;int k=0;
        for(int r=0;r<8;r++)for(int c=0;c<8;c++){restored.tile[r][c]=a.getInt(k++);restored.power[r][c]=a.getInt(k++);restored.hp[r][c]=a.getInt(k++);restored.obstacle[r][c]=a.getInt(k++);}
        restored.moves=j.getInt("moves");restored.progress=j.getInt("progress");restored.remaining=j.getInt("remaining");restored.initialObstacles=j.getInt("initial");restored.turns=j.getInt("turns");restored.danger=j.optInt("danger");restored.freeze=j.getInt("freeze");restored.shield=j.getInt("shield");restored.score=j.getInt("score");restored.won=j.getBoolean("won");restored.lost=j.getBoolean("lost");game=restored;credited=j.getBoolean("credited");lastReward=j.optInt("reward");
    }catch(Exception ignored){game=null;}}
    private void start(int n){game=new AdventureEngine(n,System.nanoTime(),ContentPatch.adventureLevel(host,n));credited=false;lastReward=0;selected=-1;item=-1;brief=true;paused=false;screen="play";lastInteraction=System.currentTimeMillis();save();invalidate();}
    private void afterAction(){
        lastInteraction=System.currentTimeMillis();animationUntil=lastInteraction+480;
        if(game.won&&!credited){
            lastReward=game.reward();coins+=lastReward;credited=true;highest=Math.min(120,Math.max(highest,game.level.number+1));
            int stars=game.moves>=game.level.moves/3?3:game.moves>=3?2:1;
            prefs.edit().putInt("stars_"+game.level.number,Math.max(stars,prefs.getInt("stars_"+game.level.number,0))).apply();
            sound(GameAudio.WIN);host.onLevelCompleted(game.level.number);
        }else if(game.lost)sound(GameAudio.LOSE);else sound(game.chains>1?GameAudio.MAGIC:GameAudio.MATCH);
        selected=-1;save();invalidate();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);hits.clear();canvas.drawColor(0xff15132b);
        float width=getWidth()-insetLeft-insetRight,height=getHeight()-insetTop-insetBottom;
        scale=Math.min(width/400f,height/820f);ox=insetLeft+(width-400*scale)/2;oy=insetTop+(height-820*scale)/2;
        if(scale<=0)return;
        // Full-bleed background extends beyond the safe interaction area.
        if(background!=null){paint.setAlpha(255);canvas.drawBitmap(background,null,new RectF(0,0,getWidth(),getHeight()),paint);}
        canvas.save();canvas.translate(ox,oy);canvas.scale(scale,scale);
        if(screen.equals("home"))drawHome(canvas);else if(screen.equals("shop"))drawShop(canvas);else if(screen.equals("map"))drawMap(canvas);else drawGame(canvas);
        if(System.currentTimeMillis()<noticeUntil){round(canvas,18,777,382,813,0xf5231738,16);text(canvas,notice,200,800,12,WHITE);}
        canvas.restore();
        host.updateBannerVisibility(false,0);
        if(active&&isShown()&&(screen.equals("play")||System.currentTimeMillis()<noticeUntil))postInvalidateDelayed(33);
    }
    private void text(Canvas c,String s,float x,float y,float size,int color){paint.setShader(null);paint.setColor(color);paint.setAlpha(255);paint.setTextSize(size);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextAlign(Paint.Align.CENTER);c.drawText(s,x,y,paint);}
    private void round(Canvas c,float l,float t,float r,float b,int color,float radius){paint.setShader(null);paint.setColor(color);paint.setAlpha(255);c.drawRoundRect(l,t,r,b,radius,radius,paint);}
    private void gradient(Canvas c,RectF box,int a,int b,float radius){paint.setShader(new LinearGradient(0,box.top,0,box.bottom,a,b,Shader.TileMode.CLAMP));paint.setAlpha(255);c.drawRoundRect(box,radius,radius,paint);paint.setShader(null);}
    private void button(Canvas c,float l,float t,float r,float b,String label,int color,Runnable action){
        round(c,l,t+4,r,b+4,0xff251530,18);gradient(c,new RectF(l,t,r,b),color,darken(color),18);text(c,label,(l+r)/2,(t+b)/2+6,18,color==GOLD?INK:WHITE);hits.add(new Hit(new RectF(l,t,r,b),action));
    }
    private int darken(int color){return Color.rgb((int)(Color.red(color)*.82),(int)(Color.green(color)*.82),(int)(Color.blue(color)*.82));}
    private void icon(Canvas c,int index,float x,float y,float size){if(atlas==null)return;int w=atlas.getWidth()/4,h=atlas.getHeight()/4;paint.setAlpha(255);paint.setShader(null);c.drawBitmap(atlas,new Rect((index%4)*w,(index/4)*h,(index%4+1)*w,(index/4+1)*h),new RectF(x-size/2,y-size/2,x+size/2,y+size/2),paint);}
    private void header(Canvas c,String title,Runnable back){button(c,16,16,68,61,"‹",0xff604783,back);round(c,222,16,384,61,0xf529193d,18);text(c,"ทอง  "+coins,303,45,18,GOLD);text(c,title,200,99,27,WHITE);}
    private void drawHome(Canvas c){
        round(c,16,18,192,61,0xef241637,18);text(c,"ทอง  "+coins,104,46,20,GOLD);
        button(c,320,18,384,61,host.soundEnabled()?"♪":"เงียบ",0xff604783,()->{host.toggleSound();invalidate();});
        text(c,"GHOST",200,151,51,GOLD);text(c,"MATCH 3",200,199,45,WHITE);text(c,"ผจญภัยอาณาจักรผี",200,234,22,TEAL);
        round(c,65,437,335,485,0xd8241637,20);text(c,"120 ด่าน • ช่วยเพื่อน • ล่าสมบัติ",200,467,16,WHITE);
        boolean resume=game!=null&&!game.won&&!game.lost;
        button(c,68,507,332,578,resume?"เล่นต่อ • ด่าน "+game.level.number:"เล่น • ด่าน "+highest,GOLD,()->{if(resume){screen="play";brief=false;paused=false;}else start(highest);invalidate();});
        button(c,24,610,192,691,"เลือกด่าน",0xff6953a1,()->{screen="map";mapPage=(highest-1)/20;invalidate();});
        button(c,208,610,376,691,"ร้านค้า",0xff318e8c,()->openShop("home"));
        text(c,"บอสทุก 6 ด่าน • โบนัสทุก 10 ด่าน",200,732,15,WHITE);
        button(c,45,755,195,796,"ความเป็นส่วนตัว",0xff604783,()->host.openPrivacyPolicy());
        button(c,205,755,355,796,host.needsPrivacyOptions()?"ตัวเลือกโฆษณา":"เสียง / ดนตรี",0xff604783,()->{if(host.needsPrivacyOptions())host.openPrivacyOptions();else host.toggleSound();invalidate();});
    }
    private void openShop(String from){returnFromShop=from;screen="shop";item=-1;invalidate();}
    private void drawShop(Canvas c){
        round(c,0,0,400,820,0xe51a1230,0);header(c,"ร้านเวทมนตร์ผีน้อย",()->{screen=returnFromShop;invalidate();});
        text(c,"ซื้อด้วยเหรียญทอง • เก็บไว้ใช้เมื่อพร้อม",200,130,14,TEAL);
        for(int i=0;i<6;i++){
            final int id=i;float x=18+(i%2)*190,y=151+(i/2)*204;
            round(c,x,y,x+174,y+188,0xff38264e,22);round(c,x+2,y+2,x+172,y+125,0xff15132b,20);
            icon(c,i+6,x+87,y+54,83);text(c,ITEMS[i],x+87,y+112,18,WHITE);text(c,"มี "+inventory[i]+" ชิ้น",x+87,y+137,13,TEAL);
            button(c,x+13,y+148,x+161,y+181,PRICES[i]+" ทอง",coins>=PRICES[i]?GOLD:0xff655b71,()->{
                new android.app.AlertDialog.Builder(host).setTitle(ITEMS[id]+" • "+PRICES[id]+" ทอง").setMessage(DETAILS[id]+"\nมีอยู่ "+inventory[id]+" ชิ้น")
                    .setNegativeButton("ยกเลิก",null).setPositiveButton("ซื้อ 1 ชิ้น",(d,w)->{if(coins<PRICES[id]){message("เหรียญทองยังไม่พอ");return;}coins-=PRICES[id];inventory[id]++;save();sound(GameAudio.MAGIC);message("ได้รับ "+ITEMS[id]);}).show();
            });
        }
    }
    private void drawMap(Canvas c){
        round(c,0,0,400,820,0xda1a1230,0);header(c,"เส้นทางผจญภัย",()->{screen="home";invalidate();});
        text(c,"ด่าน "+(mapPage*20+1)+" – "+(mapPage*20+20),200,136,18,TEAL);
        for(int i=0;i<20;i++){
            final int n=mapPage*20+i+1;AdventureEngine.Level lv=new AdventureEngine.Level(n);
            float x=19+(i%4)*96,y=163+(i/4)*104;int color=n>highest?0xff50465d:lv.kind==3?0xffa6508c:lv.bonus?0xffa57935:0xff438a86;
            button(c,x,y,x+77,y+69,""+n,color,()->{if(n<=highest)start(n);else message("ผ่านด่านก่อนหน้าเพื่อปลดล็อก");});
            text(c,lv.kind==3?(lv.bonus?"บอส + โบนัส":"บอส"):lv.bonus?"โบนัส":lv.kind==1?"ช่วยเพื่อน":"จับคู่",x+38,y+88,12,WHITE);
            int stars=prefs.getInt("stars_"+n,0);if(stars>0)text(c,stars==3?"★★★":stars==2?"★★":"★",x+38,y+66,12,GOLD);
        }
        button(c,26,715,138,766,"ก่อนหน้า",0xff604783,()->{mapPage=Math.max(0,mapPage-1);invalidate();});
        button(c,262,715,374,766,"ถัดไป",0xff604783,()->{mapPage=Math.min(5,mapPage+1);invalidate();});
        text(c,(mapPage+1)+" / 6",200,748,18,GOLD);
    }
    private void drawGame(Canvas c){
        if(game==null){screen="home";drawHome(c);return;}
        round(c,0,0,400,820,0xdd161329,0);
        AdventureEngine.Level lv=game.level;
        button(c,13,12,64,57,"Ⅱ",0xff604783,()->{paused=true;invalidate();});
        text(c,"ด่าน "+lv.number+" • "+lv.name()+(lv.bonus&&lv.kind==3?" + โบนัส":""),228,38,18,GOLD);
        round(c,16,72,384,133,0xff38264e,18);
        icon(c,lv.color,47,103,41);text(c,Math.min(lv.goal,game.progress)+" / "+lv.goal,107,109,18,WHITE);
        text(c,"อุปสรรค "+game.remaining,217,109,15,TEAL);text(c,"ตา "+game.moves,333,110,23,GOLD);
        drawScene(c);
        text(c,item>=0?"แตะช่องเพื่อใช้ "+ITEMS[item]:lv.objective(),200,319,14,WHITE);
        float bx=24,by=337,cell=44;
        round(c,bx-7,by-7,bx+352+7,by+352+7,GOLD,17);
        round(c,bx-4,by-4,bx+356,by+356,0xff15132b,15);
        float anim=Math.max(0,(animationUntil-System.currentTimeMillis())/480f);
        for(int r=0;r<8;r++)for(int col=0;col<8;col++)if(game.active[r][col]){
            int pos=r*8+col;float x=bx+col*cell,y=by+r*cell;
            round(c,x+1,y+1,x+43,y+43,(r+col)%2==0?0xff25213d:0xff302944,6);
            float size=41;if(game.changed.contains(pos))size*=1-.17f*anim;
            icon(c,game.hp[r][col]>0?12+game.obstacle[r][col]:game.tile[r][col],x+22,y+22,size);
            if(game.hp[r][col]>1)text(c,""+game.hp[r][col],x+34,y+38,13,GOLD);
            if(game.power[r][col]>0&&game.hp[r][col]==0){round(c,x+3,y+27,x+41,y+42,0xeeb48638,5);text(c,new String[]{"","↔","↕","✦","★"}[game.power[r][col]],x+22,y+40,17,WHITE);}
            if(selected==pos){paint.setColor(GOLD);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);c.drawRoundRect(x+1,y+1,x+43,y+43,7,7,paint);paint.setStyle(Paint.Style.FILL);}
        }
        if(System.currentTimeMillis()-lastInteraction>6000&&!game.won&&!game.lost&&!paused&&!brief){int[] hint=game.hint();if(hint!=null){for(int pos:hint){paint.setColor(TEAL);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);float x=24+pos%8*44,y=337+pos/8*44;c.drawRoundRect(x+2,y+2,x+42,y+42,8,8,paint);paint.setStyle(Paint.Style.FILL);}}}
        for(int i=0;i<6;i++){final int id=i;float x=14+i*63;round(c,x,711,x+58,773,item==i?0xff9a773c:0xff38264e,12);icon(c,i+6,x+29,737,42);text(c,""+inventory[i],x+47,730,13,GOLD);text(c,ITEMS[i],x+29,767,10,WHITE);hits.add(new Hit(new RectF(x,711,x+58,773),()->{
            if(inventory[id]==0){message("ยังไม่มี "+ITEMS[id]+" • ซื้อได้ในร้านค้า");return;}
            if(id<3){item=item==id?-1:id;selected=-1;message(DETAILS[id]);}
            else if(game.use(id,-1)){inventory[id]--;afterAction();}else message("ใช้ไม่ได้ในตอนนี้");
        }));}
        text(c,"ทอง "+coins+"   •   รางวัลผ่านด่าน "+lv.reward+"+",200,803,14,GOLD);
        if(brief)drawBrief(c);
        else if(paused)drawPause(c);
        else if(game.won||game.lost)drawResult(c);
    }
    private void drawScene(Canvas c){
        AdventureEngine.Level lv=game.level;float y=145;
        gradient(c,new RectF(16,y,384,296),0xff305b6a,0xff272043,18);
        text(c,AdventureEngine.WORLDS[lv.world],200,166,13,TEAL);
        float tick=(float)Math.sin(System.currentTimeMillis()/260.0);
        if(lv.kind==AdventureEngine.BOSS){
            icon(c,lv.variant%2==0?4:1,294,219+tick*3,89);icon(c,0,84,229-tick*3,65);
            float health=Math.max(0,1-game.progress/(float)lv.goal);round(c,198,273,366,283,0xff15132b,5);round(c,198,273,198+168*health,283,0xfff071a6,5);
            text(c,game.freeze>0?"แช่แข็ง "+game.freeze+" ตา":"บอสพ่นน้ำแข็งใน "+(4-game.turns%4)+" ตา",200,190,13,GOLD);
        }else if(lv.kind==AdventureEngine.RESCUE){
            float cleared=1-game.remaining/(float)Math.max(1,game.initialObstacles),danger=Math.min(1,game.danger/(float)lv.moves);
            round(c,34,262,366,276,0xff8c718c,6);
            icon(c,0,62+cleared*220,230+tick*3,59);icon(c,15,342,231,57);
            if(lv.variant==0){paint.setColor(0x9935c9eb);c.drawRect(24,289-danger*78,376,290,paint);}
            else if(lv.variant==1)icon(c,12,40+danger*90,221,60);
            else if(lv.variant==2)icon(c,14,300-danger*100,215,70);
            else icon(c,7,35+danger*70,224,58);
            text(c,game.freeze>0?"หยุดภัย "+game.freeze+" ตา":"ภัยขยับหลังเดิน • เหลือ "+Math.max(0,lv.moves-game.danger)+" ตา",200,190,13,GOLD);
        }else if(lv.kind==AdventureEngine.BONUS){
            icon(c,15,200,232+tick*2,96);icon(c,0,84,238,51);text(c,"เปิดหีบ • ทองพิเศษ "+lv.reward+"+",200,190,14,GOLD);
        }else{
            icon(c,0,83,233+tick*3,72);for(int i=0;i<4;i++)icon(c,12+i,167+i*50,237,43);
            text(c,"จับคู่ข้างอุปสรรคเพื่อทำลาย",200,190,14,GOLD);
        }
        if(game.shield>0)icon(c,10,354,169,28);
    }
    private void overlay(Canvas c,String title){hits.clear();round(c,0,0,400,820,0xb8100b20,0);round(c,25,192,375,647,0xff39244f,25);round(c,31,198,369,641,0xff241734,22);text(c,title,200,239,26,GOLD);}
    private void drawBrief(Canvas c){
        overlay(c,game.level.name());icon(c,game.level.kind==3?4:game.level.kind==2?15:0,200,303,83);
        text(c,game.level.objective(),200,367,17,WHITE);
        text(c,"เก็บสีเป้าหมาย "+game.level.goal+" ชิ้น",200,403,17,TEAL);
        text(c,game.level.kind==0||game.level.kind==1?"พร้อมเคลียร์อุปสรรคทั้งหมด":"ใช้คอมโบเพื่อไปถึงเป้าหมาย",200,434,16,WHITE);
        text(c,"รางวัล "+game.level.reward+" ทอง + โบนัสตาที่เหลือ",200,475,15,GOLD);
        text(c,"ลากสลับ 3 ชิ้น • เรียง 4–5 ได้พลังพิเศษ",200,509,14,WHITE);
        button(c,75,548,325,608,"เริ่มผจญภัย",GOLD,()->{brief=false;lastInteraction=System.currentTimeMillis();invalidate();});
    }
    private void drawPause(Canvas c){
        overlay(c,"พักผจญภัย");button(c,68,278,332,331,"เล่นต่อ",GOLD,()->{paused=false;invalidate();});
        button(c,68,352,332,405,"ร้านค้า",0xff318e8c,()->openShop("play"));
        button(c,68,426,332,479,host.soundEnabled()?"ปิดเสียง":"เปิดเสียง",0xff604783,()->{host.toggleSound();invalidate();});
        button(c,68,500,332,553,"กลับหน้าแรก",0xff604783,()->{screen="home";save();invalidate();});
        button(c,68,574,332,619,"เริ่มด่านใหม่",0xff604783,()->new android.app.AlertDialog.Builder(host).setTitle("เริ่มใหม่?").setMessage("ความคืบหน้าในด่านนี้จะเริ่มใหม่ ของที่ใช้ไปแล้วไม่คืน").setNegativeButton("ยกเลิก",null).setPositiveButton("เริ่มใหม่",(d,w)->start(game.level.number)).show());
    }
    private void drawResult(Canvas c){
        boolean win=game.won;overlay(c,win?"สำเร็จแล้ว!":"อีกนิดเดียว!");icon(c,win?15:0,200,307,95);
        text(c,win?"+ "+lastReward+" เหรียญทอง":"ลองวางแผนใหม่ได้ครับ",200,389,25,win?GOLD:WHITE);
        text(c,win?AdventureEngine.JOKES[game.level.number%6]:"ของช่วยเล่นเลือกใช้ได้จากด้านล่าง",200,431,13,TEAL);
        text(c,win?"เหรียญรวม "+coins:"เป้าหมายที่ทำได้ "+game.progress+" / "+game.level.goal,200,469,17,WHITE);
        button(c,67,505,333,559,win?(game.level.number==120?"ครบ 120 ด่าน!":"ด่านต่อไป"):"ลองอีกครั้ง",GOLD,()->{
            if(win&&game.level.number==120){screen="map";mapPage=5;invalidate();}
            else if(win)host.showAdBeforeNextLevel(()->start(game.level.number+1));else start(game.level.number);
        });
        button(c,67,577,194,620,"หน้าแรก",0xff604783,()->{screen="home";invalidate();});
        button(c,206,577,333,620,"ร้านค้า",0xff318e8c,()->openShop("play"));
    }
    private int cellAt(float x,float y){int col=(int)((x-24)/44),r=(int)((y-337)/44);return x>=24&&x<376&&y>=337&&y<689&&game!=null&&game.inside(r,col)?r*8+col:-1;}
    private void tapCell(int pos){
        if(pos<0)return;if(item>=0){if(game.use(item,pos)){inventory[item]--;item=-1;afterAction();}return;}
        if(!game.movable(pos/8,pos%8)){message(AdventureEngine.OBSTACLES[game.obstacle[pos/8][pos%8]]+" • จับคู่ข้าง ๆ เพื่อทำลาย");return;}
        if(selected>=0&&selected!=pos){if(game.move(selected,pos)){afterAction();return;}sound(GameAudio.INVALID);}
        selected=selected==pos?-1:pos;invalidate();
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent e){
        if(scale<=0)return true;float x=(e.getX()-ox)/scale,y=(e.getY()-oy)/scale;
        if(e.getAction()==MotionEvent.ACTION_DOWN){down=screen.equals("play")?cellAt(x,y):-1;return true;}
        if(e.getAction()==MotionEvent.ACTION_CANCEL){down=-1;return true;}
        if(e.getAction()!=MotionEvent.ACTION_UP)return true;
        performClick();lastInteraction=System.currentTimeMillis();
        if(screen.equals("play")&&!brief&&!paused&&!game.won&&!game.lost&&System.currentTimeMillis()<animationUntil)return true;
        for(Hit hit:new ArrayList<>(hits))if(hit.box.contains(x,y)){sound(GameAudio.SWAP);hit.action.run();return true;}
        if(screen.equals("play")&&!brief&&!paused&&!game.won&&!game.lost){int pos=cellAt(x,y);if(down>=0&&pos>=0&&pos!=down&&item<0){if(game.move(down,pos))afterAction();else sound(GameAudio.INVALID);}else tapCell(pos);}
        down=-1;return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
