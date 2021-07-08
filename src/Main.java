public class Main {
    public static void main(String[] args) {
        int[] portsOfServices = {6998,6999};
        BookableServer hotelServer = new BookableServer(portsOfServices[0], 1);
        BookableServer rentalCarServer = new BookableServer(portsOfServices[1], 1);
        Thread t0 = new Thread(hotelServer);
        Thread t1 = new Thread(rentalCarServer);
        t0.start();
        t1.start();

        BrokerServer server0 = new BrokerServer(0,2,6111, portsOfServices);
        BrokerServer server1 = new BrokerServer(1,2,6112, portsOfServices);
        Thread t2 = new Thread(server0);
        Thread t3 = new Thread(server1);
        t2.start();
        t3.start();
    }
}
