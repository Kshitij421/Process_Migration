import java.io.*;
import java.net.*;
import java.security.KeyPairGenerator;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.PrivateKey;
import javax.crypto.Cipher;


public class TCPServer {
  private static byte[] encrypt(byte[] inpBytes, PublicKey key,
      String xform) throws Exception {
    Cipher cipher = Cipher.getInstance(xform);
    cipher.init(Cipher.ENCRYPT_MODE, key);
    return cipher.doFinal(inpBytes);
  }
  private static byte[] decrypt(byte[] inpBytes, PrivateKey key,
      String xform) throws Exception{
    Cipher cipher = Cipher.getInstance(xform);
    cipher.init(Cipher.DECRYPT_MODE, key);
    return cipher.doFinal(inpBytes);
  }

  public static void main(String[] args) throws Exception {

	if (args.length != 1) {
            System.out.println("Usage: GenSig nameOfFileToSign");
            }
	else
	{

        ServerSocket welcomeSocket = new ServerSocket(6788);
 	String xform = "RSA/ECB/PKCS1PADDING";
	PublicKey cpub;
	
	 while(true)
         {
	     File myFile = new File(args[0]);
            Socket connectionSocket = welcomeSocket.accept();

	    ObjectInputStream inFromClient = new ObjectInputStream(connectionSocket.getInputStream());

	    Object obj = inFromClient.readObject();
	    cpub=(PublicKey)obj;
            System.out.println("Received public key");
              Thread.sleep(10);

	    byte[] dataBytes = new byte[(int) myFile.length()];
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(myFile));
            bis.read(dataBytes, 0, dataBytes.length);

	   
   	   byte[] encBytes = encrypt(dataBytes, cpub, xform);
	
	   OutputStream os = connectionSocket.getOutputStream();
      	   os.write(encBytes, 0, encBytes.length);
           os.flush();
	 
	  System.out.println("Sent Successfully");
         
         }
  	
	}
  }
}

  


