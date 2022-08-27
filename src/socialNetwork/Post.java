package socialNetwork;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;


/* 
* This class contains all the necessary informations for a post sent 
* by the client to the server.
*/
public class Post {
    
    private String postID;				//unique number generated for each post
    private String clientID;			//username

    private String photoCaption1;		//caption of a post/photo

    private String photoCaption2;
    private String photoName;			//the name of a photo
    private Date date;					//the date when a post is posted
    
    //Constructor for posts that have a caption included
    public Post(String postID, String clientID, String photoName,String photoCaption1, String photoCaption2, Date date) {
        this.postID = postID;
        this.clientID = clientID;
        this.photoName = photoName;
        this.photoCaption1 = readPhotoCaption(photoCaption1);
        this.photoCaption2 = readPhotoCaption(photoCaption2);
        this.date = date;
    }



    //Constructor for posts that do not have a caption included
    public Post(String postID, String clientID, String photoName, Date date) {
        this.postID = postID;
        this.clientID = clientID;
        this.photoName = photoName;
        this.photoCaption1 = "";
        this.photoCaption2 = "";
        this.date = date;
    }
    
    //this method is used to read the caption of a photo that is located to client's directory
    private String readPhotoCaption(String photoCaptionName) {
    	if(photoCaptionName.equals(""))
            return "";

        String dirPath = "ClientDirectory/"+this.clientID+"/";
    	BufferedReader reader;
    	String caption = "";
        try{
        	reader = new BufferedReader(new FileReader(dirPath+photoCaptionName));
        	String line = reader.readLine();
        	while(line!=null) {
        		caption+=line;
        		line = reader.readLine();
        	}
        }
        catch(IOException e) {
        	e.printStackTrace();
        }
    	return caption;
    }
    
    //this method is used to properly display each post posted on user's profile
    public String toString() {
    	SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        if(!photoCaption2.equals("")) {
            return "\n\n" + this.clientID + " posted\n" + this.photoCaption1 + "\n" + this.photoCaption2 + "\n" + this.photoName + "\n" + formatter.format(this.date) + "\n"
                    + this.postID;
        }
        else{
            return "\n\n" + this.clientID + " posted\n" + this.photoCaption1 + "\n"+ this.photoName + "\n" + formatter.format(this.date) + "\n"
                    + this.postID;
        }
    }


}
