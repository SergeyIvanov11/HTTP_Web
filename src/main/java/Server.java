import org.apache.http.HttpRequest;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Server implements Runnable {
    final List<String> availableMethods = List.of("GET", "POST");
    protected Socket clientSocket;
    protected String serverText;
    protected BufferedInputStream in;
    protected BufferedOutputStream out;

    public Server(Socket clientSocket, String serverText) {
        this.clientSocket = clientSocket;
        this.serverText = serverText;
    }

    @Override
    public void run() {
        try {
            in = new BufferedInputStream(clientSocket.getInputStream());
            out = new BufferedOutputStream(clientSocket.getOutputStream());
            while (true) {
                final var limit = 4096;

                in.mark(limit);
                final var buffer = new byte[limit];
                final var read = in.read(buffer);

                // ищем request line
                final var requestLineDelimiter = new byte[]{'\r', '\n'};
                final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
                if (requestLineEnd == -1) {
                    badRequest(out);
                    continue;
                }

                // читаем request line
                final var requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
                if (requestLine.length != 3) {
                    badRequest(out);
                    continue;
                }

                final var method = requestLine[0];
                if (!availableMethods.contains(method)) {
                    badRequest(out);
                    continue;
                }
                System.out.println(method);

                final var path = requestLine[1];
                if (!path.startsWith("/")) {
                    badRequest(out);
                    continue;
                }
                System.out.println("Path: " + path);

                URI uri = new URI(path);
                String rawPath = uri.getRawPath();
                System.out.println("RawPath: " + rawPath);
                String query = uri.getQuery();
                parseAndPrintQuery(query);

                // ищем заголовки
                final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
                final var headersStart = requestLineEnd + requestLineDelimiter.length;
                final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
                if (headersEnd == -1) {
                    badRequest(out);
                    continue;
                }

                // отматываем на начало буфера
                in.reset();
                // пропускаем requestLine
                in.skip(headersStart);

                final var headersBytes = in.readNBytes(headersEnd - headersStart);
                final var headers = Arrays.asList(new String(headersBytes).split("\r\n"));
                System.out.println(headers);

                // для GET тела нет
                if (!method.equals("GET")) {
                    in.skip(headersDelimiter.length);
                    // вычитываем Content-Length, чтобы прочитать body
                    final var contentLength = extractHeader(headers, "Content-Length");
                    if (contentLength.isPresent()) {
                        final var length = Integer.parseInt(contentLength.get());
                        final var bodyBytes = in.readNBytes(length);

                        final var body = new String(bodyBytes);
                        System.out.println(body);
                    }
                }

                out.write((
                        "HTTP/1.1 200 OK\r\n" +
                                "Content-Length: 0\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                out.flush();

            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    private static void badRequest(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 400 Bad Request\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }

    // from google guava with modifications
    private static int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    public static void parseAndPrintQuery(String query) {
        /*
        String[] components = query.split("[\\&]");
        Map<String,Set<String>> params = new HashMap<String,Set<String>>();

        for(String component: components) {
            if( component.length() == 0 ) continue;

            int eq = component.indexOf('=');
            if( eq < 0 ) {
                component += "=";
                eq = component.length()-1;
            }

            String name = component.substring(0,eq);
            String value = component.substring(eq+1,component.length());

            Set<String> values = params.get(name);
            if( values == null ) {
                values = new HashSet<String>();
                params.put(name,values);
            }
            values.add(value);
        }
        System.out.println(params);
        */
        Map<String, String> params = new HashMap<>();
        List<NameValuePair> result = URLEncodedUtils.parse(query, UTF_8);
        for (NameValuePair nvp : result) {
            params.put(nvp.getName(), nvp.getValue());
        }
        System.out.println("Query:");
        for(String key : params.keySet()) {
            System.out.println(key + " = " + params.get(key));
        }
    }
}