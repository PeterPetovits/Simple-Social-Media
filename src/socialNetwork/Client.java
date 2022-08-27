package socialNetwork;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;


public class Client {

	private Socket socket;
	private ObjectInputStream 	in = null;
	private ObjectOutputStream out = null;
	private String clientID;//clients ID and username
	private ArrayList<String> following;//list to store the user's the client is following
	private ArrayList<String> followers;//list to store the user's that follow the client
	private final ArrayBlockingQueue<Message> incMessages;//list used for storing each incoming Message(see Listener Class)
	private final ArrayList<String> followRequests;//list to store all the followRequests so that the user can interact with them(accept/reject them)
	private final ArrayList<String> CommentRequestsForClient;

	private Listener incomingRequestsHandler;
	
	public Client() {
		this.followRequests = new ArrayList<String>();
		this.following = new ArrayList<>();
		this.followers = new ArrayList<>();
		this.incMessages = new ArrayBlockingQueue<>(5);
		this.CommentRequestsForClient = new ArrayList<>();
		this.connect();
		this.initListener();
	}
	
	/*
	 Used to start the listener thread which is used to handle certain input messages and store all incoming Messages in the incMessages ArrayBlockingQueue
	*/
	private void initListener() {
		incomingRequestsHandler = new Listener(this.in, this.incMessages, this.followRequests, this.following, this.followers,this.CommentRequestsForClient);
		Thread listenerThread = new Thread(incomingRequestsHandler);
		listenerThread.start();
	}
	
	/*
	 Notifier class used as a "timer" in order to check if we have a notification every 2s.
	*/
	private void initNotifier() {
		Notifier notifyClientHandler = new Notifier(this.out, this.clientID);
		Thread notifyThread = new Thread(notifyClientHandler);
		notifyThread.start();
	}
	
	private void connect() {
		try {
			this.socket = new Socket("localhost",5000);
			this.out 	= new ObjectOutputStream(this.socket.getOutputStream());
			this.in 	= new ObjectInputStream(this.socket.getInputStream());
			
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/*
	 We ask from the user to give us his username and password and then send them to the server to store the information and sign up the user
	 (if the username is already in used then the sign up is failed)
	*/
	private void signUp() {
		Scanner input = new Scanner(System.in);
		while(true) {
			System.out.println("Select a username");
			String userName = input.nextLine();
			System.out.println("Select a password");
			String password = input.nextLine();
			Message msg = new Message("Sign up", password, userName);
			try {
				this.out.writeObject(msg);
				this.out.flush();
				Message m = this.incMessages.take();
				while(true) {
					if(m.getHeader().equals("Sing up reply"))
						break;
					this.incMessages.add(m);
					m = this.incMessages.take();
				}
				if(m.getData().contains("Sing up failed")) {
					System.out.println(m.getData());
				}
				else {
					System.out.println(m.getData());
					break;
				}		
			} 
			catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void login() {
		Scanner input = new Scanner(System.in);
		while(true) {
			System.out.println("Give your username");
			String userName = input.nextLine();
			System.out.println("Give your password");
			String password = input.nextLine();
			Message msg = new Message("Login", password, userName);
			try {
				this.out.writeObject(msg);
				this.out.flush();
				Message m = this.incMessages.take();
				
				while(true) {
					if(m.getHeader().equals("Login success") || m.getHeader().equals("Login failed"))
						break;
					this.incMessages.add(m);
					m = this.incMessages.take();
				}
				
				if(m.getHeader().equals("Login success")) {
					setClientID(userName);
					this.initNotifier();
					System.out.println(m.getData());
					break;
				}
				else {
					System.out.println(m.getData());
				}
			}
			catch (IOException |  InterruptedException e) {
				e.printStackTrace();
			}
		}
		
	}
	
	public String getClientID() {
		return this.clientID;
	}
	
	public void setClientID(String clientID) {
		this.clientID = clientID;
	}

	/*
	 This is used to get our followers list from the Server after we login.(since the Server is initialized using the SocialGraph.txt file)
	*/
	private void initFollowers() {
		try {
			this.out.writeObject(new Message("Get followers","",this.clientID));
			this.out.flush();

			Message reply = this.incMessages.take();
			while(true) {
				if(reply.getHeader().equals("Followers reply"))
					break;
				this.incMessages.add(reply);
				reply = this.incMessages.take();
			}
			
			String checkEmptyFollowersReply = reply.getData();
			if(checkEmptyFollowersReply.equals(""))
				return;
			String[] followers = reply.getData().split("\\s+");
			for(String follower : followers) {
				this.followers.add(follower);
			}

		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	/*
	 This is used to get our following list from the Server after we login.(since the Server is initialized using the SocialGraph.txt file)
	*/
	private void initFollowing() {
		try {
			this.out.writeObject(new Message("Get following","",clientID));
			this.out.flush();

			Message reply = this.incMessages.take();
			while(true) {
				if(reply.getHeader().equals("Following reply"))
					break;
				this.incMessages.add(reply);
				reply = this.incMessages.take();
			}
			String checkEmptyFollowingReply = reply.getData();
			if(checkEmptyFollowingReply.equals(""))
				return;
			String[] following = reply.getData().split("\\s+");
			for(String follow : following) {
				this.following.add(follow);
			}

		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	//We initialize our Client directory where all the files we post are stored
	private void initDirectory() throws IOException {
		File directory = new File("ClientDirectory/"+this.clientID);
		directory.mkdir();
		File clientProfile = new File("ClientDirectory/"+this.clientID+"/"+"Profile_998"+this.clientID+".txt");
		File othersProfile = new File("ClientDirectory/"+this.clientID+"/"+"Others_998"+this.clientID+".txt");
		clientProfile.createNewFile();
		othersProfile.createNewFile();
	}

	/*
	 This method works as follows:
	 	-We get all the users signed in that we do not already follow (Pull Users Message)
		-We select which user we want to follow
		-Then we send the follow request to the server so that it can be sent to the appropriate user
	*/
	private void follow() {
		try {
			this.out.writeObject(new Message("Pull Users","",clientID));
			this.out.flush();

			Message reply = this.incMessages.take();
			while(true) {
				if(reply.getHeader().equals("Pull Users reply"))
					break;
				this.incMessages.add(reply);
				reply = this.incMessages.take();
			}
			
			String[] userList = reply.getData().split("\\s+");
			System.out.println("People you might know! : ");
			for(String user : userList)
				System.out.println(user);

			System.out.println("Select someone to follow or type back to return");
			Scanner input = new Scanner(System.in);
			String userToFollow = input.nextLine();
			if(userToFollow.equals("back"))
				return;
			else {
				Message msg = new Message("Follow request",userToFollow,clientID);
				this.out.writeObject(msg);
				this.out.flush();
			}
		}
		catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	/*
	 We print all the users we follow then we get the name of the user we want to unfollow we remove him from our following list and then
	 we send an Unfollow message to the server so that he can also remove the user from our followings list and send him notification we unfollowed him. 
	*/
	private void unfollow() {
		System.out.println("Who do you want to unfollow?");
		displayFollowing();

		Scanner scanner = new Scanner(System.in);
		String toUnfollow = scanner.nextLine();
		Message m = new Message("Unfollow", toUnfollow, this.clientID);
		this.following.remove(toUnfollow);
		try {
			this.out.writeObject(m);
			this.out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/*
	 The method works as follows:
	 	-First we output all the files in the repository folder which stores all the photo files we have.
		-We get the name of the file to upload 
		-We copy that file to our folder in the ClientDirectory 
		-We get the caption for the file(not obligatory)
		-We update our Profile with this new post(we append the ProfileXclientID.txt file and write the new post)
		-We synchronize our ClientDirectory with our directory in the ServerDirectory sending the specified photo file, the caption file, and our Profile file
	*/
	private void uploadAndSync() throws IOException {
		//Display repository and upload a file to the client directory.
		File repository = new File("Repository/");
		File[] reposFiles = repository.listFiles();
		for(File file: reposFiles)
			System.out.println(file.getName());
		Scanner input = new Scanner(System.in);
		
		System.out.println("Select file to upload");
		String fileName = input.nextLine();
		Path srcPath = Paths.get("Repository/"+fileName);
		String destPath = "ClientDirectory/"+this.clientID+"/"+fileName;
		Files.copy(srcPath,new File(destPath).toPath(),StandardCopyOption.REPLACE_EXISTING);

		String fileNameWithoutExtension = fileName.split("\\.")[0];

		System.out.println("Choose the language you want to give the caption : ENG / GR");
		String language;
		while(true) {
			language = input.nextLine();
			if(language.equals("ENG") || language.equals("GR"))
				break;
			System.out.println("Wrong Input! Please type ENG or GR depending on your choice");
		}

		String[] captions = {"",""};
		String[] captionFiles = {"",""};


		System.out.println("Give caption for the file");
		captions[0] = input.nextLine();
		
		if(!captions[0].equals("")) {
			String captionFilePath = "ClientDirectory/"+this.clientID+"/";
			PrintWriter out = new PrintWriter(captionFilePath+fileNameWithoutExtension+"_"+language+".txt");
			out.println(captions[0]);
			out.close();
			captionFiles[0] = fileNameWithoutExtension+"_"+language+".txt";
		}

		language = "ENGGR".replace(language,"");

		System.out.println("Do you want to give a caption to " + language + " as well? : Y or N ");
		String answer;
		while(true) {
			answer = input.nextLine();
			if(answer.equals("Y")) {
				System.out.println("Give caption for the file");
				captions[1] = input.nextLine();
			
				if(!captions[1].equals("")) {
					String captionFilePath = "ClientDirectory/"+this.clientID+"/";
					PrintWriter out = new PrintWriter(captionFilePath+fileNameWithoutExtension+"_"+language+".txt");
					out.println(captions[1]);
					out.close();
					captionFiles[1] = fileNameWithoutExtension+"_"+language+".txt";
				}
				break;
			}
			else if(answer.equals("N")) {
				break;
			}
			else {
				System.out.println("Wrong choice! Please choose Y or N");
			}
		}
		
		//upload the post to our local page
		String dirPath = "ClientDirectory/"+this.clientID+"/";
		Random rand = new Random();
		String postID = String.valueOf(rand.nextInt(1000000));
		
		Post post = null;

		post = new Post("ID"+postID, this.clientID, fileNameWithoutExtension, captionFiles[0],captionFiles[1],new Date());


		FileWriter fw 		= new FileWriter(dirPath+"Profile_998"+this.clientID+".txt",true);
		BufferedWriter bw 	= new BufferedWriter(fw);
		bw.append(post.toString());
		bw.close();
	
		
		//synchronize direcotry to server
		Message synch = new Message("Synchronization", fileName, this.clientID);
		this.out.writeObject(synch);
		
		DataOutputStream dos = new DataOutputStream(this.socket.getOutputStream());
		
		File photoToSynch = new File(dirPath+fileName);
		FileInputStream fis = new FileInputStream(photoToSynch);
		byte[] data = new byte[(int)photoToSynch.length()];
		fis.read(data);
		dos.writeInt(data.length);
		dos.write(data);
		
		File captionToSynch = null;

		for(int i=0;i<2;i++) {
			if (!captionFiles[i].equals("")) {
				captionToSynch = new File(dirPath + captionFiles[i]);
				fis = new FileInputStream(captionToSynch);
				data = new byte[(int) captionToSynch.length()];
				fis.read(data);
				dos.writeUTF(captionFiles[i]);
				dos.writeInt(data.length);
				dos.write(data);
			} else {
				dos.writeUTF("");
				dos.writeInt(0);
			}
		}

		File profileToSynch = new File(dirPath+"Profile_998"+this.clientID+".txt");
		fis = new FileInputStream(profileToSynch);
		data = new byte[(int)profileToSynch.length()];
		fis.read(data);
		fis.close();
		dos.writeInt(data.length);
		dos.write(data);
	}


	private void otherPosts(){
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader("ClientDirectory/"+this.clientID+"/Others_998"+this.clientID+".txt"));
			String line;
			String feed = "\n";
			line = reader.readLine();

			while(line!=null) {
				feed += line;
				feed += "\n";
				line = reader.readLine();
			}
			reader.close();
			System.out.println(feed);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/*
	 We get the name of the profile we want to access and then send a Message to the Server
	 If we are following this profile we get an Accept Profile answer and the requested profile is printed in the screen(The requested users Profile file)
	 If we are not following this profile we get a Deny Profile answer and a prompt is printed in the screen
	*/
	private void accessProfile() {
		Scanner scanner = new Scanner(System.in);
		System.out.println("Which user's profile do you want to access?");
		String clientToAccess = scanner.nextLine();

		try {
			Message m = new Message("Access Profile",clientToAccess,clientID);
			this.out.writeObject(m);
			this.out.flush();

			Message profileToAcess = this.incMessages.take();

			while(true) {
				if(profileToAcess.getHeader().equals("Accept Profile") || profileToAcess.getHeader().equals("Deny Profile"))
					break;
				this.incMessages.add(profileToAcess);
				profileToAcess = this.incMessages.take();
			}
			
			if(profileToAcess.getHeader().equals("Accept Profile")) {
				String posts = profileToAcess.getData();
				System.out.println(posts);
			}
			else {
				if(profileToAcess.getData().length() > 1) {
					System.out.println(profileToAcess.getHeader());
					System.out.println(profileToAcess.getData());
				}
				else {
					System.out.println(profileToAcess.getHeader());
				}
				
			}
		}
		catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}

	}

	/*
	 This method works as follows:
	 	-We get the name of the photo the user wants to search
		-We send a Search message to the Server
		-We get the name(s) of the user(s) we follow that have this photo
		-This method returns a String array where(Used in the download method):
			-The first block holds the user(s) names that have the photo
			-The second block holds the name of the photo we searched(without the file extension)
			-The third block holds the name of the photo we searched(with the file extension)
	*/
	private String[] search() {
		Scanner scanner = new Scanner(System.in);
		System.out.println("Which photo would you like to search?");
		String photoName = scanner.nextLine();

		System.out.println("In which language would you like to receive the caption");
		String captionLanguage = scanner.nextLine();

		try {
			
			Message m = new Message("Search",photoName+","+captionLanguage,clientID);
			
			this.out.writeObject(m);
			this.out.flush();

			Message reply = this.incMessages.take();
			
			while(true) {
				if(reply.getHeader().equals("Search reply"))
					break;
				this.incMessages.add(reply);
				reply = incMessages.take();
			}

			String[] clientsWithSamePhoto = reply.getData().split("\\s+");

			if(reply.getData().equals("")) {
				System.out.println("There are no clients with this photo");
				return null;
			}
			else {
				for(String s : clientsWithSamePhoto) {
					System.out.println(s);
				}
			}

			String[] tmp = {reply.getData(),photoName, reply.getSender()}; //getSender() here is the file name.
			return tmp;

		}
		catch(IOException | InterruptedException e) {
			e.printStackTrace();
			return null;
		}
	}

	/*
	 This method works as follows:
	 	-We first use the search method to learn the photo name the user wants to download and which user(s) has this photo
		-Secondly we execute the 3-way handshake sending a Download Request Message first,then receiving a Download accept Message 
		 and lastly sending the Download Message which holds the photo name and the name of the user which has the photo
		-Then we execute the Stop and Wait protocol sending an ACK when receiving a chunk of the file.(we delay sending the 6th ACK as explained in the Instructions given)
		-We receive the caption file 
		-We store them in our ClientDirectory 

	*/
	private void download() {
		String[] data = search();

		if(data == null) {
			return;
		}

		String tmp = data[0];
		String photoName = data[1];
		String captionName = data[2];
		String[] clientsWithPhoto = tmp.split("\\s+");
		Message toDownload = null;

		try {
			
			if(clientsWithPhoto.length == 1) {
	
				Message m = new Message("Download request", "", "");
				this.out.writeObject(m);
				this.out.flush();

				Message downloadAnswer = this.incMessages.take();
				while(true) {
					if(downloadAnswer.getHeader().equals("Download accept")) 
						break;
					this.incMessages.add(downloadAnswer);
					downloadAnswer = this.incMessages.take();
				}
				toDownload = new Message("Download", captionName, clientsWithPhoto[0]);
			}
			
			else {
				Random rand = new Random();
				int randomClient = rand.nextInt(clientsWithPhoto.length-1);

				Message m = new Message("Download request", "", "");
				this.out.writeObject(m);
				this.out.flush();

				Message downloadAnswer = this.incMessages.take();
				while(true) {
					if(downloadAnswer.getHeader().equals("Download accept")) 
						break;
					this.incMessages.add(downloadAnswer);
					downloadAnswer = this.incMessages.take();
				}

				toDownload = new Message("Download", captionName, clientsWithPhoto[randomClient]);
			}
			
			this.out.writeObject(toDownload);
			this.out.flush();

			int seq = 0;
			int received = 0;
			ArrayList<Byte[]> chunks = new ArrayList<>();
			while(received < 10) {
				Message chunk = this.incMessages.take();
				while(true) {
					if(chunk.getHeader().equals("File Chunk")) 
						break;
					this.incMessages.add(chunk);
					chunk = this.incMessages.take();
				}
				System.out.println("Received packet " + received + " with sequence " + chunk.getSequence());
				if(chunk.getSequence() == seq) {
					Message ack = new Message("Ack", seq);
					this.out.writeObject(ack);
					this.out.flush();
					Thread.sleep(1000);
					System.out.println("Sent ack " + seq);
					seq = (seq + 1) % 4;
					received++;
				}
				else {
					Message ack = new Message("Ack", received);
					this.out.writeObject(ack);
					this.out.flush();
					System.out.println("Received out of order packet " + received);
				}
			}
			
			byte[] downloadFileBytes = this.mergeToFile(chunks);
			System.out.println(data[2]);
			File downloadedFile = new File("ClientDirectory/"+this.clientID+"/"+photoName+".jpg");
			FileOutputStream fos = new FileOutputStream(downloadedFile);
			fos.write(downloadFileBytes);
			fos.close();
			
			
			Message caption = this.incMessages.take();
			while(true) {
				if(caption.getHeader().equals("Caption Download")) 
					break;
				this.incMessages.add(caption);
				caption = this.incMessages.take();
			}
			if(caption.getData().equals("Caption for this photo does not exist"))
				System.out.println(caption.getData());
			else {
				File captionFile = new File("ClientDirectory/"+this.clientID+"/"+caption.getSender());
				FileWriter fw = new FileWriter(captionFile);
				fw.write(caption.getData());
				fw.close();
			}
			
			System.out.println("The transmission is completed");
			
		}
		catch(IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	/*
	 This method is used to merge all the chunks received from the download method in a complete file
	*/
	private byte[] mergeToFile(ArrayList<Byte[]> chunks) {
		ArrayList<Byte> bytes = new ArrayList<Byte>();
		for(Byte[] chunk: chunks) {
			for(Byte b: chunk)
				bytes.add(b);
		}
		byte[] retBytes = new byte[bytes.size()];
		int i = 0;
		for(Byte b: bytes) {
			retBytes[i] = b.byteValue();
			i++;
		}
		return retBytes;
	}
	

	private void displayFollowers() {
		System.out.println("Followers: "+this.followers.size());
		for(String s: this.followers) {
			System.out.println(s);
		}
	}
	
	private void displayFollowing() {
		System.out.println("Following: "+this.following.size());
		for(String s: this.following) {
			System.out.println(s);
		}
	}
	
	/*
	 This method is used to handle all pending follow requests
	 We print all the names of the users requesting to follow us
	 And then the user can:
	 	-Type accept <<client name>> to accept the follow request(a follow request accept message is sent to the server to execute the appropriate operations)
		-Type reject <<client name>> to reject the follow request
		-Type back to exit the follow requests()
	 When a follow request is accepted/rejected we remove it from the list
	*/
	private void handleFollowRequests() {
		Scanner input = new Scanner(System.in);
		System.out.println("Press accept plus name to accept a request for every name");
		System.out.println("Press reject plus name to reject a request for every name");
		System.out.println("Press back to return back to menu");
		System.out.println("");
		System.out.println("Your follow requests: ");
		for(String req: this.followRequests) {
			System.out.println("request from: "+req);
		}
		
		String choice = input.nextLine();
		while(!choice.equals("back")) {
			String[] str = choice.split("\\s+");
			if(str[0].equals("reject"))
				this.followRequests.remove(str[1]);
			else if(str[0].equals("accept")){
				this.followRequests.remove(str[1]);
				this.followers.add(str[1]);
				Message m = new Message("Follow request accept", str[1], this.clientID);
				try {
					this.out.writeObject(m);
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
			choice = input.nextLine();
		}
	}

	private void comment() {
		Scanner input = new Scanner(System.in);

		System.out.println("Which photo would you like to comment?");
		String photo = input.nextLine();
		System.out.println("Who is the owner of this photo?");
		String userToComment = input.nextLine();
		System.out.println("Write your comment:");
		String comment = input.nextLine();
		askComment(photo,userToComment,comment);

	}


	private void askComment(String photo,String userToComment,String comment) {
		try {
			String data = photo+"|"+userToComment+"|"+comment;
			Message ask = new Message("Ask Comment",data,this.clientID);
			this.out.writeObject(ask);
			this.out.flush();

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void handleCommentRequests() {
		ArrayList<String> comments = new ArrayList<>();
		ArrayList<String> photos = new ArrayList<>();
		ArrayList<String> commenter = new ArrayList<>();

		for(int i = 0; i < this.CommentRequestsForClient.size(); i++) {
			String[] tmp = this.CommentRequestsForClient.get(i).split("`");
			for(String s : tmp) {
				String[] str = s.split("\\|");
				System.out.println(i+". "+ str[2] + " commented: "+"\n"+str[1]+"\n"+ "at your " + str[0] + " photo\n");
				comments.add(str[1]);
				photos.add(str[0]);
				commenter.add(str[2]);
			}
		}

		Scanner input = new Scanner(System.in);
		ArrayList<Integer> commentsToAccept = new ArrayList<>();
		System.out.println("Type the numbers corresponding to the comments you want to approve(leave a blank space between each number or type back to return to the menu)");
		String[] numbers = input.nextLine().split(" ");
		for(String s : numbers) {
			if(s.equalsIgnoreCase("back"))
				return;
			commentsToAccept.add(Integer.parseInt(s));
		}
		this.CommentRequestsForClient.clear();
		for(int i = 0; i < comments.size(); i++) {
			if(commentsToAccept.contains(i)) {
				Message approve = new Message("Approve Comment",photos.get(i)+"|"+comments.get(i)+"|"+commenter.get(i),this.clientID);
				try {
					this.out.writeObject(approve);
					this.out.flush();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
			else {
				Message deny = new Message ("Deny Comment",photos.get(i)+"|"+comments.get(i)+"|"+commenter.get(i),this.clientID);
				try {
					this.out.writeObject(deny);
					this.out.flush();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}

	}

	//This method is used to make the logging out of the user smooth.
	private void logOut() {
		try {
			this.out.writeObject(new Message("Log Out","",""));
			this.out.flush();
			this.socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	public static void main(String[] args) throws IOException {
		Client c = new Client();
			
		Scanner input = new Scanner(System.in);
		while(true) {
			System.out.println("1.Sign Up");
			System.out.println("2.Login");
			
			int choice = input.nextInt();
			input.nextLine();
			
			if(choice == 1) {
				c.signUp();
			}
			else if(choice == 2) {
				c.login();
				break;
			}
		}
		//Logged in
		c.incomingRequestsHandler.setClientID(c.clientID);
		c.initFollowers();
		c.initFollowing();
		c.initDirectory();
		while(true) {
			System.out.println("Press 1 to follow new users");
			System.out.println("Press 2 to unfollow a user");
			System.out.println("Press 3 to view your following list");
			System.out.println("Press 4 to view your followers list");
			System.out.println("Press 5 to view your follow requests");
			System.out.println("Press 6 to upload a new post");
			System.out.println("Press 7 to access a profile");
			System.out.println("Press 8 to search for a photo");
			System.out.println("Press 9 to download a photo");
			System.out.println("Press 10 to view your feed");
			System.out.println("Press 11 to comment a post");
			System.out.println("Press 12 to view your comment requests");
			System.out.println("Press 13 to log out");
			String choice = input.nextLine();
			if(choice.equals("1"))
				c.follow();
			if(choice.equals("2"))
				c.unfollow();
			if(choice.equals("3"))
				c.displayFollowing();
			if(choice.equals("4"))
				c.displayFollowers();
			if(choice.equals("5"))
				c.handleFollowRequests();
			if(choice.equals("6"))
				c.uploadAndSync();
			if(choice.equals("7"))
				c.accessProfile();
			if(choice.equals("8"))
				c.search();
			if(choice.equals("9"))
				c.download();
			if(choice.equals("10"))
				c.otherPosts();
			if(choice.equals("11"))
				c.comment();
			if(choice.equals("12"))
				c.handleCommentRequests();
			if(choice.equals("13")) {
				c.logOut();
				break;
			}
		}
	}
}
