package com.netflix.eureka2.server.service.selfinfo;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.model.datacenter.DataCenterInfo;
import com.netflix.eureka2.model.datacenter.LocalDataCenterInfo;
import com.netflix.eureka2.model.instance.InstanceInfoBuilder;
import com.netflix.eureka2.server.config.EurekaInstanceInfoConfig;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;
import com.netflix.eureka2.testkit.data.builder.SampleAwsDataCenterInfo;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.functions.Func0;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class PeriodicDataCenterInfoResolverTest {

    private static final long RESOLVE_INTERVAL = 30L;
    private static final int WEB_ADMIN_PORT = 8088;

    private final TestScheduler scheduler = Schedulers.test();
    private final TestSubscriber<InstanceInfoBuilder> testSubscriber = new TestSubscriber<>();
    private final DataCenterInfo dataCenterInfo1 = SampleAwsDataCenterInfo.UsEast1a.build();
    private final DataCenterInfo dataCenterInfo2 = SampleAwsDataCenterInfo.UsEast1c.build();

    private EurekaInstanceInfoConfig mockInstanceConfig;
    private EurekaServerTransportConfig mockTransportConfig;
    private Func0 dataCenterInfoFunc;
    private PeriodicDataCenterInfoResolver resolver;

    @Before
    public void setUp() {
        mockInstanceConfig = mock(EurekaInstanceInfoConfig.class);
        when(mockInstanceConfig.getDataCenterResolveIntervalSec()).thenReturn(RESOLVE_INTERVAL);
        when(mockInstanceConfig.getDataCenterType()).thenReturn(LocalDataCenterInfo.DataCenterType.AWS);

        mockTransportConfig = mock(EurekaServerTransportConfig.class);
        when(mockTransportConfig.getWebAdminPort()).thenReturn(WEB_ADMIN_PORT);

        dataCenterInfoFunc = mock(Func0.class);
        when(dataCenterInfoFunc.call())
                .thenReturn(Observable.just(dataCenterInfo1))
                .thenReturn(Observable.just(dataCenterInfo2));
    }

    @Test
    public void testUpdatesToDataCenterInfo() throws Exception {
        resolver = new PeriodicDataCenterInfoResolver(mockInstanceConfig, mockTransportConfig, dataCenterInfoFunc, scheduler);

        // use resolveMutable to get the builder the .build() will fail due to not having an InstanceInfo id
        resolver.resolveMutable().subscribe(testSubscriber);

        scheduler.triggerActions();

        List<InstanceInfoBuilder> infos = testSubscriber.getOnNextEvents();
        assertThat(infos.size(), is(1));
        assertThat(infos.get(0).withId("something").build().getDataCenterInfo(), is(dataCenterInfo1));

        scheduler.advanceTimeBy(RESOLVE_INTERVAL, TimeUnit.SECONDS);
        infos = testSubscriber.getOnNextEvents();
        assertThat(infos.size(), is(2));
        assertThat(infos.get(0).withId("something").build().getDataCenterInfo(), is(dataCenterInfo1));
        assertThat(infos.get(1).withId("something").build().getDataCenterInfo(), is(dataCenterInfo2));

        testSubscriber.assertNoErrors();
    }

    @Test
    public void testHandleErrors() {
        when(dataCenterInfoFunc.call())
                .thenReturn(Observable.error(new Exception("test error")))
                .thenReturn(Observable.just(dataCenterInfo1));

        resolver = new PeriodicDataCenterInfoResolver(mockInstanceConfig, mockTransportConfig, dataCenterInfoFunc, scheduler);

        // use resolveMutable to get the builder the .build() will fail due to not having an InstanceInfo id
        resolver.resolveMutable().subscribe(testSubscriber);

        scheduler.triggerActions();

        List<InstanceInfoBuilder> infos = testSubscriber.getOnNextEvents();
        assertThat(infos.size(), is(0));

        scheduler.advanceTimeBy(RESOLVE_INTERVAL, TimeUnit.SECONDS);
        infos = testSubscriber.getOnNextEvents();

        assertThat(infos.size(), is(1));
        assertThat(infos.get(0).withId("something").build().getDataCenterInfo(), is(dataCenterInfo1));

        testSubscriber.assertNoErrors();
    }
}
