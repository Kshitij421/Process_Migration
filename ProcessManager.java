import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.collect.MinMaxPriorityQueue;

/**
 * 
 * @author LI FANGSHI, Mingyuan Li
 * 
 */
public class ProcessManager {
  public static final int SUCCESS = 1;
  public static final int ERROR = -1;
  public static  int MASTER_PORT = 0;
  private boolean isMaster;
  public static Server master;
  public static Server localhost;
  
  // Master
  public static ReentrantLock masterLock;
  public static MinMaxPriorityQueue<Server> masterServerList; // List of slave servers
  public static int threadId = 0; // Thread Id for next new thread
  
  // Slave
  public static ReentrantLock slaveLock;
  public static LinkedList<Thread> slaveThreadList; // List of running threads
  public static HashMap<Thread, Integer> slaveThreadIdMap;  // Mapping from Thread to thread ID
  public static HashMap<Thread, MigratableProcess> slaveThreadMPMap;  // Mapping from Thread to MigratableProcess


  /*
   * General method used to send a message and return the response
   * The server argument refers to the destination, which contains IP and Port
   */
  

  
  
  // Print messages followed by prompt
  public void printWithPrompt(String msg) {
    System.out.println("\n" + msg);
    System.out.print(">>>");
  }
  
  public static void showUsage() {
    System.out.println("Invalid input!");
    System.out.println("Usage:");
    System.out.println("\tMaster: ProcessManager");
    System.out.println("\tSlave: ProcessManager -c <MasterIP>:<MasterPort>");
  }

  /**
   * @param args
   */
  public static void main(String[] args) {

    if (args.length == 1) {
      // This is Master
      ProcessManager.MASTER_PORT=Integer.parseInt(args[0]);
      Master master = new Master();
      master.MasterRun();
    }
     else if (args.length == 2) {
      if (!args[0].equals("-c")) {
        // Invalid input
        ProcessManager.showUsage();
        System.exit(0);
      }
      
      try {
        //This is Slavemasterser
        String[] master_addr = args[1].split(":");
        Server master = new Server(master_addr[0],
            Integer.parseInt(master_addr[1]));
        int slave_port = (int) (1000 * Math.random()) + 10000;
        String slave_host = null;
        slave_host = InetAddress.getLocalHost().getHostAddress();
        System.out.println("SlaveHost:"+slave_host);
        Server slave = new Server(slave_host, slave_port);
        //ProcessManager manager = new ProcessManager(false, master, slave);
        Slave slaveobject=new Slave(master,slave);
        slaveobject.SlaveRun();
      } catch (Exception e) {
        // Invalid input
        ProcessManager.showUsage();
        System.exit(0);
      }
    } else {
      // Invalid input
      ProcessManager.showUsage();
      System.exit(0);
    }
  }  
}
