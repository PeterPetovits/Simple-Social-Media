package socialNetwork;

import java.io.IOException;
import java.io.ObjectInputStream;

/*
 * This class is part of the implementation of the Stop-and-Wait protocol.
 * It runs on a separate thread so it cannot block, when it runs, other functions of the system.
 */

public class StopAndWait implements Runnable{

	private ObjectInputStream in;			//from this variable we receive all the message used to implement the Stop-and-Wait protocol
	private boolean receivedAck;			//this variable indicates if a packet is received in the right order based on the expected sequence number

	
	//Constructor of the class Stop-and-Wait
	public StopAndWait(ObjectInputStream in, boolean receivedAck) {
		this.in = in;
		this.receivedAck = receivedAck;
	}
	
	//returns true if a packet is received in the correct order based on the expected sequence number. Otherwise it returns false
	public boolean getReceivedAck() {
		return this.receivedAck;
	}
	
	//this method is used to set the variable receivedAck. We use it to change the value of the variable in order to monitor the next packets
	public void setReceivedAck(boolean value) {
		this.receivedAck = value;
	}
	
	//the method run contains what the thread runs
	@Override
	public void run() {
		try {
			int expectedSeq = 0;						//expected sequence stores the number of the sequence we expect to receive next. It can be 0 or 1.
			int ackReceived = 0;						//we count how many acknowledgments we have received. In our case if it equals to 10, means we have received all the packets correctly
			while(ackReceived < 10) {
				Message m = (Message)in.readObject();
				if(!m.getHeader().equals("Ack"))
					continue;
				
				if(m.getSequence() == expectedSeq) {			//if an acknowledgement (that it just arrived) has the expected number 
					this.receivedAck = true;					//we change the variable to true
					ackReceived++;								//we increase the counter (until total =  10)
					expectedSeq = (expectedSeq + 1) % 2;		//we change the expected sequence to the next number. If it was 0 turns to 1, and vice versa.
				}
				else {											//if the acknowledgment has not the expected sequence number
					System.out.println("Got the same ACK");		//we drop the acknowledgement packet and we print this message
				}
			}
			System.out.println("Thread closed");				//after we have received all the acknowledgments the thread it's not needed anymore
		} 
		catch (ClassNotFoundException | IOException e) {
			e.printStackTrace();
		}	
	}
}
