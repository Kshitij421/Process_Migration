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
public class Master{
	
	public Master(){
		ProcessManager.masterServerList = MinMaxPriorityQueue.<Server>create();
      	ProcessManager.masterLock = new ReentrantLock();
	}

	public void MasterRun() {
    SocketListener listener = new SocketListener(ProcessManager.MASTER_PORT);
    listener.start();

    System.out.println("This is Master!");
    Timer timer = new Timer();
    timer.schedule(new LoadBalanceTimer(), 10000, 10000);

    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    while (true) {
      String input = null;
      MigratableProcess process = null;
      
      try {
        System.out.print(">>>");
        input = reader.readLine();
        System.out.println("Input Aceepted");

        if (input.equals("")) {
          continue;
        }
        else if (input.equals("ps")) {
          this.debug();
        }
        else if (input.equals("quit")) {
          this.quit();
        }
        
        String[] inputStrings = input.split(" ", 2);
        String[] threadArgs = null;
        if(inputStrings.length > 1) {
          // Thread has arguments
          threadArgs = inputStrings[1].split(" ");
        }
        System.out.println("File Encrypted here\n");
        
        @SuppressWarnings("unchecked")
        Class<MigratableProcess> processClass = (Class<MigratableProcess>)(Class.forName(inputStrings[0]));
        Constructor<?>[] processConstructor = processClass.getConstructors();
        if(inputStrings.length == 1) {
          // Thread has no argument
          process = (MigratableProcess)processConstructor[0].newInstance();
        } else {
          // Thread has arguments
          process = (MigratableProcess)processConstructor[0].newInstance(new Object[]{threadArgs});
        }
      } catch (Exception e) {
        //e.printStackTrace();
        System.out.println("Invalid input!");
        continue;
      }
      ProcessManager.threadId++;
      Server server = addThreadToSlave(ProcessManager.threadId);
      if (server == null) {
        System.out.println("No Slave found!");
        ProcessManager.threadId--;
        continue;
      }
      Message msg = new Message(MessageType.MsgProcessStart, (Object)process, (Object)ProcessManager.threadId);
      if(sendMessage(server, msg) == null) {
        System.out.println("Failed to Send MsgProcessStart Message!");
      }
    }
  }

  public Server addThreadToSlave(int tid) {
    Server server = null;
    ProcessManager.masterLock.lock();
    try {
      // Remove slave Server and add it back to keep MinMaxPriorityQueue working
      server = ProcessManager.masterServerList.pollLast();
      if(server == null) {
        return null;
      }
      server.getThreadSet().add(tid);
      ProcessManager.masterServerList.offer(server);
    } finally {
      ProcessManager.masterLock.unlock();
    }
    return server;
  }

  public Message sendMessage(Server server, Message msg) {
    try {
      Socket socket = new Socket(server.getIP(), server.getPort());
      ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
      out.writeObject(msg);
      out.flush();
      ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
      Message response = (Message)in.readObject();
      out.close();
      in.close();      
      socket.close();
      return response;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }
  

  private void debug() {
    ProcessManager.masterLock.lock();
    try {
      System.out.println("SlaveIP\t\tSlavePort\tThreadCount");
      System.out.println("-------------------------------------------");
      for (Server server :ProcessManager. masterServerList) {
        System.out.println(server.getIP() + "\t" + server.getPort() + "\t\t" + server.getThreadSet().size());
        System.out.print("ThreadID: ");
        for (int i : server.getThreadSet()) {
          System.out.print(i + " ");
        }
        System.out.println("\n-------------------------------------------");
      }
    } finally {
      ProcessManager.masterLock.unlock();
    }
  }

  private void quit() {
    ProcessManager.masterLock.lock();
    try {
      for (Server server : ProcessManager.masterServerList) {
        Message msg = new Message(MessageType.MsgTerminate, null, null);
        if (sendMessage(server, msg).getType() != MessageType.MsgResponseSuccess) {
          System.out.println("Terminate error!");
        }
      }
    } finally {
      ProcessManager.masterLock.unlock();
    }
    System.exit(0);
  }
}