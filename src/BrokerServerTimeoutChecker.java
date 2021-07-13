import java.io.IOException;
import java.net.DatagramPacket;
import java.util.LinkedList;

public class BrokerServerTimeoutChecker implements Runnable{
    BrokerServer server;
    BrokerServerTimeoutChecker(BrokerServer server){
        this.server = server;
    }

    @Override
    public void run() {
        while(true) {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
                server.userRequestObjects.forEach(userRequestObject -> {
                        LinkedList<DatagramPacket> list = userRequestObject.whatServerMessagesAreNext();
                        if(list != null){
                            list.forEach(datagramPacket -> {
                                    try {
                                        server.datagramSocket.send(datagramPacket);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                            });
                        }
                }
            );

        }
    }
}
