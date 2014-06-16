package net.vandenberge.metrics.kairosdb;

/**
 * The policy for handling invalid tags.  It's possible that someone could
 * attempt to log a tag with a null name and a null value.  This could break a
 * reporter as this behavior is undefined.  Further, tags have constrained names
 * values as well as the metric name.  A metric name can not include ?...
 */
public enum InvalidTagPolicy {

    /**
     * Completely fail to report the event.  The code will throw an exception
     * when you create a tag which is invalid.
     */
    FAIL,

    /**
     * This metric will not be used and instead we will just ignore it.
     */
    IGNORE,

    /**
     * This metric will not be used and instead we will just ignore it.
     *
     * Additionally we log that the tag is invalid and the name of the metric.
     */
    IGNORE_AND_LOG,

    /**
     * Mangle the tag name and value based on rules in the underlying reporter.
     * This means replacing invalid characters (including null) with underscore.
     *
     */
    MANGLE,

    /**
     * Mangle the tag name and/or value and log that this is happening.
     */
    MANGLE_AND_LOG
    ;

}
