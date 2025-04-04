package org.example.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;

public class ChatClient {
    private static final int BUFFER_SIZE = 1024;
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort;
    private String nick;
    private boolean running;

    public ChatClient(String serverIp, int serverPort) {
        try {
            this.serverAddress = InetAddress.getByName(serverIp);
            this.serverPort = serverPort;
            this.socket = new DatagramSocket();
            this.running = true;
        } catch (UnknownHostException | SocketException e) {
            System.err.println("Error initializing client: " + e.getMessage());
            System.exit(1);
        }
    }

    public void start() {
        new Thread(new ClientReceiver(socket)).start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.print("Enter your nickname: ");
            nick = reader.readLine();
            sendMessage("/nick " + nick);

            System.out.println("Connected to server. Type /help for commands.");
            printPrompt();

            String input;
            while (running && (input = reader.readLine()) != null) {
                printPrompt();
                if (input.equalsIgnoreCase("/quit")) {
                    sendMessage("/quit");
                    running = false;
                } else if (input.equalsIgnoreCase("/reconnect")) {
                    reconnect();
                } else if (input.equalsIgnoreCase("/help")) {
                    printHelp();
                } else {
                    sendMessage(input);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading input: " + e.getMessage());
        } finally {
            socket.close();
        }
    }

    private void printPrompt() {
        System.out.print("> ");
        System.out.flush();
    }

    private void reconnect() {
        try {
            socket.close();
            socket = new DatagramSocket();
            sendMessage("/nick " + nick);
            System.out.println("\nReconnected to server");
            printPrompt();
        } catch (SocketException e) {
            System.err.println("Reconnection failed: " + e.getMessage());
            printPrompt();
        }
    }

    private void sendMessage(String message) {
        try {
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, serverAddress, serverPort);
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("Error sending message: " + e.getMessage());
        }
    }

    private void printHelp() {
        System.out.println("\nAvailable commands:");
        System.out.println("/join <channel> - Join a channel");
        System.out.println("/leave <channel> - Leave current channel");
        System.out.println("/channels - List all channels");
        System.out.println("/msg <nick> <message> - Send private message");
        System.out.println("/reconnect - Reconnect to server");
        System.out.println("/quit - Disconnect from server");
        printPrompt();
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java ChatClient <server_ip> <server_port>");
            System.exit(1);
        }

        String serverIp = args[0];
        int serverPort = Integer.parseInt(args[1]);

        new ChatClient(serverIp, serverPort).start();
    }
}