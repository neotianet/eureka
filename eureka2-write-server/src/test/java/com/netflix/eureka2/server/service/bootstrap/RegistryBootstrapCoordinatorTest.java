package com.netflix.eureka2.server.service.bootstrap;

import com.netflix.eureka2.health.HealthStatusUpdate;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.Source.Origin;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfo.Status;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.EurekaRegistryImpl;
import com.netflix.eureka2.server.config.bean.BootstrapConfigBean;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import rx.Observable;

import static com.netflix.eureka2.server.config.bean.BootstrapConfigBean.aBootstrapConfig;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class RegistryBootstrapCoordinatorTest {

    private static final InstanceInfo INSTANCE = SampleInstanceInfo.WebServer.build();
    private static final Source SOURCE = InstanceModel.getDefaultModel().createSource(Origin.BOOTSTRAP, "test");

    private final RegistryBootstrapService bootstrapService = mock(RegistryBootstrapService.class);
    private final EurekaRegistry<InstanceInfo> registry = new EurekaRegistryImpl(EurekaRegistryMetricFactory.registryMetrics());

    private RegistryBootstrapCoordinator bootstrapCoordinator;

    @Before
    public void setUp() throws Exception {
        when(bootstrapService.loadIntoRegistry(any(EurekaRegistry.class), any(Source.class))).thenAnswer(new Answer<Observable<Void>>() {
            @Override
            public Observable<Void> answer(InvocationOnMock invocation) throws Throwable {
                registry.connect(SOURCE, Observable.just(new ChangeNotification<>(ChangeNotification.Kind.Add, INSTANCE))).subscribe();
                return Observable.empty();
            }
        });

        BootstrapConfigBean config = aBootstrapConfig().withBootstrapEnabled(true).build();
        bootstrapCoordinator = new RegistryBootstrapCoordinator(config, bootstrapService, registry);
    }

    @Test
    public void testRegistryBootstrap() throws Exception {
        bootstrapCoordinator.bootstrap();

        // If bootstrap is, health status should change to UP
        ExtTestSubscriber<HealthStatusUpdate<?>> testSubscriber = new ExtTestSubscriber<>();
        bootstrapCoordinator.healthStatus().subscribe(testSubscriber);

        assertThat(testSubscriber.takeNextOrWait().getStatus(), is(equalTo(Status.UP)));
    }
}