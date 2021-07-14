import javax.xml.crypto.Data;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;

public class UserRequestObject {
    private int prepareTimeoutCounter = 0;//zählen, wie oft wir nachichten erneut gesendet haben
    private static final int timeoutMillis = 1000;//wie lange warten wir mindestens auf eine antwort, bis wir eine nachicht erneut senden
    private int id;
    private boolean answerToUserSent;//wurde der user bereits benachichtigt?
    private int[] servicePorts;//ports von miet/hotel server
    //hier speichern wir informationen über den stand im 2PC. Jedes der folgenden arrays hat die länge von servicePorts. wenn wir vom serviceport[0] ein READY erhalten, wird readyReceived[0] auf true gesetzt
    private BooleanWithTimestamp[] preperationSent;//welche preperations haben wir rausgesendet
    private BooleanWithTimestamp[] readyReceived;//welche readys haben wir empfangen
    private BooleanWithTimestamp abortReceived;//wurde ein abort erhalten (egal von wem, eins reicht, um alles aborten zu müssen
    private BooleanWithTimestamp[] commitSent;//an wen haben wir commits rausgesant
    private BooleanWithTimestamp[] commitOKReceived;//wer hat auf commit mit OK geantwortet
    private BooleanWithTimestamp[] abortsSent;//an wen haben wir aborts versant
    private BooleanWithTimestamp[] abortOKReceived;//wer hat auf abort mit ok geantwortet

    DatagramPacket userRequest;//das packet der originalen useranfrage
    String[] words;//der inhalt der userrequest

    private static String booleanToTOrF(boolean b){//selbsterklärend
        return b?"t":"f";
    }
    public static boolean tOrFToBoolean(String s){//selbsterklärend
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

    public String toString(){//überführt this in einen String, welcher in das Backupfile geschrieben werden kann
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

    public DatagramPacket whatUserMessageIsNext(){//hier wird die nutzerantwort gebaut
        String message = "";
        if(answerToUserSent){//niemals doppelt dem nutzer antworten (falls die timeouts unglücklich auslösen, wird diese methode 2 mal aufgerufen, wir wollen trz. nur einmal antworten
            return null;
        }
        if(abortReceived.isValue()){//sobald ein abort empfangen wurde, können wir den misserfolg mitteilen
            message = "Die Buchung ist zu diesem Zeitpunkt leider nicht möglich";
            this.answerToUserSent = true;

        }else{
            for(BooleanWithTimestamp b : commitOKReceived){
                if(!b.isValue()){
                    return null;
                }
            }//alle server (hotel/mietwagen) haben commitet. Der erfolg wird dem nutzer mitgeteilt
            message = "Ihre Anfrage wurde erfolgreich gespeichert. Ihre Reservierungs-/Buchungs-ID lautet: " + this.getId();
            this.answerToUserSent = true;
        }
        //bauen des datagrampackets
        byte[] data = message.getBytes();
        InetAddress address = userRequest.getAddress();
        int port = userRequest.getPort();
        return new DatagramPacket(data, data.length, address, port);
    }

    public LinkedList<DatagramPacket> whatServerMessagesAreNext(){//hier findet die logik des 2PC statt
        LinkedList<DatagramPacket> response = new LinkedList<>();//rückgabe ist eine liste fertig adressierter und verfasster datagrampackets
        int index = -1;
        boolean stop = false;
        for(BooleanWithTimestamp b: preperationSent){//prüfen, ob alle preperations gesendet wurden
            index++;
            if(!b.isValue()){//falls nein:
                stop = true;//wir müssen nicht nach readys/aborts schauen, wenn noch nicht alle prepares gesendet wurden
                String prepare = "";
                if(words.length == 3) {//verfassen der prepare nachicht (mit 2 daten oder einer reservierungsnummer)
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
                response.add(new DatagramPacket(data, data.length, address, servicePorts[index]));//erstellen des datagrampackets und hinzufügen zur antwort
                preperationSent(index);//die preperations werden jetzt abgeschickt, das wird hinterlegt
            }
        }
        if(stop) return response;//es mussten noch pereperations gesendet werden. die müssen raus gehen, bevor wir auf Ready/abort schauen
        //wenn man hier ist, wurden bereits alle preperations gesendet
        if(abortReceived.isValue()){//haben wir einen abort erhalten
            //wir haben einen abort erhalten
            index = -1;
            for(BooleanWithTimestamp b: abortsSent){//wir prüfen, ob wir alle aborts gesendet haben
                index++;
                if(!b.isValue()){
                    stop = true;//falls wir nicht alle aborts gesendet haben, müssen wir noch aborts raussenden, bevor wir prüfen, ob wir die abortOks erhalten haben
                    String abort = "ABORT " + this.getId();
                    byte[] data = abort.getBytes();
                    InetAddress address = null;
                    try {
                        address = InetAddress.getByName("localhost");
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }
                    response.add(new DatagramPacket(data, data.length, address, servicePorts[index]));//verfassen uns zurückgeben der abort datagrampackets
                    abortSent(index);//die aborts werden jetzt abgeschickt, das wird hinterlegt
                }
            }
            if(stop) return response;//es mussten noch weitere aborts gesendet werden, wir brauchen nicht nach abortOks zu gucken
            else{//alle aborts wurden gesendet, wir prüfen nurnoch, ob abortOks zu lange brauchen
                return checkAbortOksForTimeout();
            }
        }else{//wir haben keinen abort erhalten
            boolean allReadyReceived = true;
            for(BooleanWithTimestamp b:readyReceived){
                if(allReadyReceived) {
                    allReadyReceived = b.isValue();//sobald einmal false kommt, ändert sich allReadyReceived nicht mehr
                }
            }
            if(!allReadyReceived){
                return checkPrepareForTimeout();//falls noch nicht alle mit ready geantwortet haben (und kein abort kam siehe oben) können wir auf timeouts prügen
            }
            //wenn alle readys erhalten wurden, schauen wir nach den commits
            index = -1;
            stop = false;
            for(BooleanWithTimestamp b: commitSent){
                index++;
                if(!b.isValue()){//wurden alle commits gesendet? falls nein, dann verfassen wir die entsprechenden Datagrampackets
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
            if(stop) return response;//es werden noch commits rausgesant
            else{//alle commits wurden verschickt, wir prüfen nach timeouts, falls commitOks zu lange brauchen und senden ggf. den commit erneut
                return checkCommitForTimeout();
            }

        }
    }
    private LinkedList<DatagramPacket> checkCommitForTimeout(){
        //dies wird endlos probiert, weil bereits andere commits rausgesendet wurden.
        LinkedList<DatagramPacket> messages = new LinkedList<>();
        for(int i = 0; i < commitSent.length; i++){
            if(commitSent[i].isValue() && !commitOKReceived[i].isValue()) {//wenn ein commit gesendet wurde, aber noch kein ok zurück kam
                if (System.currentTimeMillis() - commitSent[i].getTime() > timeoutMillis) {//dann wird die zeitliche differenz geprüft
                    String commit = "COMMIT " + this.getId();//ist der zeitabstand zu groß, werden entsprechenden nachichten erneut zusammengestellt
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
        //wie bei checkCommitForTimeout() nur, dass wenn 10 mal keine antwort kam, wird ein abort verzeichnet.
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
        //wie bei checkCommitForTimeout()
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
    public static UserRequestObject createFromString(String s, int[] servicePorts){ //this wird aus einem String rekonstruiert
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

    public UserRequestObject(int id, int numberOfServices, DatagramPacket originPacket, int[] servicePorts) { //wir brauchen eine id, die anzahl der miet/hotelserver, das datagrampacket der userrequest und die ports von hotel/mietwagen server
        this.servicePorts = servicePorts;
        this.id = id;
        this.answerToUserSent = false;
        this.preperationSent = new BooleanWithTimestamp[numberOfServices];
        this.readyReceived = new BooleanWithTimestamp[numberOfServices];
        this.commitSent = new BooleanWithTimestamp[numberOfServices];
        this.commitOKReceived = new BooleanWithTimestamp[numberOfServices];
        this.abortsSent = new BooleanWithTimestamp[numberOfServices];
        this.abortOKReceived = new BooleanWithTimestamp[numberOfServices];
        try {//deepcopy der userrequest, damit der brokerserver sein packet überschreiben kann, aber das packet hier bestehen bleibt
            this.userRequest = new DatagramPacket(originPacket.getData(), originPacket.getOffset(), originPacket.getLength(), InetAddress.getByAddress(originPacket.getAddress().getHostName(), originPacket.getAddress().getAddress()), originPacket.getPort());
        }catch (UnknownHostException e){e.printStackTrace();}
        words = new String(this.userRequest.getData()).trim().split(" ");//extrahieren der wörter aus der userrequest
        for(int i = 0; i < words.length; i++){
            words[i] = words[i].trim();
        }
        //initialisieren aller werte
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
