import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

public class Server implements Runnable {
    final List<String> availableMethods = List.of("GET", "POST");
    final List<String> validPaths = List.of("index.html", "spring.svg", "spring.png", "resources.html",
            "styles.css", "app.js", "links.html", "forms.html", "classic.html", "events.html", "events.js");
    protected Socket clientSocket;
    protected String serverText;
    protected BufferedReader in;
    protected BufferedOutputStream out;

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
                String requestLine = in.readLine();
                Request request = new Request(requestLine);
                RequestHandler requestHandler = new RequestHandler(request, out);
                requestHandler.handle();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected byte[] serverMessage(String mimeType, int length) {
        return ("HTTP/1.1 200 OK\r\n" +
                "Content-Type: " + mimeType + "\r\n" +
                "Content-Length: " + length + "\r\n" +
                "Connection: close\r\n" +
                "\r\n").getBytes();
    }

    protected void closeServer(BufferedOutputStream bos) throws IOException {
        bos.write(("HTTP/1.1 404 Not Found\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n" +
                "\r\n").getBytes());
        bos.flush();
        bos.close();
    }

    class Request {
        protected String[] parts;
        protected String method;
        protected String path;
        protected String[] headers;
        protected String body;

        public Request(String requestLine) {
            this.parts = requestLine.split(" ");
            this.method = parts[0];
            String[] url = parts[1].split("/");
            this.path = url[url.length - 1];
        }
    }

    class RequestHandler {
        protected String handlersMethod;
        protected String handlersPath;
        protected BufferedOutputStream responseStream;

        public RequestHandler(Request request, BufferedOutputStream responseStream) throws IOException {
            this.responseStream = responseStream;
            this.handlersMethod = request.method;
            if (!availableMethods.contains(handlersMethod)) {
                closeServer(responseStream);
            }

            this.handlersPath = request.path;
            if (!validPaths.contains(handlersPath)) {
                closeServer(responseStream);
            }
        }

        void handle() throws IOException {
            Path filePath = Path.of(".", "public/", this.handlersPath);
            String mimeType = Files.probeContentType(filePath);

            // special case for classic
            if (handlersPath.equals("classic.html")) {
                String template = Files.readString(filePath);
                byte[] content = template.replace(
                        "{time}",
                        LocalDateTime.now().toString()
                ).getBytes();
                responseStream.write(serverMessage(mimeType, content.length));
                responseStream.write(content);
                responseStream.flush();
            }

            int length = (int) Files.size(filePath);
            responseStream.write(serverMessage(mimeType, length));
            Files.copy(filePath, responseStream);
            responseStream.flush();
        }
    }
}