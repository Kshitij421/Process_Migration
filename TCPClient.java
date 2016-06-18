import java.io.*;
import java.net.*;
import java.security.KeyPairGenerator;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.PrivateKey;
import javax.crypto.Cipher;

public class Security {
  private  byte[] encrypt(byte[] inpBytes, PublicKey key,
      String xform) throws Exception {
    Cipher cipher = Cipher.getInstance(xform);
    cipher.init(Cipher.ENCRYPT_MODE, key);
    return cipher.doFinal(inpBytes);
  }
  public  byte[] decrypt(byte[] inpBytes, PrivateKey key,
      String xform) throws Exception{
    Cipher cipher = Cipher.getInstance(xform);
    cipher.init(Cipher.DECRYPT_MODE, key);
    return cipher.doFinal(inpBytes);
  }

  

	//Socket Code

  
  Socket clientSocket = new Socket("localhost", 6788);
 ObjectOutputStream outToServer = new ObjectOutputStream(clientSocket.getOutputStream());
  BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

  outToServer.writeObject(pubk);
  outToServer.flush();
   System.out.println("Public Key Sent to server");
   Thread.sleep(50);


byte[] mybytearray = new byte[256];
 InputStream is = clientSocket.getInputStream();
int bytesRead = is.read(mybytearray, 0, mybytearray.length);

 byte[] decBytes = decrypt(mybytearray, prvk, xform);
 String str= new String(decBytes);
 System.out.println("Received : "+str);
 
  clientSocket.close();




  }
}


