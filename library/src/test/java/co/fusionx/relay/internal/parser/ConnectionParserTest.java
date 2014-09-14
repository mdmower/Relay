package co.fusionx.relay.internal.parser;

import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.util.HashSet;

import co.fusionx.relay.constants.CapCapability;
import co.fusionx.relay.core.ConnectionConfiguration;
import co.fusionx.relay.core.SessionConfiguration;
import co.fusionx.relay.event.Event;
import co.fusionx.relay.internal.base.RelayServer;
import co.fusionx.relay.internal.base.TestUtils;
import co.fusionx.relay.internal.bus.FakePostable;
import co.fusionx.relay.internal.core.DefaultSettingsProvider;
import co.fusionx.relay.internal.core.InternalServer;
import co.fusionx.relay.internal.sender.CapPacketSender;
import co.fusionx.relay.internal.sender.InternalPacketSender;
import co.fusionx.relay.internal.sender.InternalSender;
import co.fusionx.relay.internal.sender.PacketSender;
import co.fusionx.relay.internal.sender.RelayServerSender;
import co.fusionx.relay.sender.ServerSender;

import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.assertj.core.api.Assertions.assertThat;

public class ConnectionParserTest {

    private BufferedReader mBufferedReaderForTesting;

    private ConnectionParser mConnectionParser;

    private InternalServer mServer;

    private FakePostable<Event> mSessionBus;

    private HashSet<CapCapability> mCapabilities;

    @Before
    public void setup() throws IOException {
        final ConnectionConfiguration.Builder saslBuilder = TestUtils.getFreenodeBuilderSasl();
        final ConnectionConfiguration saslConfiguration = saslBuilder.build();

        final SessionConfiguration configuration = new SessionConfiguration.Builder()
                .setConnectionConfiguration(saslConfiguration).build();

        final PipedReader readerForTesting = new PipedReader();
        final PipedWriter writerForParser = new PipedWriter(readerForTesting);

        mBufferedReaderForTesting = new BufferedReader(readerForTesting);
        final BufferedWriter bufferedWriterForParser = new BufferedWriter(writerForParser);

        final PacketSender packetSender = new PacketSender(new DefaultSettingsProvider(),
                newDirectExecutorService());
        packetSender.onOutputStreamCreated(bufferedWriterForParser);

        final ServerSender serverSender = new RelayServerSender(packetSender
        );

        final CapPacketSender capPacketSender = new CapPacketSender(packetSender);
        final InternalSender internalSender = new InternalPacketSender(packetSender);

        mSessionBus = new FakePostable<>();
        mCapabilities = new HashSet<>();

        mServer = new RelayServer(mSessionBus, configuration, serverSender, mCapabilities);
        mConnectionParser = new ConnectionParser(saslConfiguration,
                mServer, internalSender, capPacketSender);
    }

    @Test
    public void testCapIRC31Requests() throws IOException {
        // Test
        mConnectionParser.parseLine("CAP * LS :multi-prefix");
        assertThat(mBufferedReaderForTesting.readLine())
                .isEqualTo("CAP REQ :multi-prefix");

        mConnectionParser.parseLine("CAP * ACK :multi-prefix");
        assertThat(mCapabilities).contains(CapCapability.MULTIPREFIX);
    }

    @Test
    public void testSaslAuthentication() throws IOException {
        // Test
        mConnectionParser.parseLine("CAP * LS :sasl");
        assertThat(mBufferedReaderForTesting.readLine())
                .isEqualTo("CAP REQ :sasl");

        mConnectionParser.parseLine("CAP * ACK :sasl");
        assertThat(mBufferedReaderForTesting.readLine())
                .isEqualTo("AUTHENTICATE PLAIN");

        mConnectionParser.parseLine("AUTHENTICATE +");
        assertThat(mBufferedReaderForTesting.readLine())
                .isEqualTo("AUTHENTICATE cmVsYXkAcmVsYXkAcmVsYXk=");
    }
}