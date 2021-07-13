public class BooleanWithTimestamp {
    private boolean value;
    private long time;

    private String booleanToTOrF(boolean b){
        return b?"t":"f";
    }

    public String toString(){
        return (booleanToTOrF(this.isValue()) + this.getTime());
    }

    public static BooleanWithTimestamp createFromString(String s){
        char[] chars = s.toCharArray();
        BooleanWithTimestamp b = new BooleanWithTimestamp(UserRequestObject.tOrFToBoolean(chars[0] + ""));
        char[] timestamp = new char[chars.length -1];
        for(int i = 1; i < chars.length; i++){
            timestamp[i-1] = chars[i];
        }
        String timestampString = "";
        for(char c : timestamp){
            timestampString += c;
        }
        b.setTime(Long.parseLong(timestampString));
        return b;
    }

    private void setTime(long time){
        this.time = time;
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
