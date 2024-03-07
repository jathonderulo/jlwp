import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SharedListWrapper {
    protected final List<String> listOfBlockedURLs = new ArrayList<>();
    protected final LinkedList<String> requestsQueue = new LinkedList<>();
    protected int requestsQueueLength = 0;
    private final Lock lock = new ReentrantLock();

    public String getFromList(int index) {
        lock.lock();
        try {
            return listOfBlockedURLs.get(index-1);
        } finally {
            lock.unlock();
        }
    }

    public void addToList(String item) {
        lock.lock();
        try {
            listOfBlockedURLs.add(item);
        } finally {
            lock.unlock();
        }
    }

    public void deleteFromList(int index) {
        lock.lock();
        try {
            listOfBlockedURLs.remove(index-1);
        } finally {
            lock.unlock();
        }
    }

    public String getFromQueue(int index) {
        lock.lock();
        try {
            return requestsQueue.get(index-1);
        } finally {
            lock.unlock();
        }
    }

    public void addToQueue(String item) {
        lock.lock();
        try {
            if (requestsQueueLength >= 10) {
                requestsQueue.removeLast();
                requestsQueueLength--;
            }
            requestsQueue.addFirst(item);
            requestsQueueLength++;
        } finally {
            lock.unlock();
        }
    }

    public void deleteFromQueue(int index) {
        lock.lock();
        try {
            requestsQueue.remove(index-1);
            requestsQueueLength--;
        } finally {
            lock.unlock();
        }
    }
}
    

