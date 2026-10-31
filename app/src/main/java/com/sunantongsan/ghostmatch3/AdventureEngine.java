package com.sunantongsan.ghostmatch3;

import java.util.*;

/** Platform-independent rules. Every move resolves before the danger clock advances. */
final class AdventureEngine {
    static final int SIZE=8, COLORS=6, CLEAR=0, RESCUE=1, BONUS=2, BOSS=3;
    static final String[] WORLDS={"สวนฟักทอง","ห้องปรุงยา","ป่าเรืองแสง","หอนาฬิกา","คลองผีน้อย","ห้องสมุดลับ","ตลาดเที่ยงคืน","ถ้ำน้ำแข็ง","โรงละครผี","สวนเมฆ","ปราสาทดาว","ประตูแสงจันทร์"};
    static final String[] OBSTACLES={"หินอาคม","น้ำแข็ง","ใยแมงมุม","หีบล็อก","ผียักษ์","ผียักษ์"};
    static final int BIG=4,BIG_PART=5;
    /** Authored Babytopia-style boards from level 4: '.' piece, '#' hole, r/R stone, i ice, w web, l lock, G+g 2x2 giant ghost. */
    static final String[][] LAYOUTS={
        {"..#..#..","........","..i..i..","........","rr....rr","........","..#..#..","........"},
        {"...##...","........","..w..w..","#......#","#......#","..w..w..","........","...##..."},
        {"#......#","........","...Gg...","...gg...","..r..r..","........","........","#......#"},
        {"rrrr....","rrrr....","rrr.....","rr......","........","........","........","........"},
        {"###..###","##....##","#......#","...ww...","...ww...","#......#","##....##","###..###"},
        {"........",".ii..ii.","........","........","........","........",".ii..ii.","........"},
        {"l......l","l......l","........","..Gg....","..gg....","........","l......l","l......l"},
        {"....rrrr","....rrrr",".....rrr","......rr","........","........","..i..i..","........"},
        {"........","........","#..rr..#","........","........",".Gg..Gg.",".gg..gg.","........"},
        {"........","........","........","...rr...","..rRRr..","..rrrr..","........","........"},
        {"........","........","........","........","rr....rr","r......r","........","........"},
        {"Gg....Gg","gg....gg","........","........","..####..","........","........","........"}
    };
    static final class Level {
        final int number,world,kind,variant,moves,goal,color,reward,color2,goal2; final boolean bonus;
        Level(int n){this(n,null);}
        Level(int n,int[] balance){
            if(n<1||n>120)throw new IllegalArgumentException("level");
            number=n;world=(n-1)/10;bonus=n%10==0;
            kind=n%6==0?BOSS:bonus?BONUS:n%2==0?RESCUE:CLEAR;
            variant=(n/2+world)%4;color=(n+world)%COLORS;color2=(color+3)%COLORS;
            // Second collection goal (like Babytopia's multi-goal panel) for ordinary and rescue stages from level 11.
            goal2=n>=11&&(kind==CLEAR||kind==RESCUE)?4+world/2+n%3:0;
            if(balance!=null&&(balance.length!=3||balance[0]<1||balance[0]>100||balance[1]<1||balance[1]>500||balance[2]<0||balance[2]>10000))throw new IllegalArgumentException("balance");
            moves=balance!=null?balance[0]:n<=10?48:n<=20?44:kind==BONUS?28:kind==BOSS?40:kind==RESCUE?38:36;
            goal=balance!=null?balance[1]:n<=5?6:n<=10?(kind==BOSS?16:10):kind==BOSS?26+world*2:kind==BONUS?20+world:kind==RESCUE?12+world:10+world;
            reward=balance!=null?balance[2]:60+world*10+(kind==RESCUE?30:kind==BOSS?100:0)+(bonus?220:0);
        }
        String name(){return kind==BOSS?"บอสจอมป่วน":kind==BONUS?"ห้องสมบัติ":kind==RESCUE?"คนดีผีคุ้ม":"เคลียร์อาคม";}
        String objective(){
            if(kind==BOSS)return "จับคู่สีเป้าหมายลดพลังบอส";
            if(kind==BONUS)return "เก็บสีเป้าหมาย เปิดหีบรับทอง";
            if(kind==RESCUE)return "รวมพลังผี เปิดทางช่วยคนดี";
            return "เก็บสีเป้าหมายและเคลียร์อุปสรรค";
        }
    }
    final Level level;
    final int[][] tile=new int[SIZE][SIZE],power=new int[SIZE][SIZE],hp=new int[SIZE][SIZE],obstacle=new int[SIZE][SIZE];
    final boolean[][] active=new boolean[SIZE][SIZE];
    final Random random;
    int moves,progress,progress2,remaining,initialObstacles,turns,danger,freeze,shield,score,chains,lastCollected;
    boolean won,lost;
    final Set<Integer> changed=new HashSet<>();
    final List<int[]> casts=new ArrayList<>();
    AdventureEngine(int n,long seed){this(n,seed,null);}
    AdventureEngine(int n,long seed,int[] balance){
        level=new Level(n,balance);random=new Random(seed);moves=level.moves+(level.kind==RESCUE?8:0);
        // Distinct masks and obstacle placements: seeded per level, pieces vary per attempt.
        Random layout=new Random(n*7919L);
        if(n>=4){
            String[] map=LAYOUTS[(n-4)%LAYOUTS.length];
            int bigHp=Math.min(6,3+level.world/3);
            for(int r=0;r<SIZE;r++)for(int c=0;c<SIZE;c++){
                char ch=map[r].charAt(c);tile[r][c]=-1;active[r][c]=ch!='#';
                if(level.world==0){if(ch=='l')ch='r';if(ch=='w')ch='i';}
                if(n<8&&(ch=='G'||ch=='g'))ch='.';
                if(ch=='r'){obstacle[r][c]=0;hp[r][c]=1+(level.world>=3?1:0);}
                else if(ch=='R'){obstacle[r][c]=0;hp[r][c]=2;}
                else if(ch=='i'){obstacle[r][c]=1;hp[r][c]=1;}
                else if(ch=='w'){obstacle[r][c]=2;hp[r][c]=1;}
                else if(ch=='l'){obstacle[r][c]=3;hp[r][c]=2;}
                else if(ch=='G'){obstacle[r][c]=BIG;hp[r][c]=bigHp;}
                else if(ch=='g'){obstacle[r][c]=BIG_PART;hp[r][c]=bigHp;}
                if(ch!='g')remaining+=hp[r][c];
            }
        }else{
            for(int r=0;r<SIZE;r++)for(int c=0;c<SIZE;c++){
                int shape=(n-1)%5;
                active[r][c]=!(shape==1&&(r==0||r==7)&&(c==0||c==7))
                    &&!(shape==2&&(r==0||r==7)&&(c<2||c>5))
                    &&!(shape==3&&((r==0&&c<2)||(r==7&&c>5)));
                tile[r][c]=-1;
            }
            List<Integer> spots=new ArrayList<>();
            for(int r=1;r<7;r++)for(int c=0;c<8;c++)if(active[r][c])spots.add(r*8+c);
            Collections.shuffle(spots,layout);
            int count=n<=3?2:3;
            for(int i=0;i<count;i++){
                int p=spots.get(i),r=p/8,c=p%8;
                obstacle[r][c]=0;hp[r][c]=1;remaining+=hp[r][c];
            }
        }
        initialObstacles=remaining;refillFresh();ensureMove();
    }
    boolean inside(int r,int c){return r>=0&&r<8&&c>=0&&c<8&&active[r][c];}
    boolean movable(int r,int c){return inside(r,c)&&hp[r][c]==0;}
    /** Giant ghosts cover 2x2 cells; every hit on any of the four cells is routed to the top-left anchor. */
    int anchor(int pos){
        int r=pos/8,c=pos%8;if(obstacle[r][c]!=BIG_PART)return pos;
        for(int[] d:new int[][]{{0,-1},{-1,0},{-1,-1}}){int y=r+d[0],x=c+d[1];if(y>=0&&x>=0&&obstacle[y][x]==BIG&&hp[y][x]>0)return y*8+x;}
        return pos;
    }
    void refillFresh(){
        for(int r=0;r<8;r++)for(int c=0;c<8;c++){
            if(!active[r][c])continue;
            int t;do{t=random.nextInt(COLORS);}while((c>1&&tile[r][c-1]==t&&tile[r][c-2]==t)||(r>1&&tile[r-1][c]==t&&tile[r-2][c]==t));
            tile[r][c]=t;
        }
    }
    Set<Integer> matches(){
        Set<Integer> hits=new HashSet<>();
        for(int r=0;r<8;r++)for(int c=0;c<8;c++)if(inside(r,c)&&hp[r][c]==0&&tile[r][c]>=0){
            int t=tile[r][c],end=c+1;
            while(end<8&&inside(r,end)&&hp[r][end]==0&&tile[r][end]==t)end++;
            if(end-c>=3)for(int x=c;x<end;x++)hits.add(r*8+x);
            end=r+1;while(end<8&&inside(end,c)&&hp[end][c]==0&&tile[end][c]==t)end++;
            if(end-r>=3)for(int y=r;y<end;y++)hits.add(y*8+c);
        }
        return hits;
    }
    void swap(int a,int b){int r=a/8,c=a%8,y=b/8,x=b%8,t=tile[r][c];tile[r][c]=tile[y][x];tile[y][x]=t;t=power[r][c];power[r][c]=power[y][x];power[y][x]=t;}
    boolean move(int a,int b){
        if(won||lost||moves<=0||a<0||b<0||a>=64||b>=64)return false;
        if(Math.abs(a/8-b/8)+Math.abs(a%8-b%8)!=1||!movable(a/8,a%8)||!movable(b/8,b%8))return false;
        changed.clear();casts.clear();lastCollected=0;chains=0;int pa=power[a/8][a%8],pb=power[b/8][b%8];swap(a,b);
        Set<Integer> hits=matches();
        if(pa>0||pb>0){hits.add(a);hits.add(b);}
        if(hits.isEmpty()){swap(a,b);return false;}
        moves--;turns++;
        resolve(hits,pa==0&&pb==0?b:-1);
        advanceDanger();finish();ensureMove();return true;
    }
    int makePower(Set<Integer> hits,int pos){
        if(pos<0||!hits.contains(pos))return 0;
        int r=pos/8,c=pos%8,t=tile[r][c],h=1,v=1;
        for(int x=c-1;x>=0&&hits.contains(r*8+x)&&tile[r][x]==t;x--)h++;
        for(int x=c+1;x<8&&hits.contains(r*8+x)&&tile[r][x]==t;x++)h++;
        for(int y=r-1;y>=0&&hits.contains(y*8+c)&&tile[y][c]==t;y--)v++;
        for(int y=r+1;y<8&&hits.contains(y*8+c)&&tile[y][c]==t;y++)v++;
        return Math.max(h,v)>=5?4:h>=3&&v>=3?3:h>=4?1:v>=4?2:0;
    }
    void resolve(Set<Integer> hits,int preferred){
        // Hard cap protects against pathological random cascades; board is settled afterwards.
        while(!hits.isEmpty()&&chains<32){
            chains++;int born=makePower(hits,preferred);
            if(born==0){for(int pos:hits){born=makePower(hits,pos);if(born>0){preferred=pos;break;}}}
            Set<Integer> fired=new HashSet<>();boolean expanded;
            do{expanded=false;for(int pos:new HashSet<>(hits)){
                int r=pos/8,c=pos%8,p=power[r][c];if(p==0||!fired.add(pos))continue;
                expanded=true;casts.add(new int[]{pos,p});
                for(int y=0;y<8;y++)for(int x=0;x<8;x++)if(inside(y,x)&&(
                    p==1&&y==r||p==2&&x==c||p==3&&Math.abs(y-r)<=1&&Math.abs(x-c)<=1||p==4&&tile[y][x]==tile[r][c]))hits.add(y*8+x);
            }}while(expanded);
            Set<Integer> damaged=new HashSet<>();
            for(int pos:hits){
                int r=pos/8,c=pos%8;if(!inside(r,c))continue;
                if(hp[r][c]>0){damaged.add(pos);continue;}
                if(tile[r][c]==level.color){progress++;lastCollected++;}else if(level.goal2>0&&tile[r][c]==level.color2)progress2++;
                score+=10*chains;
                for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){int y=r+d[0],x=c+d[1];if(inside(y,x)&&hp[y][x]>0)damaged.add(y*8+x);}
                if(born>0&&pos==preferred&&!fired.contains(pos)){power[r][c]=born;}else{tile[r][c]=-1;power[r][c]=0;}
                changed.add(pos);
            }
            Set<Integer> struck=new HashSet<>();for(int pos:damaged)struck.add(anchor(pos));
            for(int pos:struck){int r=pos/8,c=pos%8;if(hp[r][c]<=0)continue;
                if(obstacle[r][c]==BIG&&r<7&&c<7){for(int y=r;y<=r+1;y++)for(int x=c;x<=c+1;x++)if(hp[y][x]>0){hp[y][x]--;changed.add(y*8+x);}}
                else{hp[r][c]--;changed.add(pos);}
                remaining--;score+=obstacle[r][c]==BIG?60:25;}
            collapse();preferred=-1;hits=matches();
        }
        if(chains>=32)refillFresh();
    }
    void collapse(){
        for(int c=0;c<8;c++){
            int end=7;
            while(end>=0){
                while(end>=0&&(!active[end][c]||hp[end][c]>0))end--;
                if(end<0)break;
                int start=end;while(start>0&&active[start-1][c]&&hp[start-1][c]==0)start--;
                int write=end;
                for(int r=end;r>=start;r--)if(tile[r][c]>=0){tile[write][c]=tile[r][c];power[write][c]=power[r][c];write--;}
                while(write>=start){tile[write][c]=random.nextInt(COLORS);power[write][c]=0;write--;}
                end=start-1;
            }
        }
    }
    void advanceDanger(){
        if(freeze>0){freeze--;return;}
        if(level.kind==RESCUE)danger++;
        if(level.kind==BOSS&&turns%4==0&&progress<level.goal){
            if(shield>0){shield--;return;}
            // Telegraphed boss frost adds a tactical restriction, never an instant loss.
            List<Integer> empty=new ArrayList<>();for(int r=0;r<8;r++)for(int c=0;c<8;c++)if(movable(r,c)&&power[r][c]==0)empty.add(r*8+c);
            if(empty.size()>12){int pos=empty.get(random.nextInt(empty.size()));hp[pos/8][pos%8]=1;obstacle[pos/8][pos%8]=1;remaining++;}
        }
    }
    boolean use(int item,int pos){
        if(won||lost||item<0||item>9||item==6)return false;
        changed.clear();casts.clear();lastCollected=0;chains=0;
        if(item==9){moves+=5;if(level.kind==RESCUE)danger=Math.max(0,danger-5);return true;}
        if(item==3){if(freeze>0||level.kind!=BOSS&&level.kind!=RESCUE)return false;freeze=3;return true;}
        if(item==4){if(shield>0||level.kind!=BOSS&&level.kind!=RESCUE)return false;shield=1;return true;}
        if(item==5){shuffle();return true;}
        if(pos<0||pos>=64||!inside(pos/8,pos%8))return false;
        changed.clear();casts.clear();lastCollected=0;chains=0;Set<Integer> hits=new HashSet<>();
        for(int r=0;r<8;r++)for(int c=0;c<8;c++)if(inside(r,c)&&(item==0&&pos==r*8+c||item==1&&Math.abs(r-pos/8)<=1&&Math.abs(c-pos%8)<=1||item==2&&tile[r][c]==tile[pos/8][pos%8]||item==7&&r==pos/8||item==8&&c==pos%8))hits.add(r*8+c);
        casts.add(new int[]{pos,item==7?1:item==8?2:item==1?3:item==2?4:0});
        resolve(hits,-1);finish();ensureMove();return true;
    }
    void finish(){
        won=(level.kind==BOSS||level.kind==BONUS?progress>=level.goal:remaining==0&&progress>=level.goal)&&progress2>=level.goal2;
        if(!won&&(moves<=0))lost=true;
    }
    boolean freeSwap(int a,int b){
        if(won||lost||a<0||a>=64||b<0||b>=64||Math.abs(a/8-b/8)+Math.abs(a%8-b%8)!=1||!movable(a/8,a%8)||!movable(b/8,b%8))return false;
        changed.clear();casts.clear();lastCollected=0;chains=0;
        int pa=power[a/8][a%8],pb=power[b/8][b%8];swap(a,b);changed.add(a);changed.add(b);
        Set<Integer> hits=matches();if(pa>0||pb>0){hits.add(a);hits.add(b);}
        resolve(hits,pa==0&&pb==0?b:-1);finish();ensureMove();return true;
    }
    boolean possible(){
        for(int a=0;a<64;a++)if(movable(a/8,a%8)){

            for(int b:new int[]{a+1,a+8})if(b<64&&Math.abs(a/8-b/8)+Math.abs(a%8-b%8)==1&&movable(b/8,b%8)){
                swap(a,b);boolean ok=!matches().isEmpty()||power[a/8][a%8]>0||power[b/8][b%8]>0;swap(a,b);if(ok)return true;
            }
        }return false;
    }
    int[] hint(){for(int a=0;a<64;a++)if(movable(a/8,a%8))for(int b:new int[]{a+1,a+8})if(b<64&&Math.abs(a/8-b/8)+Math.abs(a%8-b%8)==1&&movable(b/8,b%8)){swap(a,b);boolean ok=!matches().isEmpty()||power[a/8][a%8]>0||power[b/8][b%8]>0;swap(a,b);if(ok)return new int[]{a,b};}return null;}
    void ensureMove(){if(!won&&!lost&&!possible())shuffle();}
    void shuffle(){
        for(int tries=0;tries<100;tries++){refillFresh();if(matches().isEmpty()&&possible())return;}
        // Guaranteed activation pair when obstacles leave no conventional three-in-a-row move.
        for(int a=0;a<64;a++)if(movable(a/8,a%8))for(int b:new int[]{a+1,a+8})if(b<64&&Math.abs(a/8-b/8)+Math.abs(a%8-b%8)==1&&movable(b/8,b%8)){power[a/8][a%8]=4;return;}
    }
    int reward(){return level.reward+Math.min(100,moves*3)+(level.kind==BONUS?Math.min(200,score/10):0);}
}
