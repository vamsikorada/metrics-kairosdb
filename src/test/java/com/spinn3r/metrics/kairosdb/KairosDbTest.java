package com.spinn3r.metrics.kairosdb;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import org.junit.Before;
import org.junit.Test;

import javax.net.SocketFactory;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import static com.spinn3r.metrics.kairosdb.TaggedMetrics.tag;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Fail.failBecauseExceptionWasNotThrown;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.*;

import static org.junit.Assert.*;

public class KairosDbTest {

    private final Map<String,String> NO_TAGS = new HashMap<>();

    private final SocketFactory socketFactory = mock(SocketFactory.class);
    private final InetSocketAddress address = new InetSocketAddress("example.com", 1234);
    private final KairosDb kairosDb = new KairosDb(address, socketFactory);

    private final Socket socket = mock(Socket.class);
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    @Before
    public void setUp() throws Exception {
        when(socket.getOutputStream()).thenReturn(output);

        when(socketFactory.createSocket(any(InetAddress.class),
                                        anyInt())).thenReturn(socket);

    }

    @Test
    public void connect() throws Exception {
        kairosDb.connect();

        verify(socketFactory).createSocket(address.getAddress(), address.getPort());
    }

    @Test
    public void disconnect() throws Exception {
        kairosDb.connect();
        kairosDb.close();

        verify(socket).close();
    }

    @Test
    public void doesNotAllowDoubleConnections() throws Exception {
        kairosDb.connect();
        try {
            kairosDb.connect();
            failBecauseExceptionWasNotThrown(IllegalStateException.class);
        } catch (IllegalStateException e) {
            assertThat(e.getMessage())
                    .isEqualTo("Already connected");
        }
    }

    @Test
    public void writesValues() throws Exception {
        kairosDb.connect();
        kairosDb.send("name", "value", 100, NO_TAGS);

        assertThat(output.toString())
                .isEqualTo("put name 100 value\n");
    }

    @Test
    public void sanitizesNames() throws Exception {
        kairosDb.connect();
        kairosDb.send("name woo", "value", 100, NO_TAGS);

        assertThat(output.toString())
                .isEqualTo("put name-woo 100 value\n");
    }

    @Test
    public void sanitizesValues() throws Exception {
        kairosDb.connect();
        kairosDb.send("name", "value woo", 100, NO_TAGS);

        assertThat(output.toString())
                .isEqualTo("put name 100 value-woo\n");
    }

    @Test
    public void tags1() throws Exception {

        Map<String,String> tags = new HashMap<>();
        tags.put( "foo", "bar" );

        kairosDb.connect();
        kairosDb.send("name woo", "value", 100, tags);

        assertThat(output.toString())
          .isEqualTo("put name-woo 100 value foo=bar\n");

    }

    @Test
    public void tags2() throws Exception {

        Map<String,String> tags = new HashMap<>();
        tags.put( "foo", "bar" );
        tags.put( "cat", "dog" );

        kairosDb.connect();
        kairosDb.send("name woo", "value", 100, tags);

        assertThat(output.toString())
          .isEqualTo("put name-woo 100 value cat=dog foo=bar\n");

    }

    @Test
    public void testSendingToReporter() throws Exception {

        MetricRegistry metricRegistry = new MetricRegistry();

        TaggedMetrics taggedMetrics = new TaggedMetrics( metricRegistry,
                                                         InvalidTagPolicy.FAIL,
                                                         DuplicateTagPolicy.FAIL );

        KairosDbReporter reporter
            = KairosDbReporter.forRegistry( metricRegistry )
              .withTag( "host", "test-host" )
              .build( kairosDb )
              ;

        final Meter requests = taggedMetrics.meter( TaggedMetricsTest.class, "requests", tag( "foo", "bar" ) );
        requests.mark();

        reporter.report();

        String out = output.toString();

        assertTrue( out.contains( " 1 host=test-host foo=bar" ) );

        assertTrue( out.contains( "put com.spinn3r.metrics.kairosdb.TaggedMetricsTest.requests.count" ) );

    }

}
