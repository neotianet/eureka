package com.netflix.eureka2;

import java.util.Arrays;
import java.util.List;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.datacenter.AwsDataCenterInfo;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.testkit.data.builder.SampleChangeNotification;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import rx.Observable;

import static org.mockito.Mockito.when;

public class RegistryTestBase {

    public static final String ZUUL = "zuul";

    @Mock
    protected EurekaInterestClient interestClient;

    private InstanceInfo instance(String app, int instId) {
        final AwsDataCenterInfo awsDataCenterInfo = InstanceModel.getDefaultModel().newAwsDataCenterInfo().withInstanceId("Inst-" + instId).build();
        return InstanceModel.getDefaultModel().newInstanceInfo()
                .withApp(app)
                .withId("id#-" + instId)
                .withVipAddress(app + ":8080")
                .withStatus(InstanceInfo.Status.UP)
                .withDataCenterInfo(awsDataCenterInfo).build();
    }

    private Observable<ChangeNotification<InstanceInfo>> buildMockEurekaRegistryObservable() {
        final List<ChangeNotification<InstanceInfo>> notifications = Arrays.asList(
                SampleChangeNotification.ZuulAdd.newNotification(instance(ZUUL, 1)),
                SampleChangeNotification.ZuulAdd.newNotification(instance(ZUUL, 2)),
                SampleChangeNotification.ZuulAdd.newNotification(instance(ZUUL, 3)),
                SampleChangeNotification.ZuulAdd.newNotification(instance(ZUUL, 4)));
        return Observable.from(notifications);
    }

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        when(interestClient.forInterest(Interests.forFullRegistry())).thenReturn(buildMockEurekaRegistryObservable());
    }
}
