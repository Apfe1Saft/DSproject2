package org.example.server;


import java.net.InetAddress;
public class ClientInfo {
    private InetAddress address;
    private int port;
    private String nickname;
    private String currentChannel;
    private long lastActive;

    public ClientInfo(InetAddress address, int port, String nickname) {
        this.address = address;
        this.port = port;
        this.nickname = nickname;
        this.lastActive = System.currentTimeMillis();
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public String getNickname() {
        return nickname;
    }

    public String getCurrentChannel() {
        return currentChannel;
    }

    public void setCurrentChannel(String channel) {
        this.currentChannel = channel;
    }

    public long getLastActive() {
        return lastActive;
    }

    public void updateLastActive() {
        this.lastActive = System.currentTimeMillis();
    }
}