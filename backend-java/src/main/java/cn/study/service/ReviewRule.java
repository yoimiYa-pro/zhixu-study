package cn.study.service;

public final class ReviewRule {
    private static final int[] DAYS={1,3,7,14,30};
    public record Schedule(int stage,int days) {}
    private ReviewRule() {}
    public static Schedule next(int stage,boolean correct,int confidence,String difficulty,double mastery,int attempts) {
        if(stage<0 || stage>4) throw new IllegalArgumentException("Invalid review stage");
        if(!correct) return new Schedule(Math.max(0,stage-2),1);
        int next=Math.min(4,stage+1),days=DAYS[next];
        if(confidence<=2) days=Math.min(days,3);
        if(difficulty.equals("困难")) days=(int)Math.ceil(days*0.75);
        if(attempts>=2 && mastery<50) days=Math.max(1,(int)Math.ceil(days*0.5));
        return new Schedule(next,days);
    }
}
