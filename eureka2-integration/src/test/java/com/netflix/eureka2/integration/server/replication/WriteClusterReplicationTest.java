package com.netflix.eureka2.integration.server.replication;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.EurekaRegistrationClient.RegistrationStatus;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfoBuilder;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import rx.Observable;
import rx.Subscription;
import rx.subjects.BehaviorSubject;

import static com.netflix.eureka2.testkit.internal.rx.RxBlocking.iteratorFrom;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.*;
import static com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource.anEurekaDeploymentResource;
import static com.netflix.eureka2.utils.functions.ChangeNotifications.dataOnlyFilter;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * @author David Liu
 */
@Category(IntegrationTest.class)
public class WriteClusterReplicationTest {

    @Rule
    public final EurekaDeploymentResource eurekaDeploymentResource = anEurekaDeploymentResource(2, 0).build();

    /**
     * This test verifies that the data are replicated in both ways between the two cluster nodes.
     * It verifies two cases where one of the nodes came up first (so had no peers first), and the
     * other joined afterwards (so was initialized with one peer already).
     */
    @Test(timeout = 60000)
    public void testWriteClusterReplicationWorksBothWays() throws Exception {
        EurekaRegistrationClient registrationClientToFirst = eurekaDeploymentResource.registrationClientToWriteServer(0);
        EurekaRegistrationClient registrationClientToSecond = eurekaDeploymentResource.registrationClientToWriteServer(1);

        EurekaInterestClient interestClientToFirst = eurekaDeploymentResource.interestClientToWriteServer(0);
        EurekaInterestClient interestClientToSecond = eurekaDeploymentResource.interestClientToWriteServer(1);

        // First -> Second
        testWriteClusterReplicationWorksBothWays(registrationClientToFirst, interestClientToSecond, SampleInstanceInfo.DiscoveryServer.build());

        // Second <- First
        testWriteClusterReplicationWorksBothWays(registrationClientToSecond, interestClientToFirst, SampleInstanceInfo.ZuulServer.build());

        registrationClientToFirst.shutdown();
        registrationClientToSecond.shutdown();

        interestClientToFirst.shutdown();
        interestClientToSecond.shutdown();
    }

    protected void testWriteClusterReplicationWorksBothWays(EurekaRegistrationClient registrationClient,
                                                            EurekaInterestClient interestClient,
                                                            InstanceInfo clientInfo) throws Exception {
        // Register via first write server
        ExtTestSubscriber<RegistrationStatus> registrationSubscriber = new ExtTestSubscriber<>();
        registrationClient.register(Observable.just(clientInfo).concatWith(Observable.never())).subscribe(registrationSubscriber);
        assertThat(registrationSubscriber.takeNext(5, TimeUnit.SECONDS), is(notNullValue()));

        // Subscribe to second write server
        Interest<InstanceInfo> interest = Interests.forApplications(clientInfo.getApp());
        Observable<ChangeNotification<InstanceInfo>> notifications = interestClient.forInterest(interest).filter(dataOnlyFilter());

        Iterator<ChangeNotification<InstanceInfo>> notificationIterator = iteratorFrom(60, TimeUnit.SECONDS, notifications);
        assertThat(notificationIterator.next(), is(addChangeNotificationOf(clientInfo)));

        // Now unregister
        registrationSubscriber.unsubscribe();
        assertThat(notificationIterator.next(), is(deleteChangeNotificationOf(clientInfo)));
    }

    @Test(timeout = 60000)
    public void testWriteClusterReplicationWithRegistrationLifecycle() throws Exception {
        final EurekaRegistrationClient registrationClient = eurekaDeploymentResource.registrationClientToWriteServer(0);
        final EurekaInterestClient interestClient = eurekaDeploymentResource.interestClientToWriteServer(1);

        InstanceInfoBuilder seedBuilder = InstanceModel.getDefaultModel().newInstanceInfo().withId("id123").withApp("app");
        List<InstanceInfo> infos = Arrays.asList(
                seedBuilder.withAppGroup("AAA").build(),
                seedBuilder.withAppGroup("BBB").build(),
                seedBuilder.withAppGroup("CCC").build()
        );

        // Subscribe to second write server
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();
        Interest<InstanceInfo> interest = Interests.forApplications(infos.get(0).getApp());
        interestClient.forInterest(interest).filter(dataOnlyFilter()).subscribe(testSubscriber);

        // We need to wait for notification after each registry update, to avoid compaction
        // on the way.
        BehaviorSubject<InstanceInfo> registrant = BehaviorSubject.create();
        Subscription subscription = registrationClient.register(registrant).subscribe();
        registrant.onNext(infos.get(0));
        assertThat(testSubscriber.takeNextOrWait(), is(addChangeNotificationOf(infos.get(0))));

        registrant.onNext(infos.get(1));
        assertThat(testSubscriber.takeNextOrWait(), is(modifyChangeNotificationOf(infos.get(1))));

        registrant.onNext(infos.get(2));
        assertThat(testSubscriber.takeNextOrWait(), is(modifyChangeNotificationOf(infos.get(2))));

        // do the unregister after we've looked at the register and updates. Otherwise the unregister may process
        // before replication happen which means no data will be replicated to the second write server.
        subscription.unsubscribe();
        assertThat(testSubscriber.takeNextOrWait(), is(deleteChangeNotificationOf(infos.get(2))));

        registrationClient.shutdown();
        interestClient.shutdown();
    }
}
