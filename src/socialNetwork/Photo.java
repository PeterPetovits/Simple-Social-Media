package socialNetwork;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/*
 * The class Photo contains all the necessary information for a photo,
 * like the name and the content of a photo
 */
public class Photo {
    
    private byte[] photoBytes;		//this variable contains all the data of a photo stored in a byte array
    private String photoName;

    //Constructor for the Photo class. It contains the photo name and initiates the byte array
    //(later filled by the method readPhotoBytes)
    public Photo(String photoName) {
        this.photoName = photoName;
        this.photoBytes = null; 
    }

    //this method reads a specific photo and stores it's bytes to a byte array
    public void readPhotoBytes(String photoName) {
        File photo = new File(photoName);
        try {
            FileInputStream reader = new FileInputStream(photo);
            this.photoBytes = new byte[(int)photo.length()];
            reader.read(photoBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
