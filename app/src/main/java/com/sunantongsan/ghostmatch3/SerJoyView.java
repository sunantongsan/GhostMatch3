package com.sunantongsan.ghostmatch3;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Object-choice comedy game, with the original match-three engine as a short break. */
final class SerJoyView extends ScrollView {
    private final MainActivity host;
    private final SharedPreferences prefs;
    private final JSONArray jokes;
    final QuizProgress progress;
    private final Bitmap reactions, ghosts, scenery;
    private LinearLayout page;
    private ReactionView actor;
    private StoryArt storyArt;
    private StorySceneView story;
    private int spokenIndex=-1;
    private AdventureEngine board;
    private TextView breakStatus;
    private BoardView boardView;
    private boolean active=true,adBusy=false,breakOpen=false;
    private int selected=-1;
    private final int ink=0xFF24212B,yellow=0xFFFFCE3B,paper=0xFFFFFBF0;

    SerJoyView(MainActivity activity){
        super(activity);host=activity;prefs=host.getSharedPreferences("ser_joy_v1",Context.MODE_PRIVATE);
        jokes=host.content.jokes;
        try(java.io.InputStream in=host.getAssets().open("ser-joy-fullbody.webp")){reactions=BitmapFactory.decodeStream(in);}
        catch(Exception e){throw new IllegalStateException("Missing reaction artwork",e);}
        try(java.io.InputStream in=host.getAssets().open("ser-joy-neighborhood.webp")){scenery=BitmapFactory.decodeStream(in);}
        catch(Exception e){throw new IllegalStateException("Missing scenery",e);}
        ghosts=BitmapFactory.decodeResource(getResources(),R.drawable.ghost_sprites);
        try{storyArt=new StoryArt(host);}catch(Exception e){throw new IllegalStateException("Story art missing",e);}
        // Content v3 replaces the rejected tail. Migrate once, preserving the first 22.
        if (host.content.version >= 3 && prefs.getInt("curatedContentVersion", 0) < 3) {
            SharedPreferences.Editor migration = prefs.edit().putInt("curatedContentVersion", 3);
            if (prefs.getInt("index", 0) >= 22) {
                migration.putInt("index", 22).putInt("streak", 0)
                    .putString("phase", "QUESTION").remove("board");
            }
            migration.commit();
        }
        // v5 inserts fourteen scenes before the retained old tail; keep its saved question.
        if (host.content.version >= 5 && prefs.getInt("curatedContentVersion", 0) < 5) {
            SharedPreferences.Editor migration = prefs.edit().putInt("curatedContentVersion", 5);
            if (prefs.getInt("index", 0) >= 42) migration.putInt("index", prefs.getInt("index", 0) + 14);
            migration.commit();
        }
        progress=new QuizProgress(jokes.length(),prefs.getInt("index",0),prefs.getInt("streak",0),prefs.getString("phase","QUESTION"));
        setFillViewport(true);setBackground(new android.graphics.drawable.Drawable(){
            final Paint brush=new Paint(3);
            public void draw(Canvas canvas){Rect bounds=getBounds();float scale=Math.max(bounds.width()/(float)scenery.getWidth(),bounds.height()/(float)scenery.getHeight());float w=scenery.getWidth()*scale,h=scenery.getHeight()*scale;canvas.drawBitmap(scenery,null,new RectF(bounds.centerX()-w/2,bounds.top,bounds.centerX()+w/2,bounds.top+h),brush);}
            public void setAlpha(int a){brush.setAlpha(a);}public void setColorFilter(ColorFilter f){brush.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        });
        setOnApplyWindowInsetsListener((v,i)->{setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        render();
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int border){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(20));d.setStroke(dp(2),border);return d;}
    private TextView text(String value,int size){TextView t=new TextView(host);t.setText(value);t.setTextSize(size);t.setTextColor(ink);t.setGravity(Gravity.CENTER);t.setPadding(dp(8),dp(6),dp(8),dp(6));return t;}
    private void add(View v){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);page.addView(v,lp);}
    private Button button(String label,Runnable action){Button b=new Button(host);b.setText(label);b.setTextSize(17);b.setTextColor(ink);b.setAllCaps(false);b.setMinHeight(dp(56));b.setPadding(dp(12),dp(10),dp(12),dp(10));b.setBackground(shape(yellow,ink));b.setOnClickListener(v->action.run());add(b);return b;}
    private void save(){prefs.edit().putInt("index",progress.index).putInt("streak",progress.streak).putString("phase",progress.phase.name()).apply();}
    void setActive(boolean value){active=value;if(story!=null)story.setActive(value);if(actor!=null)actor.setRunning(value);if(value&&boardView!=null)boardView.invalidate();}
    void release(){active=false;if(story!=null)story.release();if(actor!=null)actor.setRunning(false);}
    private JSONObject joke(){return jokes.optJSONObject(Math.min(progress.index,jokes.length()-1));}
    private void render(){
        if(story!=null){story.release();story=null;}
        if(!breakOpen&&progress.phase!=QuizProgress.Phase.COMPLETE){renderStory();return;}
        removeAllViews();page=new LinearLayout(host);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(18),dp(12),dp(18),dp(20));addView(page);
        LinearLayout top=new LinearLayout(host);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=text("ท่านเซ่อจ่อย",24);brand.setTypeface(null,Typeface.BOLD);brand.setBackground(shape(0xEFFFF9E9,0x00FFFFFF));top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button settings=new Button(host);settings.setText("☰");settings.setTextSize(22);settings.setContentDescription("ตั้งค่าเสียงและความเป็นส่วนตัว");settings.setOnClickListener(v->settings());top.addView(settings,new LinearLayout.LayoutParams(dp(54),dp(50)));add(top);
        host.setBreakMusic(breakOpen);
        if(breakOpen){renderBreak();return;}
        boolean done=progress.phase==QuizProgress.Phase.COMPLETE;
        TextView step=text(done?"ครบทุกมุกแล้ว!":"เรื่องที่ "+(progress.index+1)+" / "+progress.total+"  •  ผิดติดกัน "+progress.streak+" / 3",14);step.setBackground(shape(0xEDFFF8E7,0x00FFFFFF));add(step);
        ProgressBar bar=new ProgressBar(host,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(progress.total);bar.setProgress(progress.index);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFE8AC00));add(bar);
        TextView question=text(done?"เซ่อจ่อยยอมแล้ว…คุณนี่สุดจริง!":joke().optString("question"),24);question.setTypeface(null,Typeface.BOLD);question.setBackground(shape(Color.WHITE,ink));question.setPadding(dp(16),dp(14),dp(16),dp(14));add(question);
        actor=new ReactionView();int h=Math.min(320,Math.max(230,(int)(getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density*.36f)));
        page.addView(actor,new LinearLayout.LayoutParams(-1,dp(h)));
        if(done){button("เล่นมุกทั้งหมดอีกครั้ง",()->{progress.index=0;progress.streak=0;progress.phase=QuizProgress.Phase.QUESTION;save();render();});return;}
        if(progress.phase==QuizProgress.Phase.QUESTION){
            add(text("เลือกของที่เป็นคำตอบ",16));
            LinearLayout choices=new LinearLayout(host);JSONArray list=joke().optJSONArray("choices");
            ArrayList<Integer> order=new ArrayList<>(Arrays.asList(0,1,2));Collections.shuffle(order,new Random(821L+progress.index));
            for(int choice:order){JSONObject item=list.optJSONObject(choice);LinearLayout card=new LinearLayout(host);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER);card.setPadding(dp(4),dp(12),dp(4),dp(12));card.setBackground(shape(Color.WHITE,ink));
                TextView icon=text(item.optString("icon"),38);card.addView(icon);TextView label=text(item.optString("label"),16);label.setTypeface(null,Typeface.BOLD);card.addView(label);
                card.setContentDescription(item.optString("label"));card.setFocusable(true);card.setClickable(true);card.setOnClickListener(v->answer(choice));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(dp(3),0,dp(3),0);choices.addView(card,lp);
            }add(choices);add(text("ตอบถูกไปต่อ • ตอบผิดดูโฆษณา\nผิดติดกัน 3 ครั้ง พักจับคู่ผี 1 ตา",13));
        }else{
            boolean correct=progress.phase==QuizProgress.Phase.CORRECT;
            TextView verdict=text(correct?"ถูกซะด้วย! ยอมเลย!":"แหม…โดนเซ่อจ่อยหลอกแล้ว!",20);verdict.setTypeface(null,Typeface.BOLD);verdict.setTextColor(correct?0xFF237857:0xFFAA4932);add(verdict);
            TextView punch=text(joke().optString("punchline"),22);punch.setBackground(shape(0xFFFFEBA8,ink));add(punch);
            if(correct)button("เรื่องต่อไป  →",()->{if(progress.next()){if(progress.phase==QuizProgress.Phase.COMPLETE)host.playSound(GameAudio.FINALE);save();render();}});
            else if(progress.phase==QuizProgress.Phase.BREAK_REQUIRED){add(text("ผิดครบ 3 ครั้งแล้ว พักจับคู่ผีกัน!\nเล่นจบ 1 ตา ก็ไปเรื่องต่อไปได้",15));button("พักจับคู่ผี  👻",this::startBreak);}
            else {Button watch=button("ดูโฆษณาเพื่อลองตอบอีกครั้ง  ▶",this::watchAd);watch.setEnabled(!adBusy);add(text("โฆษณาทดสอบ • ดูจบจึงไปต่อ",13));}
        }
        post(()->smoothScrollTo(0,0));
    }
    private void renderStory(){
        removeAllViews();host.setBreakMusic(false);
        FrameLayout frame=new FrameLayout(host);
        int available=getResources().getDisplayMetrics().heightPixels-dp(36);
        addView(frame,new ScrollView.LayoutParams(-1,Math.max(dp(540),available)));
        StorySceneView created=new StorySceneView(host,storyArt,storyArt.scenes[progress.index],joke(),progress.index,progress.total,new StorySceneView.Listener(){
            public void choose(int choice){answer(choice);}
            public void wrongFinished(){showStoryGate(frame);}
            public void revealFinished(){host.sayStory(joke().optString("punchline"));showStoryNext(frame);}
        });
        story=created;frame.addView(story,new FrameLayout.LayoutParams(-1,-1));story.setActive(active);
        Button menu=new Button(host);menu.setText("☰");menu.setContentDescription("ตั้งค่าเสียงและความเป็นส่วนตัว");menu.setTextColor(Color.WHITE);menu.setBackgroundColor(Color.TRANSPARENT);menu.setOnClickListener(v->settings());
        FrameLayout.LayoutParams ml=new FrameLayout.LayoutParams(dp(56),dp(48),Gravity.TOP|Gravity.RIGHT);frame.addView(menu,ml);
        if(progress.phase==QuizProgress.Phase.CORRECT){story.showResolution(joke().optInt("answer"));}
        else if(progress.phase!=QuizProgress.Phase.QUESTION){story.showGate();showStoryGate(frame);}
        else if(spokenIndex!=progress.index){spokenIndex=progress.index;host.sayStory(story.questionText());}
        post(()->{if(getHeight()>0){frame.getLayoutParams().height=getHeight()-getPaddingTop()-getPaddingBottom();frame.requestLayout();}scrollTo(0,0);});
    }
    private Button storyButton(FrameLayout frame,String label,Runnable action){
        Button b=new Button(host);b.setText(label);b.setTextColor(ink);b.setTextSize(17);b.setAllCaps(false);b.setBackground(shape(yellow,ink));b.setOnClickListener(v->action.run());
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,dp(60),Gravity.BOTTOM);lp.setMargins(dp(22),0,dp(22),dp(22));frame.addView(b,lp);return b;
    }
    private void showStoryNext(FrameLayout frame){
        storyButton(frame,"เรื่องต่อไป  →",()->{if(progress.next()){save();render();}});
    }
    private void showStoryGate(FrameLayout frame){
        if(progress.phase==QuizProgress.Phase.BREAK_REQUIRED)storyButton(frame,"พักจับคู่ผี แล้วกลับมาตอบ  👻",this::startBreak);
        else if(progress.phase==QuizProgress.Phase.AD_REQUIRED){Button watch=storyButton(frame,"ดูโฆษณา แล้วลองเลือกใหม่  ▶",this::watchAd);watch.setEnabled(!adBusy);}
    }
    private void answer(int choice){
        if(choice<0||choice>2||!progress.answer(choice==joke().optInt("answer")))return;
        save();host.stopStorySpeech();host.playSound(progress.phase==QuizProgress.Phase.CORRECT?GameAudio.WIN:GameAudio.GHOST);
        if(story!=null){if(progress.phase==QuizProgress.Phase.CORRECT)story.showResolution(choice);else story.showWrong(choice);}
        else render();
    }
    private void finishRewardAd(){
        adBusy=false;if(progress.reward())save();render();
    }
    private void watchAd(){
        if(adBusy||progress.phase!=QuizProgress.Phase.AD_REQUIRED)return;adBusy=true;render();
        host.showQuizReward(this::finishRewardAd,()->{adBusy=false;render();Toast.makeText(host,"ยังดูไม่จบ ลองอีกครั้งเพื่อลองตอบอีกครั้ง",Toast.LENGTH_LONG).show();},()->{
            adBusy=false;render();new android.app.AlertDialog.Builder(host).setTitle("โฆษณายังไม่พร้อม")
                .setMessage("ลองโหลดอีกครั้ง หรือเล่นจับคู่ผี 1 ตาเพื่อลองตอบอีกครั้งได้")
                .setPositiveButton("ลองอีกครั้ง",(d,w)->watchAd())
                .setNeutralButton("จับคู่ผีแทน",(d,w)->{progress.phase=QuizProgress.Phase.BREAK_REQUIRED;save();startBreak();})
                .setNegativeButton("รอก่อน",null).show();
        });
    }
    private void settings(){ArrayList<String> items=new ArrayList<>();items.add(host.soundEnabled()?"ปิดเสียง":"เปิดเสียง");items.add("นโยบายความเป็นส่วนตัว");if(host.getPackageName().endsWith(".storypreview"))items.add("ทดลองเลือกฉาก");if(host.needsPrivacyOptions())items.add("จัดการความยินยอมโฆษณา");items.add(host.updateAvailable?"เวอร์ชันใหม่ • อัปเดต":"อัปเดตใน Google Play");
        new android.app.AlertDialog.Builder(host).setTitle("ตั้งค่า").setItems(items.toArray(new String[0]),(d,n)->{if(n==items.size()-1)host.openStoreUpdate();else if(n==0)host.toggleSound();else if(n==1)host.openPrivacyPolicy();else if(n==2&&host.getPackageName().endsWith(".storypreview")){
            String[] names=new String[jokes.length()];for(int i=0;i<names.length;i++)names[i]=(i+1)+". "+jokes.optJSONObject(i).optString("question");
            new android.app.AlertDialog.Builder(host).setTitle("ทดลองฉากทั้งหมด").setItems(names,(dialog,index)->{progress.index=index;progress.streak=0;progress.phase=QuizProgress.Phase.QUESTION;breakOpen=false;board=null;save();render();}).setNegativeButton("กลับ",null).show();
        }else host.openPrivacyOptions();}).setNegativeButton("กลับ",null).show();}
    private void startBreak(){breakOpen=true;selected=-1;if(board==null){board=new AdventureEngine(1,113L+progress.index,new int[]{10,500,0},3);for(int r=0;r<8;r++)for(int c=0;c<8;c++)board.hp[r][c]=0;board.remaining=0;board.refillFresh();board.ensureMove();restoreBoard();board.recordAnimation=true;}render();}
    private void renderBreak(){
        TextView title=text("พักสมอง จับคู่ผี",21);title.setTypeface(null,Typeface.BOLD);title.setBackground(shape(0xF4FFF7DE,ink));add(title);
        TextView help=text("ลากสลับผีเหมือนกัน 3 ตัวขึ้นไป\nจับต่อเนื่องได้คอมโบ • เล่นครบ 10 ตา",14);help.setBackground(shape(0xEDFFF9EA,0x00FFFFFF));add(help);
        breakStatus=text("เหลือ "+board.moves+" ตา  •  คะแนน "+board.score,18);breakStatus.setTypeface(null,Typeface.BOLD);breakStatus.setBackground(shape(0xF4FFF7DE,ink));add(breakStatus);
        boardView=new BoardView();page.addView(boardView,new LinearLayout.LayoutParams(-1,dp(360)));
        Button hint=button("ใบ้คู่ที่สลับได้",()->{int[] pair=board.hint();if(pair!=null){if(!boardView.animating()){boardView.hint=pair;boardView.invalidate();}}});
        if(board.moves<=0||board.won||board.lost){hint.setVisibility(GONE);button("สบายใจแล้ว กลับไปตอบ  →",()->{if(progress.finishBreak()){board=null;breakOpen=false;prefs.edit().remove("board").apply();save();render();}});}
    }
    private void saveBoard(){if(board==null)return;try{JSONObject b=new JSONObject();b.put("schema",2);b.put("index",progress.index);b.put("moves",board.moves);b.put("score",board.score);JSONArray cells=new JSONArray();for(int r=0;r<8;r++)for(int c=0;c<8;c++){cells.put(board.tile[r][c]);cells.put(board.power[r][c]);}b.put("cells",cells);prefs.edit().putString("board",b.toString()).apply();}catch(JSONException ignored){}}
    private void restoreBoard(){try{JSONObject b=new JSONObject(prefs.getString("board","{}"));if(b.optInt("index",-1)!=progress.index)return;JSONArray cells=b.getJSONArray("cells");if(cells.length()!=128)return;for(int n=0;n<128;n+=2){if(cells.getInt(n)<0||cells.getInt(n)>(b.optInt("schema",0)==2?2:5)||cells.getInt(n+1)<0||cells.getInt(n+1)>4)return;}board.moves=Math.max(0,Math.min(10,b.getInt("moves")));board.score=b.getInt("score");if(b.optInt("schema",0)==2){for(int i=0;i<64;i++){board.tile[i/8][i%8]=cells.getInt(i*2);board.power[i/8][i%8]=cells.getInt(i*2+1);}}board.lost=board.moves==0;board.ensureMove();}catch(JSONException ignored){}}
    private final class ReactionView extends View {
        final Paint p=new Paint(3);long elapsed,last=SystemClock.uptimeMillis();boolean running=true;
        ReactionView(){super(host);setBackgroundColor(Color.TRANSPARENT);setContentDescription(progress.phase==QuizProgress.Phase.QUESTION?"ท่านเซ่อจ่อยยกมือคิดและกะพริบตา":progress.phase==QuizProgress.Phase.CORRECT?"ท่านเซ่อจ่อยเกาหัว ยกนิ้ว ชี้และยกนิ้วโป้งแสดงความยินดี":"ท่านเซ่อจ่อยยื่นหน้า ชี้ แล้วอ้าปากหุบปากหัวเราะอย่างขี้เล่น");}
        void setRunning(boolean value){running=value;last=SystemClock.uptimeMillis();if(value)invalidate();}
        protected void onDraw(Canvas c){super.onDraw(c);long now=SystemClock.uptimeMillis();if(running)elapsed+=Math.min(100,Math.max(0,now-last));last=now;
            int frame=CharacterAnimation.frame(progress.phase,elapsed);int w=reactions.getWidth()/4;
            // Full-body sheet row gutters, including both feet in every frame.
            int[] top={0,420,825},bottom={420,825,1254};int row=frame/4,h=bottom[row]-top[row];
            Rect src=new Rect(frame%4*w,top[row],(frame%4+1)*w,bottom[row]);
            float scale=Math.min(getWidth()/(float)w,(getHeight()-dp(8))/(float)h),dw=w*scale,dh=h*scale;
            float x=(getWidth()-dw)/2f,y=(getHeight()-dh)/2f;
            p.setColor(0x26000000);c.drawOval(getWidth()*.32f,getHeight()-dp(17),getWidth()*.68f,getHeight()-dp(4),p);p.setColor(Color.WHITE);
            c.drawBitmap(reactions,src,new RectF(x,y,x+dw,y+dh),p);
            if(active&&running)postInvalidateDelayed(50);
        }
    }
    private final class BoardView extends View {
        final Paint p=new Paint(3);int[] hint;float cell;final MatchEffects effects=new MatchEffects();int effectStep=-1;
        final int[] colors={0xFFE6D8FF,0xFFD7F4E0,0xFFFFDFEA,0xFFD6EEFF,0xFFFFE8C2,0xFFE8E1FF};
        BoardView(){super(host);setContentDescription("กระดานจับคู่ผี 8 คูณ 8 ลากเพื่อสลับ หรือแตะสองช่องติดกัน");}
        @Override protected void onMeasure(int w,int h){int side=MeasureSpec.getSize(w);setMeasuredDimension(side,side);}
        AdventureEngine.Step shown=new AdventureEngine.Step("idle",board,null),before=shown;
        final ArrayList<AdventureEngine.Step> frames=new ArrayList<>();int frameIndex;long frameStarted;int swapA,swapB;
        boolean animating(){return frameIndex<frames.size();}
        private long duration(AdventureEngine.Step step){return step.kind.equals("swap")||step.kind.equals("back")?180:step.kind.equals("clear")?(frameIndex>10?50:frameIndex>6?110:220):step.kind.equals("fall")?(frameIndex>10?80:frameIndex>6?160:280):120;}
        private void piece(Canvas canvas,int type,int power,float x,float y,float size,int alpha){
            if(type<0)return;p.setAlpha(alpha);int gw=ghosts.getWidth()/3;
            canvas.drawBitmap(ghosts,new Rect(type*gw,0,(type+1)*gw,ghosts.getHeight()),new RectF(x+cell*(1-size)/2+3,y+cell*(1-size)/2+3,x+cell*(1+size)/2-3,y+cell*(1+size)/2-3),p);
            if(power>0){p.setColor(0xFFFFC52F);canvas.drawCircle(x+cell*.78f,y+cell*.22f,cell*.19f,p);p.setColor(ink);p.setTextSize(cell*.27f);p.setTextAlign(Paint.Align.CENTER);canvas.drawText(power==1?"↔":power==2?"↕":power==3?"✹":"★",x+cell*.78f,y+cell*.31f,p);}p.setAlpha(255);
        }
        protected void onDraw(Canvas c){
            cell=getWidth()/8f;long now=SystemClock.uptimeMillis();
            while(animating()&&now-frameStarted>=duration(frames.get(frameIndex))){frameStarted+=duration(frames.get(frameIndex));shown=frames.get(frameIndex++);before=shown;}
            AdventureEngine.Step target=animating()?frames.get(frameIndex):shown;
            if(animating()&&effectStep!=frameIndex){effectStep=frameIndex;if(target.kind.equals("clear"))effects.clear(before,target,now);}
            float t=animating()?Math.min(1,(now-frameStarted)/(float)duration(target)):1;
            for(int r=0;r<8;r++)for(int col=0;col<8;col++){float x=col*cell,y=r*cell;p.setColor((r+col)%2==0?0xFFEEE6FF:0xFFE0D5F3);c.drawRoundRect(x+1,y+1,x+cell-1,y+cell-1,8,8,p);}
            c.save();c.clipRect(0,0,getWidth(),getHeight());
            for(int r=0;r<8;r++)for(int col=0;col<8;col++){
                int pos=r*8+col,type=target.tiles[r][col],power=target.powers[r][col];float x=col*cell,y=r*cell,size=1;int alpha=255;
                if(animating()&&(target.kind.equals("swap")||target.kind.equals("back"))){type=before.tiles[r][col];power=before.powers[r][col];int dest=pos==swapA?swapB:pos==swapB?swapA:pos;x+=(dest%8-col)*cell*t;y+=(dest/8-r)*cell*t;}
                else if(animating()&&target.kind.equals("clear")&&type<0&&before.tiles[r][col]>=0){type=before.tiles[r][col];power=before.powers[r][col];size=1-t;alpha=(int)(255*(1-t));}
                else if(animating()&&target.kind.equals("fall")){float ease=1-(1-t)*(1-t);y=(target.origins[r][col]+(r-target.origins[r][col])*ease)*cell;}
                piece(c,type,power,x,y,size,alpha);
                if(!animating()&&(selected==pos||hint!=null&&(hint[0]==pos||hint[1]==pos))){p.setColor(0xFFE89C00);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(3));c.drawRoundRect(col*cell+3,r*cell+3,(col+1)*cell-3,(r+1)*cell-3,8,8,p);p.setStyle(Paint.Style.FILL);}
            }c.restore();
            effects.draw(c,cell,now);
            if((animating()||effects.alive(now))&&active)postInvalidateOnAnimation();
            else if(!animating()&&!effects.alive(now)&&completionPending){completionPending=false;post(()->{host.playSound(GameAudio.COMPLETE);render();});}
        }
        boolean completionPending;
        int fingerStart=-1,previousSelection=-1;float downX,downY;boolean swiped;
        private int position(float x,float y){float side=getWidth()/8f;if(side<=0||x<0||y<0||x>=getWidth()||y>=getHeight())return -1;return (int)(y/side)*8+(int)(x/side);}
        private void swapCells(int a,int b){
            if(animating()||a<0||b<0||Math.abs(a/8-b/8)+Math.abs(a%8-b%8)!=1){selected=b;invalidate();return;}
            selected=-1;hint=null;swapA=a;swapB=b;before=shown=new AdventureEngine.Step("idle",board,null);frames.clear();effects.reset();effectStep=-1;frameIndex=0;frameStarted=SystemClock.uptimeMillis();
            if(board.move(a,b)){
                frames.addAll(board.animation);host.playSound(board.casts.isEmpty()?GameAudio.MATCH:GameAudio.EXPLOSION);saveBoard();
                breakStatus.setText("เหลือ "+board.moves+" ตา  •  คะแนน "+board.score);
                completionPending=board.moves<=0||board.won||board.lost;
            }else{
                board.swap(a,b);frames.add(new AdventureEngine.Step("swap",board,null));board.swap(a,b);frames.add(new AdventureEngine.Step("back",board,null));
                host.playSound(GameAudio.INVALID);breakStatus.setText("ต้องเรียงผีชนิดเดียวกันอย่างน้อย 3 ตัว • ไม่เสียตา");
            }invalidate();
        }
        private void parentInterception(boolean disallow){if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(disallow);}
        private void swipe(float x,float y){
            if(fingerStart<0||swiped)return;
            float dx=x-downX,dy=y-downY,threshold=Math.max(dp(8),getWidth()/8f*.22f);
            if(Math.max(Math.abs(dx),Math.abs(dy))<threshold)return;
            swiped=true;int row=fingerStart/8,col=fingerStart%8;
            if(Math.abs(dx)>=Math.abs(dy))col+=dx>0?1:-1;else row+=dy>0?1:-1;
            if(row>=0&&row<8&&col>=0&&col<8)swapCells(fingerStart,row*8+col);
            else{selected=-1;invalidate();}
        }
        public boolean onTouchEvent(MotionEvent e){
            if(board.moves<=0||animating()){if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){fingerStart=-1;parentInterception(false);}return true;}
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    fingerStart=position(e.getX(),e.getY());if(fingerStart<0)return false;
                    previousSelection=selected;selected=fingerStart;downX=e.getX();downY=e.getY();swiped=false;
                    parentInterception(true);invalidate();return true;
                case MotionEvent.ACTION_MOVE:swipe(e.getX(),e.getY());return true;
                case MotionEvent.ACTION_UP:
                    swipe(e.getX(),e.getY());
                    if(fingerStart>=0&&!swiped){performClick();int pos=position(e.getX(),e.getY());
                        if(pos==fingerStart&&previousSelection>=0&&previousSelection!=pos)swapCells(previousSelection,pos);
                        else selected=pos==previousSelection?-1:pos;
                    }
                    fingerStart=-1;parentInterception(false);invalidate();return true;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_POINTER_DOWN:
                    fingerStart=-1;selected=-1;parentInterception(false);invalidate();return true;
                default:return true;
            }
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
