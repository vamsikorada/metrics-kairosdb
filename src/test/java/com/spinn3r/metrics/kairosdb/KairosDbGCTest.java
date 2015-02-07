package com.spinn3r.metrics.kairosdb;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
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

import static com.spinn3r.metrics.kairosdb.TaggedMetrics.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

public class KairosDbGCTest {

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
    public void testSendingToReporter() throws Exception {

        MockClock clock = new MockClock();

        MetricRegistry metricRegistry = new MetricRegistry();

        TaggedMetrics taggedMetrics = new TaggedMetrics( metricRegistry,
                                                         InvalidTagPolicy.FAIL,
                                                         DuplicateTagPolicy.FAIL );

        KairosDbReporter reporter
            = KairosDbReporter.forRegistry( metricRegistry )
              .withTag( "host", "test-host" )
              .withClock( clock )
              .garbageCollectAndDeriveCounters( true )
              .build( kairosDb )
              ;

        final Counter requests = taggedMetrics.counter( TaggedMetricsTest.class, "requests", tag( "foo", "bar" ) );
        requests.inc();

        assertEquals( 1, requests.getCount() );

        reporter.report();

        String out = output.toString();

        System.out.printf( "out: %s\n", out );

        assertTrue( out.contains( " 1 host=test-host foo=bar" ) );

        assertTrue( out.contains( "put com.spinn3r.metrics.kairosdb.TaggedMetricsTest.requests.count" ) );

        System.out.printf( "%s\n", metricRegistry.getMetrics().keySet() );

        assertEquals( 0, requests.getCount() );

        assertEquals( 1, reporter.gcMetricIndex.size() );

        clock.setTime( 10 * 60 * 1000 );

        assertTrue( clock.getTime() > 0 );

        reporter.report();

        assertEquals( 0, reporter.gcMetricIndex.size() );

    }

}

class MockClock extends Clock {

    private long time = 0;

    @Override
    public long getTick() {
        throw new RuntimeException( "not implemented" );
    }

    @Override
    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

}