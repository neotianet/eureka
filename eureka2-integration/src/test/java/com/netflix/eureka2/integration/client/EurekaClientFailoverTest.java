package com.netflix.eureka2.integration.client;

import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.junit.categories.LongRunningTest;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfo.Status;
import com.netflix.eureka2.model.interest.Interest.Operator;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.embedded.cluster.EmbeddedReadCluster;
import com.netflix.eureka2.testkit.embedded.cluster.EmbeddedWriteCluster;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import com.netflix.eureka2.testkit.netrouter.NetworkLink;
import com.netflix.eureka2.testkit.netrouter.NetworkRouter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Subscription;
import rx.subjects.PublishSubject;

import static com.netflix.eureka2.testkit.junit.EurekaMatchers.addChangeNotificationOf;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.modifyChangeNotificationOf;
import static com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource.anEurekaDeploymentResource;
import static com.netflix.eureka2.utils.functions.ChangeNotifications.dataOnlyFilter;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author Tomasz Bak
 */
@Category({IntegrationTest.class, LongRunningTest.class})
public class EurekaClientFailoverTest {

    private static final Logger logger = LoggerFactory.getLogger(EurekaClientFailoverTest.class);

    private static final InstanceInfo INSTANCE_UP = SampleInstanceInfo.WebServer.build();
    private static final InstanceInfo INSTANCE_DOWN = InstanceModel.getDefaultModel().newInstanceInfo().withInstanceInfo(INSTANCE_UP).withStatus(Status.DOWN).build();

    @Rule
    public final EurekaDeploymentResource eurekaDeploymentResource =
            anEurekaDeploymentResource(1, 1).withNetworkRouter(true).build();

    private NetworkRouter networkRouter;
    private EmbeddedWriteCluster writeCluster;
    private EmbeddedReadCluster readCluster;

    @Before
    public void setUp() throws Exception {
        networkRouter = eurekaDeploymentResource.getEurekaDeployment().getNetworkRouter();
        writeCluster = eurekaDeploymentResource.getEurekaDeployment().getWriteCluster();
        readCluster = eurekaDeploymentResource.getEurekaDeployment().getReadCluster();
    }

    @Test(timeout = 60000)
    public void testRegistrationFailover() throws Exception {
        executeFailoverTest(new Runnable() {
            @Override
            public void run() {
                // Scale the write cluster up, and break network connection to the first node
                logger.info("===== scaling up another write server =====");
                writeCluster.scaleUpByOne();

                // before we break the links, verify that replication has completed to the new server (serverId 1)
                EurekaInterestClient interestClient = eurekaDeploymentResource.getEurekaDeployment().interestClientToWriteServer(1);
                ExtTestSubscriber<ChangeNotification<InstanceInfo>> interestSubscriber = subscribeTo(interestClient, INSTANCE_UP);
                try {
                    assertThat(interestSubscriber.takeNext(60, TimeUnit.SECONDS), is(addChangeNotificationOf(INSTANCE_UP)));
                } catch (InterruptedException e) {
                    fail("test fail");
                } finally {
                    interestClient.shutdown();
                }

                logger.info("===== breaking the network links ... =====");

                NetworkLink registrationLink = networkRouter.getLinkTo(writeCluster.getServer(0).getServerPort());
                NetworkLink interestLink = networkRouter.getLinkTo(writeCluster.getServer(0).getServerPort());
                NetworkLink replicationLink = networkRouter.getLinkTo(writeCluster.getServer(1).getServerPort());
                interestLink.disconnect(1, TimeUnit.SECONDS);
                replicationLink.disconnect(1, TimeUnit.SECONDS);
                registrationLink.disconnect(1, TimeUnit.SECONDS);

                logger.info("===== done breaking the network links =====");
            }
        });
    }

    @Test(timeout = 60000)
    public void testInterestFailover() throws Exception {
        executeFailoverTest(new Runnable() {
            @Override
            public void run() {
                // Scale the read cluster up, and break the network connection to the first read server
                readCluster.scaleUpByOne();
                NetworkLink networkLink = networkRouter.getLinkTo(readCluster.getServer(0).getServerPort());
                networkLink.disconnect(1, TimeUnit.SECONDS);
            }
        });
    }

    private void executeFailoverTest(Runnable failureInjector) throws Exception {
        EurekaRegistrationClient registrationClient = eurekaDeploymentResource.getEurekaDeployment().registrationClientToWriteCluster();
        EurekaInterestClient interestClient = eurekaDeploymentResource.getEurekaDeployment().cannonicalInterestClient();
        // Subscribe to instance registration updates
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> interestSubscriber = subscribeTo(interestClient, INSTANCE_UP);

        // Register
        PublishSubject<InstanceInfo> registrationSubject = PublishSubject.create();
        Subscription registrationSubscription = registrationClient.register(registrationSubject).subscribe();
        registrationSubject.onNext(INSTANCE_UP);

        logger.info("===== waiting for test instance to be UP =====");
        assertThat(interestSubscriber.takeNext(60, TimeUnit.SECONDS), is(addChangeNotificationOf(INSTANCE_UP)));

        // Inject failure
        failureInjector.run();

        // Update instance status, and verify that it was handled
        registrationSubject.onNext(INSTANCE_DOWN);

        assertThat(registrationSubscription.isUnsubscribed(), is(false));
        assertThat(interestSubscriber.takeNext(60, TimeUnit.SECONDS), is(modifyChangeNotificationOf(INSTANCE_DOWN)));

        logger.info("TEST COMPLETED");
    }

    private ExtTestSubscriber<ChangeNotification<InstanceInfo>> subscribeTo(EurekaInterestClient interestClient, InstanceInfo instanceInfo) {
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> interestSubscriber = new ExtTestSubscriber<>();
        interestClient.forInterest(Interests.forInstance(Operator.Equals, instanceInfo.getId()))
                .filter(dataOnlyFilter())
                .subscribe(interestSubscriber);
        return interestSubscriber;
    }
}
