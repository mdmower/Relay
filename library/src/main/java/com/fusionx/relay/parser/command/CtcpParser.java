package com.fusionx.relay.parser.command;

import com.fusionx.relay.Channel;
import com.fusionx.relay.PrivateMessageUser;
import com.fusionx.relay.Server;
import com.fusionx.relay.WorldUser;
import com.fusionx.relay.call.VersionCall;
import com.fusionx.relay.event.NewPrivateMessage;
import com.fusionx.relay.event.channel.ChannelEvent;
import com.fusionx.relay.event.channel.WorldActionEvent;
import com.fusionx.relay.event.user.WorldPrivateActionEvent;
import com.fusionx.relay.parser.MentionParser;
import com.fusionx.relay.util.IRCUtils;

import java.util.List;

class CtcpParser extends CommandParser {

    public CtcpParser(Server server) {
        super(server);
    }

    public static boolean isCtcpCommand(final String message) {
        return message.startsWith("\u0001") && message.endsWith("\u0001");
    }

    @Override
    public void onParseCommand(final List<String> parsedArray, final String rawSource) {
        final String normalMessage = parsedArray.get(3);
        final String message = normalMessage.substring(1, normalMessage.length() - 1);

        // TODO - THIS IS INCOMPLETE
        if (message.startsWith("ACTION")) {
            onAction(parsedArray, rawSource);
        } else if (message.startsWith("VERSION")) {
            final String nick = IRCUtils.getNickFromRaw(rawSource);
            mServer.getServerCallBus().post(new VersionCall(nick, "Relay Android Library"));
        }
    }

    private void onAction(final List<String> parsedArray, final String rawSource) {
        final String nick = IRCUtils.getNickFromRaw(rawSource);
        if (!mServer.shouldIgnoreUser(nick)) {
            final String action = parsedArray.get(3).replace("ACTION ", "");
            final String recipient = parsedArray.get(2);
            if (Channel.isChannelPrefix(recipient.charAt(0))) {
                onParseChannelAction(recipient, nick, action);
            } else {
                onParseUserAction(nick, action);
            }
        }
    }

    private void onParseUserAction(final String nick, final String action) {
        final PrivateMessageUser user = mUserChannelInterface.getPrivateMessageUser(nick);
        if (user == null) {
            mUserChannelInterface.addNewPrivateMessageUser(nick, action, true, false);
            mServerEventBus.post(new NewPrivateMessage(nick));
        } else {
            mServerEventBus.postAndStoreEvent(new WorldPrivateActionEvent(user, action), user);
        }
    }

    private void onParseChannelAction(final String channelName, final String userNick,
            final String action) {
        final Channel channel = mUserChannelInterface.getChannel(channelName);
        final WorldUser sendingUser = mUserChannelInterface.getUserIfExists(userNick);
        final boolean mention = MentionParser.onMentionableCommand(action,
                mServer.getUser().getNick());
        final WorldActionEvent event = new WorldActionEvent(channel, action, sendingUser,
                userNick, mention);
        mServerEventBus.postAndStoreEvent(event, channel);
    }
}