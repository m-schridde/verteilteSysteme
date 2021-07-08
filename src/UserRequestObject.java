import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;

public class UserRequestObject {
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

    private String booleanToTOrF(boolean b){
        return b?"t":"f";
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

        for(String w : words){
            me += w + " ";
        }
        return me.trim();
    }

    public DatagramPacket whatUserMessageIsNext(){
        String message = "";
        if(abortReceived.isValue()){
            message = "Die Buchung ist zu diesem Zeitpunkt leider nicht möglich";

        }else{
            for(BooleanWithTimestamp b : commitOKReceived){
                if(!b.isValue()){
                    return null;
                }
            }
            message = "Ihre Anfraga wurde erfolgreich gespeichert. Ihre Reservierungs-/Buchungs-ID lautet: " + this.getId();
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
                System.out.println("Words in User RequestObject before builing prepare message: ");
                for (int i = 0; i < words.length; i++) {
                    System.out.println(i + ": " + words[i]);
                }
                if(words.length == 3) {
                    prepare = "PREPARE" + " " + this.getId() + " " + words[1] + " " + words[2] + " " + words[0];
                }else if(words.length == 2){
                    prepare = "PREPARE " + this.getId() + " " + words[1] + " " + words[0];
                }
                System.out.println("Prepare message is: " + prepare);
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
                return null; //TODO check for timeouts on abortOks, maybe resend abort
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
                //TODO check for timeout, maybe resend prepare
                return null;
            }
            //all readys werde received
            index = -1;
            stop = false;
            for(BooleanWithTimestamp b: commitSent){
                index++;
                if(!b.isValue()){
                    stop = true;
                    String abort = "COMMIT " + this.getId();
                    byte[] data = abort.getBytes();
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
                return null; //TODO check for timeout on Commits sent, maybe resend commit
            }

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
        }catch (UnknownHostException e){e.printStackTrace();};
        System.out.println(originPacket.getData().toString());
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
