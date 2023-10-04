package com.example;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MockServer {

    private static final String POSTGRES_HOST = System.getenv("POSTGRES_HOST");
    private static final String POSTGRES_PORT = System.getenv("POSTGRES_PORT");
    private static final String DATABASE_NAME = System.getenv("POSTGRES_DB");
    private static final String POSTGRES_USER = System.getenv("POSTGRES_USER");
    private static final String POSTGRES_PASSWORD = System.getenv("POSTGRES_PASSWORD");

    private static final int READ_TIMEOUT_MS = 5000;
    private static final int MAX_CONTENT_LENGTH = 1024 * 1024; // 1 MB

    private static final int THREAD_COUNT = 10;
    private static final ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

    public static void main(String[] args) {
        int port = 8080;  // default port
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Listening on port " + port);
            while (true) {
                try {
                    final Socket clientSocket = serverSocket.accept();
                    executor.submit(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    System.err.println("Error accepting client connection: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket clientSocket) {
        System.out.println(", Name: " + Thread.currentThread().getName());
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            clientSocket.setSoTimeout(READ_TIMEOUT_MS);

            StringBuilder headers = new StringBuilder();
            String line;

            // Read headers
            while (!(line = in.readLine()).isEmpty()) {
                headers.append(line).append("\n");
            }

            // Find Content-Length from headers
            int contentLength = 0;
            String[] headerLines = headers.toString().split("\n");
            for (String headerLine : headerLines) {
                if (headerLine.startsWith("Content-Length: ")) {
                    contentLength = Integer.parseInt(headerLine.split(":")[1].trim());
                    if (contentLength > MAX_CONTENT_LENGTH) {
                        throw new IllegalArgumentException("Content-Length too large");
                    }
                }
            }

            String method = headerLines[0].split(" ")[0];
            String endpoint = headerLines[0].split(" ")[1];
            
            // Read body based on Content-Length
            char[] bodyChars = new char[contentLength];
            int bytesRead = in.read(bodyChars, 0, contentLength);
            String body = new String(bodyChars, 0, bytesRead);

            IncomingHttpRequest request = new IncomingHttpRequest(method, endpoint, body);
            queryPostgres(request);

            // Responding to the client to close the connection gracefully
            out.println("HTTP/1.1 200 OK");
            out.println("Content-Length: 0");
            out.println();

        } catch (SocketTimeoutException ste) {
            System.err.println("Client read timed out");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void queryPostgres(IncomingHttpRequest httpRequest) {
        String sql = "INSERT INTO messages (method, uri, body) VALUES (?, ?, ?)";
        
        final String db_url = String.format("jdbc:postgresql://%s:%s/%s", POSTGRES_HOST, POSTGRES_PORT, DATABASE_NAME);
        try (Connection connection = DriverManager.getConnection(db_url, POSTGRES_USER, POSTGRES_PASSWORD);
             PreparedStatement pstmt = connection.prepareStatement(sql)) {
        
            pstmt.setString(1, httpRequest.getMethod());
            pstmt.setString(2, httpRequest.getEndpoint());
            pstmt.setString(3, httpRequest.getBody());
            pstmt.executeUpdate();
        
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
