package com.sunantongsan.ghostmatch3;
final class QuizHistoryTest {
    public static void main(String[] args){
        for(QuizProgress.Phase phase:QuizProgress.Phase.values()){
            QuizProgress p=new QuizProgress(156,73,2,phase.name());
            int before=p.index,streak=p.streak;QuizProgress.Phase state=p.phase;
            int count=QuizHistory.count(p);
            if(count!=(state==QuizProgress.Phase.CORRECT?74:73))throw new AssertionError("locked current answer leaked");
            if(p.index!=before||p.streak!=streak||p.phase!=state)throw new AssertionError("history mutated game");
        }
        if(QuizHistory.count(new QuizProgress(156,0,0,"QUESTION"))!=0)throw new AssertionError();
        if(QuizHistory.count(new QuizProgress(156,156,0,"COMPLETE"))!=156)throw new AssertionError();
        System.out.println("PASS: history boundaries, no current wrong-answer leak or gate mutation");
    }
}
