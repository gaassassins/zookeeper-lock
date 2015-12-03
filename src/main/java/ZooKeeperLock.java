import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

/**
 * Created by 1 on 25.11.2015.
 */

public class ZooKeeperLock implements  Watcher {
    private ZooKeeper zk;
    private String root = "/Lock";
    private String waitNode;
    private String myZnode;
    private CountDownLatch latch;

    public ZooKeeperLock() throws InterruptedException, IOException, KeeperException {
        zk = new ZooKeeper("127.0.0.1:2181", 30000, this);
        if (ZooKeeper.States.CONNECTING == zk.getState()) {
            this.latch = new CountDownLatch(1);
            this.latch.await();
        }

        Stat stat = zk.exists(root, false);
        if (stat == null) {
            zk.create(root, new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
    }

    public void process(WatchedEvent event) {
        if (this.latch != null) {
            this.latch.countDown();
        }
    }

    public void lock() {
        try {
            if (this.tryLock()) {
                System.out.println("Thread " + Thread.currentThread().getId() + " " + myZnode + " get lock true");
                return;
            } else {
                waitForLock(waitNode, 30000);
            }
        } catch (KeeperException e) {
            System.out.println(e);
        } catch (InterruptedException e) {
            System.out.println(e);
        }
    }

    public boolean tryLock() {
        try {
            myZnode = zk.create(root + "/"+ "Lock_", new byte[0],  Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
            System.out.println(myZnode + " is created ");

            List<String> subNodes = zk.getChildren(root, false);
            List<String> lockObjNodes = new ArrayList<String>();
            for (String node : subNodes) {
                lockObjNodes.add(node);
            }
            Collections.sort(lockObjNodes);
            //	System.out.println(myZnode + "==" + lockObjNodes.get(0));

            if (myZnode.equals(root + "/" + lockObjNodes.get(0))) {
                return true;
            }

            String subMyZnode = myZnode.substring(myZnode.lastIndexOf("/") + 1);
            waitNode = lockObjNodes.get(Collections.binarySearch(lockObjNodes, subMyZnode) - 1);
        } catch (KeeperException e) {
            System.out.println(e);
        } catch (InterruptedException e) {
            System.out.println(e);
        }
        return false;
    }



    private boolean waitForLock(String lower, long waitTime)
            throws InterruptedException, KeeperException {
        Stat stat = zk.exists(root + "/" + lower, true);
        if (stat != null) {
            //System.out.println("Thread " + Thread.currentThread().getId() + " waiting for " + root + "/" + lower);
            this.latch = new CountDownLatch(1);
            this.latch.await(waitTime, TimeUnit.MILLISECONDS);
            this.latch = null;
        }
        return true;
    }

    public void unlock() {
        try {
            System.out.println("unlock " + myZnode);
            zk.delete(myZnode, -1);
            myZnode = null;
            zk.close();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            e.printStackTrace();
        }
    }

    static int value = 0;
    public static void main(String[] args) {
        for (int i = 0; i<20; i++){
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ZooKeeperLock lock = new ZooKeeperLock();
                        lock.lock();
                        value++;
                        Thread.sleep(200);
                        System.out.println(value);
                        lock.unlock();
                        System.err.println("=============================");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (KeeperException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            t.start();
        }
    }
}