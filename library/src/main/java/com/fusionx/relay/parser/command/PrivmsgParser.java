package com.fusionx.relay.parser.command;

import com.fusionx.relay.Channel;
import com.fusionx.relay.PrivateMessageUser;
import com.fusionx.relay.Server;
import com.fusionx.relay.WorldUser;
import com.fusionx.relay.event.NewPrivateMessage;
import com.fusionx.relay.event.channel.ChannelEvent;
import com.fusionx.relay.event.channel.WorldMessageEvent;
import com.fusionx.relay.event.user.WorldPrivateMessageEvent;
import com.fusionx.relay.parser.MentionParser;
import com.fusionx.relay.util.IRCUtils;

import java.util.List;

public class PrivmsgParser extends CommandParser {

    private final CtcpParser mCtcpParser;

    public PrivmsgParser(final Server server, final CtcpParser ctcpParser) {
        super(server);

        mCtcpParser = ctcpParser;
    }

    @Override
    public void onParseCommand(final List<String> parsedArray, final String rawSource) {
        final String message = parsedArray.get(3);

        // PRIVMSGs can be CTCP commands - e.g. actions to a channel or in a PM etc.
        if (CtcpParser.isCtcpCommand(message)) {
            mCtcpParser.onParseCommand(parsedArray, rawSource);
        } else {
            final String nick = IRCUtils.getNickFromRaw(rawSource);
            if (!mServer.shouldIgnoreUser(nick)) {
                final String recipient = parsedArray.get(2);
                if (Channel.isChannelPrefix(recipient.charAt(0))) {
                    onParseChannelMessage(nick, recipient, message);
                } else {
                    onParsePrivateMessage(nick, message);
                }
            }
        }
    }

    private void onParsePrivateMessage(final String nick, final String message) {
        final PrivateMessageUser user = mUserChannelInterface.getPrivateMessageUser(nick);
        if (user == null) {
            mUserChannelInterface.addNewPrivateMessageUser(nick, message, false, false);
            mServerEventBus.post(new NewPrivateMessage(nick));
        } else {
            mServerEventBus.postAndStoreEvent(new WorldPrivateMessageEvent(user, message), user);
        }
    }

    private void onParseChannelMessage(final String sendingNick, final String channelName,
            final String message) {
        final WorldUser sendingUser = mUserChannelInterface.getUserIfExists(sendingNick);
        final Channel channel = mUserChannelInterface.getChannel(channelName);
        final boolean mention = MentionParser.onMentionableCommand(message,
                mServer.getUser().getNick());
        final ChannelEvent event = new WorldMessageEvent(channel, message, sendingUser,
                sendingNick, mention);
        mServerEventBus.postAndStoreEvent(event, channel);
    }
}