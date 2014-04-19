package com.fusionx.relay.call;

public class NickChangeCall extends Call {

    private final String newNick;

    public NickChangeCall(String newNick) {
        this.newNick = newNick;
    }

    @Override
    public String getLineToSendServer() {
        return "NICK " + newNick;
    }
}