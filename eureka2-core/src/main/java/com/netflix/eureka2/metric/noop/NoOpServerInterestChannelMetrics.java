package com.netflix.eureka2.metric.noop;

import com.netflix.eureka2.metric.server.ServerInterestChannelMetrics;

/**
 * @author Tomasz Bak
 */
public class NoOpServerInterestChannelMetrics implements ServerInterestChannelMetrics {

    public static final NoOpServerInterestChannelMetrics INSTANCE = new NoOpServerInterestChannelMetrics();

    @Override
    public void incrementApplicationNotificationCounter(String applicationName) {
    }

    @Override
    public void incrementSubscriptionCounter(AtomicInterest interestType, String id) {
    }

    @Override
    public void decrementSubscriptionCounter(AtomicInterest interestType, String id) {
    }
}
