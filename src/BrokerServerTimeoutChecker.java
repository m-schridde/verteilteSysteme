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
                Thread.sleep(1500);//alle 1.5 sek. prüfen wir auf einen timeout
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
                server.userRequestObjects.forEach(userRequestObject -> {//jedes userRequestobject wird geprüft
                        LinkedList<DatagramPacket> list = userRequestObject.whatServerMessagesAreNext();//alle nötigen servermessages werden zusammengestellt
                        if(list != null){
                            list.forEach(datagramPacket -> {//falls nachtichten verschickt werden müssen, tun wir das (unabhängig davon, ob der Broker gerade auf nachichten wartet oder nicht)
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
