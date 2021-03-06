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

package com.netflix.eureka2.utils.rx;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import com.netflix.eureka2.utils.rx.ResourceObservable.ResourceLoader;
import com.netflix.eureka2.utils.rx.ResourceObservable.ResourceLoaderException;
import com.netflix.eureka2.utils.rx.ResourceObservable.ResourceUpdate;
import com.netflix.eureka2.utils.rx.ResourceObservableTest.ItemUpdate.Operation;
import org.junit.Test;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static java.util.Collections.newSetFromMap;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class ResourceObservableTest {

    private static final int REFRESH = 30;
    private static final int IDLE = 300;

    private final TestScheduler scheduler = Schedulers.test();
    private final ItemLoader loader = new ItemLoader();
    private final Observable<ItemUpdate> resourceObservable = ResourceObservable.fromResource(loader, REFRESH, IDLE, TimeUnit.SECONDS, scheduler);
    private final Set<String> collector = new HashSet<>();
    private final Set<String> expected = new HashSet<>();

    @Test(timeout = 60000)
    public void testTaskIsScheduledPeriodically() throws Exception {
        subscribe();

        // Add first item and pass over refresh period
        ItemUpdate addA = new ItemUpdate(Operation.Add, "A");
        loader.add(addA);
        expected.add("A");

        // Add second item and pass over refresh period
        ItemUpdate addB = new ItemUpdate(Operation.Add, "B");
        loader.add(addA, addB);
        expected.add("B");

        scheduler.advanceTimeBy(REFRESH, TimeUnit.SECONDS);
        assertThat(collector, is(equalTo(expected)));

        // Add third item, remove first and pass over refresh period
        ItemUpdate addC = new ItemUpdate(Operation.Add, "C");
        ItemUpdate removeA = new ItemUpdate(Operation.Remove, "A");
        loader.add(addB, addC);
        loader.remove(removeA);
        expected.add("C");
        expected.remove("A");

        scheduler.advanceTimeBy(REFRESH, TimeUnit.SECONDS);
        assertThat(collector, is(equalTo(expected)));
    }

    @Test(timeout = 60000)
    public void testTaskIsCanceledIfNoSubscriber() throws Exception {
        Subscription subscription = subscribe();

        // Add first item and pass over refresh period
        ItemUpdate addA = new ItemUpdate(Operation.Add, "A");
        loader.add(addA);
        expected.add("A");

        scheduler.advanceTimeBy(REFRESH, TimeUnit.SECONDS);
        assertThat(collector, is(equalTo(expected)));

        // Now unsubscribe and subscribe again
        subscription.unsubscribe();
        collector.clear();
        subscription = subscribe();

        assertThat(collector, is(equalTo(expected)));

        // Now unsubscribe and wait till idle timeout expires
        subscription.unsubscribe();
        collector.clear();
        scheduler.advanceTimeBy(IDLE, TimeUnit.SECONDS);

        subscribe();
        assertThat(collector.size(), is(0));
    }

    @Test
    public void testNonRecoverableErrorIsPropagatedToClient() throws Exception {
        ExtTestSubscriber<ItemUpdate> testSubscriber = new ExtTestSubscriber<>();
        resourceObservable.subscribe(testSubscriber);

        loader.sendError(new ResourceLoaderException("error", false, new Exception()));
        scheduler.advanceTimeBy(REFRESH, TimeUnit.SECONDS);

        testSubscriber.assertOnError();
    }

    @Test
    public void testRecoverableErrorIsNotPropagatedToClient() throws Exception {
        ExtTestSubscriber<ItemUpdate> testSubscriber = new ExtTestSubscriber<>();
        resourceObservable.subscribe(testSubscriber);

        // First resolve round
        ItemUpdate addA = new ItemUpdate(Operation.Add, "A");
        loader.add(addA);

        scheduler.advanceTimeBy(REFRESH, TimeUnit.SECONDS);
        assertThat(testSubscriber.takeNext(1), is(equalTo(singletonList(addA))));

        // Trigger recoverable error
        loader.sendError(new ResourceLoaderException("error", true, new Exception()));
        scheduler.advanceTimeBy(REFRESH, TimeUnit.SECONDS);

        testSubscriber.assertOpen();
        assertThat(testSubscriber.takeNext(), is(nullValue()));

        // Now another update round
        loader.cancelError();

        ItemUpdate addB = new ItemUpdate(Operation.Add, "B");
        loader.add(addB);

        scheduler.advanceTimeBy(REFRESH, TimeUnit.SECONDS);
        assertThat(testSubscriber.takeNext(1), is(equalTo(singletonList(addB))));
    }

    private Subscription subscribe() {
        return resourceObservable.subscribe(new Action1<ItemUpdate>() {
            @Override
            public void call(ItemUpdate itemUpdate) {
                if (itemUpdate.getOperation() == Operation.Add) {
                    collector.add(itemUpdate.getValue());
                } else { // Remove
                    collector.remove(itemUpdate.getValue());
                }
            }
        });
    }

    static class ItemUpdate {
        enum Operation {Add, Remove}

        Operation operation;
        String value;

        ItemUpdate(Operation operation, String value) {
            this.operation = operation;
            this.value = value;
        }

        public Operation getOperation() {
            return operation;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "ItemUpdate{operation=" + operation + ", value='" + value + '\'' + '}';
        }
    }

    static class ItemLoader implements ResourceLoader<ItemUpdate> {

        private volatile Set<ItemUpdate> addQueue = newSetFromMap(new ConcurrentHashMap<ItemUpdate, Boolean>());
        private volatile Set<ItemUpdate> removeQueue = newSetFromMap(new ConcurrentHashMap<ItemUpdate, Boolean>());
        private volatile ResourceLoaderException error;

        @Override
        public ResourceUpdate<ItemUpdate> reload(Set<ItemUpdate> currentSnapshot) {
            if (error != null) {
                throw error;
            }
            Set<ItemUpdate> newItems = addQueue;
            addQueue = newSetFromMap(new ConcurrentHashMap<ItemUpdate, Boolean>());
            Set<ItemUpdate> cancelled = removeQueue;
            removeQueue = newSetFromMap(new ConcurrentHashMap<ItemUpdate, Boolean>());
            return new ResourceUpdate<>(newItems, cancelled);
        }

        public void add(ItemUpdate... itemUpdates) {
            Collections.addAll(addQueue, itemUpdates);
        }

        public void remove(ItemUpdate... itemUpdates) {
            Collections.addAll(removeQueue, itemUpdates);
        }

        public void sendError(ResourceLoaderException error) {
            this.error = error;
        }

        public void cancelError() {
            this.error = null;
        }
    }
}