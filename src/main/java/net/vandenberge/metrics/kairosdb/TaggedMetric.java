package net.vandenberge.metrics.kairosdb;

import java.util.Map;

/**
 * Represents a parsed tag metric which can be used by the reporter.
 */
class TaggedMetric {

    private final String name;

    private final Map<String,String> tags;

    public TaggedMetric(String name, Map<String, String> tags) {
        this.name = name;
        this.tags = tags;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    @Override
    public String toString() {
        return "TaggedMetric{" +
                 "name='" + name + '\'' +
                 ", tags=" + tags +
                 '}';
    }

}
