package com.example;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class MockServer {

    private static final String DB_URL = "jdbc:postgresql://postgres:5432/mydatabase"; // Update with your URL, port, and database name
    private static final String USER = "postgres"; // Update with your username
    private static final String PASS = "postgres"; // Update with your password

    private static final int READ_TIMEOUT_MS = 5000;  // 5 seconds timeout
    private static final int MAX_CONTENT_LENGTH = 1024 * 1024;  // 1 MB maximum content length

    public static void main(String[] args) {
        int port = 8080;  // default port
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Listening on port " + port);
            while (true) {
                try (Socket clientSocket = serverSocket.accept();
                     BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void queryPostgres(IncomingHttpRequest httpRequest) {
        String sql = "INSERT INTO messages (method, uri, body) VALUES (?, ?, ?)";
        
        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASS);
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
