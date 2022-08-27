package socialNetwork;

import java.io.Serializable;

/*
 * This class contains all the necessary informations for the messages sent 
 * by the client to the server and vice versa.
 */

public class Message implements Serializable {

	private String header;
	private String data;
	private String sender;
	private Byte[] chunk;		//this variable contains the data in chunks of a photo requested by a client
	private int seq;			//sequence variable indicates the number of a packet. This variable is used by the Stop-and-Wait protocol
	
	//the regular constructor for messages that contain a header, data and a sender field
	public Message(String header, String data, String sender) {
		this.header = header;
		this.data 	= data;
		this.sender = sender;		
	}

	//Constructor for messages that contain chunks of data
	public Message(String header, Byte[] chunk, int seq) {
		this.header = header;
		this.chunk = chunk;
		this.seq   = seq;
	}
	
	//Constructor for Acknowledgement messages, that contains the sequence number for checking
	public Message(String header, int seq) {
		this.header = header;
		this.seq = seq;
	}
	
	//returns the field header of a message
	public String getHeader() {
		return this.header;
	}
	
	//return the field data of a message
	public String getData() {
		return this.data;
	}
	
	//returns the field sender of a message
	public String getSender() {
		return this.sender;
	}

	//returns a Byte array with the chunked data
	public Byte[] getChunk() {
		return this.chunk;
	}
	
	//returns the sequence number of a specific chunk
	public int getSequence() {
		return this.seq;
	}
	
}
