import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;

public class BookableServer implements Runnable{

    private LinkedList<BookingObject> booked;
    private LinkedList<BookingObject> reserved;
    private LinkedList<BookingObject> toBeDeleted;
    private DatagramSocket datagramSocket;
    private boolean gotSocket;
    private DatagramPacket packet;
    private int numberOfSimultaneouslyBookableEntitys;

    private static Date stringToDate(String date){
        System.out.println("String to date for String: " + date);
        String[] split = date.split("-");
        Date result = new Date(Integer.parseInt(split[0])-1900, Integer.parseInt(split[1].trim()) - 1, Integer.parseInt(split[2].trim()));
        System.out.println("Result is: " + result);
        return result;
    }

    public BookableServer(int port, int numberOfSimultaneouslyBookableEntitys){
        this.booked = new LinkedList<BookingObject>();
        this.reserved = new LinkedList<BookingObject>();
        this.toBeDeleted = new LinkedList<BookingObject>();
        this.numberOfSimultaneouslyBookableEntitys = numberOfSimultaneouslyBookableEntitys;
        int tryCounter = 0;
        gotSocket = false;
        while(tryCounter < 10 && !gotSocket){
            tryCounter++;
            try{
                datagramSocket = new DatagramSocket(port);
                gotSocket = true;
            }catch(IOException ioE){ioE.printStackTrace();}
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
