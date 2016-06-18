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

public class Slave{

	public Slave(Server master, Server slave){
		ProcessManager.master=master;
		ProcessManager.localhost=slave;
		ProcessManager.slaveLock = new ReentrantLock();
      	ProcessManager.slaveThreadList = new LinkedList<Thread>();
      	ProcessManager.slaveThreadIdMap = new HashMap<Thread, Integer>();
      	ProcessManager.slaveThreadMPMap = new HashMap<Thread, MigratableProcess>();
	}
	public void SlaveRun() {
    SocketListener listener = new SocketListener(ProcessManager.localhost.getPort());
    listener.start();

    Message msg = new Message(MessageType.MsgNewSlaveRequest, ProcessManager.localhost, null);
    if (sendMessage1(ProcessManager.master, msg).getType() == MessageType.MsgResponseSuccess) {
      System.out.println("Connected to Master!");
      System.out.println("Slave public key send to master\n");
    } else {
      System.out.println("Failed to connect to Master!");
      return;
    }
    System.out.println("This is Slave!");
    
    while (true) {
      try {
        Thread.sleep(30);
      } 
      catch (Exception e) {
        e.printStackTrace();
        continue;
      }
      LinkedList<Thread> removeList = new LinkedList<Thread>();
      ProcessManager.slaveLock.lock();
      try {
        for (Thread thread : ProcessManager.slaveThreadList) {
          if (!thread.isAlive()) {
            removeList.add(thread);
          }
        }
        for (Thread thread : removeList) {
          int tid = ProcessManager.slaveThreadIdMap.get(thread);
          ProcessManager.slaveThreadList.remove(thread);
          ProcessManager.slaveThreadIdMap.remove(thread);
          ProcessManager.slaveThreadMPMap.remove(thread);
          Message finMsg = new Message(MessageType.MsgProcessFinish, null, (Object) tid);
          sendMessage(ProcessManager.master, finMsg);
        }
      } finally {
        ProcessManager.slaveLock.unlock();
      }
    }
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
  public Message sendMessage1(Server server, Message msg) {
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

}