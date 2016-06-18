
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
public class LoadBalanceTimer extends TimerTask {
    public void run() {
      LoadBalanceTimer.this.loadBalance();
    }    

     public void loadBalance() {    
    int round = 0;
    
    // The round of load balance we do is 1/2 * NumberOfSlaves
    ProcessManager.masterLock.lock();
    try {
      round = ProcessManager.masterServerList.size() / 2;
    } finally {
      ProcessManager.masterLock.unlock();
    }

    // Each round we balance load between server with the max and min load
    while (round > 0) {
      Server dst = null;
      Server src = null;
      int min = 0;
      int max = 0;
      int diff = 0;
      ProcessManager.masterLock.lock();
      try {
        dst = ProcessManager.masterServerList.peekFirst();
        src = ProcessManager.masterServerList.peekLast();
        min = dst.getThreadSet().size();
        max = src.getThreadSet().size();
        diff = (max - min) / 2;
      } finally {
        ProcessManager.masterLock.unlock();
      }

      if (diff < 1)
        break;

      Message requestSrc = new Message(MessageType.MsgBalanceRequestSrc, (Object)diff, (Object)max);
      Message responseSrc = sendMessage(src, requestSrc);
      if (responseSrc.getType() != MessageType.MsgBalanceResponse) {
        this.printWithPrompt("Load balance skip this round!");
        continue;
      }

      @SuppressWarnings("unchecked")
      LinkedList<Integer> tidList = (LinkedList<Integer>)responseSrc.getArg();
      migrateThreadSet(src, dst, tidList);
      Message requestDst = new Message(MessageType.MsgBalanceRequestDst, responseSrc.getObj(), responseSrc.getArg());

      if(sendMessage(dst, requestDst) == null) {
        this.printWithPrompt("Load balance error!");
      }
      
      round--;
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

  private void migrateThreadSet(Server src, Server dst, List<Integer> tidList) {
    if (src != null && dst != null) {
      ProcessManager.masterLock.lock();
      try {
        // Remove slave Server and add it back to keep MinMaxPriorityQueue working
        ProcessManager.masterServerList.remove(src);
        ProcessManager.masterServerList.remove(dst);
        src.getThreadSet().removeAll(tidList);
        dst.getThreadSet().addAll(tidList);
        ProcessManager.masterServerList.offer(src);
        ProcessManager.masterServerList.offer(dst);
      } finally {
        ProcessManager.masterLock.unlock();
      }
    }
  }

  public void printWithPrompt(String msg) {
    System.out.println("\n" + msg);
    System.out.print(">>>");
  }
  }