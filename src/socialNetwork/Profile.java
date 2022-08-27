package socialNetwork;

import java.util.ArrayList;

/*
 * The class Profile is the profile of each client. 
 * It contains the username of a client all the posts.
 */

public class Profile {

    private ArrayList<Post> posts;		//List with all the posts
    private String clientID;			//username
    
    //Constructor of class Profile. It has the client id a.k.a username and also initiates the list with all the posts
    public Profile(String clientID) {
        this.posts = new ArrayList<>();
        this.clientID = clientID;
    }

    //returns the list with the posts
    public ArrayList<Post> getPosts() {
        return this.posts;
    }

    //return the client's id/username
    public String getlientID() {
        return this.clientID;
    }
}
