import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.io.*;
import java.net.*;

public class WebProxy {
    Map<String, String> cache;
    ServerSocket serverSocket;
    int port;

    public WebProxy(int port) {
        this.port = port;
        try {
            this.serverSocket = new ServerSocket(port);
            cache = new HashMap<>();
        } catch (IOException e) {
            e.printStackTrace();
        }
    } 
    public void run() {
        while(true) {
            try{
                Socket clientSocket = this.serverSocket.accept();
                System.out.printf("\nClient is connected to proxy\n");

                // Handling client request in new thread
                Thread thread = new Thread(() -> {
                    try {
                        long startTime = System.nanoTime();
                        int[] bandwidthBytes = {0};
                        handleClientRequest(clientSocket, cache, bandwidthBytes);
                        double duration = System.nanoTime() - startTime; // Calculate the duration
                        System.out.printf("Execution time in milliseconds: %.2fms\n", duration/1000000);
                        System.out.printf("Bandwidth is %.2f bits per seconds\n", (double) ((bandwidthBytes[0]*8) / (duration/(double)1000000000)));

                    } catch (IOException e){
                        e.printStackTrace();
                    } finally {
                        try {
                            clientSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
                thread.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } 
    }

    private void handleClientRequest(Socket clientSocket, Map<String, String> cache, int[] bandwidthBytes) throws IOException {
        try {
            Socket mgmtConsoleSocket = new Socket("localhost", 10000);
            InputStream clientInput = clientSocket.getInputStream();
            System.out.println("Bandwidth is " + bandwidthBytes[0]);
            PrintWriter toMgmtConsole =  new PrintWriter(mgmtConsoleSocket.getOutputStream(), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientInput, StandardCharsets.UTF_8));

            // Read the request line and headers
            String requestLine = reader.readLine();
            bandwidthBytes[0] += requestLine.length()*2;

            // Send request to management console 
            toMgmtConsole.println(requestLine);

            // Making sure requested URL is not blocked
            BufferedReader consoleReplyReader = new BufferedReader(new InputStreamReader(mgmtConsoleSocket.getInputStream(), StandardCharsets.UTF_8));
            String consoleReply = consoleReplyReader.readLine(); 

            if (consoleReply.equals("No")) {
                mgmtConsoleSocket.close();
                System.out.println("Received console reply: No");
                return;
            }

            String[] requestParts = requestLine.split(" ");
            
            // Handle HTTP request
            if(requestParts[0].equals("GET")) 
                handleHTTPrequest(clientSocket, cache, bandwidthBytes, requestLine);

            // Handle HTTPS request
            else if(requestParts[0].equals("CONNECT"))
                handleHTTPSrequest(clientSocket, requestLine);

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void handleHTTPrequest(Socket clientSocket, Map<String, String> cache,
     int[] bandwidthBytes, String requestLine) throws IOException 
    {
        try {
            PrintWriter toClient = new PrintWriter(clientSocket.getOutputStream(), true);
            bandwidthBytes[0] += requestLine.length()*2;

            if (cache.containsKey(requestLine)) {
                System.out.println("This is already cached. Response will be sent to client faster.");
                bandwidthBytes[0] += cache.get(requestLine).length()*2;
                toClient.print(cache.get(requestLine));
                toClient.flush();
                return;
            }
            
            // Parse the request line
            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 3) {
                return; // Invalid request line
            }

            String method = requestParts[0];
            String uri = requestParts[1];
            System.out.println("Destination: " + uri);

            // Create a URL
            URL url = new URL(uri);

            // Open a connection to the destination server
            HttpURLConnection connection = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
            connection.setRequestMethod(method);   

            connection.connect();
            System.out.println("Connection attempted.");

            // Get the response
            int responseCode = connection.getResponseCode();

            // Check if the connection succeeded
            if (responseCode >= 200 && responseCode < 300) {
                System.out.println("Connection successful. Response code: " + responseCode);
                
                InputStream inputStream = connection.getInputStream();

                // Read the response body
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }
                cache.put(requestLine, response.toString());

                bandwidthBytes[0] += response.toString().length()*2;
                
                // Relay response back to the client
                toClient.print(response.toString());
                toClient.flush();

            } else {
                System.out.println("Connection failed. Response code: " + responseCode);
            }
            // Close all resources
            connection.disconnect();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleHTTPSrequest(Socket clientSocket, String requestLine) throws IOException {
        // Get host and port from requestLine

        String[] parts = requestLine.split(" ")[1].split(":");
        String host = parts[0];
        int port = (parts.length == 2) ? Integer.parseInt(parts[1]) : 443;
        System.out.println("HTTPS request is " + host + ":" + port);

        PrintWriter clientWriter = new PrintWriter(clientSocket.getOutputStream(), true);
        clientWriter.println("HTTP/1.1 200 ok\r\n");
        clientWriter.flush();   

        try(Socket webServerSocket = new Socket(host, port)) {

            Thread clientToServerThread = new Thread(() -> {
                try {
                    sendData(clientSocket.getInputStream(), webServerSocket.getOutputStream());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            Thread serverToClientThread = new Thread(() -> {
                try {
                    sendData(webServerSocket.getInputStream(), clientSocket.getOutputStream());
                } catch (IOException e) {
                    e.printStackTrace();
                } 
            });

            clientToServerThread.start();
            serverToClientThread.start();

            clientToServerThread.join();
            serverToClientThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void sendData(InputStream inputStream, OutputStream outputStream) throws IOException {
        try {
            byte[] byteArrayBuffer = new byte[4096];
            int bytes;
            while((bytes = inputStream.read(byteArrayBuffer)) != -1) {
                outputStream.write(byteArrayBuffer, 0, bytes);
                outputStream.flush();
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

}