package org.example.client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class ClientReceiver implements Runnable {
    private static final int BUFFER_SIZE = 1024;
    private DatagramSocket socket;

    public ClientReceiver(DatagramSocket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[BUFFER_SIZE];

        while (!socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength());
                System.out.print("\r" + message + "\n> ");
                System.out.flush();
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    System.err.println("\rError receiving message: " + e.getMessage() + "\n> ");
                    System.out.flush();
                }
            }
        }
    }
}