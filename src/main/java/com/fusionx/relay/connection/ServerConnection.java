package com.fusionx.relay.connection;

import com.fusionx.relay.Server;
import com.fusionx.relay.ServerConfiguration;
import com.fusionx.relay.misc.InterfaceHolders;

import android.os.Handler;
import android.os.HandlerThread;

public class ServerConnection extends Thread {

    private final Server mServer;

    private final BaseConnection mConnection;

    private final Handler mUiThreadHandler;

    private Handler mServerCallHandler;

    ServerConnection(final ServerConfiguration configuration, final Handler handler) {
        final HandlerThread handlerThread = new HandlerThread("ServerCalls");
        handlerThread.start();
        mServerCallHandler = new Handler(handlerThread.getLooper());

        mServer = new Server(configuration.getTitle(), this);
        mConnection = new BaseConnection(configuration, mServer);
        mUiThreadHandler = handler;
    }

    @Override
    public void run() {
        try {
            mConnection.connectToServer();
        } catch (final Exception ex) {
            mUiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    throw new RuntimeException(ex);
                }
            });
        }
    }

    public void onDisconnect() {
        mServerCallHandler.post(new Runnable() {
            @Override
            public void run() {
                final String status = mServer.getStatus();
                if (status.equals(InterfaceHolders.getEventResponses().getConnectedStatus())) {
                    mConnection.onDisconnect();
                } else if (isAlive()) {
                    interrupt();
                    mConnection.closeSocket();
                }
            }
        });
    }

    public Handler getServerCallHandler() {
        return mServerCallHandler;
    }

    public Server getServer() {
        return mServer;
    }
}