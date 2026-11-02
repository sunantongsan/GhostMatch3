package com.sunantongsan.ghostmatch3;

import java.util.Random;

/** Each clip plays once per shuffled round; adjacent rounds cannot repeat a clip. */
final class AudioShuffle {
    private final int[] values;
    private final Random random;
    private int cursor, previous=-1;
    AudioShuffle(int[] values){this(values,new Random());}
    AudioShuffle(int[] values,Random random){
        if(values.length==0)throw new IllegalArgumentException("Empty audio playlist");
        this.values=values.clone();this.random=random;cursor=values.length;
    }
    int next(){
        if(cursor==values.length){
            for(int i=values.length-1;i>0;i--){int j=random.nextInt(i+1),v=values[i];values[i]=values[j];values[j]=v;}
            if(values.length>1&&values[0]==previous){int j=1+random.nextInt(values.length-1),v=values[0];values[0]=values[j];values[j]=v;}
            cursor=0;
        }
        return previous=values[cursor++];
    }
}
