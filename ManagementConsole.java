import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.HashSet;

public class ManagementConsole {
    SharedListWrapper threadSafe;
    ServerSocket requestsProxyServerSocket;
    int port;

    public ManagementConsole(int port) {
        try {
            this.port = port;
            this.threadSafe = new SharedListWrapper();
            this.requestsProxyServerSocket = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        Thread t1 = new Thread(() -> {
            Scanner input = new Scanner(System.in);
            talkToUser(threadSafe, input);
        });
        t1.start();

        while (true) 
        {
            try {
                Socket proxyRequestsSocket = requestsProxyServerSocket.accept();
                
                Thread t2 = new Thread(() -> {
                    try {
                        handleProxyData(proxyRequestsSocket, threadSafe);
                    }
                    catch(IOException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            proxyRequestsSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
                t2.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleProxyData(Socket proxySocket, SharedListWrapper threadSafe) throws IOException {
        try 
        {   
            InputStream input = proxySocket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            String requestLine = reader.readLine();
            PrintWriter proxyWriter =  new PrintWriter(proxySocket.getOutputStream(), true);

            if (requestLine != null && !requestLine.isEmpty()) 
            {
                String[] requestParts = requestLine.split(" ");
                String uri = "URI_PLACEHOLDER";
                if (requestParts.length >= 3) 
                {
                    uri = requestParts[1];
                    System.out.printf("\n<--------- NEW REQUEST --------->" 
                    + "\n URI: %s\n", uri);
                    
                    if(!threadSafe.listOfBlockedURLs.contains(uri)) { // is not blocked
                        threadSafe.addToQueue(uri);
                        proxyWriter.println("Yes"); 
                    }
                    else { // is blocked
                        proxyWriter.println("No");
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void talkToUser(SharedListWrapper threadSafe, Scanner input) {
        
        System.out.printf("\nEach time a request comes in, it will be printed out. "
        + "It will not affect your input. \n\n"
        + "Enter 'r' to display the last 10 requests. Type the number of an existing entry to block it.\n"
        + "Enter 'b' to display the list of blocked URLs. Type the number of an existing entry to unblock it.\n"
        + "Enter 'c' to clear the requests queue\n"
        + "Enter 'u' to claer the blocked lists queue.\n"
        + "Any invalid inputs will be disregarded.\n");

        // Initialize runnable maps that map valid user input to function 
        Map<String, Runnable> listCommandMap = new HashMap<>();
        Set<String> hset = new HashSet<>();
        for(int i = 1; i <= 10; i++) hset.add(Integer.toString(i));

        // Map the valid inputs to their function
        listCommandMap.put("r", () -> displayRequestsQueue(threadSafe));
        listCommandMap.put("b", () -> displayBlockedList(threadSafe));
        listCommandMap.put("c", () -> clearRequestsQueue(threadSafe));
        listCommandMap.put("u", () -> clearBlockedList(threadSafe));

        // Read user inputs 
        String userInput;
        boolean currentlyDisplayingRequests = false;
        boolean currentlyDisplayingBlockedList = false;

        while(true) 
        {
            userInput = input.nextLine().toLowerCase();
            if(listCommandMap.containsKey(userInput)) 
            {
                if(userInput.equals("r")) 
                    currentlyDisplayingRequests = true;
                else if(userInput.equals("b")) 
                    currentlyDisplayingBlockedList = true;

                listCommandMap.get(userInput).run();
            }
            else if(hset.contains(userInput)) 
            {   
                int index = Integer.parseInt(userInput);
                if(currentlyDisplayingRequests) {
                    currentlyDisplayingRequests = false;
                    if(threadSafe.requestsQueueLength == 0) {
                        System.out.println("There are no requests in the queue. ");
                        continue;
                    } else if (threadSafe.requestsQueueLength < index) {
                        System.out.println("Invalid index for requests queue. ");
                        continue;
                    }

                    blockURL(threadSafe, index);
                } else if (currentlyDisplayingBlockedList) {
                    currentlyDisplayingBlockedList = false;
                    if (threadSafe.listOfBlockedURLs.size() == 0) {
                        System.out.println("There are no blocked URLs. ");
                        continue;
                    }
                    else if(threadSafe.listOfBlockedURLs.size() < index) {
                        System.out.println("Invalid index for blocked URLs list. ");
                        continue;
                    } 
                    unblockURL(threadSafe, index);
                }
            }
        }
    }
    
    // Displays requests queue. if empty, displays empty queue message. 
    private void displayRequestsQueue(SharedListWrapper threadSafe) {
        int len = threadSafe.requestsQueueLength;
        if (len <= 0) System.out.println("There are currently no requests!");
        else for(int i = len; i > 0; i--) 
            System.out.println(i + ". " + threadSafe.getFromQueue(i));
    }   

    private void clearRequestsQueue(SharedListWrapper threadSafe) {
        threadSafe.requestsQueue.clear();
        threadSafe.requestsQueueLength = 0;
        System.out.println("Cleared requests queue.");
    }  

    // Displays blocked URLs list, if empty, displays empty list message.
    private void displayBlockedList(SharedListWrapper threadSafe) {
        int len = threadSafe.listOfBlockedURLs.size();
        if (len <= 0) System.out.println("There are currently no blocked URLs!");

        else for(int i = len; i > 0; i--) 
            System.out.println(i + ". " + threadSafe.getFromList(i));
    }

    private void clearBlockedList(SharedListWrapper threadSafe) {
        threadSafe.listOfBlockedURLs.clear();
        System.out.println("Unblocked all blocked URLs.");
    }


    private void blockURL(SharedListWrapper threadSafe, int index) {
        String URL = threadSafe.getFromQueue(index);
        if(!threadSafe.listOfBlockedURLs.contains(URL)) {
            threadSafe.addToList(URL);
            System.out.println("Blocked URL " + URL);
            threadSafe.deleteFromQueue(index);
        } else {
            System.out.println("That URL is already blocked");
        }
    }

    private void unblockURL(SharedListWrapper threadSafe, int index) {   
        System.out.println("Unblocked URL " + threadSafe.getFromList(index));
        threadSafe.deleteFromList(index);
    }
}
