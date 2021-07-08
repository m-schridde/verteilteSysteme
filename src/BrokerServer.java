import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Date;
import java.util.LinkedList;

public class BrokerServer implements Runnable{

    private DatagramSocket datagramSocket;
    private boolean gotSocket;
    private int port;
    private int serverId;
    private int serverCount;
    private DatagramPacket packet;
    private String response;
    private int[] portsOfServices;
    private int nextId;
    private LinkedList<UserRequestObject> userRequestObjects;
    File file;


    private LinkedList<String> toStrings(){
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

    private String composeFileName(){
        String s = "brokerServer_" + this.port + "_" + this.serverId  + "_" + serverCount;
        for(int i : portsOfServices){
            s += "_" + i;
        }
        s += ".txt";
        return s;
    }

    private String composeFilePath(){
        return System.getProperty("user.dir") + System.lineSeparator() + composeFileName();
    }

    private int getNextId(){
        int response = nextId;
        nextId = nextId + serverCount;
        return response;
    }

    private boolean createFileOrLoadFile() throws IOException {
        file = new File(composeFilePath());
        boolean result;
        result = file.createNewFile();
        return result; // true, if file was created, false, if file existed
    }



    public BrokerServer(int serverId, int serverCount, int port, int[] portsOfServices){
        this.serverCount = serverCount;
        this.serverId = serverId;
        this.nextId = serverId;
        this.port = port;
        this.portsOfServices = portsOfServices;
        this.userRequestObjects = new LinkedList<>();
        int tryCounter = 0;
        while(tryCounter < 10 && !gotSocket){
            tryCounter++;
            try{
                datagramSocket = new DatagramSocket(port);
                gotSocket = true;
            }catch(IOException ioE){ioE.printStackTrace();}
        }
        boolean wasFileNewlyCreated = false;
        try {
            wasFileNewlyCreated = createFileOrLoadFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(wasFileNewlyCreated) {
            writeThisToFile();
        }else{
            rebuildFromFile();
        }
    }

    private void rebuildFromFile() {
        //TODO implement
    }

    private void writeThisToFile() {
        //TODO implement
    }

    private static Date stringToDate(String date)throws Exception{
        String[] split = date.split("-");
        int year = Integer.parseInt(split[0])-1900;
        int month = Integer.parseInt(split[1].trim()) - 1;
        int day = Integer.parseInt(split[2].trim());
        if(year+1900 < 2020 || month > 11 || month < 0){
            throw new Exception();
        }
        Date result = new Date(year, month, day);
        return result;
    }

    private boolean isValid(String message){
        message = message.trim();
        String[] words = message.split(" ");
        if(words.length == 3){
            if (!(words[0].equals("RESERVE") || words[0].equals("BOOK"))){
                return false;
            }
            try{
                Date startDate = stringToDate(words[1]);
                Date endDate = stringToDate(words[2]);
                if(endDate.before(startDate)){
                    return false;
                }
            }catch (Exception e){
                return false;
            }
            return true;
        }else if(words.length == 2){
            if (!(words[0].equals("RESERVE") || words[0].equals("BOOK"))){
                return false;
            }
            try{
                int reservationId = Integer.parseInt(words[1]);
                boolean result = false;
                for(int i = 0; i < userRequestObjects.size(); i++){
                    result = (userRequestObjects.get(i).getId() == reservationId);
                }
                return result;
            }catch(Exception e){
                return false;
            }
        }else{
            return false;
        }
    }
    private boolean isUserMessage(DatagramPacket packet){
        for(int port : portsOfServices){
            if(packet.getPort() == port){
                return false;
            }
        }
        return true;
    }
    private int getIndexFromPort(int port){
        int index = -1;
        for(int i : portsOfServices){
            index++;
            if(i == port){
                break;
            }
        }
        return index;
    }

    public void run(){
        while(gotSocket){
            try{
                response = "";
                packet = new DatagramPacket(new byte[1024], 1024);
                datagramSocket.receive(packet);
                String message = new String(packet.getData());
                message = message.trim();
                System.out.println(message);
                String[] words = message.split(" ");
                if(isUserMessage(packet)) {
                    if (isValid(message)) {
                        UserRequestObject userRequestObject = new UserRequestObject(this.getNextId(), this.portsOfServices.length, packet, portsOfServices);
                        LinkedList<DatagramPacket> nextSteps = userRequestObject.whatServerMessagesAreNext();
                        for(DatagramPacket d:nextSteps){
                            System.out.println("Message form broker to server: " + new String(d.getData()));
                            datagramSocket.send(d);
                            userRequestObject.preperationSent(getIndexFromPort(d.getPort()));
                        }
                        userRequestObjects.add(userRequestObject);
                    } else {
                        response = "Invalid Command, please review the format and contents of what you typed";
                        byte[] data = response.getBytes();
                        InetAddress address = packet.getAddress();
                        int port = packet.getPort();
                        DatagramPacket preparePacket = new DatagramPacket(data, data.length, address, port);
                        datagramSocket.send(preparePacket);
                    }
                }else {
                    //server message
                    System.out.println("Received server Message: " + message);

                    int id = Integer.parseInt(words[1]);
                    int index = -1;
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
                            userRequestObject.readyReceived(index);
                            break;
                        case "ABORT":
                            userRequestObject.abortWasReceived();
                            break;
                        case "OK":
                            userRequestObject.okReceivedFrom(index);
                            break;
                    }

                    LinkedList<DatagramPacket> reactions = userRequestObject.whatServerMessagesAreNext();
                    if(reactions != null){
                        reactions.forEach(d-> {
                            try {
                                datagramSocket.send(d);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });;
                    }
                    DatagramPacket userResponse = userRequestObject.whatUserMessageIsNext();
                    if(userResponse != null){
                        try {
                            datagramSocket.send(userResponse);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    userRequestObjects.add(userRequestObject);
                }
            }catch(IOException ioE){ioE.printStackTrace();}
        }
    }
    public static void main(String[] args) {
        int[] portsOfServices = {6998,6999};
        BrokerServer server0 = new BrokerServer(0,2,6111, portsOfServices);
        BrokerServer server1 = new BrokerServer(1,2,6112, portsOfServices);
        Thread t0 = new Thread(server0);
        Thread t1 = new Thread(server1);
        t0.start();
        t1.start();
    }
}