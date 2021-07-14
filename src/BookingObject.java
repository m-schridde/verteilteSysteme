import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;

public class BookingObject {
    private int id;
    private int reservationId;//wenn es eine reservierung ist, oder eine direkte buchung ohne reservierung steht hier eine -1
    private boolean committed;
    private boolean approved;
    private Date startDate;
    private Date endDate;
    private DatagramPacket originPacket;

    private static String booleanToTOrF(boolean b){
        return b?"t":"f";
    }
    public static boolean tOrFToBoolean(String s){
        s = s.toUpperCase();
        switch (s){
            case "T":
            case "TRUE":
                return true;
            case "F":
            case "FALSE":
                return false;
        }
        return false;
    }

    public static BookingObject createFromString(String s){//rekonstruieren dieses Objects aus einem String
        String[] words = s.split(" ");
        int id = Integer.parseInt(words[0]);
        int reservationId = Integer.parseInt(words[1]);
        boolean committed = tOrFToBoolean(words[2]);
        boolean approved = tOrFToBoolean(words[3]);
        Date startDate = new Date(Long.parseLong(words[4]));
        Date endtDate = new Date(Long.parseLong(words[5]));
        InetAddress address = null;
        try {
            address = InetAddress.getByName(words[6]);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        int port = Integer.parseInt(words[7]);
        String originalMessage = "";
        for(int i = 8; i<words.length;i++){
            originalMessage += words[i] + " ";
        }
        originalMessage = originalMessage.trim();
        DatagramPacket packet = new DatagramPacket(originalMessage.getBytes(), 0, originalMessage.getBytes().length, address, port);
        BookingObject b = new BookingObject(committed, approved, startDate, endtDate, id, packet);
        b.reservationId = reservationId;
        return b;
    }

    public String toString(){ //wir parsen this in einen String welcher im Backup .txt file gespeichert werden kann
        String s = this.id + " " + this.reservationId + " " + booleanToTOrF(committed) + " "
                + booleanToTOrF(approved) + " " + startDate.getTime() + " " + endDate.getTime()
                + " " + originPacket.getAddress().getHostName() + " " + originPacket.getPort() + " "
                + new String(originPacket.getData()).trim();
        return s;
    }

    public BookingObject(boolean committed, boolean approved, Date startDate, Date endDate, int id, DatagramPacket originPacket) {
        this.committed = committed;
        this.approved = approved;
        this.startDate = startDate;
        this.endDate = endDate;

        this.id = id;
        try {//wieder eine deepcopy, damit wir nicht auf volatile daten zugreifen
            this.originPacket = new DatagramPacket(originPacket.getData(), originPacket.getOffset(), originPacket.getLength(), InetAddress.getByAddress(originPacket.getAddress().getHostName(), originPacket.getAddress().getAddress()), originPacket.getPort());
        }catch (UnknownHostException e){e.printStackTrace();};
        reservationId = -1;
    }



    public int getReservationId() {
        return reservationId;
    }

    public void setReservationId(int reservationId) {
        this.reservationId = reservationId;
    }

    public boolean isCommitted() {
        return committed;
    }

    public void setCommitted(boolean committed) {
        this.committed = committed;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public DatagramPacket getOriginPacket() {
        return originPacket;
    }

    public void setOriginPacket(DatagramPacket originPacket) {
        this.originPacket = originPacket;
    }

}
