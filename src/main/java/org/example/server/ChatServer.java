package org.example.server;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketException;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketException;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketException;

public class ChatServer {
    private static final int PORT = 9876;
    private DatagramSocket socket;

    public ChatServer() {
        try {
            socket = new DatagramSocket(PORT);
            System.out.println("Server started on port " + PORT);
        } catch (SocketException e) {
            System.err.println("Could not start server: " + e.getMessage());
            System.exit(1);
        }
    }

    public void run() {
        ClientHandler handler = new ClientHandler(socket);
        handler.start();
    }

    public static void main(String[] args) {
        new ChatServer().run();
    }
}