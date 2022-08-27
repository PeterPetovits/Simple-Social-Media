package socialNetwork;
import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;

public class Listener implements Runnable {

	private ObjectInputStream in;
	private final ArrayBlockingQueue<Message> incMessages;//list to store all incoming messages of the client
	private final ArrayList<String> followRequestsForClient;//list to store all the incoming follow requests of the client
	private final ArrayList<String> CommentRequestsForClient;
	private final ArrayList<String> following;//list to store the user's the client is following
	private final ArrayList<String> followers;//list to store the user's that follow the client

	private String clientID;
	public Listener(ObjectInputStream in,ArrayBlockingQueue<Message> incMessages,ArrayList<String> followRequestsForClient,ArrayList<String> following,ArrayList<String> followers,ArrayList<String> CommentRequestsForClient ) {
		this.following = following;
		this.in = in;
		this.incMessages = incMessages;
		this.followRequestsForClient = followRequestsForClient;
		this.followers = followers;
		this.CommentRequestsForClient = CommentRequestsForClient;
	}

	public void setClientID(String clientID){
		this.clientID = clientID;
	}
	
	@Override
	public void run() {
		
		while(true) {
			try {
				Message m = (Message)in.readObject();
				/*
				 If the incoming message is a follow request we print a notification to the user and then add the following requests to the
				 followRequestsForClient arraylist.
				*/
				if(m.getHeader().equals("Follow Request")) {//
					System.out.println("You have new follow requests");
					String[] requests = m.getData().split("\\s+");
					for(String s:requests)
						this.followRequestsForClient.add(s);
				}
				/*
				 If the incoming message is a follow accept we print the name of the user that accepted our request in a prompt and we add his name
				 in our following list
				*/
				else if(m.getHeader().equals("Follow Accept")) {
					String[] accepts = m.getData().split("\\s+");
					for(String s:accepts) {
						System.out.println(s+" accepted your follow request");
						this.following.add(s);
					}
				}
				/*
				 If the message is an Unfollow commit we print the name of the user that unfollowed us in a prompt and the we remove him from our
				 followers list
				*/
				else if(m.getHeader().equals("Unfollow commit")) {
					String[] unfollows = m.getData().split("\\s+");
					for(String s : unfollows) {
						System.out.println(s + " unfollowed you!");
						this.followers.remove(s);
					}
				}
				/*
				 If the message is an Upload Notification it means that a user we follow just uploaded a new post
				*/
				else if(m.getHeader().equals("Upload Notification")) {
					String[] notifications = m.getData().split("\\n+");
					FileWriter fw 		= new FileWriter("ClientDirectory/"+this.clientID+"/"+"Others_998"+this.clientID+".txt",true);
					BufferedWriter bw 	= new BufferedWriter(fw);
					for(String s: notifications)
						bw.append(s+"\n");
					bw.close();
					System.out.println("New post in your feed");
				}
				else if(m.getHeader().equals("Comment Request Handled")) {
					String[] s = m.getData().split("`");
					for(String tmp : s) {
						String[] str = tmp.split("\\|");
						if(str[3].equals("Approved")) {
							try {
								FileWriter fw 		= new FileWriter("ClientDirectory/"+this.clientID+"/Profile_998"+this.clientID+".txt",true);
								BufferedWriter bw 	= new BufferedWriter(fw);
								bw.append("\nComment: "+ str[1]);
								bw.close();
								System.out.println("Your comment at " + str[0] + " from user " + str[2] + " has been approved");
							} catch (IOException e) {
								throw new RuntimeException(e);
							}
						}
						else {
							System.out.println("Your comment at " + str[0] + " from user " + str[2] + " has been denied");
						}
					}

				}
				else if(m.getHeader().equals("Comment requests")) {
					System.out.println("You have new comment requests");
					this.CommentRequestsForClient.add(m.getData());
				}
				else if(m.getHeader().equals("Error Comment")) {
					System.out.println("The photo you wanted to comment does not exist");
				}
				/*
				 Every other message just gets stored in the IncMessages in order to get handled from the Client 
				*/
				else {
					this.incMessages.add(m);
				}
				
				
			} 
			catch (ClassNotFoundException | IOException e) {
				break;
			}

		}	
	}
}
