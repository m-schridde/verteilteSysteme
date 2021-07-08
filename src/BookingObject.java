import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;

public class BookingObject {
    private boolean committed;
    private boolean approved;
    private Date startDate;
    private Date endDate;
    private int id;
    private DatagramPacket originPacket;
    private int reservationId;

    public BookingObject(boolean committed, boolean approved, Date startDate, Date endDate, int id, DatagramPacket originPacket) {
        this.committed = committed;
        this.approved = approved;
        this.startDate = startDate;
        this.endDate = endDate;
        this.id = id;
        try {
            this.originPacket = new DatagramPacket(originPacket.getData(), originPacket.getOffset(), originPacket.getLength(), InetAddress.getByAddress(originPacket.getAddress().getHostName(), originPacket.getAddress().getAddress()), originPacket.getPort());
        }catch (UnknownHostException e){e.printStackTrace();};
        reservationId = -1;
    }


/*
    public BookingObject(BookingObject original)  {
        this.committed = original.isCommitted();
        this.approved = original.isApproved();
        this.startDate = new Date(original.getStartDate().getTime());
        this.endDate = new Date(original.getEndDate().getTime());
        this.id = original.getId();
        try {
            this.originPacket = new DatagramPacket(original.getOriginPacket().getData(), original.getOriginPacket().getOffset(), original.getOriginPacket().getLength(), InetAddress.getByAddress(original.getOriginPacket().getAddress().getHostName(), original.getOriginPacket().getAddress().getAddress()), original.getOriginPacket().getPort());
        }catch (UnknownHostException e){e.printStackTrace();}
    }*/

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
