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
    private final Bitmap reactions, ghosts;
    private LinearLayout page;
    private ReactionView actor;
    private AdventureEngine board;
    private TextView breakStatus;
    private BoardView boardView;
    private boolean active=true,adBusy=false,breakOpen=false;
    private int selected=-1;
    private final int ink=0xFF24212B,yellow=0xFFFFCE3B,paper=0xFFFFFBF0;

    SerJoyView(MainActivity activity){
        super(activity);host=activity;prefs=host.getSharedPreferences("ser_joy_v1",Context.MODE_PRIVATE);
        try(java.io.InputStream in=host.getAssets().open("ser-joy-jokes.json")){
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;
            while((n=in.read(b))!=-1)out.write(b,0,n);
            jokes=new JSONArray(out.toString("UTF-8"));
        }catch(Exception e){throw new IllegalStateException("Bundled joke data is invalid",e);}
        try(java.io.InputStream in=host.getAssets().open("ser-joy-reactions.webp")){reactions=BitmapFactory.decodeStream(in);}
        catch(Exception e){throw new IllegalStateException("Missing reaction artwork",e);}
        ghosts=BitmapFactory.decodeResource(getResources(),R.drawable.ghost_sprites);
        progress=new QuizProgress(jokes.length(),prefs.getInt("index",0),prefs.getInt("streak",0),prefs.getString("phase","QUESTION"));
        setFillViewport(true);setBackgroundColor(paper);
        setOnApplyWindowInsetsListener((v,i)->{setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        render();
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int border){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(20));d.setStroke(dp(2),border);return d;}
    private TextView text(String value,int size){TextView t=new TextView(host);t.setText(value);t.setTextSize(size);t.setTextColor(ink);t.setGravity(Gravity.CENTER);t.setPadding(dp(8),dp(6),dp(8),dp(6));return t;}
    private void add(View v){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(10);page.addView(v,lp);}
    private Button button(String label,Runnable action){Button b=new Button(host);b.setText(label);b.setTextSize(17);b.setTextColor(ink);b.setAllCaps(false);b.setMinHeight(dp(56));b.setPadding(dp(12),dp(10),dp(12),dp(10));b.setBackground(shape(yellow,ink));b.setOnClickListener(v->action.run());add(b);return b;}
    private void save(){prefs.edit().putInt("index",progress.index).putInt("streak",progress.streak).putString("phase",progress.phase.name()).apply();}
    void setActive(boolean value){active=value;if(!value){save();saveBoard();}else if(actor!=null)actor.invalidate();}
    void release(){save();saveBoard();}
    private JSONObject joke(){return jokes.optJSONObject(Math.min(progress.index,jokes.length()-1));}
    private void render(){
        removeAllViews();page=new LinearLayout(host);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(18),dp(12),dp(18),dp(20));addView(page);
        LinearLayout top=new LinearLayout(host);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=text("ท่านเซ่อจ่อย",24);brand.setTypeface(null,Typeface.BOLD);top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button settings=new Button(host);settings.setText("☰");settings.setTextSize(22);settings.setContentDescription("ตั้งค่าเสียงและความเป็นส่วนตัว");settings.setOnClickListener(v->settings());top.addView(settings,new LinearLayout.LayoutParams(dp(54),dp(50)));add(top);
        if(breakOpen){renderBreak();return;}
        boolean done=progress.phase==QuizProgress.Phase.COMPLETE;
        TextView step=text(done?"ครบทุกมุกแล้ว!":"เรื่องที่ "+(progress.index+1)+" / "+progress.total+"  •  ผิดติดกัน "+progress.streak+" / 3",14);add(step);
        ProgressBar bar=new ProgressBar(host,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(progress.total);bar.setProgress(progress.index);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFE8AC00));add(bar);
        TextView question=text(done?"เซ่อจ่อยยอมแล้ว…คุณนี่สุดจริง!":joke().optString("question"),24);question.setTypeface(null,Typeface.BOLD);question.setBackground(shape(Color.WHITE,ink));question.setPadding(dp(16),dp(14),dp(16),dp(14));add(question);
        actor=new ReactionView();int h=Math.min(300,Math.max(190,(int)(getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density*.32f)));
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
            if(correct)button("เรื่องต่อไป  →",()->{if(progress.next()){save();render();}});
            else if(progress.phase==QuizProgress.Phase.BREAK_REQUIRED){add(text("ผิดครบ 3 ครั้งแล้ว พักจับคู่ผีกัน!\nเล่นจบ 1 ตา ก็ไปเรื่องต่อไปได้",15));button("พักจับคู่ผี  👻",this::startBreak);}
            else {Button watch=button("ดูโฆษณาเพื่อไปเรื่องต่อไป  ▶",this::watchAd);watch.setEnabled(!adBusy);add(text("โฆษณาทดสอบ • ดูจบจึงไปต่อ",13));}
        }
        post(()->smoothScrollTo(0,0));
    }
    private void answer(int choice){if(!progress.answer(choice==joke().optInt("answer")))return;save();host.playSound(progress.phase==QuizProgress.Phase.CORRECT?GameAudio.WIN:GameAudio.GHOST);render();}
    private void watchAd(){
        if(adBusy||progress.phase!=QuizProgress.Phase.AD_REQUIRED)return;adBusy=true;render();
        host.showQuizReward(()->{adBusy=false;if(progress.reward())save();render();},()->{adBusy=false;render();Toast.makeText(host,"ยังดูไม่จบ ลองอีกครั้งเพื่อไปต่อ",Toast.LENGTH_LONG).show();},()->{
            adBusy=false;render();new android.app.AlertDialog.Builder(host).setTitle("โฆษณายังไม่พร้อม")
                .setMessage("ลองโหลดอีกครั้ง หรือเล่นจับคู่ผี 1 ตาเพื่อไปต่อได้")
                .setPositiveButton("ลองอีกครั้ง",(d,w)->watchAd())
                .setNeutralButton("จับคู่ผีแทน",(d,w)->{progress.phase=QuizProgress.Phase.BREAK_REQUIRED;save();startBreak();})
                .setNegativeButton("รอก่อน",null).show();
        });
    }
    private void settings(){ArrayList<String> items=new ArrayList<>();items.add(host.soundEnabled()?"ปิดเสียง":"เปิดเสียง");items.add("นโยบายความเป็นส่วนตัว");if(host.needsPrivacyOptions())items.add("จัดการความยินยอมโฆษณา");
        new android.app.AlertDialog.Builder(host).setTitle("ตั้งค่า").setItems(items.toArray(new String[0]),(d,n)->{if(n==0)host.toggleSound();else if(n==1)host.openPrivacyPolicy();else host.openPrivacyOptions();}).setNegativeButton("กลับ",null).show();}
    private void startBreak(){breakOpen=true;selected=-1;if(board==null){board=new AdventureEngine(1,113L+progress.index,new int[]{10,500,0});for(int r=0;r<8;r++)for(int c=0;c<8;c++)board.hp[r][c]=0;board.remaining=0;board.refillFresh();board.ensureMove();restoreBoard();}render();}
    private void renderBreak(){
        add(text("พักสมอง จับคู่ผี",25));add(text("แตะผี 2 ตัวที่ติดกันเพื่อสลับ\nเรียงเหมือนกัน 3 ตัว • เล่นครบ 10 ตาแล้วไปต่อ",15));
        breakStatus=text("เหลือ "+board.moves+" ตา  •  คะแนน "+board.score,18);add(breakStatus);
        boardView=new BoardView();page.addView(boardView,new LinearLayout.LayoutParams(-1,dp(360)));
        Button hint=button("ใบ้คู่ที่สลับได้",()->{int[] pair=board.hint();if(pair!=null){boardView.hint=pair;boardView.invalidate();}});
        if(board.moves<=0||board.won||board.lost){hint.setVisibility(GONE);button("สบายใจแล้ว ไปเรื่องต่อไป  →",()->{if(progress.finishBreak()){board=null;breakOpen=false;prefs.edit().remove("board").apply();save();render();}});}
    }
    private void saveBoard(){if(board==null)return;try{JSONObject b=new JSONObject();b.put("index",progress.index);b.put("moves",board.moves);b.put("score",board.score);JSONArray cells=new JSONArray();for(int r=0;r<8;r++)for(int c=0;c<8;c++){cells.put(board.tile[r][c]);cells.put(board.power[r][c]);}b.put("cells",cells);prefs.edit().putString("board",b.toString()).apply();}catch(JSONException ignored){}}
    private void restoreBoard(){try{JSONObject b=new JSONObject(prefs.getString("board","{}"));if(b.optInt("index",-1)!=progress.index)return;JSONArray cells=b.getJSONArray("cells");if(cells.length()!=128)return;for(int n=0;n<128;n+=2){if(cells.getInt(n)<0||cells.getInt(n)>5||cells.getInt(n+1)<0||cells.getInt(n+1)>4)return;}board.moves=Math.max(0,Math.min(10,b.getInt("moves")));board.score=b.getInt("score");for(int i=0;i<64;i++){board.tile[i/8][i%8]=cells.getInt(i*2);board.power[i/8][i%8]=cells.getInt(i*2+1);}board.lost=board.moves==0;board.ensureMove();}catch(JSONException ignored){}}
    private final class ReactionView extends View {
        final Paint p=new Paint(3);final long start=SystemClock.uptimeMillis();
        ReactionView(){super(host);setBackgroundColor(Color.WHITE);setContentDescription(progress.phase==QuizProgress.Phase.QUESTION?"ท่านเซ่อจ่อยกำลังคิด":progress.phase==QuizProgress.Phase.CORRECT?"ท่านเซ่อจ่อยทำหน้าคิดหนัก แล้วชี้แสดงความยินดี":"ท่านเซ่อจ่อยหรี่ตา ยื่นปากหัวเราะเยาะอย่างขี้เล่น");}
        protected void onDraw(Canvas c){super.onDraw(c);long elapsed=SystemClock.uptimeMillis()-start;boolean correct=progress.phase==QuizProgress.Phase.CORRECT||progress.phase==QuizProgress.Phase.COMPLETE;boolean wrong=progress.phase==QuizProgress.Phase.AD_REQUIRED||progress.phase==QuizProgress.Phase.BREAK_REQUIRED;
            int cell=correct?(elapsed<850?2:3):wrong?1:0;int w=reactions.getWidth()/2,h=reactions.getHeight()/2;Rect src=new Rect(cell%2*w,cell/2*h,(cell%2+1)*w,(cell/2+1)*h);
            float side=Math.min(getWidth(),getHeight()),x=(getWidth()-side)/2f,y=(getHeight()-side)/2f;
            c.save();if(wrong&&elapsed<2300)c.rotate((float)Math.sin(elapsed/65.0)*2.5f,getWidth()/2f,getHeight()/2f);c.drawBitmap(reactions,src,new RectF(x,y,x+side,y+side),p);c.restore();
            if(active&&(elapsed<2400))postInvalidateDelayed(32);
        }
    }
    private final class BoardView extends View {
        final Paint p=new Paint(3);int[] hint;float cell;long fxAt;final Set<Integer> sparkle=new HashSet<>();
        final int[] colors={0xFFE6D8FF,0xFFD7F4E0,0xFFFFDFEA,0xFFD6EEFF,0xFFFFE8C2,0xFFE8E1FF};
        BoardView(){super(host);setContentDescription("กระดานจับคู่ผี 8 คูณ 8 แตะสองช่องติดกันเพื่อสลับ");}
        @Override protected void onMeasure(int w,int h){int side=MeasureSpec.getSize(w);setMeasuredDimension(side,side);}
        protected void onDraw(Canvas c){cell=getWidth()/8f;for(int r=0;r<8;r++)for(int col=0;col<8;col++){int pos=r*8+col,t=board.tile[r][col];float x=col*cell,y=r*cell;p.setColor(colors[Math.max(0,t)%6]);c.drawRoundRect(x+2,y+2,x+cell-2,y+cell-2,8,8,p);
            int gw=ghosts.getWidth()/3;int variant=t%3;p.setAlpha(255);c.drawBitmap(ghosts,new Rect(variant*gw,0,(variant+1)*gw,ghosts.getHeight()),new RectF(x+3,y+3,x+cell-3,y+cell-3),p);p.setTextAlign(Paint.Align.CENTER);
            // Color badges make all six ghost types distinguishable even on monochrome emoji fonts.
            int[] badges={0xFF9A65DA,0xFF2B9B59,0xFFDE5692,0xFF3A8DCF,0xFFD88D27,0xFF6656BC};p.setColor(badges[t]);c.drawCircle(x+cell*.78f,y+cell*.22f,cell*.10f,p);
            if(board.power[r][col]>0){p.setColor(ink);p.setTextSize(cell*.35f);c.drawText("★",x+cell*.25f,y+cell*.35f,p);}
            if(selected==pos||hint!=null&&(hint[0]==pos||hint[1]==pos)){p.setColor(ink);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));c.drawRoundRect(x+3,y+3,x+cell-3,y+cell-3,8,8,p);p.setStyle(Paint.Style.FILL);}
        }
        float t=(SystemClock.uptimeMillis()-fxAt)/500f;if(t<1){p.setColor(0xFFFFBB23);p.setAlpha((int)(255*(1-t)));for(int pos:sparkle)for(int n=0;n<5;n++){double a=n*Math.PI*2/5;c.drawCircle((pos%8+.5f)*cell+(float)Math.cos(a)*cell*t,(pos/8+.5f)*cell+(float)Math.sin(a)*cell*t,dp(3),p);}p.setAlpha(255);if(active)postInvalidateDelayed(32);}}
        public boolean onTouchEvent(MotionEvent e){if(e.getAction()!=MotionEvent.ACTION_UP)return true;performClick();if(board.moves<=0)return true;int col=(int)(e.getX()/cell),r=(int)(e.getY()/cell);if(col<0||col>=8||r<0||r>=8)return true;int pos=r*8+col;
            if(selected<0){selected=pos;invalidate();return true;}int a=selected;selected=-1;hint=null;if(board.move(a,pos)){host.playSound(GameAudio.MATCH);sparkle.clear();sparkle.addAll(board.changed);fxAt=SystemClock.uptimeMillis();saveBoard();breakStatus.setText("เหลือ "+board.moves+" ตา  •  คะแนน "+board.score);if(board.moves<=0||board.won||board.lost){host.playSound(GameAudio.COMPLETE);render();return true;}}
            else{selected=pos;host.playSound(GameAudio.INVALID);}invalidate();return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
