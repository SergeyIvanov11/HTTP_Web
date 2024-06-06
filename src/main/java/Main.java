
public class Main {
    public static void main(String[] args) {
        ThreadPooledServer server = new ThreadPooledServer(9999);
        new Thread(server).start();

    }
}


