package socialNetwork;

import java.io.IOException;
import java.io.ObjectOutputStream;


public class Notifier implements Runnable{

	private ObjectOutputStream out;
	private final String clientID;
	
	public Notifier(ObjectOutputStream out, String clientID) {
		this.out = out;
		this.clientID = clientID;
	}
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		long start = System.currentTimeMillis();
		Message notifyFollow = null;
		Message notifyUnfollow = null;
		Message notifyNewComment = null;
		while(true) {
			/*
			 Every 2 seconds we send a Followers requests notification and Unfollow notification to the Client in order for him to check if
			 he has a Follow request or an Unfollow notification. 
			*/
			if(System.currentTimeMillis() - start >= 2000) {
				notifyFollow = new Message("Followers requests notification", "time "+String.valueOf(start),this.clientID);
				notifyUnfollow = new Message("Unfollow notification", "time "+String.valueOf(start),this.clientID);
				notifyNewComment = new Message("Comment Notification", "time "+String.valueOf(start),this.clientID);
				try {
					this.out.writeObject(notifyFollow);
					this.out.flush();
					this.out.writeObject(notifyUnfollow);
					this.out.flush();
					this.out.writeObject(notifyNewComment);
					this.out.flush();
					start = System.currentTimeMillis();
				} 
				catch (IOException e) {
					break;
				}
			}
		}
	}
}
