public class TestThread1 implements MigratableProcess {
  private static final long serialVersionUID = 1L;
  private volatile boolean suspending;
  private int count = 0;

  public TestThread1() {
  }

  @Override
  public void run() {
    System.out.println("TestThread1 started running!");
    while (!this.suspending) {
      try {
        Thread.sleep(2000);
        count++;
      } catch (Exception e) {
      }

      if (count > 10)
        break;
    }
    if (count > 10)
      System.out.println("TestThread1 finish success!");
    else
      System.out.println("TestThread1 suspended!");
    this.suspending = false;
  }

  @Override
  public void suspend() {
    suspending = true;
    while (suspending) {
      ;
    }
  }
}