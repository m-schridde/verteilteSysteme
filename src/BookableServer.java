import java.awt.print.Book;
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;

public class BookableServer implements Runnable{

    protected LinkedList<BookingObject> booked;
    protected LinkedList<BookingObject> reserved;
    protected LinkedList<BookingObject> toBeDeleted;
    protected DatagramSocket datagramSocket;
    private boolean gotSocket;
    private DatagramPacket packet;
    private int numberOfSimultaneouslyBookableEntitys;
    File file;
    int port;
    BookableServerTimeoutChecker timeoutChecker;

    private static Date stringToDate(String date){
        System.out.println("String to date for String: " + date);
        String[] split = date.split("-");
        Date result = new Date(Integer.parseInt(split[0])-1900, Integer.parseInt(split[1].trim()) - 1, Integer.parseInt(split[2].trim()));
        System.out.println("Result is: " + result);
        return result;
    }
    public String composeFileName(){
        return "bookableServer_" + this.port + "_" + numberOfSimultaneouslyBookableEntitys + ".txt";
    }
    private String composeFilePath(){
        return System.getProperty("user.dir") + File.separator + composeFileName();
    }

    private boolean createFileOrLoadFile() throws IOException {
        file = new File(composeFilePath());
        boolean result;
        result = file.createNewFile();
        return result; // true, if file was created, false, if file existed
    }

    private LinkedList<String> toStrings(){
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

    public BookableServer(int port, int numberOfSimultaneouslyBookableEntitys){
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
            wasFileNewlyCreated = createFileOrLoadFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(wasFileNewlyCreated) {
            writeThisToFile();
        }else{
            rebuildFromFile();
        }
        timeoutChecker = new BookableServerTimeoutChecker(this);
        Thread t1 = new Thread(timeoutChecker);
        t1.start();
    }

    private void writeThisToFile() {
        try {
            PrintWriter writer = new PrintWriter(new FileWriter(composeFilePath()));
            LinkedList<String> strings = toStrings();
            strings.forEach(s->{writer.println(s);});
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void recoverFirstLine(String line){
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

    private void rebuildFromFile() {
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

    private boolean isFree(BookingObject newBooking){
        int conflictCounter = 0;
        for(BookingObject booked: booked){
            if(booked.isApproved() || booked.isCommitted()) {
                if (!(booked.getStartDate().after(newBooking.getEndDate()) || booked.getEndDate().before(newBooking.getStartDate()))) {
                    conflictCounter++;
                }
            }
        }
        for(BookingObject reserved: reserved){
            if(reserved.isApproved() || reserved.isCommitted()) {
                if (!(reserved.getStartDate().after(newBooking.getEndDate()) || reserved.getEndDate().before(newBooking.getStartDate()))) {
                    conflictCounter++;
                }
            }
        }
        return conflictCounter<numberOfSimultaneouslyBookableEntitys;
    }

    private String handlePrepare(String[] words){
        System.out.println("Handle Prepare for words: ");
        for(String word:words){
            System.out.println(word);
        }
        String response = "";
        int id = Integer.parseInt(words[1]);
        if(words.length == 5) {
            Date startdate = stringToDate(words[2]);
            Date enddate = stringToDate(words[3]);
            boolean finalBooking = words[4].equals("BOOK");
            BookingObject bookingObject = new BookingObject(false, false, startdate, enddate, id, packet);
            if (isFree(bookingObject)) {
                if (finalBooking) {
                    booked.add(bookingObject);
                } else {
                    reserved.add(bookingObject);
                }
                response = "READY " + bookingObject.getId();
                bookingObject.setApproved(true);
            } else {
                toBeDeleted.add(bookingObject);
                response = "ABORT " + bookingObject.getId();
            }
        } else if (words.length == 4 && words[3].equals("BOOK")) {
            boolean succsess = false;
            int reservationId = Integer.parseInt(words[2]);
            int index = -1;
            for(int i = 0; i < reserved.size(); i++){
                BookingObject b = reserved.get(i);
                if(b.getId() == reservationId){
                    index = i;
                    succsess = true;
                }
            }
            if(succsess){
                BookingObject reservedObject =  reserved.get(index);
                BookingObject bookingObject = new BookingObject(false, true, reservedObject.getStartDate(), reservedObject.getEndDate(), id, reservedObject.getOriginPacket());
                bookingObject.setReservationId(reservationId);
                reservedObject.setCommitted(false);
                reservedObject.setApproved(false);
                booked.add(bookingObject);
                response = "READY " + bookingObject.getId();
            }else{
                response = "ABORT " + id;
            }
        }

        return response;
    }

    private String handleCommit(String idString){
        int id = Integer.parseInt(idString);
        for(BookingObject booked: booked){
            if(booked.getId() == id) {
                booked.setCommitted(true);
            }
        }
        for(BookingObject reserved: reserved){
            if(reserved.getId() == id) {
                reserved.setCommitted(true);
            }
        }
        return "OK " + id;
    }

    private String handleAbort(String idString) {
        int id = Integer.parseInt(idString);
        int index = 0;
        for(BookingObject booked: booked){
            if(booked.getId() == id) {
                BookingObject removed = this.booked.remove(index);
                if(removed.getReservationId() != -1){
                    int reservedIndex = -1;
                    boolean success = false;
                    for(int i = 0; i < reserved.size(); i++){
                        BookingObject b = reserved.get(i);
                        if(b.getId() == removed.getReservationId()){
                            success = true;
                            reservedIndex = i;
                        }
                    }
                    if(success) {
                        BookingObject reservedObject = reserved.get(reservedIndex);
                        reservedObject.setCommitted(true);
                        reservedObject.setApproved(true);
                    }
                }
            }else{
                index++;
            }
        }
        index = 0;
        for(BookingObject reserved: reserved){
            if(reserved.getId() == id) {
                this.reserved.remove(index);
            }else{
                index++;
            }
        }
        index = 0;
        for(BookingObject tbd: toBeDeleted){
            if(tbd.getId() == id) {
                this.toBeDeleted.remove(index);
            }else{
                index++;
            }
        }
        return "OK " + id;

    }


    public void run() {
        while(gotSocket){
            try{
                packet = new DatagramPacket(new byte[1024], 1024);
                datagramSocket.receive(packet);
                String message = new String(packet.getData());
                message = message.trim();
                System.out.println("Message received: ");
                System.out.println(message);
                String[] words = message.split("\\s");
                String response = "ABORT";
                switch (words[0]){
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

                byte[] data = response.getBytes();
                InetAddress address = packet.getAddress();
                int port = packet.getPort();
                DatagramPacket reply = new DatagramPacket(data, data.length, address, port);
                datagramSocket.send(reply);
                this.writeThisToFile();
            }catch(IOException ioE){ioE.printStackTrace();}
        }
    }

    public static void main(String[] args) {
        BookableServer hotelServer = new BookableServer(6998, 1);
        BookableServer rentalCarServer = new BookableServer(6999, 1);
        Thread t0 = new Thread(hotelServer);
        Thread t1 = new Thread(rentalCarServer);
        t0.start();
        t1.start();
    }
}
