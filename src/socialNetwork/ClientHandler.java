package socialNetwork;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


/*
 * This class is responsible for implementing all the logic 
 * in order to service the clients. It implements the java's Runnable 
 * interface because it is a Thread of our Server. It takes as parameters most of
 * server's data structures and modify them according to clients requests and activities.
 * Each instance of this class is interacting with a single client.
 */

public class ClientHandler implements Runnable {

	private Socket connection 		= null;
	private ObjectInputStream in 	= null;
	private ObjectOutputStream out 	= null;
	private final ArrayList<Pair<String, String>> registeredUsers;
	private final HashMap<String,ArrayList<String>> graph;
	private final HashMap<String, ArrayList<String>> followRequests;
	private final HashMap<String, ArrayList<String>> followAccepts;
	private final HashMap<String, ArrayList<String>> unfollowPendings;
	private final HashMap<String, ArrayList<String>> uploadNotifications;
	private final HashMap<String,ArrayList<String>> commentRequests;
	private final HashMap<String,ArrayList<String>> commentAccepts;

	private String clientID;
	
	
	
	//The constructor of the Client Handler.
	public ClientHandler
						(Socket connection,ArrayList<Pair<String, String>> registeredUsers,HashMap<String,ArrayList<String>> graph,
						HashMap<String, ArrayList<String>> followRequests, HashMap<String, ArrayList<String>> followAccepts,
						HashMap<String, ArrayList<String>> unfollowPendings, HashMap<String, ArrayList<String>> uploadNotifications,HashMap<String,ArrayList<String>> commentRequests,
						HashMap<String,ArrayList<String>> commentAccepts) {
		/*
		 * Structures passed by the server.
		 */
		this.followAccepts 			= followAccepts;
		this.followRequests 		= followRequests;
		this.registeredUsers 		= registeredUsers;
		this.unfollowPendings 		= unfollowPendings;
		this.graph 					= graph;
		this.uploadNotifications 	= uploadNotifications;
		this.commentRequests = commentRequests;
		this.commentAccepts = commentAccepts;
		
		
		this.clientID 				= ""; // ID of the client that this handler service.
		try {
			this.connection 		= connection; //connection with client.
			this.out 				= new ObjectOutputStream(connection.getOutputStream()); //output buffer.
			this.in  				= new ObjectInputStream(connection.getInputStream()); 	//input buffer.
			
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*
	 * This method is used when a new user is registered in 
	 * the social network. It's job is to add this user in the 
	 * social graph as well as the other data structures that contains
	 * all users. The method is called in the sign-up functionality.
	 */
	private void initNewUser(String username,String password) {
		this.registeredUsers.add(new Pair(username,password));
		this.graph.put(username,new ArrayList<>());
		this.followAccepts.put(username,new ArrayList<>());
		this.followRequests.put(username,new ArrayList<>());
		this.unfollowPendings.put(username,new ArrayList<>());
		this.uploadNotifications.put(username,new ArrayList<>());
	}

	
	/*
	 * This method is responsible for generating the APDUs.
	 * Because we don't want to send multimedia files as a big message but 
	 * we want to fragment it in about 10 chunks and then send them 1 by 1
	 * this method takes as input a file and then creates 10 messages. Each of the message
	 * contains a small chunk of the file's initial data buffer. The method is called in the
	 * download functionality.
	 */
	private Message[] generateAPDUs(String fileName) {
		try {
			File file = new File(fileName);
			byte[] fileBytes = new byte[(int)file.length()]; // buffer for the data of the multimedia file/

			FileInputStream reader = new FileInputStream(file); 
			reader.read(fileBytes); // fill the buffer with the data of the multimedia file.
			reader.close();

			Message[] APDUs = new Message[10]; // buffer for messages.
			
			int chunkSize = (int) fileBytes.length / 10;
			int LastChunkSize = chunkSize + (fileBytes.length % 10); //last chunk's size might differ so we add to it the remaining.

			ArrayList<Byte[]> chunks = new ArrayList<>();
			
			//for 9 first chunks, all have the same size.
			for(int i = 0; i < 9; i++) {
				Byte[] tmp = new Byte[chunkSize];
				for(int j = 0; j < chunkSize; j++) {
					tmp[j] = fileBytes[i*chunkSize + j]; // i * chunkSize is the offset from the last iteration. Adding j to it gets us the correct byte.
				}
				chunks.add(tmp);	
			}
			
			//last chunks needs different approach because it's size is different.
			Byte[] tmp = new Byte[LastChunkSize];
			for(int i = 0; i < LastChunkSize; i++) {
				tmp[i] = fileBytes[9*chunkSize + i];
			}
			chunks.add(tmp);
			
			/*
			 * Generating the messages. The field sequence is required
			 * for the implementation of stop and wait protocol.
			 */
			int seq = 0;
			for(int i = 0; i < APDUs.length; i++) {
				APDUs[i] = new Message("File Chunk",chunks.get(i),seq); 
				seq = (seq+1) % 4; //stop and wait requires 1 bit for enumerate the chunks, hence 0 1 0 1 ... 0 1.
			}
			return APDUs;

		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		
	}
	
	
	/*
	 * This method is responsible for sending the APDUs 
	 * according to the stop and wait protocol. It takes as input
	 * a buffer with messages that are generated by the method above
	 * and sends them one by one using the stop and wait protocol. The method is called in the
	 * download functionality.
	 */
	private void SendAPDUs(Message[] APDUs) {
			
		try {
			int i = 0;
			/*
			 * Starting a new thread that implementing the stop and wait and 
			 * it is responsible for receiving the acknowledgement.
			 */
			StopAndWait protocol = new StopAndWait(this.in, false);
			Thread t = new Thread(protocol);
			t.start();
			
			
			while(i < APDUs.length) {
				// send the message.
				this.out.writeObject(APDUs[i]);
				this.out.flush();
				
				
				long start = System.currentTimeMillis();// start a timer.
				while(System.currentTimeMillis() - start < 1500 ) { // as long as timer has not expired wait for the acknowledgement.
					if(protocol.getReceivedAck()) // if you received the acknowledgement before timer expired then send the next APDU. 
						break;
				}
				
				
				/*
				 * If the loop above stopped because the acknowledgement got received the send the next message
				 * should be sent. Hence increase the counter of the message received, print that the message (i) received 
				 * and go to the next iteration. Otherwise, if the timer has expired then do not increase the counter. Just 
				 * retransmit the message.
				 */
				if(protocol.getReceivedAck()) {
					System.out.println("Received ACK " + i + " with sequence " + APDUs[i].getSequence());
					i++;
				}
				else {
					System.out.println("Server did not receive ACK");
				}
					
				// update the stop and wait thread that acknowledgement is false.
				protocol.setReceivedAck(false);
			}
		} 
		catch (IOException e) {
				e.printStackTrace();
		}
	}
	
	private void sendAPDUsGBN(Message[] APDUs) {
			try {
				GBN protocol = new GBN(this.in);
				Thread t = new Thread(protocol);
				t.start();
					
				int i = 0;
				ArrayList<Pair<Message, Long>> timerPackets = new ArrayList<>();
				while(i < APDUs.length) {
					int window = protocol.getRemainingWindow();
						
					for(int j = i; j < window; j++) {
						this.out.writeObject(APDUs[j]);
						this.out.flush();
						timerPackets.add(new Pair<Message, Long>(APDUs[i], System.currentTimeMillis()));
						protocol.setRemainingWindow(protocol.getRemainingWindow() - 1);
					}
					
					//long lastTimer = timerPackets.get(0).getValue();
					
					while(true) {
						long lastTimer = timerPackets.get(0).getValue();
						while(System.currentTimeMillis() - lastTimer < protocol.getTimeout()) {
							if(protocol.getReceivedAck()) {
								System.out.println("Ack received " + protocol.getLastAckNumber());
								protocol.setReceivedAck(false);
								i = i + protocol.getLastAckNumber() + 1;
								int lastAckNumber = protocol.getLastAckNumber();
								int remove = lastAckNumber - i;
								System.out.println("i number " + i);
								for(int k = 0; k < remove; k++) {
									timerPackets.remove(0);
									protocol.setRemainingWindow(protocol.getRemainingWindow() + 1);
								}
								
								lastTimer = timerPackets.get(0).getValue();
								
								for(int k = 0; k < protocol.getLastAckNumber(); k++) {
									timerPackets.add(new Pair<Message, Long>(APDUs[i+2], System.currentTimeMillis()));
									this.out.writeObject(APDUs[i+2]);
									this.out.flush();
									protocol.setRemainingWindow(protocol.getRemainingWindow() - 1);
								}
								
								/*
								int temp = i;
								while(i < temp + protocol.getRemainingWindow()) {
									timerPackets.add(new Pair<Message, Long>(APDUs[i], System.currentTimeMillis()));
									this.out.writeObject(APDUs[i]);
									this.out.flush();
									i++;
									protocol.setRemainingWindow(protocol.getRemainingWindow() - 1);
								}
								*/
							}
						}
						break;
					}
				}
			} 
			catch (IOException e) {
					e.printStackTrace();
			};
	}
	
	private void sendCaption(String fileName, String clientName) {
		BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader("ServerDirectory/"+clientName+"/"+fileName));
			String data = "";
			String line;
			line = reader.readLine();
			while(line!=null) {
				data += line;
				line = reader.readLine();
			}
			reader.close();
			Message captionReply = new Message("Caption Download", data, fileName);
			this.out.writeObject(captionReply);
			this.out.flush();
		} 
		catch (IOException e) {
			try {
				Message captionReply = new Message("Caption Download" , "Caption for this photo does not exist", "");
				this.out.writeObject(captionReply);
				this.out.flush();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}

	}

	
	/*
	 * This method is responsible for updating the file that contains 
	 * the IP addresses and the ports of the users that are registered in 
	 * the social network. The file should be updated dynamically so it represents 
	 * the current structure of the graph. This method reads the file and stores the lines in
	 * an ArrayList. If the file that is currently examined is containing the name of the client that 
	 * this thread is servicing then we update this line with it's new IP and port. Then we write the file
	 * so it maps the new changes. This method is called in the login-in functionality. 
	 */
	private void updateIPsAndPorts(String IP, int Port) {
		try{
			BufferedReader reader = new BufferedReader(new FileReader("IPs_Ports.txt")); // read the file that contains IP addresses and ports.
			String line;
			ArrayList<String> newLines = new ArrayList<>(); // buffer for the lines.
			boolean exists = false; // this flag is used if the user is a new one so we just add him in the end of the file.
			line = reader.readLine();
			while(line!=null) {
				if(line.contains(clientID)) { // this is the old line of our client, hence an update is required.
					String newLine = clientID+","+IP+","+String.valueOf(Port);
					newLines.add(newLine);
					exists = true; // this user already exists in the graph, it's not new.
				}
				else {
					newLines.add(line);
				}
				line = reader.readLine();
			}
			reader.close();
			if(!exists) {
				newLines.add(clientID+","+IP+","+String.valueOf(Port));
			}
			/*
			 * Write the file with the new connection information.
			 */
			BufferedWriter writer = new BufferedWriter(new FileWriter("IPs_Ports.txt",false)); 
			for(String s : newLines) {
				writer.write(s+"\n");
				writer.flush();
			}
			writer.close();
		} catch (IOException e) {
			
			e.printStackTrace();
		}
	}
	
	
	/*
	 * This method is the main loop of our class that will be executed as a Thread.
	 * In this method all the messages from the client are handled and according to the 
	 * headers of the messages the correct functionality is executed. This thread is alive
	 * as long as the connection from the other side is alive too. The method waits until an input 
	 * is available. Then it takes the input, cast it to a message and accordingly to the header it 
	 * executes and different activity.
	 */
	@Override
	public void run() {
		
		while(this.connection.isConnected()) {
			try {
				Message m = (Message)this.in.readObject();
				String header = m.getHeader();
				String sender = m.getSender();
				
				/*
				 * Sing up functionality. Checks if the name that user has chosen is available. If so it 
				 * accept it's sign up request and makes it a new user, otherwise it reject it's request and
				 * answers to him accordingly.
				 */
				if(header.equals("Sign up")) {
					if(checkNameAvailability(sender)) {
						String password = m.getData();
						initNewUser(sender, password);
						FileWriter fw = new FileWriter("registeredUsers.txt",true);
						BufferedWriter bw = new BufferedWriter(fw);
						bw.append("\n"+sender+" "+password);
						bw.flush();
						bw.close();
						fw = new FileWriter("SocialGraph.txt", true);
						bw = new BufferedWriter(fw);
						bw.append("\n"+sender);
						bw.flush();
						bw.close();
						Message reply = new Message("Sing up reply","Succesfully singed up","");
						this.out.writeObject(reply);
						this.out.flush();
					}
					else {
						Message reply = new Message("Sing up reply","Sing up failed. Username already exist","");
						this.out.writeObject(reply);
						this.out.flush();
					}
				}
				
				/*
				 * Login functionality. Checks if the credentials are correct. If so accept the login request.
				 * Otherwise it reject the user and send him a message that informs him about invalid login data.
				 */
				else if(header.equals("Login")) {
					String password = m.getData();
					if(this.registeredUsers.contains(new Pair<String,String>(sender,password))) {
						this.clientID = m.getSender();
						System.out.println("Welcome client " + clientID);
						Message reply = new Message("Login success","Welcome client " + clientID,"");
						updateIPsAndPorts(this.connection.getInetAddress().getHostAddress(),this.connection.getPort());
						this.out.writeObject(reply);
						this.out.flush();
					}
					else {
						Message reply = new Message("Login failed","Invalid credentials","");
						this.out.writeObject(reply);
						this.out.flush();
					}
				}
				// return to the user a message that contains it's followers. (See followers functionality in client).
				else if(header.equals("Get followers")) {
					String clientID = m.getSender();
					String followers = "";			
					ArrayList<String> tmp = this.graph.get(clientID);
					for(String s : tmp) {
						followers += s;
						followers += " ";
					}

					Message reply = new Message("Followers reply", followers, "");
					this.out.writeObject(reply);
					this.out.flush();

				}
				// return to the user a message that contains the people that he follows. (See following functionality in client).
				else if(header.equals("Get following")) {
					String clientID = m.getSender();
					String following = "";

					for(Map.Entry<String,ArrayList<String>> set : this.graph.entrySet()) {
						if(!set.getKey().equals(clientID)) {
							if(set.getValue().contains(clientID)) {
								following += set.getKey();
								following += " ";
							}
						}
					}
					Message reply = new Message("Following reply", following, "");
					this.out.writeObject(reply);
					this.out.flush();
				}
				// return the users that a client does not follow. (See follow functionality in client).
				else if(header.equals("Pull Users")) {
					String clientID = m.getSender();
					String notFollowing = "";

					for(Map.Entry<String,ArrayList<String>> set : this.graph.entrySet()) {
						if(!set.getKey().equals(clientID)) {
							if(!set.getValue().contains(clientID)) {
								notFollowing += set.getKey();
								notFollowing += " ";
							}
						}
					}
					Message reply = new Message("Pull Users reply", notFollowing,"");
					this.out.writeObject(reply);
					this.out.flush();
				}
				/*
				 * Here the client requests to follow a user. After receiving that message, we go to this user
				 * and add to it's follow request lists a new request from our client.
				 */
				else if(header.equals("Follow request")) {
					String clientToFollow = m.getData();
					if(this.followRequests.containsKey(clientToFollow))
						this.followRequests.get(clientToFollow).add(this.clientID);
					else {
						ArrayList<String> list = new ArrayList<>();
						list.add(this.clientID);
						this.followRequests.put(clientToFollow, list);
					}
				}
				/*
				 * Here the client asks to check if he has new follow requests, 
				 * new follow accepts or a follower of his has uploaded a new post. We loop through it's follow requests list,
				 * it's follow accepts list and it's upload notification list and we send him all the update 
				 * information required. Clients send as this type of message every 2 seconds so all updates can be implemented
				 * here.
				 */
				else if(header.equals("Followers requests notification")) {
					String reply = "";
					ArrayList<String> followRequestsForClient = this.followRequests.get(this.clientID);
					if(followRequestsForClient.size() > 0) {
						for(String s: followRequestsForClient)
							reply += s+" ";
						Message msg = new Message("Follow Request", reply, "");
						this.out.writeObject(msg);
						this.followRequests.get(clientID).clear();
					}
					
					reply = "";
					ArrayList<String> followAcceptsForClient = this.followAccepts.get(this.clientID);
					if(followAcceptsForClient.size() > 0) {
						for(String s: followAcceptsForClient)
							reply += s+" ";
						Message msg = new Message("Follow Accept", reply, "");
						this.out.writeObject(msg);
						this.out.flush();
						this.followAccepts.get(clientID).clear();
					}

					ArrayList<String> myUploadNotifications = this.uploadNotifications.get(this.clientID);
					reply = "";
					if(myUploadNotifications.size() > 0) {
						for(String s : myUploadNotifications) 
							reply += s+" ";
						Message msg = new Message("Upload Notification", reply, "");
						this.out.writeObject(msg);
						this.out.flush();
						this.uploadNotifications.get(clientID).clear();
					}

					ArrayList<String> myCommentNotifications = this.commentAccepts.get(this.clientID);
					reply = "";
					if(myCommentNotifications.size() > 0) {
						for(String s : myCommentNotifications)
							reply += s+ "`";
						Message msg = new Message("Comment Request Handled",reply, "");
						this.out.writeObject(msg);
						this.out.flush();
						this.commentAccepts.get(clientID).clear();
					}
				}
				
				/*
				 * Here our client accepted a follow requests. So we firstly updated the graph and 
				 * then we add to the user that was accepted by our client a new request for follow accept.
				 */
				else if(header.equals("Follow request accept")) {
					String acceptedUser = m.getData();
					this.updateGraph(false, acceptedUser);
					this.followAccepts.get(acceptedUser).add(this.clientID);
				}
				
				/*
				 * Here our client unfollowed someone. So we firstly update the graph and then
				 * we add an unfollow request to the unfollowPendings structure of the user that 
				 * got unfollowed by our client.
				 */
				else if(header.equals("Unfollow")) {
					String toUnfollow = m.getData();
					this.updateGraph(true, toUnfollow);
					this.unfollowPendings.get(toUnfollow).add(this.clientID);

				}
				/*
				 * Here we check if anyone has unfollowed our client. If so we just create a message 
				 * with this information and we update our client.
				 */
				else if(header.equals("Unfollow notification")) {
					String reply = "";
					ArrayList<String> UnfollowNotifications = this.unfollowPendings.get(this.clientID);
					if(UnfollowNotifications.size() > 0) {
						for(String s: UnfollowNotifications)
							reply += s+" ";
						Message msg = new Message("Unfollow commit", reply, "");
						this.out.writeObject(msg);
						this.unfollowPendings.get(clientID).clear();
					}
				}
				
				/*
				 * Here the client has uploaded a new photo in his profile and he asks us to synchronize it's
				 * directory that is stored locally in server. He sends us the data of the photo and the 
				 * caption and we then call the synchronization method ( See synchClientDirectory 1st overload for more). 
				 */
				else if(header.equals("Synchronization")) {
					String fileName = m.getData();
					DataInputStream dis = new DataInputStream(this.connection.getInputStream());
					int length = dis.readInt();
					byte[] photoBytes = new byte[length];
					dis.readFully(photoBytes);

					ArrayList<Byte[]> captionsBytes = new ArrayList<>();
					ArrayList<String> captionNames = new ArrayList<>();

					for(int i=0;i<2;i++) {
						String captionName = dis.readUTF();

						int hasCaption = dis.readInt();
						byte[] captionBytes = new byte[hasCaption];
						if (hasCaption != 0) {
							captionNames.add(captionName);
							dis.readFully(captionBytes);
							Byte[] temp = new Byte[hasCaption];
							for(int j=0;j<captionBytes.length;j++)
								temp[j] = Byte.valueOf(captionBytes[j]);

							captionsBytes.add(temp);
						}
					}

					length = dis.readInt();
					byte[] profileBytes = new byte[length];
					dis.readFully(profileBytes);

					FileInputStream fis = new FileInputStream("ServerDirectory/"+this.clientID+"/"+"Profile_998"+this.clientID+".txt");
					byte[] bytes = fis.readAllBytes();

					String profileBeforeSync = new String(bytes);
					String profileAfterSync = new String(profileBytes);

					String newPost = profileAfterSync.replace(profileBeforeSync,"");

					fis.close();
					this.synchClientDirectory(fileName, photoBytes ,captionNames,captionsBytes, profileBytes);

					ArrayList<String> myFollowers = this.graph.get(this.clientID);
					for(String s: myFollowers) {
						this.uploadNotifications.get(s).add(newPost);
						FileWriter fw 		= new FileWriter("ServerDirectory/"+s+"/"+"Others_998"+s+".txt",true);
						BufferedWriter bw 	= new BufferedWriter(fw);
						bw.append(newPost);
						bw.close();

					}

				}
				/*
				 * Here the client request to see a user's profile. He can see it only if he follow the user with
				 * the profile requested profile. First we check if the profile is existing. If not then we just send him a message that the 
				 * profile does not exist. If the profile exists, then if the client follows the user with the profile asked then 
				 * we send him the profile. If he does not follow the user with the profile asked then we deny it's request and we
				 * send him a message and inform him that the access was denied. (See client hander Access Profile).
				 */
				else if(header.equals("Access Profile")) {
					String clientToAccess = m.getData();

					// check if the profile exists.
					boolean exists = false;
					for(Pair<String,String> p : registeredUsers) {
						if(p.getKey().equals(clientToAccess)) {
							exists = true;
							break;
						}
					}
					// if profile exists, then read the profile file for the user asked and write it to a message as reply.
					if(exists) {
						if(graph.get(clientToAccess).contains(clientID)) {
							BufferedReader reader = new BufferedReader(new FileReader("ServerDirectory/"+clientToAccess+"/Profile_998"+clientToAccess+".txt"));
							String line;
							String profileToSend = "\n";
							line = reader.readLine();
	
							while(line!=null) {
								profileToSend += line;
								profileToSend += "\n";
								line = reader.readLine();
							}
							reader.close();
	
							Message reply = new Message("Accept Profile", profileToSend, "");
							this.out.writeObject(reply);
							this.out.flush();
						}
						else {
							Message reply = new Message("Deny Profile", "", "");
							this.out.writeObject(reply);
							this.out.flush();
						}
					}
					//if not exists send a message with this information.
					else {
						Message reply = new Message("Deny Profile", "Profile Does not Exist", "");
						this.out.writeObject(reply);
						this.out.flush();
					}
					
				}
				
				/*
				 * Here the user has searched for a photo. First of all we find all his followers
				 * (only from them he can search for photos). After finding them all we check their directories
				 * in order to see which of them contains the file asked. We then return to the client a message that
				 * contains all the ID's of his followers who have the photo. (See search operation in client).
				 */
				else if(header.equals("Search")) {
					String[] data = m.getData().split(",");
					String photoName = data[0];
					String captionLanguage = data[1];

					ArrayList<String> clientsFollowing = new ArrayList<>();

					for(Map.Entry<String,ArrayList<String>> set : this.graph.entrySet()) {
						if(!set.getKey().equals(clientID)) {
							if(set.getValue().contains(clientID)) {
								clientsFollowing.add(set.getKey());
							}
						}
					}

					String reply = "";
					String dirPath = "ServerDirectory/";
					String fileName = "";

					for(String s : clientsFollowing) {
						File directory = new File(dirPath+s);
						String[] fileNames = directory.list();
						for(String file : fileNames) {
							if(file.contains(photoName) && file.contains(captionLanguage)) {
								fileName = file;
								reply += s;
								reply += " ";
								break;
							}
						}
					}

					Message searchReply = new Message("Search reply", reply, fileName);
					this.out.writeObject(searchReply);
					this.out.flush();

				}
				else if(header.equals("Ask Comment")) {
					String[] data = m.getData().split("\\|");
					String photo = data[0];
					String userToComment = data[1];
					String comment = data[2];
					if(validateComment(photo,userToComment))
						this.commentRequests.get(userToComment).add(photo+"|"+comment+"|"+m.getSender());
					else {
						this.out.writeObject(new Message("Error Comment","",""));
						this.out.flush();
					}
				}

				else if(header.equals("Comment Notification")) {
					ArrayList<String> CommentNotifications = this.commentRequests.get(this.clientID);
					String reply = "";
					if(CommentNotifications.size() > 0) {
						for(String s: CommentNotifications)
							reply += s+"`";
						Message msg = new Message("Comment requests", reply, "");
						this.out.writeObject(msg);
						this.commentRequests.get(clientID).clear();
					}
				}

				else if(header.equals("Approve Comment")) {
					String[] data = m.getData().split("\\|");
					FileWriter fw 		= new FileWriter("ServerDirectory/"+data[2]+"/Profile_998"+data[2]+".txt",true);
					BufferedWriter bw 	= new BufferedWriter(fw);
					bw.append("\nComment: "+ data[1]);
					bw.close();
					this.commentAccepts.get(data[2]).add(data[0]+"|"+data[1]+"|"+this.clientID+"|"+"Approved");

				}
				else if(header.equals("Deny Comment")) {
					String[] data = m.getData().split("\\|");
					this.commentAccepts.get(data[2]).add(data[0]+"|"+data[1]+"|"+this.clientID+"|"+"Deny");
				}
				
				/*
				 * Here the client asks to download a photo that is stored in someone's directory.
				 * Fist, we find the photo and the caption is exists. Then we have to generate the APDUs 
				 * because photo can not be sent in a single message. Finally we send all the APDUs to the client.
				 * See generateAPDUs and SendAPDUs above for more information. (See download operation in client).
				 */
				else if(header.equals("Download request")) {
					try {
						Message requestReply = new Message("Download accept", "", "");
						this.out.writeObject(requestReply);
						this.out.flush();

						Message download = (Message) this.in.readObject(); // Header == download, receive info

						String photoName = download.getData().split("_")[0];
						String captionName = download.getData();
						String clientWithPhoto = download.getSender();

						File directory = new File("ServerDirectory/"+clientWithPhoto);
						String[] fileNames = directory.list();
						String fileName = "";
						for(String s : fileNames) {
							if(s.contains(photoName) && !s.endsWith("txt")) {
								fileName = s; 
								break;
							}

						}
						String filePathName = "ServerDirectory/"+clientWithPhoto+"/"+fileName;
						System.out.println(fileName);
						Message[] APDUs = generateAPDUs(filePathName);
						this.sendAPDUsGBN(APDUs);
						
						this.sendCaption(captionName,clientWithPhoto);
						
						this.synchClientDirectory(clientWithPhoto, fileName,captionName);
						
						
					} catch (IOException  e) {
						e.printStackTrace();
					}
				}
				/*
				 * Client requested to log out and he has already closed the connection.
				 * We close the connection too and the Thread is terminated.
				 */
				else if(header.equals("Log Out")) {
					this.connection.close();
					break;
				}
			} 
			catch (ClassNotFoundException | IOException e) {
				e.printStackTrace();
			}
		}	
	}
	
	
	
	/*
	 * The 2 methods following are responsible for the synchronization  of the client's directory.
	 * We have 2 overloads of this method. The first is used when the client uploads a new photo with the 
	 * caption to the server. The second one is used when the client is downloading a photo from the server.
	 * 1st overload is used in the upload, while second overload is used in the download.
	 */
	
	
	/*
	 * The method takes as input the names file, the bytes of the photo, the caption and the profile.
	 * First it creates the photo file and it adds it to the client directory. Then, it checks if the client
	 * has also posted a caption with the photo ( it's not necessary ) and if this is true then it creates the caption
	 * file and adds it to the directory. Finally it updates it's profile with his new post.
	 */
	private void synchClientDirectory(String fileName, byte[] photoBytes,ArrayList<String> captionNames ,ArrayList<Byte[]> captionsBytes, byte[] profileBytes) {
		
		try {
			String dirPath = "ServerDirectory/"+this.clientID+"/"; // path of client's directory.
			FileOutputStream photo = new FileOutputStream(dirPath+fileName); // new photo file.
			photo.write(photoBytes); // write the file.
			photo.close(); // store it and close the stream.
			
			/*
			 * If caption exists then do the same for 
			 * caption.
			 */


			for(int i=0;i<captionsBytes.size();i++){
				FileOutputStream caption = new FileOutputStream(dirPath+captionNames.get(i));
				for(byte b: captionsBytes.get(i))
					caption.write(b);
				caption.close();
			}

			 //Update the profile for the user's new post.
			FileOutputStream profile = new FileOutputStream(dirPath+"Profile_998"+this.clientID+".txt");
			profile.write(profileBytes);
			profile.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	private boolean validateComment(String photo,String userToComment) {
		File directory = new File("ServerDirectory/"+userToComment);
		String[] fileNames = directory.list();
		for(String file : fileNames) {
			if(file.contains(photo) && !file.endsWith(".txt")) {
				return true;
			}
		}
		return false;
	}
	

	/*
	 * This method takes as input the photo that our user and also
	 * the name of the client from which the download occurred. Then it 
	 * copies the photo and the caption if exists (not mandatory) from this 
	 * directory to our client's directory.
	 */
	
	private void synchClientDirectory(String client,String fileName,String captionName) {

		try {
			
			/*
			 * Copy the photo file. Take the source's directory path which is the directory that contains the photo 
			 * that our user requested to download. Then take the destination path which is the directory of our client and
			 * copy the photo from source to destination.
			 */
			Path srcPath = Paths.get("ServerDirectory/"+client+"/"+fileName);
			String destPath = "ServerDirectory/"+this.clientID+"/"+fileName;
			Files.copy(srcPath,new File(destPath).toPath(),StandardCopyOption.REPLACE_EXISTING);
			
			
			// Do the same for the caption file if exists.
			File directory = new File("ServerDirectory/"+client+"/");
			String[] filesInDir = directory.list();
			boolean flag = false; // flag for checking file's existence.
			for(String s: filesInDir) {
				if(s.equals(captionName)) {
					flag = true;
					break;
				}
			}
			if(!flag) // caption does not exists just return no actions are required.
				return;
			
			//copy the caption file
			srcPath = Paths.get("ServerDirectory/"+client+"/"+captionName);
			destPath = "ServerDirectory/"+this.clientID+"/"+captionName;
			Files.copy(srcPath,new File(destPath).toPath(),StandardCopyOption.REPLACE_EXISTING);
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	
	/*
	 * This method is responsible for updating the graph according to the changes 
	 * that are getting place in the social network. Possible modifications occur when 
	 * a user follows another user, or a user unfollows another user. It is used in the
	 * follow and unfollow operations.
	 */
	private void updateGraph(boolean flag, String follower) {
		// 0 for follow update, 1 for unfollow update
		if(!flag) {
			this.graph.get(clientID).add(follower);
			// if someone followed the client we are handling the we get it's followers list and simple add him to them.
		}
		else {
			this.graph.get(follower).remove(clientID);
			// if our user unfollowed a user U then we go to the U's followers and simply delete our client from them.
		}

		/*
		 * After our graph structure is updated appropriately just loop through it's entries
		 * ( graph is modeled as a HashMap ) and write the graph file. 
		 */
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter("SocialGraph.txt", false));
			writer.close();
			writer = new BufferedWriter(new FileWriter("SocialGraph.txt", true));
			
			for(Map.Entry<String,ArrayList<String>> set : this.graph.entrySet()) {
				writer.append(set.getKey());
				for(String s : set.getValue()) {
					writer.append(" " + s);
				}
				writer.append("\n");
			}
			writer.close();
			
		} catch (IOException e) {
			
			e.printStackTrace();
		}

	}
	
	/*
	 * This method is responsible for checking if the name
	 * requested by a user during the sign up is in usage. Used in the 
	 * sing up functionality.
	 */
	private boolean checkNameAvailability(String name) {
		for(Pair<String,String> pair : this.registeredUsers) { 
			if(pair.getKey().equals(name))
				return false;
		}
		return true;
	}
}
