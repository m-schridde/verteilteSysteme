import javax.xml.crypto.Data;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;

public class UserRequestObject {
    private int prepareTimeoutCounter = 0;
    private static final int timeoutMillis = 1000;
    private int id;
    private boolean answerToUserSent;
    private int[] servicePorts;
    private BooleanWithTimestamp[] preperationSent;
    private BooleanWithTimestamp[] readyReceived;
    private BooleanWithTimestamp abortReceived;
    private BooleanWithTimestamp[] commitSent;
    private BooleanWithTimestamp[] commitOKReceived;
    private BooleanWithTimestamp[] abortsSent;
    private BooleanWithTimestamp[] abortOKReceived;

    DatagramPacket userRequest;
    String[] words;

    private static String booleanToTOrF(boolean b){
        return b?"t":"f";
    }
    public static boolean tOrFToBoolean(String s){
        s = s.toUpperCase();
        switch (s){
            case "T":
            case "TRUE":
                return true;
            case "F":
            case "FALSE":
                return false;
        }
        return false;
    }

    public String toString(){
        String me = this.id + " " + booleanToTOrF(answerToUserSent)  + " ";
        int lastIndex = servicePorts.length -1;
        for(int i = 0; i < lastIndex; i++){
            me += preperationSent[i].toString() + ",";
        }
        me += preperationSent[lastIndex].toString() + " ";

        for(int i = 0; i < lastIndex; i++){
            me += readyReceived[i].toString() + ",";
        }
        me += readyReceived[lastIndex].toString() + " ";

        me += abortReceived.toString() + " ";

        for(int i = 0; i < lastIndex; i++){
            me += commitSent[i].toString() + ",";
        }
        me += commitSent[lastIndex].toString() + " ";

        for(int i = 0; i < lastIndex; i++){
            me += commitOKReceived[i].toString() + ",";
        }
        me += commitOKReceived[lastIndex].toString() + " ";

        for(int i = 0; i < lastIndex; i++){
            me += abortsSent[i].toString() + ",";
        }
        me += abortsSent[lastIndex].toString() + " ";

        for(int i = 0; i < lastIndex; i++){
            me += abortOKReceived[i].toString() + ",";
        }
        me += abortOKReceived[lastIndex].toString() + " ";

        me += userRequest.getAddress().getHostName()  + " " + userRequest.getPort() + " ";

        for(String w : words){
            me += w + " ";
        }
        return me.trim();
    }

    public DatagramPacket whatUserMessageIsNext(){
        String message = "";
        if(answerToUserSent){
            return null;
        }
        if(abortReceived.isValue()){
            message = "Die Buchung ist zu diesem Zeitpunkt leider nicht möglich";
            this.answerToUserSent = true;

        }else{
            for(BooleanWithTimestamp b : commitOKReceived){
                if(!b.isValue()){
                    return null;
                }
            }
            message = "Ihre Anfraga wurde erfolgreich gespeichert. Ihre Reservierungs-/Buchungs-ID lautet: " + this.getId();
            this.answerToUserSent = true;
        }
        byte[] data = message.getBytes();
        InetAddress address = userRequest.getAddress();
        int port = userRequest.getPort();
        return new DatagramPacket(data, data.length, address, port);
    }

    public LinkedList<DatagramPacket> whatServerMessagesAreNext(){
        LinkedList<DatagramPacket> response = new LinkedList<>();
        int index = -1;
        boolean stop = false;
        for(BooleanWithTimestamp b: preperationSent){
            index++;
            if(!b.isValue()){
                stop = true;
                String prepare = "";
                if(words.length == 3) {
                    prepare = "PREPARE" + " " + this.getId() + " " + words[1] + " " + words[2] + " " + words[0];
                }else if(words.length == 2){
                    prepare = "PREPARE " + this.getId() + " " + words[1] + " " + words[0];
                }
                byte[] data = prepare.getBytes();
                InetAddress address = null;
                try {
                    address = InetAddress.getByName("localhost");
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
                response.add(new DatagramPacket(data, data.length, address, servicePorts[index]));
                preperationSent(index);
            }
        }
        if(stop) return response;
        //all preperations have been sent
        if(abortReceived.isValue()){
            index = -1;
            for(BooleanWithTimestamp b: abortsSent){
                index++;
                if(!b.isValue()){
                    stop = true;
                    String abort = "ABORT " + this.getId();
                    byte[] data = abort.getBytes();
                    InetAddress address = null;
                    try {
                        address = InetAddress.getByName("localhost");
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }
                    response.add(new DatagramPacket(data, data.length, address, servicePorts[index]));
                    abortSent(index);
                }
            }
            if(stop) return response;//further aborts have to be sent out
            else{//all aborts habe been sent
                return checkAbortOksForTimeout();
            }
            //all aborts have been sent
        }else{
            boolean allReadyReceived = true;
            for(BooleanWithTimestamp b:readyReceived){
                if(allReadyReceived) {
                    allReadyReceived = b.isValue();
                }
            }
            if(!allReadyReceived){
                return checkPrepareForTimeout();
            }
            //all readys werde received
            index = -1;
            stop = false;
            for(BooleanWithTimestamp b: commitSent){
                index++;
                if(!b.isValue()){
                    stop = true;
                    String commit = "COMMIT " + this.getId();
                    byte[] data = commit.getBytes();
                    InetAddress address = null;
                    try {
                        address = InetAddress.getByName("localhost");
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }
                    response.add(new DatagramPacket(data, data.length, address, servicePorts[index]));
                    commitSent(index);
                }
            }
            if(stop) return response;//further commits are being sent out
            else{//all commits have been sent out
                return checkCommitForTimeout();
            }

        }
    }
    private LinkedList<DatagramPacket> checkCommitForTimeout(){
        LinkedList<DatagramPacket> messages = new LinkedList<>();
        for(int i = 0; i < commitSent.length; i++){
            if(commitSent[i].isValue() && !commitOKReceived[i].isValue()) {
                if (System.currentTimeMillis() - commitSent[i].getTime() > timeoutMillis) {
                    String commit = "COMMIT " + this.getId();
                    byte[] data = commit.getBytes();
                    InetAddress address = null;
                    try {
                        address = InetAddress.getByName("localhost");
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }
                    messages.add(new DatagramPacket(data, data.length, address, servicePorts[i]));
                }
            }
        }
        if(messages.size() != 0){
            return messages;
        }else{
            return null;
        }
    }

    private LinkedList<DatagramPacket> checkPrepareForTimeout(){
        if(prepareTimeoutCounter > 10){//wenn 10 mal keine antwort kam, dann wird aborted
            this.abortReceived.setValue(true);
            return null;
        }
        LinkedList<DatagramPacket> messages = new LinkedList<>();
        for(int i = 0; i < preperationSent.length; i++){
            if(!abortReceived.isValue() && preperationSent[i].isValue() && !readyReceived[i].isValue()) {
                if (System.currentTimeMillis() - preperationSent[i].getTime() > timeoutMillis) {
                    prepareTimeoutCounter ++;
                    String prepare = "";
                    if(words.length == 3) {
                        prepare = "PREPARE" + " " + this.getId() + " " + words[1] + " " + words[2] + " " + words[0];
                    }else if(words.length == 2){
                        prepare = "PREPARE " + this.getId() + " " + words[1] + " " + words[0];
                    }
                    byte[] data = prepare.getBytes();
                    InetAddress address = null;
                    try {
                        address = InetAddress.getByName("localhost");
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }
                    messages.add(new DatagramPacket(data, data.length, address, servicePorts[i]));
                }
            }
        }
        if(messages.size() != 0){
            return messages;
        }else{
            return null;
        }
    }
    private LinkedList<DatagramPacket> checkAbortOksForTimeout(){
        LinkedList<DatagramPacket> messages = new LinkedList<>();
        for(int i = 0; i < abortsSent.length; i++){
            if(abortsSent[i].isValue() && !abortOKReceived[i].isValue()) {
                if (System.currentTimeMillis() - abortsSent[i].getTime() > timeoutMillis) {
                    String abort = "ABORT " + this.getId();
                    byte[] data = abort.getBytes();
                    InetAddress address = null;
                    try {
                        address = InetAddress.getByName("localhost");
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }
                    messages.add(new DatagramPacket(data, data.length, address, servicePorts[i]));
                }
            }
        }
        if(messages.size() != 0){
            return messages;
        }else{
            return null;
        }
    }

    public void answerToUserWasSent(){
        answerToUserSent = true;
    }
    public boolean isAnswerToUserSent(){
        return answerToUserSent;
    }
    public int getId(){
        return id;
    }

    public BooleanWithTimestamp getAbortReceived() {
        return abortReceived;
    }

    public void abortWasReceived() {
        if(!this.abortReceived.isValue()){
            this.abortReceived.setValue(true);
        }
    }

    public void okReceivedFrom(int index){
        if(abortReceived.isValue() && abortsSent[index].isValue()){
            abortOKReceived[index].setValue(true);
        }else if(commitSent[index].isValue()){
            commitOKReceived(index);
        }
    }
    public static UserRequestObject createFromString(String s, int[] servicePorts){
        String[] words = s.trim().split(" ");
        int id = Integer.parseInt(words[0]);
        boolean answerToUserSent = UserRequestObject.tOrFToBoolean(words[1]);
        BooleanWithTimestamp[] preperationSent = new BooleanWithTimestamp[servicePorts.length];
        BooleanWithTimestamp[] readyReceived = new BooleanWithTimestamp[servicePorts.length];
        BooleanWithTimestamp abortReceived;
        BooleanWithTimestamp[] commitSent = new BooleanWithTimestamp[servicePorts.length];
        BooleanWithTimestamp[] commitOKReceived = new BooleanWithTimestamp[servicePorts.length];
        BooleanWithTimestamp[] abortsSent = new BooleanWithTimestamp[servicePorts.length];
        BooleanWithTimestamp[] abortOKReceived = new BooleanWithTimestamp[servicePorts.length];
        int i = 0;
        for(String bool: words[2].trim().split(",")){
            preperationSent[i] = BooleanWithTimestamp.createFromString(bool);
            i++;
        }
        i = 0;
        for(String bool: words[3].trim().split(",")){
            readyReceived[i] = BooleanWithTimestamp.createFromString(bool);
            i++;
        }
        i = 0;
        abortReceived = BooleanWithTimestamp.createFromString(words[4]);
        for(String bool: words[5].trim().split(",")){
            commitSent[i] = BooleanWithTimestamp.createFromString(bool);
            i++;
        }
        i = 0;
        for(String bool: words[6].trim().split(",")){
            commitOKReceived[i] = BooleanWithTimestamp.createFromString(bool);
            i++;
        }
        i = 0;
        for(String bool: words[7].trim().split(",")){
            abortsSent[i] = BooleanWithTimestamp.createFromString(bool);
            i++;
        }
        i = 0;
        for(String bool: words[8].trim().split(",")){
            abortOKReceived[i] = BooleanWithTimestamp.createFromString(bool);
            i++;
        }
        i = 0;
        InetAddress address = null;
        try {
            address = InetAddress.getByName(words[9]);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        int port = Integer.parseInt(words[10]);

        String[] newWords = new String[words.length-11];
        for(int index = 11; index < words.length; index++){
            newWords[index-11] = words[index];
        }
        String originalMessage = "";
        for(String word : newWords){
            originalMessage += word + " ";
        }
        originalMessage = originalMessage.trim();
        UserRequestObject o = new UserRequestObject(id, servicePorts.length, new DatagramPacket(originalMessage.getBytes(), 0, originalMessage.getBytes().length, address, port),servicePorts);
        o.answerToUserSent = answerToUserSent;
        o.preperationSent = preperationSent;
        o.readyReceived = readyReceived;
        o.abortReceived = abortReceived;
        o.commitSent = commitSent;
        o.commitOKReceived = commitOKReceived;
        o.abortsSent = abortsSent;
        o.abortReceived = abortReceived;
        o.abortOKReceived = abortOKReceived;
        return o;

    }

    public UserRequestObject(int id, int numberOfServices, DatagramPacket originPacket, int[] servicePorts) {
        this.servicePorts = servicePorts;
        this.id = id;
        this.answerToUserSent = false;
        this.preperationSent = new BooleanWithTimestamp[numberOfServices];
        this.readyReceived = new BooleanWithTimestamp[numberOfServices];
        this.commitSent = new BooleanWithTimestamp[numberOfServices];
        this.commitOKReceived = new BooleanWithTimestamp[numberOfServices];
        this.abortsSent = new BooleanWithTimestamp[numberOfServices];
        this.abortOKReceived = new BooleanWithTimestamp[numberOfServices];
        try {
            this.userRequest = new DatagramPacket(originPacket.getData(), originPacket.getOffset(), originPacket.getLength(), InetAddress.getByAddress(originPacket.getAddress().getHostName(), originPacket.getAddress().getAddress()), originPacket.getPort());
        }catch (UnknownHostException e){e.printStackTrace();}
        words = new String(this.userRequest.getData()).toString().trim().split(" ");
        for(int i = 0; i < words.length; i++){
            words[i] = words[i].trim();
        }
        for(int i = 0; i < numberOfServices; i++){
            preperationSent[i] = new BooleanWithTimestamp(false);
            readyReceived[i] = new BooleanWithTimestamp(false);
            commitSent[i] = new BooleanWithTimestamp(false);
            commitOKReceived[i] = new BooleanWithTimestamp(false);
            abortsSent[i] = new BooleanWithTimestamp(false);
            abortOKReceived[i] = new BooleanWithTimestamp(false);
        }
        abortReceived = new BooleanWithTimestamp(false);
    }



    public void preperationSent(int index){
        this.preperationSent[index] = new BooleanWithTimestamp(true);
    }
    public void readyReceived(int index){
        this.readyReceived[index] = new BooleanWithTimestamp(true);
    }
    public void commitSent(int index){
        this.commitSent[index] = new BooleanWithTimestamp(true);
    }
    public void commitOKReceived(int index){
        this.commitOKReceived[index] = new BooleanWithTimestamp(true);
    }
    public void abortSent(int index){
        this.abortsSent[index] = new BooleanWithTimestamp(true);
    }
    public void abortOKReceived(int index){
        this.abortOKReceived[index] = new BooleanWithTimestamp(true);
    }

    public boolean isCommitted(){
        for(BooleanWithTimestamp b:commitOKReceived){
            if(!b.isValue()){
                return false;
            }
        }
        return true;
    }
}
