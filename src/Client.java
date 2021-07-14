import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class Client {

    public static DatagramSocket datagramSocket;
    public static boolean gotSocket = false;

    public static void main(String[] args) {
        try {
            datagramSocket = new DatagramSocket();//anonymer socket wird erstellt
            gotSocket = true;
        } catch (SocketException socketE) {
            socketE.printStackTrace();
        }
        while (gotSocket) {//wenn der datagramSocket erstellt wurde, dann fragen wir in einer Endlosschleife die Nutzereingaben ab und stellen die serveranfrage zusammen
            try {
                String message;
                BufferedReader userIn = new BufferedReader(new InputStreamReader(System.in));
                System.out.println("Do you want to make a binding booking?(Y/N)");//Reservieren oder Buchen
                String answer = userIn.readLine();
                boolean messageComplete = false;
                if(answer.equals("Y") || answer.equals("y") || answer.equals("yes") || answer.equals("Yes")){
                    message = "BOOK ";
                }else{
                    message = "RESERVE ";
                }
                if(answer.equals("Y") || answer.equals("y") || answer.equals("yes") || answer.equals("Yes")){//falls gebucht werden soll, fragen wir, ob schon reserviert wurde
                    System.out.println("Do you already have a reservation that you want to book bindingly?(Y/N)");
                    answer = userIn.readLine();
                    if(answer.equals("Y") || answer.equals("y") || answer.equals("yes") || answer.equals("Yes")){
                        System.out.println("Please type in your Reservation ID");
                        message += userIn.readLine();
                        messageComplete = true;
                    }
                }
                if(!messageComplete) {//wenn eine reservierungsnummer angegeben wurde, dann ist die nachicht bereits vollständig, wenn nicht, dann brauchen wir noch die daten
                    System.out.println("please type your startdate in the specified format (yyyy-mm-dd)(days that are exceed the days of the month, will carried over into the next month):");
                    message += userIn.readLine();
                    message += " ";
                    System.out.println("please type your enddate in the specified format (yyyy-mm-dd)(days that are exceed the days of the month, will carried over into the next month):");
                    message += userIn.readLine();
                }
                byte[] data = message.getBytes();//absenden der Serverrequest
                InetAddress serverAddress = InetAddress.getByName("localhost");
                DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, 6111);
                datagramSocket.send(packet);
                System.out.println("your message has been sent (hopefully)");
                System.out.println(message);
                DatagramPacket response = new DatagramPacket(new byte[1024 * 64], 1024);
                datagramSocket.receive(response);//warten auf die antwort
                System.out.println(new String(response.getData()));//ausgeben der antwort
            }catch (IOException ioE){ioE.printStackTrace();}
        }
    }
}
