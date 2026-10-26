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
    private final RescueArt art;
    private float shownRescue,shownDanger;
    private int rewardItem=-1;
    private final ArrayList<Hit> hits=new ArrayList<>();
    private final int[] inventory=new int[10];
    private static final String[] ITEMS={"ค้อนเวท","ระเบิด","ลูกแก้วสี","หยุดภัย","โล่ผี","สลับใหม่","สลับฟรี","ล้างแถว","ล้างคอลัมน์","เพิ่ม 5 ตา"};
    private static final String[] DETAILS={"ทุบอุปสรรค 1 ชั้นในช่องที่เลือก","ระเบิดพื้นที่ 3 × 3 ช่อง","ล้างชิ้นที่เป็นสีเดียวกับช่องที่เลือก","หยุดอันตราย 3 ตา • ใช้ในด่านช่วยเหลือและบอส","กันบอส 1 ครั้ง หรือถอยภัยช่วยเหลือ 3 ตา","เรียงกระดานใหม่โดยไม่เสียตา","เลือกสองช่องติดกันเพื่อสลับฟรี","กวาดทั้งแถวที่เลือก","กวาดทั้งคอลัมน์ที่เลือก","เพิ่ม 5 ตา และถอยภัยช่วยเหลือ 5 ตา"};
    private static final int[] PRICES={100,180,240,120,120,80,100,160,160,150};
    private static final int GOLD=0xffffd56a,WHITE=0xfffff5e2,INK=0xff231738,TEAL=0xff78e0d7;
    private String screen="home",returnFromShop="home",notice="";
    private int coins,highest,mapPage=0,selected=-1,item=-1,down=-1;
    private int insetTop,insetBottom,insetLeft,insetRight;
    private float scale=1,ox,oy,downX,downY;
    private boolean swiped;
    private long fxStart,resultAt;
    private int[] beforeTiles=new int[64];
    private int movingFrom=-1,movingTo=-1;
    private int fxKind=-1,fxTarget=-1;
    private final ArrayList<float[]> sparks=new ArrayList<>();
    private final Random fxRandom=new Random();
    private static final float BX=12,BY=280,CELL=47;
    private boolean tryMove(int a,int b){
        for(int n=0;n<64;n++)beforeTiles[n]=game.tile[n/8][n%8];
        boolean powered=a>=0&&a<64&&b>=0&&b<64&&(game.power[a/8][a%8]>0||game.power[b/8][b%8]>0);
        boolean free=item==6;
        if(!(free?game.freeSwap(a,b):game.move(a,b)))return false;
        if(free){inventory[6]--;item=-1;}
        movingFrom=a;movingTo=b;effects(free?6:powered?1:-1,b);return true;
    }
    private void effects(int kind,int target){
        fxKind=kind;fxTarget=target;fxStart=System.currentTimeMillis();sparks.clear();
        Collection<Integer> cells=game.changed;
        if(kind==3||kind==4||kind==5||kind==9){ArrayList<Integer> all=new ArrayList<>();for(int n=0;n<64;n++)if(game.inside(n/8,n%8))all.add(n);cells=all;}
        for(int pos:cells)for(int k=0;k<7;k++){
            double a=fxRandom.nextDouble()*Math.PI*2;float speed=35+fxRandom.nextFloat()*95;
            sparks.add(new float[]{BX+(pos%8+.5f)*CELL,BY+(pos/8+.5f)*CELL,(float)Math.cos(a)*speed,(float)Math.sin(a)*speed,3+fxRandom.nextFloat()*4,fxRandom.nextInt(6)});
        }
        if(kind>=0&&kind!=6)movingFrom=-1;
        if(kind==1)sound(GameAudio.EXPLOSION);else if(kind>=0)sound(GameAudio.MAGIC);
    }
    private void drawEffects(Canvas c){
        float t=(System.currentTimeMillis()-fxStart)/1000f;if(t<0||t>1.15f)return;
        int[] colors={0xffffd56a,0xff91f5ff,0xffff93d7,0xffb3ff98,0xffbf9cff,0xffffffff};
        for(float[] p:sparks){paint.setColor(colors[(int)p[5]]);paint.setAlpha((int)(255*Math.max(0,1-t/1.15f)));float x=p[0]+p[2]*t,y=p[1]+p[3]*t+65*t*t;float r=p[4]*(1-t*.6f);c.drawCircle(x,y,r,paint);}
        for(int[] cast:game.casts)art.spell(c,cast[1],cast[0],t,BX,BY,CELL);
        if(fxKind>=0){
            float x=fxTarget>=0?BX+(fxTarget%8+.5f)*CELL:200,y=fxTarget>=0?BY+(fxTarget/8+.5f)*CELL:420;
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(5*(1-t/1.15f));paint.setColor(fxKind==3?TEAL:GOLD);paint.setAlpha((int)(220*Math.max(0,1-t/1.15f)));
            c.drawCircle(x,y,15+t*(fxKind==1?155:95),paint);paint.setStyle(Paint.Style.FILL);
            if(fxKind==0){c.save();c.rotate(-35+Math.min(1,t*5)*65,x,y);art.booster(c,0,x,y-40*(1-Math.min(1,t*4)),80);c.restore();}
            if(fxKind==2){paint.setColor(TEAL);paint.setAlpha((int)(180*Math.max(0,1-t)));paint.setStrokeWidth(2);for(int pos:game.changed)c.drawLine(x,y,BX+(pos%8+.5f)*CELL,BY+(pos/8+.5f)*CELL,paint);}
            if(fxKind==3||fxKind==4||fxKind==5||fxKind==9){art.booster(c,fxKind,200,440,85+25*(float)Math.sin(t*Math.PI));text(c,ITEMS[fxKind],200,510,25,GOLD);}
        }
        if(game.chains>1&&t<1)text(c,"คอมโบ × "+game.chains,200,273-t*15,25,GOLD);
        paint.setAlpha(255);
    }
    private long noticeUntil,animationUntil,lastInteraction=System.currentTimeMillis();
    private boolean credited,brief=false,paused=false,active=true;
    private AdventureEngine game;
    private int lastReward;
    private static class Hit {RectF box;Runnable action;Hit(RectF r,Runnable a){box=r;action=a;}}
    AdventureView(MainActivity activity){
        super(activity);host=activity;prefs=activity.getSharedPreferences("ghost_adventure_v1",Context.MODE_PRIVATE);
        coins=Math.max(0,prefs.getInt("gold",150));highest=Math.max(1,Math.min(120,prefs.getInt("highest",activity.getSharedPreferences("ghostmatch_progress",Context.MODE_PRIVATE).getInt("highest_level",1))));
        for(int i=0;i<inventory.length;i++)inventory[i]=Math.max(0,prefs.getInt("item_"+i,0));
        // One-time restoration pack also reaches existing players; never resets their purchases.
        if(!prefs.getBoolean("classic_pack_39",false)){
            int[] gifts={2,1,1,1,1,1,2,1,1,2};SharedPreferences.Editor gift=prefs.edit();
            for(int i=0;i<inventory.length;i++){inventory[i]+=gifts[i];gift.putInt("item_"+i,inventory[i]);}
            gift.putBoolean("classic_pack_39",true).apply();
        }
        art=new RescueArt(getResources());
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
        for(int i=0;i<inventory.length;i++)e.putInt("item_"+i,inventory[i]);
        if(game!=null){try{
            JSONObject j=new JSONObject();j.put("level",game.level.number);j.put("moves",game.moves);j.put("progress",game.progress);j.put("remaining",game.remaining);j.put("initial",game.initialObstacles);j.put("turns",game.turns);j.put("danger",game.danger);j.put("freeze",game.freeze);j.put("shield",game.shield);j.put("score",game.score);j.put("won",game.won);j.put("lost",game.lost);j.put("credited",credited);j.put("reward",lastReward);j.put("rewardItem",rewardItem);
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
        restored.moves=j.getInt("moves");restored.progress=j.getInt("progress");restored.remaining=j.getInt("remaining");restored.initialObstacles=j.getInt("initial");restored.turns=j.getInt("turns");restored.danger=j.optInt("danger");restored.freeze=j.getInt("freeze");restored.shield=j.getInt("shield");restored.score=j.getInt("score");restored.won=j.getBoolean("won");restored.lost=j.getBoolean("lost");game=restored;credited=j.getBoolean("credited");lastReward=j.optInt("reward");rewardItem=j.optInt("rewardItem",-1);shownRescue=rescueProgress();shownDanger=game.danger/(float)game.level.moves;
    }catch(Exception ignored){game=null;}}
    private void start(int n){sparks.clear();fxStart=0;resultAt=0;fxKind=-1;movingFrom=-1;game=new AdventureEngine(n,System.nanoTime(),ContentPatch.adventureLevel(host,n));credited=false;lastReward=0;rewardItem=-1;shownRescue=0;shownDanger=0;selected=-1;item=-1;brief=true;paused=false;screen="play";lastInteraction=System.currentTimeMillis();save();invalidate();}
    private void afterAction(){
        lastInteraction=System.currentTimeMillis();animationUntil=lastInteraction+480;
        if(fxStart<lastInteraction-50)effects(-1,-1);
        if(game.won&&!credited){
            lastReward=game.reward();coins+=lastReward;credited=true;highest=Math.min(120,Math.max(highest,game.level.number+1));
            int stars=game.moves>=game.level.moves/3?3:game.moves>=3?2:1;
            prefs.edit().putInt("stars_"+game.level.number,Math.max(stars,prefs.getInt("stars_"+game.level.number,0))).apply();
            rewardItem=new int[]{0,6,7,8,1,2,9}[game.level.number%7];inventory[rewardItem]++;
            resultAt=lastInteraction+2200;sound(GameAudio.WIN);host.onLevelCompleted(game.level.number);
        }else if(game.lost){resultAt=lastInteraction+600;sound(GameAudio.LOSE);}else if(fxKind<0)sound(game.chains>1?GameAudio.MAGIC:GameAudio.MATCH);
        selected=-1;save();invalidate();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);hits.clear();canvas.drawColor(0xff15132b);
        float width=getWidth()-insetLeft-insetRight,height=getHeight()-insetTop-insetBottom;
        float designHeight=820;scale=Math.min(width/400f,height/designHeight);ox=insetLeft+(width-400*scale)/2;oy=insetTop+(height-designHeight*scale)/2;
        if(scale<=0)return;
        // Full-bleed background extends beyond the safe interaction area.
        if(background!=null){paint.setAlpha(255);canvas.drawBitmap(background,null,new RectF(0,0,getWidth(),getHeight()),paint);}
        canvas.save();canvas.translate(ox,oy);canvas.scale(scale,scale);
        if(screen.equals("home"))drawHome(canvas);else if(screen.equals("shop"))drawShop(canvas);else if(screen.equals("map"))drawMap(canvas);else drawGame(canvas);
        if(System.currentTimeMillis()<noticeUntil){round(canvas,10,391,390,436,0xf5231738,16);text(canvas,notice,200,419,12,WHITE);}
        canvas.restore();
        host.updateBannerVisibility(false,0);
        if(active&&isShown()&&(screen.equals("play")||System.currentTimeMillis()<noticeUntil))postInvalidateDelayed(33);
    }
    private void text(Canvas c,String s,float x,float y,float size,int color){paint.setShader(null);paint.setColor(color);paint.setAlpha(255);paint.setTextSize(size);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextAlign(Paint.Align.CENTER);c.drawText(s,x,y,paint);}
    private void round(Canvas c,float l,float t,float r,float b,int color,float radius){paint.setShader(null);paint.setColor(color);c.drawRoundRect(l,t,r,b,radius,radius,paint);}
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
        for(int i=0;i<inventory.length;i++){
            final int id=i;float x=18+(i%2)*190,y=148+(i/2)*128;
            round(c,x,y,x+174,y+119,0xff38264e,16);
            art.booster(c,i,x+35,y+34,49);text(c,ITEMS[i],x+111,y+30,16,WHITE);text(c,"มี "+inventory[i]+" ชิ้น",x+111,y+53,13,TEAL);
            button(c,x+12,y+74,x+162,y+108,PRICES[i]+" ทอง",coins>=PRICES[i]?GOLD:0xff655b71,()->{
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
        round(c,16,61,384,99,0xff38264e,12);
        icon(c,lv.color,42,80,32);text(c,Math.min(lv.goal,game.progress)+" / "+lv.goal,108,86,17,WHITE);
        text(c,"อุปสรรค "+game.remaining,217,86,14,TEAL);text(c,"ตา "+game.moves,334,87,22,GOLD);
        drawRescueScene(c);
        text(c,item>=0?"แตะช่องเพื่อใช้ "+ITEMS[item]:lv.objective(),200,272,13,WHITE);
        float bx=BX,by=BY,cell=CELL;
        round(c,bx-7,by-7,bx+376+5,by+376+5,GOLD,17);
        round(c,bx-4,by-4,bx+380,by+380,0xff15132b,15);
        float anim=Math.max(0,(animationUntil-System.currentTimeMillis())/480f);
        for(int r=0;r<8;r++)for(int col=0;col<8;col++)if(game.active[r][col]){
            int pos=r*8+col;float x=bx+col*cell,y=by+r*cell;
            round(c,x+1,y+1,x+46,y+46,(r+col)%2==0?0xff25213d:0xff302944,6);
            float size=46;if(game.changed.contains(pos))size*=1-.17f*anim;
            float elapsed=(System.currentTimeMillis()-fxStart)/1000f;
            float drop=game.changed.contains(pos)&&elapsed>=.16f&&elapsed<.48f?-CELL*(1-(elapsed-.16f)/.32f):0;
            int image=game.hp[r][col]>0?12+game.obstacle[r][col]:game.tile[r][col];
            float px=x+23.5f,py=y+23.5f+drop;
            if((fxKind<0||fxKind==6)&&elapsed<.16f&&movingFrom>=0){image=beforeTiles[pos];float fraction=elapsed/.16f;if(pos==movingFrom){px+=(movingTo%8-movingFrom%8)*CELL*fraction;py+=(movingTo/8-movingFrom/8)*CELL*fraction;}else if(pos==movingTo){px+=(movingFrom%8-movingTo%8)*CELL*fraction;py+=(movingFrom/8-movingTo/8)*CELL*fraction;}}
            c.save();c.clipRect(BX,BY,BX+376,BY+376);icon(c,image,px,py,size);c.restore();
            if(game.hp[r][col]>1)text(c,""+game.hp[r][col],x+34,y+38,13,GOLD);
            if(game.power[r][col]>0&&game.hp[r][col]==0){art.booster(c,new int[]{0,7,8,1,2}[game.power[r][col]],x+23.5f,y+23.5f,43);}
            if(selected==pos){paint.setColor(GOLD);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);c.drawRoundRect(x+1,y+1,x+46,y+46,7,7,paint);paint.setStyle(Paint.Style.FILL);}
        }
        if(System.currentTimeMillis()-lastInteraction>6000&&!game.won&&!game.lost&&!paused&&!brief){int[] hint=game.hint();if(hint!=null){for(int pos:hint){paint.setColor(TEAL);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);float x=BX+pos%8*CELL,y=BY+pos/8*CELL;c.drawRoundRect(x+2,y+2,x+45,y+45,8,8,paint);paint.setStyle(Paint.Style.FILL);}}}
        int[] order={6,0,7,8,1,2,9,3,4,5};
        for(int slot=0;slot<order.length;slot++){final int id=order[slot];float x=12+(slot%5)*76,y=675+(slot/5)*67;
            round(c,x,y,x+71,y+61,item==id?0xff986f45:0xff403052,12);
            art.booster(c,id,x+29,y+24,40);round(c,x+48,y+3,x+68,y+22,inventory[id]>0?0xff9d4269:0xff65566d,8);
            text(c,""+inventory[id],x+58,y+18,12,WHITE);text(c,ITEMS[id],x+35,y+55,11,WHITE);
            hits.add(new Hit(new RectF(x,y,x+71,y+61),()->{
                if(inventory[id]==0){message("ยังไม่มี "+ITEMS[id]+" • รับเมื่อผ่านด่านหรือซื้อในร้าน");return;}
                if(id<3||id==6||id==7||id==8){item=item==id?-1:id;selected=-1;message(item<0?"ยกเลิกตัวช่วย":DETAILS[id]);}
                else if(game.use(id,-1)){inventory[id]--;effects(id,-1);afterAction();}else message("ใช้ในด่านช่วยเพื่อนหรือบอสเท่านั้น");
            }));
        }
        text(c,"ทอง "+coins+" • ผ่านด่านรับตัวช่วยฟรี",200,819,11,GOLD);
        drawEffects(c);
        if(brief)drawBrief(c);
        else if(paused)drawPause(c);
        else if((game.won||game.lost)&&System.currentTimeMillis()>=resultAt)drawResult(c);
    }
    private float rescueProgress(){
        if(game==null)return 0;float goal=Math.min(1,game.progress/(float)game.level.goal);
        return game.level.kind==AdventureEngine.BOSS||game.level.kind==AdventureEngine.BONUS?goal:Math.min(goal,1-game.remaining/(float)Math.max(1,game.initialObstacles));
    }
    private void drawRescueScene(Canvas c){
        shownRescue+=(rescueProgress()-shownRescue)*.12f;shownDanger+=(Math.min(1,game.danger/(float)game.level.moves)-shownDanger)*.12f;
        art.scene(c,game,new RectF(12,103,388,259),shownRescue,shownDanger,(System.currentTimeMillis()-(resultAt-2200))/1000f);
    }
    private void overlay(Canvas c,String title){hits.clear();round(c,0,0,400,820,0xb8100b20,0);round(c,25,192,375,647,0xff39244f,25);round(c,31,198,369,641,0xff241734,22);text(c,title,200,239,26,GOLD);}
    private void drawBrief(Canvas c){
        overlay(c,game.level.name());art.scene(c,game,new RectF(43,254,357,352),rescueProgress(),0,0);
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
        boolean win=game.won;overlay(c,win?(game.level.kind==AdventureEngine.RESCUE?"ช่วยผีน้อยสำเร็จ!":"ผ่านด่านแล้ว!"):"ช่วยผีน้อยอีกครั้งนะ");
        art.scene(c,game,new RectF(39,255,361,395),rescueProgress(),shownDanger,3);
        if(win){float t=(System.currentTimeMillis()-resultAt)/1000f;int earned=game.moves>=game.level.moves/3?3:game.moves>=3?2:1;
            for(int i=0;i<3;i++){float pop=Math.min(1,Math.max(0,(t-i*.16f)*4));text(c,"★",136+i*64,426,38*pop,i<earned?GOLD:0xff6d5979);}
            for(int i=0;i<40;i++){float x=30+(i*73)%340,y=245+((t*70+i*37)%235);paint.setColor(i%3==0?GOLD:i%3==1?TEAL:0xffff95cc);c.drawCircle(x,y,2+i%3,paint);}
        }
        text(c,win?"+ "+lastReward+" เหรียญทอง":"จับคู่เปิดทาง แล้วลองใหม่ได้เลย",200,460,22,win?GOLD:WHITE);
        if(win&&rewardItem>=0){art.booster(c,rewardItem,82,485,29);text(c,"ได้รับ "+ITEMS[rewardItem]+" × 1",220,490,15,TEAL);}
        button(c,67,516,333,567,win?(game.level.number==120?"ครบ 120 ด่าน!":"ด่านต่อไป"):"ลองอีกครั้ง",GOLD,()->{
            if(win&&game.level.number==120){screen="map";mapPage=5;invalidate();}
            else if(win){sound(GameAudio.COMPLETE);host.showAdBeforeNextLevel(()->start(game.level.number+1));}else start(game.level.number);
        });
        button(c,67,583,194,624,"หน้าแรก",0xff604783,()->{screen="home";invalidate();});
        button(c,206,583,333,624,"ร้านค้า",0xff318e8c,()->openShop("play"));
    }
    private int cellAt(float x,float y){int col=(int)((x-BX)/CELL),r=(int)((y-BY)/CELL);return x>=BX&&x<BX+8*CELL&&y>=BY&&y<BY+8*CELL&&game!=null&&game.inside(r,col)?r*8+col:-1;}
    private void tapCell(int pos){
        if(pos<0)return;if(item>=0&&item!=6){if(game.use(item,pos)){inventory[item]--;effects(item,pos);item=-1;afterAction();}return;}
        if(!game.movable(pos/8,pos%8)){message(AdventureEngine.OBSTACLES[game.obstacle[pos/8][pos%8]]+" • จับคู่ข้าง ๆ เพื่อทำลาย");return;}
        if(selected>=0&&selected!=pos){if(tryMove(selected,pos)){afterAction();return;}sound(GameAudio.INVALID);}
        selected=selected==pos?-1:pos;invalidate();
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent e){
        if(scale<=0)return true;float x=(e.getX()-ox)/scale,y=(e.getY()-oy)/scale;
        if(e.getAction()==MotionEvent.ACTION_DOWN){down=screen.equals("play")?cellAt(x,y):-1;downX=x;downY=y;swiped=false;return true;}
        if(e.getAction()==MotionEvent.ACTION_CANCEL){down=-1;return true;}
        if(e.getAction()==MotionEvent.ACTION_MOVE&&down>=0&&!swiped&&(item<0||item==6)&&!brief&&!paused&&!game.won&&!game.lost&&System.currentTimeMillis()>=animationUntil){
            float dx=x-downX,dy=y-downY;
            if(Math.max(Math.abs(dx),Math.abs(dy))>=CELL*.22f){
                int target=down+(Math.abs(dx)>Math.abs(dy)?(dx>0?1:-1):(dy>0?8:-8));swiped=true;
                if(tryMove(down,target))afterAction();else sound(GameAudio.INVALID);
                down=-1;return true;
            }
        }
        if(e.getAction()!=MotionEvent.ACTION_UP)return true;
        if(swiped){swiped=false;down=-1;return true;}
        performClick();lastInteraction=System.currentTimeMillis();
        if(screen.equals("play")&&!brief&&!paused&&!game.won&&!game.lost&&System.currentTimeMillis()<animationUntil)return true;
        for(Hit hit:new ArrayList<>(hits))if(hit.box.contains(x,y)){sound(GameAudio.SWAP);hit.action.run();return true;}
        if(screen.equals("play")&&!brief&&!paused&&!game.won&&!game.lost){int pos=cellAt(x,y);if(down>=0&&pos>=0&&pos!=down&&(item<0||item==6)){if(tryMove(down,pos))afterAction();else sound(GameAudio.INVALID);}else tapCell(pos);}
        down=-1;return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
