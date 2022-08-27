package socialNetwork;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;

/*
 * This is the server class.
 * This class is responsible for initializing
 * all the structures with data from the social graph
 * that will be used later. It's main task is 
 * to wait for clients to be connected and then starting a new thread for each
 * one of them in order to service multiple clients at the same time.
 */


public class Server {
	final int PORT = 5000;
	final String GRAPH_FILE = "SocialGraph.txt"; //file that contains graph info.
	final String REGISTERED_USERS_FILE = "registeredUsers.txt"; // file that contains the users of social network.
	private ServerSocket server;
	private final HashMap<String,ArrayList<String>> graph; // user,<user followers> map : representing the network's graph.
	private final ArrayList<Pair<String, String>> registeredUsers; // < user name , password > : representing user's credentials.
	private final HashMap<String, ArrayList<String>> followRequests; // key : user name , value: < user1, user2,... > map for follow requests per user.
	private final HashMap<String, ArrayList<String>> followAccepts; //	key : user name , value: < user1, user2,... > map for follow accepts per user.
	private final HashMap<String, ArrayList<String>> unfollowPendings; //key : user name , value: < user1, user2,... > map that contains the  are not committed yet for a user. 
	private final HashMap<String, ArrayList<String>> uploadNotifications; // key : user who posted, value : users that should be notified for this post.
	private final HashMap<String,ArrayList<String>> commentRequests;
	private final HashMap<String,ArrayList<String>> commentAccepts;
	//Server's constructor
	public Server() {
		this.followAccepts 			= new HashMap<String, ArrayList<String>>();
		this.followRequests 		= new HashMap<String, ArrayList<String>>();
		this.registeredUsers 		= new ArrayList<Pair<String, String>>();
		this.graph  				= new HashMap<String,ArrayList<String>>();
		this.unfollowPendings 		= new HashMap<String,ArrayList<String>>();
		this.uploadNotifications 	= new HashMap<String,ArrayList<String>>();
		this.commentRequests		= new HashMap<String,ArrayList<String>>();
		this.commentAccepts			= new HashMap<String,ArrayList<String>>();
		this.initRegisteredUsers(REGISTERED_USERS_FILE);
		this.initUsersDirectories();
		this.initFollowRequestsBuffers();
		this.initFollowAcceptsBuffers();
		this.initUnfollowPendingsBuffers();
		this.initUploadNotifications();
		this.initCommentRequests();
		this.initCommentAccepts();
		try {
			System.out.println("Server started");
			this.server = new ServerSocket(PORT);
			this.initGraph(GRAPH_FILE);
			this.acceptConnections();
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * This method reads the file that contains the 
	 * graph info and initialize the graph 
	 */
	private void initGraph(String fileName) {
		try {
			BufferedReader reader = new BufferedReader(new FileReader(fileName));
			String line = reader.readLine();
			while(line!=null) {
				String[] s = line.split("\\s+");
				String name = s[0];
				ArrayList<String> followers = new ArrayList<>();
				
				for(int i=1;i<s.length;i++) {
					followers.add(s[i]);
				}
					
				this.graph.put(name, followers);
				line = reader.readLine();
			}
			reader.close();
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * This method initialize the map for 
	 * upload notification (see the map above).
	 */
	private void initUploadNotifications() {
		for(Pair<String,String> user: this.registeredUsers) {
			this.uploadNotifications.put(user.getKey(), new ArrayList<String>());
		}
	}

	private void initCommentAccepts() {
		for(Pair<String,String> user: this.registeredUsers) {
			this.commentAccepts.put(user.getKey(), new ArrayList<String>());
		}
	}
	
	/*
	 * This method reads the file with register users and 
	 * initialize the Array list that contains them.
	 */
	private void initRegisteredUsers(String fileName) {
		try{
			BufferedReader reader = new BufferedReader(new FileReader(fileName));
			String line;
			line = reader.readLine();
			while(line!=null) {
				String[] s = line.split("\\s+");
				String name = s[0];
				String password = s[1];
				this.registeredUsers.add(new Pair<String,String>(name,password));
				line = reader.readLine();
			}
			reader.close();
		} catch (IOException e) {
			
			e.printStackTrace();
		}
	}
	
	
	/*
	 * This method creates a directory for each user that holds their pictures,
	 * their captions and their profile page.
	 */
	private void initUsersDirectories()  {
		for(Pair<String,String> user: this.registeredUsers) {
			String dirPath = "ServerDirectory/";
			File directory = new File(dirPath+user.getKey());
			directory.mkdir();
			File clientProfile = new File("ServerDirectory/"+user.getKey()+"/"+"Profile_998"+user.getKey()+".txt");
			File othersProfile = new File("ServerDirectory/"+user.getKey()+"/"+"Others_998"+user.getKey()+".txt");
			try {
				clientProfile.createNewFile();
				othersProfile.createNewFile();
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	
	/*
	 * The 3 methods following initialize the maps that are used for
	 * follow and unfollow implementation.
	 */
	private void initFollowRequestsBuffers() {
		for(Pair<String,String> user: this.registeredUsers) {
			this.followRequests.put(user.getKey(), new ArrayList<String>());
		}
	}

	private void initCommentRequests() {
		for(Pair<String,String> user: this.registeredUsers) {
			this.commentRequests.put(user.getKey(), new ArrayList<String>());
		}
	}

	private void initFollowAcceptsBuffers() {
		for(Pair<String,String> user: this.registeredUsers) {
			this.followAccepts.put(user.getKey(), new ArrayList<String>());
		}
	}

	private void initUnfollowPendingsBuffers() {
		for(Pair<String,String> user: this.registeredUsers) {
			this.unfollowPendings.put(user.getKey(), new ArrayList<String>());
		}
	}

	/*
	 * This method waits until a new request from a client has occurred. After 
	 * receiving the request, it creates a new instance of the class Client Handler which
	 * is responsible for handling the clients and starts a new thread with this instance. This
	 * makes it possible for us to service multiple clients at the same time.
	 */
	private void acceptConnections() {
		while(!this.server.isClosed()) {
			try {
				Socket connection = this.server.accept();
				ClientHandler client = new ClientHandler(connection, this.registeredUsers,this.graph, this.followRequests,this.followAccepts,this.unfollowPendings, this.uploadNotifications,this.commentRequests,this.commentAccepts);
				Thread t = new Thread(client);
				t.start();
			} 
			catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	//Server's entry point.
	public static void main(String args[]) {
		Server s = new Server();
	}

}
