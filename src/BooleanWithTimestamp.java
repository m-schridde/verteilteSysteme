public class BooleanWithTimestamp {
    private boolean value;
    private long time;

    private String booleanToTOrF(boolean b){
        return b?"t":"f";
    }

    public String toString(){
        return (booleanToTOrF(this.isValue()) + this.getTime());
    }

    public BooleanWithTimestamp(boolean value){
        this.time = System.currentTimeMillis();
        this.value = value;
    }
    public void setValue(boolean value){
        this.time = System.currentTimeMillis();
        this.value = value;
    }
    public boolean isValue() {
        return value;
    }

    public long getTime() {
        return time;
    }

}
