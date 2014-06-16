package net.vandenberge.metrics.kairosdb;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Handles working with and building tagged metrics and interacting with the
 * MetricRegistry.
 *
 * TaggedMetrics essentially replaces the MetricRegistry for working with tags.
 *
 * The same methods are present but tags are added to the metric names.
 *
 * Because tags can have invalid characters, duplicates, nulls, etc, we need a way
 * to handle them.  You MUST pick a policy as we aren't going to make this
 * decision for you as it could be vital to your app.
 *
 * We support various modes including mangling the invalid characters, skipping
 * duplicate, tags, etc.
 *
 */
public class TaggedMetrics {

    private static final Logger LOGGER = LoggerFactory.getLogger( TaggedMetrics.class );

    protected static final Map<String,String> NO_TAGS = new HashMap<>();

    private final MetricRegistry metricRegistry;

    private final InvalidTagPolicy invalidTagPolicy;

    private final DuplicateTagPolicy duplicateTagPolicy;

    public TaggedMetrics(MetricRegistry metricRegistry,
                         InvalidTagPolicy invalidTagPolicy,
                         DuplicateTagPolicy duplicateTagPolicy) {

        this.metricRegistry = metricRegistry;
        this.invalidTagPolicy = invalidTagPolicy;
        this.duplicateTagPolicy = duplicateTagPolicy;

    }

    public String name( Class clazz, String name0, Tag... tags ) {
        return createMetricWithTags( MetricRegistry.name( clazz, name0 ), join( tags ) );
    }

    public String name( Class clazz, String name0, String name1, Tag... tags ) {
        return createMetricWithTags( MetricRegistry.name( clazz, name0, name1 ), join( tags ) );
    }

    public String name( Class clazz, String name0, String name1, String name2, Tag... tags ) {
        return createMetricWithTags( MetricRegistry.name( clazz, name0, name1, name2 ), join( tags ) );
    }

    public Meter meter( Class clazz, String name0, Tag... tags ) {
        return metricRegistry.meter( name( clazz, name0, tags ) );
    }

    public Meter meter( Class clazz, String name0, String name1, Tag... tags ) {
        return metricRegistry.meter( name( clazz, name0, name1, tags ) );
    }

    public Meter meter( Class clazz, String name0, String name1, String name2, Tag... tags ) {
        return metricRegistry.meter( name( clazz, name0, name1, name2, tags ) );
    }

    protected String createMetricWithTags( String namePart, String tagPart ) {

        if ( tagPart == null || "".equals( tagPart ) ) {
            return namePart;
        }

        return namePart + "?" + tagPart;
    }

    /**
     * The name of the metric registry.  Exposed so that we only need to keep
     * passing TaggedMetrics and not two objects (including the MetricRegistry)
     * @return
     */
    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }


    /**
     * Create an instance of a tag with the given name and value.
     *
     * @param name
     * @param value
     * @return
     */
    public static Tag tag( String name, String value ) {
        return new Tag( name, value );
    }

    protected String join( Tag... tags ) throws InvalidTagException {

        Map<String,Tag> tagMap = new HashMap<>();

        for (Tag tag : tags) {

            if ( ! tag.isValid() ) {

                String message = "Invalid tag:" + tag.toString();

                handleInvalidTagPolicy( invalidTagPolicy, message );

                if ( invalidTagPolicy == InvalidTagPolicy.MANGLE ||
                     invalidTagPolicy == InvalidTagPolicy.MANGLE_AND_LOG ) {

                    tag = new Tag( Tag.mangle( tag.getName() ),
                                   Tag.mangle( tag.getValue() ) );

                } else {
                    // we can't handle this tag as it's invalid.
                    continue;
                }

            }

            if ( tagMap.containsKey( tag.getName() ) ) {

                String message = "Duplicate tag: " + tag.toString();

                handleDuplicateTag( duplicateTagPolicy, message );

                // only called if we're in ignore policy mode.  Otherwise we will fail
                // and this will never be called.
                continue;

            }

            tagMap.put( tag.getName(), tag );

        }

        return format( tagMap.values() );

    }

    private static void handleInvalidTagPolicy( InvalidTagPolicy invalidTagPolicy, String message )
      throws InvalidTagException {

        switch(invalidTagPolicy) {

            case FAIL:
                throw new InvalidTagException( message );

            case IGNORE_AND_LOG:
                LOGGER.warn( message );

            case IGNORE:
                break;

        }

    }

    private static void handleDuplicateTag( DuplicateTagPolicy duplicateTagPolicy, String message ) {

        switch( duplicateTagPolicy ) {

            case FAIL:
                throw new DuplicateTagException( message );

            case IGNORE_AND_LOG:
                LOGGER.warn( message );

            case IGNORE:
                break;

        }

    }

    /**
     * Format a *clean* set of tags.  We perform sanity testing in join and then
     * only format when we're certain that we're not going to fail due to
     * encoding issues.
     *
     * @param tags
     * @return
     */
    public static String format( Collection<Tag> tags ) {

        StringBuilder buff = new StringBuilder();

        boolean first = true;

        for (Tag tag : tags) {

            if ( ! first ) {
                buff.append( "&" );
            }

            first = false;

            buff.append( tag );

        }

        return buff.toString();

    }

    enum State {

        READING_NAME,

        READING_TAG_NAME,

        READING_TAG_VALUE;

    }

    /**
     * Efficient URL parsing for tags ...
     *
     * @param metric
     * @return
     */
    protected static TaggedMetric parse( String metric ) {

        //NOTE: in retrospect this isn't called very often and using a regexp
        // probably would have been easier to implement

        StringBuilder buff = new StringBuilder();

        Map<String,String> tags = new HashMap<>();

        String name = null;

        State state = State.READING_NAME;

        String tag_name = null;

        String tag_value = null;

        for( int i = 0; i < metric.length(); ++i ) {
            char c = metric.charAt( i );

            switch( c ) {

                case '?':
                    name = buff.toString();
                    buff.setLength( 0 );
                    state = State.READING_TAG_NAME;
                    break;

                case '&':
                    tag_value = buff.toString();
                    buff.setLength( 0 );

                    tags.put( tag_name, tag_value );

                    tag_name = null;
                    tag_value = null;
                    state = State.READING_TAG_NAME;
                    break;

                case '=':
                    tag_name = buff.toString();
                    buff.setLength( 0 );
                    state = State.READING_TAG_VALUE;
                    break;

                default:
                    buff.append( c );
                    break;

            }

        }

        if ( state == State.READING_NAME )
            name = buff.toString();

        if ( state == State.READING_TAG_VALUE ) {

            tag_value = buff.toString();

            if ( tag_value.length() > 0 )
                tags.put( tag_name, tag_value );

        }

        return new TaggedMetric( name, tags );

    }

}
