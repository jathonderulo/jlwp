// ass 1 file
import java.io.*;

public class ass1webProxy {

    public static void main(String[] args) throws IOException {
        WebProxy webProxy = new WebProxy(4000);
        webProxy.run();
    }
}