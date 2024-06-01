import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

public class Server implements Runnable {
    final List<String> validPaths = List.of("/index.html", "/spring.svg", "/spring.png", "/resources.html",
            "/styles.css", "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js");
    protected Socket clientSocket;
    protected String serverText;
    private BufferedReader in;
    private BufferedOutputStream out;
    private String requestLine;
    private String[] parts;
    private String path;

    public Server(Socket clientSocket, String serverText) {
        this.clientSocket = clientSocket;
        this.serverText = serverText;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new BufferedOutputStream(clientSocket.getOutputStream());
            while (true) {
                // read only request line for simplicity
                // must be in form GET /path HTTP/1.1
                requestLine = in.readLine();
                parts = requestLine.split(" ");

                if (parts.length != 3) {
                    // just close socket
                    continue;
                }
                path = parts[1];
                if (!validPaths.contains(path)) {
                    out.write(serverMessage(null, 0));
                    out.flush();
                    continue;
                }

                Path filePath = Path.of(".", "public", path);
                String mimeType = Files.probeContentType(filePath);

                // special case for classic
                if (path.equals("/classic.html")) {
                    String template = Files.readString(filePath);
                    byte[] content = template.replace(
                            "{time}",
                            LocalDateTime.now().toString()
                    ).getBytes();
                    out.write(serverMessage(mimeType, content.length));
                    out.write(content);
                    out.flush();
                    continue;
                }
                int length = (int) Files.size(filePath);
                out.write(serverMessage(mimeType, length));
                Files.copy(filePath, out);
                out.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private byte[] serverMessage(String mimeType, int length) {
        String message;
        if (mimeType == null || length == 0) {
            message = "HTTP/1.1 404 Not Found\r\n" +
                    "Content-Length: 0\r\n" +
                    "Connection: close\r\n" +
                    "\r\n";
        } else {
            message = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: " + mimeType + "\r\n" +
                    "Content-Length: " + length + "\r\n" +
                    "Connection: close\r\n" +
                    "\r\n";
        }
        return message.getBytes();
    }
}