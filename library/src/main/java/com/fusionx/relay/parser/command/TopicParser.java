package com.fusionx.relay.parser.command;

import com.fusionx.relay.Channel;
import com.fusionx.relay.ChannelUser;
import com.fusionx.relay.Server;
import com.fusionx.relay.constants.UserListChangeType;

import java.util.List;

public class TopicParser extends CommandParser {

    public TopicParser(Server server) {
        super(server);
    }

    @Override
    public void onParseCommand(List<String> parsedArray, String rawSource) {
        final ChannelUser user = mUserChannelInterface.getUserFromRaw(rawSource);
        final Channel channel = mUserChannelInterface.getChannel(parsedArray.get(2));
        final String setterNick = user.getPrettyNick(channel);
        final String newTopic = parsedArray.get(3);

        final String message = mEventResponses.getTopicChangedMessage(setterNick,
                channel.getTopic(), newTopic);
        channel.setTopic(newTopic);
        mServerEventBus.sendGenericChannelEvent(channel, message, UserListChangeType.NONE);
    }
}