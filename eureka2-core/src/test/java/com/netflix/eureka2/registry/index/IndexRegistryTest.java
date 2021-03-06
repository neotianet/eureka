package com.netflix.eureka2.registry.index;

import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.utils.rx.PauseableSubject;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import rx.Subscriber;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * TODO: clean up naming with RegistryIndexTest?:)
 * @author David Liu
 */
public class IndexRegistryTest {

    private static final Iterator<ChangeNotification<InstanceInfo>> EMPTY_CHANGE_NOTIFICATION_IT = Collections.emptyIterator();

    private Interest<InstanceInfo> interest1;
    private Interest<InstanceInfo> interest2;
    private ViewableIndexRegistry<InstanceInfo> indexRegistry;

    @Rule
    public final ExternalResource registryResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            interest1 = Interests.forInstances("abc");
            interest2 = Interests.forInstances("123");

            indexRegistry = new ViewableIndexRegistry<>();
            indexRegistry.forInterest(
                    interest1,
                    PauseableSubject.<ChangeNotification<InstanceInfo>>create(),
                    new InstanceInfoInitStateHolder(EMPTY_CHANGE_NOTIFICATION_IT, Interests.forFullRegistry()));
        }

        @Override
        protected void after() {
            indexRegistry.shutdown();
        }
    };

    @Test(timeout = 60000)
    public void testForInterest() {
        assertThat(indexRegistry.getView().size(), equalTo(1));
    }

    @Test(timeout = 60000)
    public void testShutdown() throws Exception {
        final CountDownLatch completionLatch = new CountDownLatch(2);

        indexRegistry.forInterest(
                Interests.forFullRegistry(),
                PauseableSubject.<ChangeNotification<InstanceInfo>>create(),
                new InstanceInfoInitStateHolder(EMPTY_CHANGE_NOTIFICATION_IT, Interests.forFullRegistry()))
                .subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void onCompleted() {
                        completionLatch.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        Assert.fail("Should not be here");
                    }

                    @Override
                    public void onNext(ChangeNotification<InstanceInfo> notification) {
                        assertThat(notification.isDataNotification(), is(false));
                    }
                });

        indexRegistry.forInterest(
                interest2,
                PauseableSubject.<ChangeNotification<InstanceInfo>>create(),
                new InstanceInfoInitStateHolder(EMPTY_CHANGE_NOTIFICATION_IT, interest2))
                .subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void onCompleted() {
                        completionLatch.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        Assert.fail("Should not be here");
                    }

                    @Override
                    public void onNext(ChangeNotification<InstanceInfo> notification) {
                        assertThat(notification.isDataNotification(), is(false));
                    }
                });

        indexRegistry.shutdown();
        completionLatch.await(1, TimeUnit.MINUTES);
        assertThat(indexRegistry.getView().size(), equalTo(0));
    }


    private static class ViewableIndexRegistry<T> extends IndexRegistryImpl<T> {
        public ConcurrentHashMap<Interest<T>, Index<T>> getView() {
            return interestVsIndex;
        }
    }
}
