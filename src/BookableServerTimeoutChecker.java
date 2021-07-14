import java.io.IOException;
import java.net.DatagramPacket;

public class BookableServerTimeoutChecker implements Runnable{

    BookableServer server;

    public BookableServerTimeoutChecker(BookableServer server){
        this.server = server;
    }

    @Override
    public void run() {
        while(true){
            try {
                Thread.sleep(1500);//alle 1.5 sek. prüfen wir auf timeouts
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            server.booked.forEach(bookingObject -> {//alle gebuchten bookingobjects senden erneut READY, wenn wir noch kenen commit erhalten haben
                if(bookingObject.isApproved() && !bookingObject.isCommitted()){
                    String message = "READY " + bookingObject.getId();
                    try {
                        server.datagramSocket.send(new DatagramPacket(message.getBytes(), message.getBytes().length, bookingObject.getOriginPacket().getAddress(), bookingObject.getOriginPacket().getPort()));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            server.reserved.forEach(bookingObject -> {//alle reservierten bookingobjects senden erneut READY, wenn wir noch keinen commit erhalten haben
                if(bookingObject.isApproved() && !bookingObject.isCommitted()){
                    String message = "READY " + bookingObject.getId();
                    try {
                        server.datagramSocket.send(new DatagramPacket(message.getBytes(), message.getBytes().length, bookingObject.getOriginPacket().getAddress(), bookingObject.getOriginPacket().getPort()));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }
}
