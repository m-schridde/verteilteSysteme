import java.io.IOException;
import java.net.*;

public class Main {
    static long[] pingSend = new long[4];
    static Thread t0;
    static Thread t1;
    static Thread t2;
    static Thread t3;
    static int[] portsOfServices = {7111,7112};//enter those values in the PING switch case
    public static void main(String[] args) {

        boolean gotSocket = false;

        DatagramSocket socket = null;
        while(!gotSocket) {
            try {
                socket = new DatagramSocket(5555);
                gotSocket = true;
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }

        BookableServer hotelServer = new BookableServer(portsOfServices[0], 1);
        BookableServer rentalCarServer = new BookableServer(portsOfServices[1], 1);
        t0 = new Thread(hotelServer);
        t1 = new Thread(rentalCarServer);
        t0.start();
        t1.start();

        BrokerServer server0 = new BrokerServer(0,2,6111, portsOfServices);
        BrokerServer server1 = new BrokerServer(1,2,6112, portsOfServices);
        t2 = new Thread(server0);
        t3 = new Thread(server1);
        t2.start();
        t3.start();

    }
}
