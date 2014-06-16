package net.vandenberge.metrics.kairosdb;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

import static net.vandenberge.metrics.kairosdb.TaggedMetrics.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaggedMetricsTest {

    @Test
    public void testName() throws Exception {

        MetricRegistry metricRegistry = new MetricRegistry();

        TaggedMetrics taggedMetrics = new TaggedMetrics( metricRegistry,
                                                         InvalidTagPolicy.FAIL,
                                                         DuplicateTagPolicy.FAIL );

        assertEquals( "net.vandenberge.metrics.kairosdb.TaggedMetricsTest.foo",
                      taggedMetrics.name( TaggedMetricsTest.class, "foo" ) );

        assertEquals( "net.vandenberge.metrics.kairosdb.TaggedMetricsTest.foo?cat=dog",
                      taggedMetrics.name( TaggedMetricsTest.class, "foo", tag( "cat", "dog" ) ) );

        assertEquals( "net.vandenberge.metrics.kairosdb.TaggedMetricsTest.foo?lion=tiger&cat=dog",
                      taggedMetrics.name( TaggedMetricsTest.class, "foo", tag( "cat", "dog" ),
                                                                          tag( "lion", "tiger" ) ) );

    }

    @Test
    public void testJoin() throws Exception {

        MetricRegistry metricRegistry = new MetricRegistry();

        TaggedMetrics taggedMetrics = new TaggedMetrics( metricRegistry,
                                                         InvalidTagPolicy.FAIL,
                                                         DuplicateTagPolicy.FAIL );

        assertEquals( "", taggedMetrics.join() );

        assertEquals( "foo=bar", taggedMetrics.join( tag( "foo", "bar" ) ) );

        assertEquals( "cat=dog&foo=bar", taggedMetrics.join( tag( "foo", "bar" ),
                                                             tag( "cat", "dog" ) ) );

    }

    @Test(expected = DuplicateTagException.class)
    public void testDuplicateTagsWithFailure() throws Exception {

        MetricRegistry metricRegistry = new MetricRegistry();

        TaggedMetrics taggedMetrics = new TaggedMetrics( metricRegistry,
                                                         InvalidTagPolicy.FAIL,
                                                         DuplicateTagPolicy.FAIL );

        taggedMetrics.join( tag( "cat", "bar" ),
                            tag( "cat", "dog" ) );

    }

    @Test
    public void testDuplicateTagsWithIgnore() throws Exception {

        MetricRegistry metricRegistry = new MetricRegistry();

        TaggedMetrics taggedMetrics = new TaggedMetrics( metricRegistry,
                                                         InvalidTagPolicy.FAIL,
                                                         DuplicateTagPolicy.IGNORE );

        assertEquals( "cat=bar",
                      taggedMetrics.join( tag( "cat", "bar" ),
                                          tag( "cat", "dog" ) ) );

    }


    @Test
    public void testInvalidTagsWithMangle() throws Exception {

        MetricRegistry metricRegistry = new MetricRegistry();

        TaggedMetrics taggedMetrics = new TaggedMetrics( metricRegistry,
                                                         InvalidTagPolicy.MANGLE,
                                                         DuplicateTagPolicy.IGNORE );

        assertEquals( "cat=_&foo=_&_=bar",
                      taggedMetrics.join( tag( null, "bar" ),
                                          tag( "cat", null ),
                                          tag( "foo", "α" ) ) );

    }

    @Test(expected = DuplicateTagException.class)
    public void testDuplicateTagsWithMangle() throws Exception {

        MetricRegistry metricRegistry = new MetricRegistry();

        TaggedMetrics taggedMetrics = new TaggedMetrics( metricRegistry,
                                                         InvalidTagPolicy.MANGLE,
                                                         DuplicateTagPolicy.FAIL );

        assertEquals( "_=_",
                      taggedMetrics.join( tag( null, "bar" ),
                                          tag( "α",   null ) ) );

    }

    @Test(expected = InvalidTagException.class)
    public void testInvalid1() throws Exception {

        MetricRegistry metricRegistry = new MetricRegistry();

        TaggedMetrics taggedMetrics = new TaggedMetrics( metricRegistry,
                                                         InvalidTagPolicy.FAIL,
                                                         DuplicateTagPolicy.FAIL );

        taggedMetrics.join( tag( null, null) );

    }

    @Test(expected = InvalidTagException.class)
    public void testInvalid2() throws Exception {

        MetricRegistry metricRegistry = new MetricRegistry();

        TaggedMetrics taggedMetrics = new TaggedMetrics( metricRegistry,
                                                         InvalidTagPolicy.FAIL,
                                                         DuplicateTagPolicy.FAIL );

        taggedMetrics.join( tag( "foo", null) );

    }

    @Test(expected = InvalidTagException.class)
    public void testInvalid3() throws Exception {

        MetricRegistry metricRegistry = new MetricRegistry();

        TaggedMetrics taggedMetrics = new TaggedMetrics( metricRegistry,
                                                         InvalidTagPolicy.FAIL,
                                                         DuplicateTagPolicy.FAIL );

        taggedMetrics.join( tag( null, "foo") );
    }



    @Test
    public void testInvalid4() throws Exception {

        MetricRegistry metricRegistry = new MetricRegistry();

        TaggedMetrics taggedMetrics = new TaggedMetrics( metricRegistry,
                                                         InvalidTagPolicy.FAIL,
                                                         DuplicateTagPolicy.FAIL );

        String invalidTagCharacters = "?.=&: \r\n\t";

        for (int i = 0; i < invalidTagCharacters.length(); i++) {

            char invalidTagCharacter = invalidTagCharacters.charAt( i );

            try {

                String tagged = taggedMetrics.join( tag( "" + invalidTagCharacter, "foo" ) );

                System.out.printf( "tagged: %s\n", tagged );

                throw new RuntimeException( "Failed " + invalidTagCharacter );

            } catch ( InvalidTagException e ) {
                // this is correct.
            }

        }

    }

    @Test
    public void testInvalid5() throws Exception {

        assertFalse( Tag.isValid( "?" ) );
        assertFalse( Tag.isValid( "." ) );
        assertFalse( Tag.isValid( "=" ) );
        assertFalse( Tag.isValid( "&" ) );
        assertFalse( Tag.isValid( ":" ) );
        assertFalse( Tag.isValid( " " ) );
        assertFalse( Tag.isValid( "\r" ) );
        assertFalse( Tag.isValid( "\n" ) );

    }

    @Test
    public void testUsingTaggedMetrics() throws Exception {

        MetricRegistry metricRegistry = new MetricRegistry();

        TaggedMetrics taggedMetrics = new TaggedMetrics( metricRegistry,
                                                         InvalidTagPolicy.FAIL,
                                                         DuplicateTagPolicy.FAIL );

        final Meter requests = taggedMetrics.meter( TaggedMetricsTest.class, "requests", tag( "foo", "bar" ) );
        requests.mark();

        assertEquals( 1, requests.getCount() );

        assertEquals( 1, taggedMetrics.getMetricRegistry().getNames().size() );

        System.out.printf( "%s", taggedMetrics.getMetricRegistry().getNames() );

        assertTrue( taggedMetrics.getMetricRegistry().getNames().contains( "net.vandenberge.metrics.kairosdb.TaggedMetricsTest.requests?foo=bar" ) );

    }

    @Test
    public void testParse() throws Exception {

        Map<String,String> NO_TAGS = new HashMap<>();

        Map<String,String> firstPair = new HashMap<>();
        firstPair.put( "foo", "bar" );

        Map<String,String> secondPair = new HashMap<>();
        secondPair.put( "foo", "bar" );
        secondPair.put( "cat", "dog" );

        assertEquals( "asdf", parse( "asdf" ).getName() );
        assertEquals( NO_TAGS, parse( "asdf" ).getTags() );

        assertEquals( "asdf", parse( "asdf?" ).getName() );
        assertEquals( NO_TAGS, parse( "asdf?" ).getTags() );

        assertEquals( "asdf", parse( "asdf?foo" ).getName() );
        assertEquals( NO_TAGS, parse( "asdf?foo" ).getTags() );

        assertEquals( "asdf", parse( "asdf?foo=" ).getName() );
        assertEquals( NO_TAGS, parse( "asdf?foo=" ).getTags() );


        assertEquals( "asdf", parse( "asdf?foo=bar" ).getName() );
        assertEquals( firstPair, parse( "asdf?foo=bar" ).getTags() );

        assertEquals( "asdf", parse( "asdf?foo=bar&" ).getName() );
        assertEquals( firstPair, parse( "asdf?foo=bar&" ).getTags() );

        assertEquals( "asdf", parse( "asdf?foo=bar&cat" ).getName() );
        assertEquals( firstPair, parse( "asdf?foo=bar&cat" ).getTags() );

        assertEquals( "asdf", parse( "asdf?foo=bar&cat=" ).getName() );
        assertEquals( firstPair, parse( "asdf?foo=bar&cat=" ).getTags() );

        assertEquals( "asdf", parse( "asdf?foo=bar&cat=dog" ).getName() );
        assertEquals( secondPair, parse( "asdf?foo=bar&cat=dog" ).getTags() );

    }

    @Test
    public void testTagValidity() throws Exception {

        assertFalse( new Tag( null, null ).isValid() );
        assertFalse( new Tag( "asdf", null ).isValid() );
        assertFalse( new Tag( null, "asdf" ).isValid() );

        assertFalse( new Tag( "αБЬℓσ", "αБЬℓσ" ).isValid() );

    }

}