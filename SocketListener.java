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
public class SocketListener extends Thread {    
    private ServerSocket listener;

    public SocketListener(int port) {      
      try {
        this.listener = new ServerSocket(port);
      } catch (Exception e) {
        e.printStackTrace();
      }      
    }
    public   boolean sendProcessMessageResponse(Message msg, Socket socket) {
    try {
      ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
      out.writeObject(msg);
      out.flush();
      out.close();
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

    public void run() {
      while (true) {
        try {
          /* 
           * Listen until a connection is established, and process the message
           */
        	Socket socket = this.listener.accept();
        	ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
          //System.out.println(in.toString()+"\n");
          Message msg = (Message)in.readObject();
        	if(processMessage(msg, socket) != ProcessManager.SUCCESS) {
        		System.out.println("Process message failed!");
        	}
          else
            System.out.println("Process Message Success");
        	in.close();
        	socket.close();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }

    private void removeThreadFromSlave(int tid) {
    Server targetSlave = null;
    ProcessManager.masterLock.lock();
    try {
      for (Server slave : ProcessManager.masterServerList) {
        if (slave.getThreadSet().contains(tid)) {
          targetSlave = slave;
          break;
        }
      }
      if (targetSlave != null) {
        // Remove slave Server and add it back to keep MinMaxPriorityQueue working
        ProcessManager.masterServerList.remove(targetSlave);
        targetSlave.getThreadSet().remove(tid);
        ProcessManager.masterServerList.offer(targetSlave);
      }
    } finally {
      ProcessManager.masterLock.unlock();
    }
  }

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
    public int processMessage(Message msg, Socket socket) {    
    MessageType type = msg.getType();
    if (type == MessageType.MsgNewSlaveRequest) { //master 
      /*
       * Master get this msg from Slave, adding this server as Slave
       * msg.serializedObj is the Server object, which contains addr and port of the slave 
       */
      Server slave = (Server)msg.getObj();
      System.out.println("In message New Slave Request "+slave.getIP()+" "+slave.getPort()+"\n");
      ProcessManager.masterLock.lock();
      try {
        ProcessManager.masterServerList.offer(slave);
      } finally {
        ProcessManager.masterLock.unlock();
      }
      this.printWithPrompt("MsgNewSlaveRequest processed!");
      
      Message response = new Message(MessageType.MsgResponseSuccess, null, null);
      if (this.sendProcessMessageResponse(response, socket) == false) {
        System.out.println("sendProcessMessageResponse failed!");
        return ProcessManager.ERROR;
      }
    } else if (type == MessageType.MsgProcessStart) {         //slave
      /* 
       * Slave get this msg from Master, starting the thread
       * msg.serializedObj is the MigratableProcess
       * msg.arg is the thread ID
       */
      System.out.println("File Decrypted Here\n");
      MigratableProcess process = (MigratableProcess)msg.getObj();
      int tID = (int)msg.getArg();
      Thread thread = new Thread(process);
      ProcessManager.slaveLock.lock();
      try {
        ProcessManager.slaveThreadList.add(thread);
        ProcessManager.slaveThreadIdMap.put(thread, tID);
        ProcessManager.slaveThreadMPMap.put(thread, process);
      } finally {
        ProcessManager.slaveLock.unlock();
      }
      thread.start();
      System.out.println("Start thread " + tID + "!");
      System.out.println("MsgProcessStart processed!");
      
      Message response = new Message(MessageType.MsgResponseSuccess, null, null);
      if (this.sendProcessMessageResponse(response, socket) == false) {
        System.out.println("sendProcessMessageResponse failed!");
        return ProcessManager.ERROR;
      }
    } else if (type == MessageType.MsgProcessFinish) {          //master
      /*
       * Master get this msg from Slave, notifying that a thread has finished
       * msg.arg is the thread ID
       * Remove the thread ID from Master's server list
       */
      int tid = (int)msg.getArg();
      removeThreadFromSlave(tid);
      this.printWithPrompt("MsgProcessFinish processed!");
      
      Message response = new Message(MessageType.MsgResponseSuccess, null, null);
      if (this.sendProcessMessageResponse(response, socket) == false) {
        this.printWithPrompt("sendProcessMessageResponse failed!");
        return ProcessManager.ERROR;
      }
    } else if (type == MessageType.MsgBalanceRequestSrc) {        //slave
      /*
       * Slave get this msg from Master, indicating that Slave has high load
       * Slave should suspend some threads and send back to Master
       * msg.serializedObj is the count of the threads to migrate from Slave
       * msg.arg is the count of threads that Master expect Slave is running
       * msg.arg is used for sync. If msg.arg number not the same as the count of threads Slave really has,
       * then we send back a response which aborts this round of migration
       * If msg.arg matches, in response:
       * msg.serializedObj contains the list of threads, and msg.arg contains list of thread IDs
       */ 
      int migrateThreadCnt = (int)msg.getObj();
      int expectedThreadCnt = (int)msg.getArg();
      
      System.out.println("migrateThreadCnt:" + migrateThreadCnt);
      System.out.println("expectedThreadCnt:" + expectedThreadCnt);
      
      Message response = null;
      ProcessManager.slaveLock.lock();
      try {
        if (ProcessManager.slaveThreadList.size() != expectedThreadCnt) {
          // Sync problem, msg.arg does not match the real count of threads in Slave
          response = new Message(MessageType.MsgReponseError, null, null);
        } else {
          LinkedList<MigratableProcess> processList = new LinkedList<MigratableProcess>();
          LinkedList<Integer> idList = new LinkedList<Integer>();
          for (int i = 0; i < migrateThreadCnt; i++) {
            Thread thread = ProcessManager.slaveThreadList.pollLast();
            if (thread != null) {
              if (thread.isAlive()) {
                MigratableProcess mp = ProcessManager.slaveThreadMPMap.get(thread);
                mp.suspend();
                processList.add(mp);
                idList.add(ProcessManager.slaveThreadIdMap.get(thread));
              }
              ProcessManager.slaveThreadList.remove(thread);
              ProcessManager.slaveThreadIdMap.remove(thread);
              ProcessManager.slaveThreadMPMap.remove(thread);
            }
          }
          System.out.println("Src ready to migrate " + processList.size() + " Processes!");
          if (processList.size() > 0) {
            response = new Message(MessageType.MsgBalanceResponse, (Object) processList, (Object) idList);
          } else {
            response = new Message(MessageType.MsgReponseError, null, null);
          }
        }
      } finally {
        ProcessManager.slaveLock.unlock();
      }
      System.out.println("MsgBalanceRequestSrc processed!");
      if (this.sendProcessMessageResponse(response, socket) == false) {
        System.out.println("sendProcessMessageResponse failed!");
        return ProcessManager.ERROR;
      }      
    } else if (type == MessageType.MsgBalanceRequestDst) {      //slave
      /* 
       * Slave get this msg from Master
       * msg.serializedobj is the list of threads, msg.arg is the list of thread IDs
       * Slave adds these threads, and starts running them
       */
      LinkedList<MigratableProcess> processList = (LinkedList<MigratableProcess>)msg.getObj();
      LinkedList<Integer> idList = (LinkedList<Integer>) msg.getArg();
      System.out.println("Dst ready to run " + processList.size() + " Processes!");
      while (processList.size() > 0) {
        MigratableProcess process = processList.pollFirst();
        int tID = idList.pollFirst();
        Thread thread = new Thread(process);
        ProcessManager.slaveLock.lock();
        try {
          ProcessManager.slaveThreadList.add(thread);
          ProcessManager.slaveThreadIdMap.put(thread, tID);
          ProcessManager.slaveThreadMPMap.put(thread, process);
        } finally {
          ProcessManager.slaveLock.unlock();
        }
        thread.start();
        System.out.println("Start to run Thread " + tID + "!");
      }
      System.out.println("MsgBalanceRequestDst processed!");
      
      Message response = new Message(MessageType.MsgResponseSuccess, null, null);
      if (this.sendProcessMessageResponse(response, socket) == false) {
        System.out.println("sendProcessMessageResponse failed!");
        return ProcessManager.ERROR;
      }      
    } else if (type == MessageType.MsgTerminate) {      //slave
      /* 
       * Slave get this msg from Master, terminating Slave
       */
      ProcessManager.slaveLock.lock();
      try {
        for (Thread thread : ProcessManager.slaveThreadList) {
          MigratableProcess mp = ProcessManager.slaveThreadMPMap.get(thread);
          mp.suspend();
        }
      } finally {
        ProcessManager.slaveLock.unlock();
      }
      
      Message response = new Message(MessageType.MsgResponseSuccess, null, null);
      if (this.sendProcessMessageResponse(response, socket) == false) {
        System.out.println("sendProcessMessageResponse failed!");
        return ProcessManager.ERROR;
      }
      System.exit(0);
    }
    return ProcessManager.SUCCESS;
  }
  }