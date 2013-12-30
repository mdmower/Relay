package com.fusionx.relay.communication;

import com.fusionx.relay.AppUser;
import com.fusionx.relay.Channel;
import com.fusionx.relay.ChannelUser;
import com.fusionx.relay.PrivateMessageUser;
import com.fusionx.relay.Server;
import com.fusionx.relay.User;
import com.fusionx.relay.constants.UserListChangeType;
import com.fusionx.relay.event.ChannelEvent;
import com.fusionx.relay.event.ConnectedEvent;
import com.fusionx.relay.event.DisconnectEvent;
import com.fusionx.relay.event.Event;
import com.fusionx.relay.event.JoinEvent;
import com.fusionx.relay.event.KickEvent;
import com.fusionx.relay.event.MentionEvent;
import com.fusionx.relay.event.NameFinishedEvent;
import com.fusionx.relay.event.NickInUseEvent;
import com.fusionx.relay.event.PartEvent;
import com.fusionx.relay.event.PrivateActionEvent;
import com.fusionx.relay.event.PrivateEvent;
import com.fusionx.relay.event.PrivateMessageEvent;
import com.fusionx.relay.event.PrivateNickChangeEvent;
import com.fusionx.relay.event.PrivateQuitEvent;
import com.fusionx.relay.event.ServerEvent;
import com.fusionx.relay.event.SwitchToServerEvent;
import com.fusionx.relay.misc.InterfaceHolders;
import com.fusionx.relay.util.IRCUtils;
import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

import android.os.Handler;
import android.os.Looper;

import java.util.Collection;

public class ServerEventBus extends Bus {

    private final Handler mMainThread = new Handler(Looper.getMainLooper());

    private final Server mServer;

    private boolean mDisplayed;

    public ServerEventBus(final Server server) {
        super(ThreadEnforcer.ANY);

        mServer = server;
    }

    @Override
    public void post(final Object event) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            super.post(event);
        } else {
            mMainThread.post(new Runnable() {
                @Override
                public void run() {
                    ServerEventBus.super.post(event);
                }
            });
        }
    }

    /*
     * Start of internal methods
     */
    private void sendServerEvent(final Server server, final ServerEvent event) {
        if (server.getServerCache().isCached()) {
            post(event);
        } else {
            server.onServerEvent(event);
        }
    }

    private void sendChannelEvent(final Channel channel, final ChannelEvent event) {
        if (channel.isCached()) {
            post(event);
        } else {
            channel.onChannelEvent(event);
        }
    }

    private void sendUserEvent(final PrivateMessageUser user, final PrivateEvent event) {
        // Make an exception if this is a new private message - we need the client to catch that
        // it's a new message
        if (user.isCached() || event.newPrivateMessage) {
            post(event);
        } else {
            user.onUserEvent(event);
        }
    }
    /*
     * End of internal methods
     */

    // Generic events start
    public ServerEvent sendGenericServerEvent(final Server server, final String message) {
        final ServerEvent event = new ServerEvent(message);
        sendServerEvent(server, event);
        return event;
    }

    public ChannelEvent sendGenericChannelEvent(final Channel channel, final String message,
            final UserListChangeType changeType, final ChannelUser channelUser) {
        final ChannelEvent event = new ChannelEvent(channel.getName(), message,
                changeType, channelUser);
        sendChannelEvent(channel, event);
        return event;
    }

    public ChannelEvent sendGenericChannelEvent(final Channel channel, final String message,
            final UserListChangeType changeType) {
        return sendGenericChannelEvent(channel, message, changeType, null);
    }
    // Generic events end

    public void onDisconnected(final Server server, final String disconnectLine,
            final boolean retryPending) {
        final DisconnectEvent event = new DisconnectEvent(disconnectLine, retryPending, false);
        if (!server.getServerCache().isCached()) {
            server.onServerEvent(event);
        }
        // Post this event no matter what
        post(event);
    }

    public JoinEvent onChannelJoined(final String channelName) {
        final JoinEvent event = new JoinEvent(channelName);
        post(event);
        return event;
    }

    public void onChannelParted(final String channelName) {
        final PartEvent event = new PartEvent(channelName);
        post(event);
    }

    public void onKicked(final String channelName, final String reason) {
        final KickEvent event = new KickEvent(channelName, mServer.getUser().getNick(), reason);
        post(event);
    }

    public ChannelEvent onChannelMessage(final AppUser user, final Channel channel,
            final ChannelUser channelUser, final String rawMessage) {
        return onChannelMessage(user, channel, channelUser.getBracketedNick(channel),
                rawMessage);
    }

    public ChannelEvent onChannelMessage(final AppUser user, final Channel channel,
            final String nick, final String rawMessage) {
        String preMessage = InterfaceHolders.getEventResponses().getMessage(nick, rawMessage);
        if (IRCUtils.splitRawLine(rawMessage, false).contains(user.getNick().toLowerCase())) {
            onUserMentioned(channel.getName());
            preMessage = "<bold>" + preMessage + "</bold>";
        }
        return sendGenericChannelEvent(channel, preMessage, UserListChangeType.NONE);
    }

    public ChannelEvent onChannelAction(final AppUser user, final Channel channel,
            final String nick, final String rawAction) {
        final String finalMessage = InterfaceHolders.getEventResponses()
                .getActionMessage(nick, rawAction);
        if (IRCUtils.splitRawLine(rawAction, false).contains(user.getNick().toLowerCase())) {
            onUserMentioned(channel.getName());
        }
        return sendGenericChannelEvent(channel, finalMessage, UserListChangeType.NONE);
    }

    public ChannelEvent onChannelAction(final AppUser user, final Channel channel,
            final ChannelUser sendingUser, final String rawAction) {
        final String nick = sendingUser.getPrettyNick(channel);
        return onChannelAction(user, channel, nick, rawAction);
    }

    public PrivateEvent onPrivateMessage(final PrivateMessageUser user, final User sending,
            final String rawMessage, final boolean newMessage) {
        final String message = InterfaceHolders.getEventResponses().getMessage(sending
                .getColorfulNick(), rawMessage);
        // TODO - change this to be specific for PMs
        onUserMentioned(user.getNick());

        final PrivateMessageEvent privateMessageEvent = new PrivateMessageEvent(user.getNick(),
                message, newMessage);
        sendUserEvent(user, privateMessageEvent);

        return privateMessageEvent;
    }

    public PrivateEvent onPrivateAction(final PrivateMessageUser user, final User sendingUser,
            final String rawAction, final boolean newMessage) {
        final String message = InterfaceHolders.getEventResponses().getActionMessage(sendingUser
                .getColorfulNick(), rawAction);
        // TODO - change this to be specific for PMs
        if (sendingUser.equals(user)) {
            onUserMentioned(user.getNick());
        }

        final PrivateActionEvent privateMessageEvent = new PrivateActionEvent(message,
                user.getNick(),
                newMessage);
        sendUserEvent(user, privateMessageEvent);

        return privateMessageEvent;
    }

    public Event onNameFinished(final Channel channel, final Collection<ChannelUser> channelUsers) {
        final NameFinishedEvent event = new NameFinishedEvent(channel.getName(), channelUsers);
        if (channel.isObserving()) {
            post(event);
        }
        return event;
    }

    public NickInUseEvent sendNickInUseMessage(final Server server) {
        final NickInUseEvent event = new NickInUseEvent();
        sendServerEvent(server, event);
        return event;
    }

    public SwitchToServerEvent sendSwitchToServerEvent(final Server server, final String message) {
        final SwitchToServerEvent event = new SwitchToServerEvent(message);
        sendServerEvent(server, event);
        return event;
    }

    public void sendConnected(final Server server, final String url) {
        final ConnectedEvent event = new ConnectedEvent(url);
        sendServerEvent(server, event);
    }

    public void sendPrivateQuitEvent(final PrivateMessageUser privateMessageUser,
            final String message) {
        final PrivateQuitEvent event = new PrivateQuitEvent(privateMessageUser.getNick(), message);
        sendUserEvent(privateMessageUser, event);
    }

    public void sendPrivateNickChangeEvent(final PrivateMessageUser privateMessageUser,
            final String oldNick, final String message) {
        final PrivateNickChangeEvent event = new PrivateNickChangeEvent(oldNick, message,
                privateMessageUser.getNick());
        sendUserEvent(privateMessageUser, event);
    }

    public void sendInviteEvent(Server server, String channelName) {
        // TODO figure out what to do here
    }

    // TODO - refine this
    void onUserMentioned(final String messageDestination) {
        if (mDisplayed) {
            post(new MentionEvent(messageDestination));
        } else {
            InterfaceHolders.getEventResponses().onUserMentioned(mServer, messageDestination);
        }
    }

    // Getters and setters
    public void setDisplayed(final boolean toast) {
        mDisplayed = toast;
    }
}