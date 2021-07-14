import java.awt.print.Book;
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;

public class BookableServer implements Runnable{

    protected LinkedList<BookingObject> booked;//hier stehen alle gebuchten nutzeranfragen drin
    protected LinkedList<BookingObject> reserved;//hier alle reservierten
    protected LinkedList<BookingObject> toBeDeleted;//hier alle, auf die wir mit abort geantwortet haben
    protected DatagramSocket datagramSocket;
    private boolean gotSocket;
    private DatagramPacket packet;//das sich aktuell in bearbeitung befindliche datagrampacket
    private int numberOfSimultaneouslyBookableEntitys;//wie viele mietwagen/hotels können am gleichen tag gebucht werden
    File file;//filehandle für das backupfile
    int port;//unser port
    BookableServerTimeoutChecker timeoutChecker;//prüft, ob wir auf antworten warten, die zu lange brauchen

    private static Date stringToDate(String date){//wie bei brokerServer
        String[] split = date.split("-");
        Date result = new Date(Integer.parseInt(split[0])-1900, Integer.parseInt(split[1].trim()) - 1, Integer.parseInt(split[2].trim()));
        return result;
    }
    public String composeFileName(){//wie bei brokerServer
        return "bookableServer_" + this.port + "_" + numberOfSimultaneouslyBookableEntitys + ".txt";
    }
    private String composeFilePath(){//wie bei brokerServer
        return System.getProperty("user.dir") + File.separator + composeFileName();
    }

    private boolean createFileOrLoadFile() throws IOException {//wie bei brokerServer
        file = new File(composeFilePath());
        boolean result;
        result = file.createNewFile();
        return result; // true, if file was created, false, if file existed
    }

    private LinkedList<String> toStrings(){//konvertiert this in eine menge an strings zum speichern in der Backupfile
        LinkedList<String> strings = new LinkedList<>();
        String s = this.port + " " + this.numberOfSimultaneouslyBookableEntitys;
        strings.add(s);
        strings.add("BOOKED");
        booked.stream().forEach(bookingObject -> strings.add(bookingObject.toString()));
        strings.add("RESERVED");
        reserved.stream().forEach(bookingObject -> strings.add(bookingObject.toString()));
        strings.add("TBD");
        toBeDeleted .stream().forEach(bookingObject -> strings.add(bookingObject.toString()));
        return strings;
    }

    public BookableServer(int port, int numberOfSimultaneouslyBookableEntitys){//benötig wird der port und die anzahl gleichzeitig buchbarer entitäten
        this.booked = new LinkedList<BookingObject>();
        this.reserved = new LinkedList<BookingObject>();
        this.toBeDeleted = new LinkedList<BookingObject>();
        this.numberOfSimultaneouslyBookableEntitys = numberOfSimultaneouslyBookableEntitys;
        this.port = port;
        int tryCounter = 0;
        gotSocket = false;
        while(tryCounter < 10 && !gotSocket){
            tryCounter++;
            try{
                datagramSocket = new DatagramSocket(port);
                gotSocket = true;
            }catch(IOException ioE){ioE.printStackTrace();}
        }
        boolean wasFileNewlyCreated = false;
        try {
            wasFileNewlyCreated = createFileOrLoadFile();//gibt es ein backup?
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(wasFileNewlyCreated) {
            writeThisToFile();//falls es kein backup gab, erstellen wir eins
        }else{
            rebuildFromFile();// falls es ein backup gab, rekonstruieren wir den stand vom Backup in this
        }
        timeoutChecker = new BookableServerTimeoutChecker(this);//starten des timeoutcheckers in einem eigenen thread
        Thread t1 = new Thread(timeoutChecker);
        t1.start();
    }

    private void writeThisToFile() {//überschreiben des backupfiles
        try {
            PrintWriter writer = new PrintWriter(new FileWriter(composeFilePath()));
            LinkedList<String> strings = toStrings();
            strings.forEach(s->{writer.println(s);});
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void recoverFirstLine(String line){//wiederherstellen der ersten zeile des backupfiles
        String[] words = line.trim().split(" ");
        int tryCounter = 0;
        int port = Integer.parseInt(words[0]);
        gotSocket = false;
        while(tryCounter < 10 && !gotSocket){
            tryCounter++;
            try{
                this.datagramSocket.close();
                this.datagramSocket = new DatagramSocket(port);
                gotSocket = true;
            }catch(IOException ioE){ioE.printStackTrace();}
        }
        this.numberOfSimultaneouslyBookableEntitys = Integer.parseInt(words[1]);
    }

    private void rebuildFromFile() {//wiederherstellen des standes des backupfiles
        try {
            BufferedReader reader = new BufferedReader(new FileReader(composeFilePath()));
            int lineGroup = 0;
            String line;
            line = reader.readLine();
            while(line != null && line.length() > 0){
                if(lineGroup == 0){
                    this.recoverFirstLine(line);
                    lineGroup++;
                }
                else if(lineGroup == 1){
                    if(line.contains("RESERVED")){
                        lineGroup++;
                    }else if(line.contains("BOOKED")){
                    }else{
                        booked.add(BookingObject.createFromString(line));
                    }
                }else if(lineGroup == 2){
                    if(line.trim().equals("TBD")){
                        lineGroup++;
                    }else{
                        reserved.add(BookingObject.createFromString(line));
                    }
                }else if(lineGroup == 3){
                    toBeDeleted.add(BookingObject.createFromString(line));
                }
                line = reader.readLine();
            }
            reader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isFree(BookingObject newBooking){//prüfen, ob kapazität für eine buchungs/reservierungsangrage besteht
        int conflictCounter = 0;
        LinkedList<Integer> reservationIdsAlreadyBooked = new LinkedList<Integer>();
        for(BookingObject booked: booked){
            if(booked.isApproved() || booked.isCommitted()) {
                reservationIdsAlreadyBooked.add(booked.getId());
                if (!(booked.getStartDate().after(newBooking.getEndDate()) || booked.getEndDate().before(newBooking.getStartDate()))) {
                    conflictCounter++;
                }
            }
        }
        for(BookingObject reserved: reserved){
            if(reserved.isApproved() || reserved.isCommitted()) {
                if (!(reserved.getStartDate().after(newBooking.getEndDate()) || reserved.getEndDate().before(newBooking.getStartDate()))) {
                    if(!reservationIdsAlreadyBooked.contains(new Integer(reserved.getId()))){//reservierung nur zählen, wenn sie nicht schon bei den buchungen gezählt wurde
                        conflictCounter++;
                    }
                }
            }
        }
        return conflictCounter<numberOfSimultaneouslyBookableEntitys;//reservierungen und buchungen blockieren beide für ihren jeweiligen zeitraum eine ressource
    }

    private String handlePrepare(String[] words){
        String response = "";
        int id = Integer.parseInt(words[1]);
        if(words.length == 5) {//ein neuer eintrag mit 2 daten
            Date startdate = stringToDate(words[2]);
            Date enddate = stringToDate(words[3]);
            boolean finalBooking = words[4].equals("BOOK");//buchen oder reservieren
            BookingObject bookingObject = new BookingObject(false, false, startdate, enddate, id, packet);//erstellen des bookingObjects
            if (isFree(bookingObject)) {//falls frei ist, speichern in booked/reserved
                if (finalBooking) {
                    booked.add(bookingObject);
                } else {
                    reserved.add(bookingObject);
                }
                response = "READY " + bookingObject.getId();//ready antworten
                bookingObject.setApproved(true);//vermeken, dass approved wurde
            } else {
                toBeDeleted.add(bookingObject);//falls keine kapazität da ist, speichen in toBeDeleted
                response = "ABORT " + bookingObject.getId();//verfassen der Abortnachicht
            }
        } else if (words.length == 4 && words[3].equals("BOOK")) {//die buchung mit einer reservierungsnummer
            boolean succsess = false;
            int reservationId = Integer.parseInt(words[2]);
            int index = -1;
            for(int i = 0; i < reserved.size(); i++){//haben wir eine reservierung mit der nummer?
                BookingObject b = reserved.get(i);
                if(b.getId() == reservationId){
                    index = i;
                    succsess = true;
                }
            }
            if(succsess){//falls ja, speichern wir das neue bookingobject in booked und invalidieren das entsprechende booking object in reserved
                BookingObject reservedObject =  reserved.get(index);
                BookingObject bookingObject = new BookingObject(false, true, reservedObject.getStartDate(), reservedObject.getEndDate(), id, reservedObject.getOriginPacket());
                bookingObject.setReservationId(reservationId);
                reservedObject.setCommitted(false);
                reservedObject.setApproved(false);
                booked.add(bookingObject);
                response = "READY " + bookingObject.getId();//antworten mit ready
            }else{//falls wir keine solche reservierung kennen, antworten wir mit abort
                response = "ABORT " + id;
            }
        }

        return response;
    }

    private String handleCommit(String idString){
        int id = Integer.parseInt(idString);
        for(BookingObject booked: booked){//wir finden das booking object mit der id und setzen commited auf true
            if(booked.getId() == id) {
                booked.setCommitted(true);
            }
        }
        for(BookingObject reserved: reserved){//wir finden das booking object mit der id und setzen commited auf true
            if(reserved.getId() == id) {
                reserved.setCommitted(true);
            }
        }
        return "OK " + id;//antworten mit OK
    }

    private String handleAbort(String idString) {
        int id = Integer.parseInt(idString);
        int index = 0;
        for(BookingObject booked: booked){//durchsuchen aller bookingobjects in booked nach dem mit der richtigen id
            if(booked.getId() == id) {
                BookingObject removed = this.booked.remove(index);//entfernen des bookingobjects
                if(removed.getReservationId() != -1){//gibt es zu der buchung eine reservierung?
                    int reservedIndex = -1;
                    boolean success = false;
                    for(int i = 0; i < reserved.size(); i++){
                        BookingObject b = reserved.get(i);
                        if(b.getId() == removed.getReservationId()){//finden der reservierung welche zur buchung gehört
                            success = true;
                            reservedIndex = i;
                        }
                    }
                    if(success) {
                        BookingObject reservedObject = reserved.get(reservedIndex);//die entsprechende reservierung muss revalidiert werden, sonst verfällt die reservierung wegen kommunikationsfehler bei der entgültigen buchung
                        reservedObject.setCommitted(true);
                        reservedObject.setApproved(true);
                    }
                }
            }else{
                index++;
            }
        }
        index = 0;
        for(BookingObject reserved: reserved){//durchsuchen aller reservierungen nach der, welche abortet werden soll
            if(reserved.getId() == id) {
                this.reserved.remove(index);//löschen der reservierung
            }else{
                index++;
            }
        }
        index = 0;
        for(BookingObject tbd: toBeDeleted){
            if(tbd.getId() == id) {
                this.toBeDeleted.remove(index);//löschen des bookingObjects, welches von uns mit abort beantwortet wurde
            }else{
                index++;
            }
        }
        return "OK " + id;//antoworte mit ok

    }


    public void run() {
        while(gotSocket){//wieder eine endlosschleife
            try{
                packet = new DatagramPacket(new byte[1024], 1024);
                datagramSocket.receive(packet);//warten auf ein packet (hier würden wir endlos warten, wenn der timeoutchecker nicht unabhängig nachichten, welche unbeantwortet bleiben erneut senden könnte)
                String message = new String(packet.getData());
                message = message.trim();
                System.out.println(this.port + " received: " + message + " from " + packet.getPort());
                String[] words = message.split("\\s");//aufteielen der nachicht in wörter
                String response = "ABORT"; //im notfall abort
                switch (words[0]){
                    case "PING"://auch hier können wir pingen, auch hier brauchen wir es eig. garnicht
                        response = handelePing();
                        break;
                    case "PREPARE":
                        response = handlePrepare(words);
                        break;
                    case "ABORT":
                        response = handleAbort(words[1]);
                        break;
                    case "COMMIT":
                        response = handleCommit(words[1]);
                        break;
                }
                //absenden der erstellten antwort
                byte[] data = response.getBytes();
                InetAddress address = packet.getAddress();
                int port = packet.getPort();
                DatagramPacket reply = new DatagramPacket(data, data.length, address, port);
                System.out.println(this.port + " sent " + response + " to " + reply.getPort());
                datagramSocket.send(reply);
                this.writeThisToFile();
            }catch(IOException ioE){ioE.printStackTrace();}
        }
    }

    private String handelePing() {
        return "PONG";
    }

    public static void main(String[] args) {//obsolet, Main übernimmt diese aufgaben
        BookableServer hotelServer = new BookableServer(6998, 1);
        BookableServer rentalCarServer = new BookableServer(6999, 1);
        Thread t0 = new Thread(hotelServer);
        Thread t1 = new Thread(rentalCarServer);
        t0.start();
        t1.start();
    }
}
