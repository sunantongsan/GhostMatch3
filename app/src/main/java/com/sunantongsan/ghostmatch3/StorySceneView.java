package com.sunantongsan.ghostmatch3;

import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import android.text.*;
import org.json.*;

/** Illustrated story stage used by every question. Choice hit targets are native accessible views. */
final class StorySceneView extends FrameLayout {
    interface Listener {void choose(int choice);void wrongFinished();void revealFinished();}
    private final MainActivity host;
    private final StoryArt art;
    private final JSONObject scene,joke;
    private final Listener listener;
    private final Stage stage;
    private final View[] choices=new View[3];
    private final int[] objects=new int[3];
    private final String[] tags=new String[3];
    private final int room,target,layout;
    private final String action;
    private final int questionNumber,total;
    private long elapsed,last=SystemClock.uptimeMillis();
    private boolean active=true,released,callbackSent;
    private int mode=0,chosen=-1; // intro/choose, wrong, resolution, final
    private long transition;
    static final long WRONG_MS=1500,REVEAL_MS=5200;
    StorySceneView(MainActivity host,StoryArt art,JSONObject scene,JSONObject joke,int index,int total,Listener listener){
        super(host);this.host=host;this.art=art;this.scene=scene;this.joke=joke;this.listener=listener;this.questionNumber=index+1;this.total=total;
        room=scene.optInt("room");target=scene.optInt("target");action=scene.optString("action");layout=scene.optInt("layout");
        setClipChildren(true);setBackgroundColor(0xFF152A36);
        stage=new Stage();addView(stage,new FrameLayout.LayoutParams(-1,-1));
        JSONArray list=joke.optJSONArray("choices");
        for(int n=0;n<3;n++){
            final int choice=n;objects[n]=scene.optJSONArray("objects").optInt(n);tags[n]=scene.optJSONArray("labels").optString(n);
            View hit=new View(host);hit.setBackgroundColor(Color.TRANSPARENT);hit.setContentDescription(tags[n]);hit.setFocusable(true);hit.setClickable(true);
            hit.setOnClickListener(v->{if(mode==0&&active&&!released){setChoicesEnabled(false);listener.choose(choice);}});
            hit.setOnLongClickListener(v->{host.sayStory(tags[choice]);return true;});
            choices[n]=hit;addView(hit,new FrameLayout.LayoutParams(1,1));
        }
        setContentDescription("ฉากเรื่องที่ "+questionNumber+" "+questionText());
    }
    String questionText(){return scene.optString("prompt",joke.optString("question"));}
    private void setChoicesEnabled(boolean value){for(View v:choices){v.setEnabled(value);v.setVisibility(value?VISIBLE:INVISIBLE);}}
    private float ox(int i){return new float[][]{{.16f,.51f,.84f},{.20f,.80f,.50f},{.82f,.18f,.50f},{.50f,.84f,.16f},{.16f,.84f,.50f}}[layout%5][i];}
    private float oy(int i){return new float[][]{{.91f,.85f,.93f},{.84f,.87f,.96f},{.85f,.92f,.96f},{.85f,.93f,.91f},{.88f,.88f,.97f}}[layout%5][i]-.07f;}
    // A single MATCH_PARENT child can retain a zero measurement after ScrollView's
    // unconstrained pass. Always measure and lay out the drawing surface explicitly.
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        super.onMeasure(widthSpec,heightSpec);
        int w=getMeasuredWidth(),h=getMeasuredHeight();
        stage.measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));
        int size=Math.round(w*.27f),spec=MeasureSpec.makeMeasureSpec(size,MeasureSpec.EXACTLY);
        for(View choice:choices)choice.measure(spec,MeasureSpec.makeMeasureSpec(size+Math.round(h*.07f),MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        int w=r-l,h=b-t;stage.layout(0,0,w,h);
        for(int i=0;i<3;i++){
            int size=choices[i].getMeasuredWidth(),x=Math.round(w*ox(i)-size/2f),y=Math.round(h*oy(i)-size);
            choices[i].layout(x,y,x+size,y+choices[i].getMeasuredHeight());
        }
    }
    void showWrong(int choice){chosen=choice;mode=1;transition=elapsed;callbackSent=false;setChoicesEnabled(false);stage.invalidate();}
    void showResolution(int choice){chosen=choice;mode=2;transition=elapsed;callbackSent=false;setChoicesEnabled(false);host.stopStorySpeech();stage.invalidate();}
    void showGate(){mode=1;transition=elapsed-WRONG_MS;callbackSent=true;setChoicesEnabled(false);stage.invalidate();}
    void showFinal(){mode=3;chosen=joke.optInt("answer");setChoicesEnabled(false);stage.invalidate();}
    void setActive(boolean value){active=value;last=SystemClock.uptimeMillis();if(value)stage.invalidate();}
    void release(){released=true;active=false;setChoicesEnabled(false);}
    int mode(){return mode;}
    View choiceView(int n){return choices[n];}
    private static float clamp(float t){return Math.max(0,Math.min(1,t));}
    private static float ease(float t){t=clamp(t);return t*t*(3-2*t);}
    private boolean effect(String names){return ("|"+names+"|").contains("|"+action+"|");}
    private final class Stage extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final TextPaint text=new TextPaint(Paint.ANTI_ALIAS_FLAG);
        Stage(){super(host);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        private void shadow(Canvas c,float x,float y,float size){int alpha=p.getAlpha();p.setColor(0x380B1720);p.setAlpha(alpha*56/255);c.drawOval(x-size*.45f,y-size*.06f,x+size*.45f,y+size*.06f,p);p.setColor(Color.WHITE);p.setAlpha(alpha);}
        private void prop(Canvas c,int id,float x,float y,float size){shadow(c,x,y,size);art.sprite(c,id,x,y,size,size,p);}
        private void line(Canvas c,float x1,float y1,float x2,float y2,int color,float width){p.setColor(color);p.setStrokeWidth(width);c.drawLine(x1,y1,x2,y2,p);p.setColor(Color.WHITE);}
        private void label(Canvas c,String value,float x,float y,float width,float size,int color){
            if(value.isEmpty())return;text.setColor(color);text.setTextSize(size);text.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
            StaticLayout lines=StaticLayout.Builder.obtain(value,0,value.length(),text,Math.max(1,(int)width)).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setLineSpacing(0,1.05f).build();
            c.save();c.translate(x-width/2,y);lines.draw(c);c.restore();
        }
        protected void onDraw(Canvas c){
            long now=SystemClock.uptimeMillis();if(active&&!released)elapsed+=Math.min(80,Math.max(0,now-last));last=now;
            float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
            art.background(c,room,w,h,p);
            // Soft cinematic shading keeps actors and actual objects legible, without card UI.
            p.setShader(new LinearGradient(0,0,0,h,new int[]{0xA5101929,0x00000000,0x00101A29,0x60101A29},new float[]{0,.3f,.65f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);p.setColor(Color.WHITE);
            float seconds=elapsed/1000f,cut=(elapsed-transition)/1000f;
            float a=mode>=2?clamp(cut/5.2f):0,act=mode==3?1:ease((a-.40f)/.43f);
            float tx=w*.72f,ty=h*.64f,ts=w*.39f;
            if(action.equals("door"))ts=w*.48f;
            float hx=w*.25f,hy=h*.70f,hs=h*.35f;
            int frame=16+(int)(seconds*3)%4;
            if(mode==1){frame=20+(int)(cut*6)%4;}
            if(mode>=2){hx+=w*.20f*ease(a/.35f);frame=a<.43f?12+(int)(seconds*6)%4:a<.88f?25:27;}
            if(mode==3){hx=w*.43f;frame=27;}
            shadow(c,hx,hy,w*.24f);
            art.actor(c,frame,hx,hy,hs,mode==1?(float)Math.sin(cut*15)*7:0,p);
            // Story subject responds only after Ser Joy has carried the chosen object over.
            c.save();float dx=0,dy=0,rotation=0,sx=1,sy=1;int alpha=255;int drawnTarget=target;
            if(mode>=2){
                if(action.equals("wetshoe")){dy=(float)Math.sin(act*Math.PI)*h*.10f;if(act>.55f)drawnTarget=256;}
                else if(action.equals("straighten")){rotation=-14*(1-act);if(act>.9f)drawnTarget=272;}
                else if(action.equals("grill")){if(act>.4f)drawnTarget=275;}
                else if(action.equals("fishboss")){if(act>.35f)drawnTarget=263;dy=-(float)Math.abs(Math.sin(act*12))*h*.02f;}
                else if(action.equals("antswim")){if(act>.3f)drawnTarget=282;dx=(float)Math.sin(act*10)*w*.08f;}
                else if(action.equals("escape")){dx=w*act;dy=-(float)Math.abs(Math.sin(act*25))*h*.025f;}
                else if(action.equals("bellring")){rotation=(float)Math.sin(act*26)*25;}
                else if(action.equals("delegate")){rotation=-10*act;}
                else if(action.equals("door")){drawnTarget=act>0?-2:target;dx=w*1.3f*ease(act);dy=-(float)Math.abs(Math.sin(act*42))*h*.028f;rotation=(float)Math.sin(act*38)*9;}
                else if(effect("sink|distance")){dy=h*.25f*act;sx=sy=1-.65f*act;alpha=(int)(255*(1-act*.7f));}
                else if(effect("fall|dead|sneeze|hit|punch|shiver|surprise")){rotation=action.equals("dead")?150*act:action.equals("fall")?75*act:(float)Math.sin(act*25)*15;dy=action.equals("fall")?act*h*.07f:-(float)Math.abs(Math.sin(act*16))*h*.015f;}
                else if(effect("float|fly|lift|jump|grow|rise|speed|walk")){dy=-h*.2f*act;if(action.equals("grow"))sx=sy=1+act*.5f;if(action.equals("jump"))dx=w*.2f*act;if(action.equals("speed"))dx=w*act;}
                else if(effect("shrink|vanish|erase|empty|dark")){sx=sy=1-act*.85f;if(!action.equals("shrink"))alpha=(int)(255*(1-act));}
                else if(effect("spin|wheels|wind|music|dance|march")){rotation=(float)Math.sin(act*20)*18;if(action.equals("spin"))rotation=act*1080;dy=-(float)Math.abs(Math.sin(act*20))*h*.02f;}
                else if(effect("inflate|stretch|burst")){sx=1+act*.65f;sy=1+act*.25f;}
                else if(effect("open|mirror|matchbox|reveal|eyes|family")){sx=(float)Math.cos(act*Math.PI);}
                else if(effect("sleep|soothe|stop")){rotation=-8*act;}
                else if(effect("weight|balance|seat|stuck|stand")){dy=h*.05f*act;rotation=(float)Math.sin(act*10)*5*(1-act);}
                c.translate(dx,dy);c.rotate(rotation,tx,ty-ts*.5f);c.scale(sx,sy,tx,ty);p.setAlpha(alpha);
            }
            prop(c,drawnTarget,tx,ty,ts);p.setAlpha(255);c.restore();
            if(mode>=2){
                float pickup=ease(a/.18f),carry=ease((a-.18f)/.23f);
                float cx=ox(chosen)*w+(hx+w*.13f-ox(chosen)*w)*pickup;
                float cy=oy(chosen)*h+(hy-hs*.42f-oy(chosen)*h)*pickup;
                cx+=(tx-w*.13f-cx)*carry;cy+=(ty-cy)*carry;
                if(mode==3){cx=tx-w*.13f;cy=ty;}
                prop(c,objects[chosen],cx,cy,w*.23f);
                resolvedEffects(c,act,w,h,tx,ty,ts,objects[chosen]);
            }
            if(mode<2){
                for(int i=0;i<3;i++){
                    float x=ox(i)*w,y=oy(i)*h;
                    if(mode==1&&i==chosen){c.save();c.rotate((float)Math.sin(cut*19)*8,x,y);}
                    if(scene.has("tints")&&scene.optJSONArray("tints").optInt(i)!=0)p.setColorFilter(new PorterDuffColorFilter(scene.optJSONArray("tints").optInt(i),PorterDuff.Mode.SRC_ATOP));
                    prop(c,objects[i],x,y,w*.25f*(scene.has("sizes")?(float)scene.optJSONArray("sizes").optDouble(i,1):1));p.setColorFilter(null);
                    if(!tags[i].isEmpty()){
                        float font=w*.032f;StaticLayout caption;
                        do{text.setTextSize(font);text.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));caption=StaticLayout.Builder.obtain(tags[i],0,tags[i].length(),text,Math.max(1,(int)(w*.25f))).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build();if(caption.getHeight()<=h*.057f||font<=w*.023f)break;font-=w*.001f;}while(true);
                        float top=y+h*.005f,bottom=top+caption.getHeight()+h*.009f;
                        p.setColor(0xF5FFF5DD);c.drawRoundRect(x-w*.135f,top,x+w*.135f,bottom,w*.014f,w*.014f,p);
                        text.setColor(0xFF302A35);c.save();c.translate(x-w*.125f,top+h*.004f);caption.draw(c);c.restore();p.setColor(Color.WHITE);
                    }
                    if(mode==1&&i==chosen)c.restore();
                }
            }
            String speech=mode==1?"ฮ่า ๆ! ยังไม่ใช่ ลองอีกที!":mode==3?joke.optString("punchline"):questionText();
            float bubbleTop=h*.105f,bubbleW=w*.89f;
            text.setTextSize(w*.047f);text.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
            StaticLayout measure=StaticLayout.Builder.obtain(speech,0,speech.length(),text,(int)(bubbleW-w*.06f)).setIncludePad(false).build();
            float bubbleH=Math.max(h*.09f,measure.getHeight()+h*.033f);
            p.setColor(0xF9FFF9ED);c.drawRoundRect(w*.055f,bubbleTop,w*.945f,bubbleTop+bubbleH,w*.035f,w*.035f,p);
            Path tail=new Path();tail.moveTo(w*.20f,bubbleTop+bubbleH-1);tail.lineTo(w*.25f,bubbleTop+bubbleH+h*.025f);tail.lineTo(w*.32f,bubbleTop+bubbleH-1);c.drawPath(tail,p);
            label(c,speech,w*.5f,bubbleTop+h*.013f,bubbleW-w*.06f,w*.047f,0xFF27283C);
            label(c,"ท่านเซ่อจ่อย  •  "+questionNumber+" / "+total,w*.39f,h*.025f,w*.72f,w*.032f,Color.WHITE);
            if(mode==0)label(c,"แตะของในฉากเพื่อตอบ",w*.5f,h*.975f,w*.8f,w*.028f,Color.WHITE);
            if(!callbackSent&&mode==1&&elapsed-transition>=WRONG_MS){callbackSent=true;post(()->{if(!released)listener.wrongFinished();});}
            if(!callbackSent&&mode==2&&elapsed-transition>=REVEAL_MS){callbackSent=true;mode=3;post(()->{if(!released)listener.revealFinished();});}
            if(active&&!released)postInvalidateDelayed(mode==3?80:33);
        }
        private void resolvedEffects(Canvas c,float t,float w,float h,float x,float y,float size,int selected){
            if(t<=0)return;
            float pulse=(float)Math.sin(t*22);
            if(action.equals("cliffstep")){
                float step=ease(t);art.sprite(c,224,x-size*.2f+step*size*.75f,y-size*.75f+step*step*h*.35f,size*.4f,size*.4f,p);
            }else if(action.equals("motherwash")){
                art.sprite(c,-13,x-size*.55f,y,size*.65f,size*.8f,p);
                art.sprite(c,202,x+(float)Math.sin(t*24)*size*.22f,y-size*.3f,size*.23f,size*.23f,p);
                for(int i=0;i<5;i++){p.setColor(0xCC9CF1FF);c.drawCircle(x+(i-2)*size*.14f,y-size*(.1f+t*.3f),size*.04f,p);}
            }else if(action.equals("delegate")){
                art.sprite(c,287,x-size*.5f,y-size*.1f,size*.55f,size*.55f,p);label(c,"วานหน่อย…",x,y-size-h*.025f,w*.4f,w*.035f,Color.WHITE);
            }else if(action.equals("onepen")){
                art.sprite(c,138,x-size*.2f,y-size*.5f-t*size*.1f,size*.5f,size*.5f,p);label(c,"ONE PEN",x,y-size-h*.025f,w*.4f,w*.04f,Color.WHITE);
            }else if(action.equals("dwarfs")){
                for(int i=0;i<7;i++)art.sprite(c,280,w*(.17f+i*.105f),h*.77f-(float)Math.abs(Math.sin(t*14+i))*h*.025f,w*.11f,w*.17f,p);
            }else if(action.equals("outbreak")){
                for(int i=0;i<5;i++)art.sprite(c,276,x+(i-2)*size*.24f*t,y-(i%2)*size*.3f*t,size*.25f,size*.25f,p);
                label(c,"บาทเดียว!",x,y-size-h*.03f,w*.4f,w*.045f,Color.WHITE);
            }else if(action.equals("bellring")){
                label(c,"กริ๊ง!",x,y-size-h*.04f,w*.4f,w*.05f,Color.WHITE);
            }else if(action.equals("door")){
                for(int i=0;i<7;i++){p.setColor(Color.argb((int)(120*(1-t)),234,215,177));c.drawCircle(x+w*t*.4f-i*w*.055f,y-h*.01f,w*(.02f+.025f*t),p);}
            }else if(effect("sink|swim|wash|shower|pour|bottle|wetshoe|antswim")){
                p.setColor(0x8840D8ED);c.drawOval(x-size*.65f,y-size*.14f,x+size*.65f,y+size*.11f,p);
                for(int i=0;i<8;i++){p.setColor(0xDD9CF1FF);float bx=x+(float)Math.sin(i*2+t*5)*size*.6f,by=y-(float)Math.abs(Math.sin(i+t*9))*size*.4f;c.drawCircle(bx,by,w*.011f,p);}
            }else if(effect("crack|cut|burst")){
                for(int i=0;i<6;i++){float angle=(float)(i*Math.PI/3);p.setColor(0xFFFFDB8E);c.save();c.translate(x+(float)Math.cos(angle)*t*size,y-size*.5f+(float)Math.sin(angle)*t*size);c.rotate(t*350);c.drawRect(-w*.017f,-w*.025f,w*.017f,w*.025f,p);c.restore();}
                line(c,x-size*.15f,y-size*.9f,x+size*.05f,y-size*.6f,0xFF263044,w*.012f);line(c,x+size*.05f,y-size*.6f,x-size*.08f,y-size*.3f,0xFF263044,w*.012f);
            }else if(action.equals("basket")){
                float bx=w*.40f+w*.31f*t,by=h*.64f-(float)Math.sin(t*Math.PI)*h*.26f;
                prop(c,59,bx,by,w*.12f);art.sprite(c,-14,x,y-size*.28f,size*.7f,size*.7f,p);
            }else if(action.equals("toad")){art.sprite(c,-15,x,y-size*.7f-(float)Math.sin(t*Math.PI)*h*.15f,w*.22f,w*.22f,p);}
            else if(action.equals("matchbox")){art.sprite(c,-12,x,y,size,size,p);}
            else if(action.equals("freeze")){p.setColor(0x889BEAFF);c.drawRoundRect(x-size*.55f,y-size,x+size*.55f,y,size*.1f,size*.1f,p);}
            else if(action.equals("sleep")){label(c,"Z z z",x+w*.12f,y-size-h*.03f*t,w*.3f,w*.07f,Color.WHITE);}
            else if(effect("tie|wire|glue|lock|socks|ring|helmet|blanket|pack|frame|bookmark|hang|photo|carpet")){
                if(action.equals("frame")){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w*.035f);p.setColor(0xFFE6AC43);c.drawRoundRect(x-size*.55f,y-size*1.05f,x+size*.55f,y,size*.04f,size*.04f,p);p.setStyle(Paint.Style.FILL);}
                else {line(c,x-size*.4f,y-size*.6f,x+size*.4f,y-size*.2f,0xFFEDB657,w*.012f);art.sprite(c,selected,x,y-size*.35f,size*.55f,size*.55f,p);}
            }else if(effect("copy|split|family|count|calendar|march")){
                for(int i=0;i<3;i++)art.sprite(c,target,x-size*.5f+i*size*.35f,y-h*.07f*t,size*.30f,size*.30f,p);
            }else if(effect("eyes|jaw|talk|taste|eat|soothe")){
                p.setColor(Color.WHITE);c.drawOval(x-size*.25f,y-size*.72f,x-size*.02f,y-size*.43f,p);c.drawOval(x+size*.02f,y-size*.72f,x+size*.25f,y-size*.43f,p);
                p.setColor(0xFF1F2938);c.drawCircle(x-size*.12f,y-size*.55f,size*.045f,p);c.drawCircle(x+size*.13f,y-size*.55f,size*.045f,p);
            }else if(effect("paint|stamp|dot|price|letters")){
                p.setColor(0xFFD86B68);c.save();c.rotate(-12,x,y);c.drawCircle(x,y-size*.4f,size*.20f*ease(t),p);c.restore();
                if(action.equals("letters"))label(c,"ก ข ค",x,y-size*.9f-h*.1f*t,size,w*.075f,0xFFFFED97);
            }else if(effect("sweep|wipe|filter|dig|drill|grab|catch|fish|pull|measure|magnify|map|light|charge|mute|stop|balance")){
                c.save();c.translate((float)Math.sin(t*14)*w*.07f,-h*.04f*t);art.sprite(c,selected,x,y-size*.3f,size*.6f,size*.6f,p);c.restore();
                for(int i=0;i<6;i++){p.setColor(0xCCFFF2BA);c.drawCircle(x+(float)Math.cos(i+t*5)*size*.6f,y-size*.5f+(float)Math.sin(i+t*5)*size*.4f,w*.008f,p);}
            }else{
                // Dust, motion arcs and the actual answer prop complete the remaining authored actions.
                for(int i=0;i<4;i++){p.setColor(0xA8FFF0C3);c.drawCircle(x-size*.5f+i*size*.3f,y-size*.5f-(float)Math.sin(i+t*8)*size*.35f,w*.012f,p);}
            }
            p.setColor(Color.WHITE);p.setAlpha(255);
        }
    }
}
