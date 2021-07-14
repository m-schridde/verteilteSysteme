import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Date;
import java.util.LinkedList;

public class BrokerServer implements Runnable{

    protected DatagramSocket datagramSocket;//serversocket
    private boolean gotSocket;
    private int port;//der eigene port
    private int serverId;//die eigene id
    private int serverCount;//die anzahl alles broker server
    private DatagramPacket packet;//Datagrampacket, welches aktuell in bearbeitung ist
    private String response;//nachicht, welche als nächstes gesendet wird
    private int[] portsOfServices;//ports der mietwagen/hotelserver
    private int nextId;//die id der nächsten userrequest
    protected LinkedList<UserRequestObject> userRequestObjects;//liste aller von diesem server bearbeiteten userrequests als userRequestobjects
    File file;//Filehandle für das backup .txt file
    BrokerServerTimeoutChecker timeoutChecker;//diese klasse läuft in einem unabhängigen thread und prüft, ob nachichten zu lange ohne antwort blieben und senden diese dann erneut


    private LinkedList<String> toStrings(){//parsed diese Klasse in eine menge aus Strings
        LinkedList<String> strings = new LinkedList<>();
        String s = this.port + " " + this.serverId + " " + this.serverCount;
        for(int i : portsOfServices){
            s += " " + i;
        }
        strings.add(s);
        strings.add(nextId + "");
        userRequestObjects.stream().forEach(userRequestObject -> strings.add(userRequestObject.toString()));
        return strings;
    }

    private String composeFileName(){//stellt den namen des Backupfiles zusammen. so wird vermieden, dass ungleiche server auf das gleiche .txt file zugreifen
        String s = "brokerServer_" + this.port + "_" + this.serverId  + "_" + serverCount;
        for(int i : portsOfServices){
            s += "_" + i;
        }
        s += ".txt";
        return s;
    }

    private String composeFilePath(){//stellt den path zum Backupfile zusammen
        return System.getProperty("user.dir") + File.separator + composeFileName();
    }

    private int getNextId(){//gibt die id für die nächste userrequest zurück und errechnet unter berücksichtigung der gesamtanzahl an brokerservern die nächste id (keine 2 brokerserver erzeigen userrequests mit der selben id)
        int response = nextId;
        nextId = nextId + serverCount;
        return response;
    }

    private boolean createFileOrLoadFile() throws IOException {//Backupfile wird erstellt, falls es noch nicht existiert, und wird immer in file gespeichert
        file = new File(composeFilePath());
        boolean result;
        result = file.createNewFile();
        return result; // true, if file was created, false, if file already existed
    }



    public BrokerServer(int serverId, int serverCount, int port, int[] portsOfServices){//wir benötigen die eigene id, die anzahl der broker server, den eigenen port und die ports von hotel/mietwagenserver
        this.serverCount = serverCount;
        this.serverId = serverId;
        this.nextId = serverId;
        this.port = port;
        this.portsOfServices = portsOfServices;
        this.userRequestObjects = new LinkedList<>();
        int tryCounter = 0;
        while(tryCounter < 10 && !gotSocket){//wir versuchen den socket zu bekommen
            tryCounter++;
            try{
                datagramSocket = new DatagramSocket(port);
                gotSocket = true;
            }catch(IOException ioE){ioE.printStackTrace();}
        }
        boolean wasFileNewlyCreated = false;
        try {
            wasFileNewlyCreated = createFileOrLoadFile();//wir prüfen, ob ein backupfile für unseren server existiert
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(wasFileNewlyCreated) {//falls es noch kein backupfile gab, schreiben wir this als text in das file
            writeThisToFile();
        }else{
            rebuildFromFile(); //falls ein Backupfile existierte, bringen wir this auf den stand des backupfiles
        }
        this.timeoutChecker = new BrokerServerTimeoutChecker(this);//wir starten den timeoutchecker in einem thread, damit timeouts auf diesem server erkannt werden können
        Thread t1 = new Thread(timeoutChecker);
        t1.start();
    }

    private void recoverFirstLine(String line){//die daten aus der ersten zeile des Backupfiles werden gelesen und in this gespeichert
        String[] words = line.trim().split(" ");
        this.port = Integer.parseInt(words[0]);
        this.serverId = Integer.parseInt(words[1]);
        this.serverCount = Integer.parseInt(words[2]);
        this.portsOfServices = new int[words.length - 3];
        for(int i = 3; i < words.length; i++){
            this.portsOfServices[i-3] = Integer.parseInt(words[i]);
        }
    }

    private void recoverSecondLine(String line){//die daten aus der zweiten zeile des Backupfiles werden gelesen und in this gespeichert
        this.nextId = Integer.parseInt(line.trim());
    }

    private void rebuildFromFile() {//wir bringen this auf den stand des backupfiles
        try {
            BufferedReader reader = new BufferedReader(new FileReader(composeFilePath()));
            int index = 0;
            String line;
            line = reader.readLine();
            while(line != null && line.length() > 0){
                if(index == 0){
                    this.recoverFirstLine(line);
                }
                else if(index == 1){
                    this.recoverSecondLine(line);
                }else{
                    userRequestObjects.add(UserRequestObject.createFromString(line, portsOfServices));//wir rekonstruieren die liste aller userrequests aus dem backupfile
                }
                index++;
                line = reader.readLine();
            }
            reader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeThisToFile() {//wir überschreiben das backupfile mit dem aktuellen stand von this
        try {
            PrintWriter writer = new PrintWriter(new FileWriter(composeFilePath()));
            LinkedList<String> strings = toStrings();
            strings.forEach(s->{writer.println(s);});
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Date stringToDate(String date)throws Exception{//wir wandeln yyyy-mm-dd strings in Date um und prüfen, ob das datum valide ist und in der Zukunft liegt
        String[] split = date.split("-");
        int year = Integer.parseInt(split[0])-1900;
        int month = Integer.parseInt(split[1].trim()) - 1;
        int day = Integer.parseInt(split[2].trim());
        if(year+1900 < 2020 || month > 11 || month < 0){
            throw new Exception();
        }
        Date result = new Date(year, month, day);
        if(result.getTime() < System.currentTimeMillis()){
            throw new Exception();
        }
        return result;
    }

    private boolean isValid(String message){//wir prüfen, ob eine nachicht eines nutzers korrekt sein kann
        message = message.trim();
        String[] words = message.split(" ");
        if(words.length == 3){//reserve oder book mit start und enddatum
            if (!(words[0].equals("RESERVE") || words[0].equals("BOOK"))){//beginnt es mit reserve oder book
                return false;
            }
            try{
                Date startDate = stringToDate(words[1]);//sind beide daten valide
                Date endDate = stringToDate(words[2]);
                if(endDate.before(startDate)){//ist das enddatum nach dem startdatum
                    return false;
                }
            }catch (Exception e){
                return false;
            }
            return true;
        }else if(words.length == 2){//book mit reservierungs id
            if (!words[0].equals("BOOK")){
                return false;
            }
            try{
                int reservationId = Integer.parseInt(words[1]);//ist die reservierungsid eine zahl (nicht abgleichen mit userrequest objects, da die reservierung vom anderen server durchgeführt worden sein könnte
                return true;
            }catch(Exception e){
                return false;
            }
        }else{
            return false;
        }
    }
    private boolean isUserMessage(DatagramPacket packet){//kommt die nachicht von einem port der hotel/mietwagen server, dann ist es keine usermessage
        for(int port : portsOfServices){
            if(packet.getPort() == port){
                return false;
            }
        }
        return true;
    }
    private int getIndexFromPort(int port){//gibt den index des serviceports an (z.B. mietwageport->0, hotelport->1)
        int index = -1;
        for(int i : portsOfServices){
            index++;
            if(i == port){
                break;
            }
        }
        return index;
    }

    public void run(){//diese methode wird in einer endlosschleife ausgeführt
        while(gotSocket){
            try{
                response = "";
                packet = new DatagramPacket(new byte[1024], 1024);
                datagramSocket.receive(packet);//empfangen eines pakets
                String message = new String(packet.getData());
                message = message.trim();
                System.out.println(this.port + " received " + message + " from " + packet.getPort());//ausgabe, welche nachicht erhalten wurde
                String[] words = message.split(" ");
                if(words[0].equals("PING")){//pingfunktion wurde überlegt, aber letzenendes verworfen. jetzt kann unser broker angepingt werden und antwortet mit PONG
                    datagramSocket.send(new DatagramPacket("PONG".getBytes(), "PONG".getBytes().length, packet.getAddress(), packet.getPort()));
                    System.out.println(this.port + " sent PONG");
                }else if(isUserMessage(packet)) {//hier behandeln wir packets, die nicht von hotel/mietwagenservern kommen
                    if (isValid(message)) {//auf validität prüfen
                        UserRequestObject userRequestObject = new UserRequestObject(this.getNextId(), this.portsOfServices.length, packet, portsOfServices);//aus einer validen userrequest wird ein userRequestObject erstellt
                        LinkedList<DatagramPacket> nextSteps = userRequestObject.whatServerMessagesAreNext();//sämtliche logik übernimmt das userRequestObject. es gibt uns fertig formulierte und adressierte datagrampackets zurück
                        for(DatagramPacket d:nextSteps){
                            System.out.println(this.port + " sent: " + new String(d.getData()) + " to Port " + d.getPort());
                            datagramSocket.send(d);//wir senden alle datagrampackets (hier an die server)
                            userRequestObject.preperationSent(getIndexFromPort(d.getPort()));//wir melden dem userrequestobject, welche prepares gesendet wurden
                        }
                        userRequestObjects.add(userRequestObject);//wir speichern das userrequestobject
                        writeThisToFile();//wir aktualisieren das Backup
                    } else {//bei nicht validen nachichten, dem user entsprechend antworten
                        response = "Invalid Command, please review the format and contents of what you typed. Dates must be in the right order and must lie in the Future";
                        byte[] data = response.getBytes();
                        InetAddress address = packet.getAddress();
                        int port = packet.getPort();
                        DatagramPacket preparePacket = new DatagramPacket(data, data.length, address, port);
                        datagramSocket.send(preparePacket);
                    }
                }else {//hier behandeln wir packets, die von hotel/mietwagenservern kommen
                    //server message
                    int id = Integer.parseInt(words[1]);
                    int index = -1;
                    //raussuchen des richtigen userrequestObjects
                    for(UserRequestObject object: userRequestObjects){
                        index++;
                        if(id == object.getId()){
                            break;
                        }
                    }
                    UserRequestObject userRequestObject = userRequestObjects.remove(index);
                    index = -1;
                    for(int servicePort: portsOfServices){
                        index++;
                        if(packet.getPort() == servicePort){
                            break;
                        }
                    }

                    switch (words[0]){
                        case "READY":
                            userRequestObject.readyReceived(index);//wir melden dem userRequestObject von welchem mietwagen/hotelserver wir welche nachicht des 2PC erhalten haben
                            break;
                        case "ABORT":
                            userRequestObject.abortWasReceived();
                            break;
                        case "OK":
                            userRequestObject.okReceivedFrom(index);
                            break;
                    }

                    LinkedList<DatagramPacket> reactions = userRequestObject.whatServerMessagesAreNext();//wir fragen das userrequestobject, welche nachichten wir u.U. an server senden müssen
                    if(reactions != null){
                        reactions.forEach(d-> {
                            try {
                                System.out.println(this.port + "sent: " + new String(d.getData()) + " to Port " + d.getPort());
                                datagramSocket.send(d);//wir senden alle nachichten raus
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });;
                    }
                    DatagramPacket userResponse = userRequestObject.whatUserMessageIsNext();//nach einer erhaltenen nachicht von hotel/mietwagenserver kann es sein, dass wir den User benachichtigen müssen. die nachicht stellt das Userrequest object ggf. zusammen
                    if(userResponse != null){
                        try {
                            System.out.println(this.port + "sent: " + new String(userResponse.getData()) + " to Port " + userResponse.getPort());
                            datagramSocket.send(userResponse);//antworten an den User
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    userRequestObjects.add(userRequestObject);//speichern des userRequestObjects
                    writeThisToFile();//aktualisieren des Backups
                }
            }catch(IOException ioE){ioE.printStackTrace();}
        }
    }
    public static void main(String[] args) {//nur zu testzwecken, wird von Main übernommen
        int[] portsOfServices = {6998,6999};
        BrokerServer server0 = new BrokerServer(0,2,6111, portsOfServices);
        BrokerServer server1 = new BrokerServer(1,2,6112, portsOfServices);
        Thread t0 = new Thread(server0);
        Thread t1 = new Thread(server1);
        t0.start();
        t1.start();
    }
}
