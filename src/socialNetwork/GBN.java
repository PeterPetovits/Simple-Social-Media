package socialNetwork;

import java.io.IOException;
import java.io.ObjectInputStream;

public class GBN implements Runnable {
	
	public int windowSize = 3;
	public long timeout = 1500;
	private ObjectInputStream in;
	private boolean receivedAck =  false;
	private int lastAckNumberReceived;
	
	public GBN(ObjectInputStream in) {
		this.in = in;
	}

	@Override
	public void run() {
		try {
			while(true) {
				Message m = (Message)in.readObject();
				if(!m.getHeader().equals("Ack"))
					continue;
				System.out.println("Received ack " + m.getSequence());
				this.lastAckNumberReceived = m.getSequence();
				this.receivedAck = true;
			}
		} catch (ClassNotFoundException | IOException e) {
			e.printStackTrace();
		}
		
	}
	
	public int getRemainingWindow() {
		return this.windowSize;
	}
	
	public long getTimeout() {
		return this.timeout;
	}
	
	public boolean getReceivedAck() {
		return this.receivedAck;
	}
	
	public int getLastAckNumber() {
		return this.lastAckNumberReceived;
	}
	
	public void setRemainingWindow(int window) {
		this.windowSize = window;
	}
	
	public void setReceivedAck(boolean status) {
		this.receivedAck = status;
	}

}
