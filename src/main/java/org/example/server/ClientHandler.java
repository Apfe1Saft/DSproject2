package org.example.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler {
    private static final int BUFFER_SIZE = 1024;
    private DatagramSocket socket;
    private Map<String, ClientInfo> clients = new ConcurrentHashMap<>();
    private Map<String, Set<String>> channels = new ConcurrentHashMap<>();
    private Map<String, List<String>> channelHistory = new ConcurrentHashMap<>();

    public ClientHandler(DatagramSocket socket) {
        this.socket = socket;
    }

    public void start() {
        byte[] buffer = new byte[BUFFER_SIZE];

        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                InetAddress clientAddress = packet.getAddress();
                int clientPort = packet.getPort();

                handleMessage(message, clientAddress, clientPort);

            } catch (IOException e) {
                System.err.println("Error receiving packet: " + e.getMessage());
            }
        }
    }

    private void handleMessage(String message, InetAddress clientAddress, int clientPort) {
        ClientInfo client = getClientByAddress(clientAddress, clientPort);
        if (client != null) {
            client.updateLastActive();
        }

        String[] parts = message.split(" ", 3);
        String command = parts[0];

        switch (command) {
            case "/nick":
                if (parts.length >= 2) {
                    String nick = parts[1];
                    registerClient(nick, clientAddress, clientPort);
                }
                break;
            case "/join":
                if (parts.length >= 2) {
                    String channel = parts[1];
                    joinChannel(channel, clientAddress, clientPort);
                }
                break;
            case "/leave":
                if (parts.length >= 2) {
                    String channel = parts[1];
                    leaveChannel(channel, clientAddress, clientPort);
                }
                break;
            case "/channels":
                listChannels(clientAddress, clientPort);
                break;
            case "/msg":
                if (parts.length >= 3) {
                    String recipient = parts[1];
                    String privateMessage = parts[2];
                    sendPrivateMessage(recipient, privateMessage, clientAddress, clientPort);
                }
                break;
            case "/quit":
                removeClient(clientAddress, clientPort);
                break;
            default:
                broadcastMessage(message, clientAddress, clientPort);
                break;
        }
    }

    private void registerClient(String nick, InetAddress address, int port) {
        if (clients.containsKey(nick)) {
            sendMessage("Nickname " + nick + " is already taken", address, port);
            return;
        }

        clients.values().removeIf(c -> c.getAddress().equals(address) && c.getPort() == port);
        clients.put(nick, new ClientInfo(address, port, nick));
        System.out.println(nick + " has joined the chat");
        sendMessage("Welcome " + nick + "! Type /join <channel> to join a channel.", address, port);
    }

    private void joinChannel(String channel, InetAddress clientAddress, int clientPort) {
        String nick = getNickByAddress(clientAddress, clientPort);
        if (nick != null) {
            String currentChannel = clients.get(nick).getCurrentChannel();
            if (currentChannel != null) {
                leaveChannel(currentChannel, clientAddress, clientPort);
            }

            channels.computeIfAbsent(channel, k -> ConcurrentHashMap.newKeySet()).add(nick);
            channelHistory.computeIfAbsent(channel, k -> Collections.synchronizedList(new ArrayList<>()));
            clients.get(nick).setCurrentChannel(channel);

            sendChannelHistory(channel, clientAddress, clientPort);
            String joinMessage = nick + " has joined the channel";
            broadcastToChannel(joinMessage, channel, nick);
            channelHistory.get(channel).add(joinMessage);
        }
    }

    private void sendChannelHistory(String channel, InetAddress address, int port) {
        List<String> history = channelHistory.get(channel);
        if (history != null) {
            sendMessage("--- Channel history for " + channel + " ---", address, port);
            synchronized (history) {
                for (String message : history) {
                    sendMessage(message, address, port);
                }
            }
            sendMessage("--- End of history ---", address, port);
        }
    }

    private void leaveChannel(String channel, InetAddress clientAddress, int clientPort) {
        String nick = getNickByAddress(clientAddress, clientPort);
        if (nick != null && channel.equals(clients.get(nick).getCurrentChannel())) {
            Set<String> channelMembers = channels.get(channel);
            if (channelMembers != null) {
                channelMembers.remove(nick);
                clients.get(nick).setCurrentChannel(null);
                sendMessage("You left channel: " + channel, clientAddress, clientPort);

                String leaveMessage = nick + " has left the channel";
                broadcastToChannel(leaveMessage, channel, null);
                channelHistory.get(channel).add(leaveMessage);

                if (channelMembers.isEmpty()) {
                    channels.remove(channel);
                    channelHistory.remove(channel);
                }
            }
        }
    }

    private void listChannels(InetAddress clientAddress, int clientPort) {
        if (channels.isEmpty()) {
            sendMessage("No active channels", clientAddress, clientPort);
        } else {
            StringBuilder sb = new StringBuilder("Active channels:\n");
            for (Map.Entry<String, Set<String>> entry : channels.entrySet()) {
                sb.append(entry.getKey()).append(" (").append(entry.getValue().size()).append(" users)\n");
            }
            sendMessage(sb.toString(), clientAddress, clientPort);
        }
    }

    private void sendPrivateMessage(String recipient, String message, InetAddress senderAddress, int senderPort) {
        String senderNick = getNickByAddress(senderAddress, senderPort);
        if (senderNick == null) return;

        ClientInfo recipientInfo = clients.get(recipient);
        if (recipientInfo != null) {
            sendMessage("[PM from " + senderNick + "] " + message,
                    recipientInfo.getAddress(), recipientInfo.getPort());
            sendMessage("[PM to " + recipient + "] " + message,
                    senderAddress, clients.get(senderNick).getPort());
        } else {
            sendMessage("User " + recipient + " not found",
                    senderAddress, clients.get(senderNick).getPort());
        }
    }

    private void broadcastToChannel(String message, String channel, String excludeNick) {
        Set<String> channelMembers = channels.get(channel);
        if (channelMembers != null) {
            List<String> history = channelHistory.get(channel);
            if (history != null) {
                history.add(message);
            }

            for (String member : channelMembers) {
                if (!member.equals(excludeNick)) {
                    ClientInfo client = clients.get(member);
                    sendMessage("[" + channel + "] " + message,
                            client.getAddress(), client.getPort());
                }
            }
        }
    }

    private void broadcastMessage(String message, InetAddress senderAddress, int senderPort) {
        String senderNick = getNickByAddress(senderAddress, senderPort);
        if (senderNick == null) return;

        String currentChannel = clients.get(senderNick).getCurrentChannel();
        if (currentChannel != null) {
            String formattedMessage = senderNick + ": " + message;
            broadcastToChannel(formattedMessage, currentChannel, senderNick);
        } else {
            String formattedMessage = "[Global] " + senderNick + ": " + message;
            for (ClientInfo client : clients.values()) {
                if (!(client.getAddress().equals(senderAddress) && client.getPort() == senderPort)) {
                    sendMessage(formattedMessage, client.getAddress(), client.getPort());
                }
            }
        }
    }

    private void removeClient(InetAddress clientAddress, int clientPort) {
        String nick = getNickByAddress(clientAddress, clientPort);
        if (nick != null) {
            String currentChannel = clients.get(nick).getCurrentChannel();
            if (currentChannel != null) {
                leaveChannel(currentChannel, clientAddress, clientPort);
            }
            clients.remove(nick);
            System.out.println(nick + " has left the chat");
        }
    }

    private String getNickByAddress(InetAddress address, int port) {
        for (Map.Entry<String, ClientInfo> entry : clients.entrySet()) {
            ClientInfo client = entry.getValue();
            if (client.getAddress().equals(address) && client.getPort() == port) {
                return entry.getKey();
            }
        }
        return null;
    }

    private ClientInfo getClientByAddress(InetAddress address, int port) {
        for (ClientInfo client : clients.values()) {
            if (client.getAddress().equals(address) && client.getPort() == port) {
                return client;
            }
        }
        return null;
    }

    private void sendMessage(String message, InetAddress address, int port) {
        try {
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("Error sending message: " + e.getMessage());
        }
    }
}