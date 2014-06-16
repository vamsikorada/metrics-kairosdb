package net.vandenberge.metrics.kairosdb;

/**
 * Control how often we expire metrics from the VM.
 */
public enum ExpirationPolicy {

    /**
     * Perform no expiration on metrics.  Metrics stay around for as long as the
     * VM is online.
     */
    NONE,

    /**
     * Expire based on the expiration interval.
     */
    EXPIRE,

}
