/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.eureka2.server.channel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.MultipleInterests;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.registry.EurekaRegistryView;
import com.netflix.eureka2.utils.rx.BreakerSwitchOperator;
import rx.Observable;
import rx.subjects.PublishSubject;

/**
 * Interest notification multiplexer is channel scoped object, so we can depend here on its
 * single-threaded property from the channel side. However as we subscribe to multiple observables
 * we need to merge them properly. For that we use {@link rx.Observable#merge} with atomic observable
 * streams wrapped by {@link BreakerSwitchOperator}, which allows us to close the notification
 * stream during interest upgrades with interest removal.
 *
 * Note that the changeNotification stream obtained from the multiplexer will never onComplete regardless
 * of the onComplete state of the individual upgrade streams. The only way to onComplete is to explicitly
 * call .unregister() on the multiplexer.
 *
 * @author Tomasz Bak
 */
public class InterestNotificationMultiplexer {

    private final EurekaRegistryView<InstanceInfo> eurekaRegistryView;

    private final Map<Interest<InstanceInfo>, BreakerSwitchOperator> subscriptionBreakers = new HashMap<>();

    private final PublishSubject<Observable<ChangeNotification<InstanceInfo>>> upgrades = PublishSubject.create();
    private final Observable<ChangeNotification<InstanceInfo>> aggregatedStream = Observable.merge(upgrades);

    public InterestNotificationMultiplexer(EurekaRegistryView<InstanceInfo> eurekaRegistry) {
        this.eurekaRegistryView = eurekaRegistry;
    }

    /**
     * For composite interest, we flatten it first, and than make parallel subscriptionBreakers to the registry.
     */
    public void update(Interest<InstanceInfo> newInterest) {
        if (newInterest instanceof MultipleInterests) {
            Set<Interest<InstanceInfo>> newInterestSet = ((MultipleInterests<InstanceInfo>) newInterest).flatten();
            // Remove interests not present in the new subscription
            Set<Interest<InstanceInfo>> toRemove = new HashSet<>(subscriptionBreakers.keySet());
            toRemove.removeAll(newInterestSet);
            for (Interest<InstanceInfo> item : toRemove) {
                removeInterest(item);
            }
            // Add new interests
            Set<Interest<InstanceInfo>> toAdd = new HashSet<>(newInterestSet);
            toAdd.removeAll(subscriptionBreakers.keySet());
            for (Interest<InstanceInfo> item : toAdd) {
                subscribeToInterest(item);
            }
        } else {
            // First remove all interests except the current one if present
            Set<Interest<InstanceInfo>> toRemove = new HashSet<>(subscriptionBreakers.keySet());
            toRemove.remove(newInterest);
            for (Interest<InstanceInfo> item : toRemove) {
                removeInterest(item);
            }
            // Add new interests
            if (subscriptionBreakers.isEmpty()) {
                subscribeToInterest(newInterest);
            }
        }
    }

    private void subscribeToInterest(Interest<InstanceInfo> newInterest) {
        BreakerSwitchOperator<ChangeNotification<InstanceInfo>> breaker = new BreakerSwitchOperator<>();
        upgrades.onNext(eurekaRegistryView.forInterest(newInterest).lift(breaker));
        subscriptionBreakers.put(newInterest, breaker);
    }

    private void removeInterest(Interest<InstanceInfo> currentInterest) {
        subscriptionBreakers.remove(currentInterest).close();
    }

    public void unregister() {
        for (BreakerSwitchOperator subject : subscriptionBreakers.values()) {
            subject.close();
        }
        subscriptionBreakers.clear();
        upgrades.onCompleted();
    }

    /**
     * Interest channel creates a single subscription to this observable prior to
     * registering any interest set. We can safely use hot observable, which
     * simplifies implementation of the multiplexer.
     */
    public Observable<ChangeNotification<InstanceInfo>> changeNotifications() {
        return aggregatedStream;
    }
}
