package com.netflix.eureka2.server;

import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.registry.EurekaRegistry;

import javax.inject.Inject;

/**
 * @author Tomasz Bak
 */
@Singleton
public class EurekaWriteServer extends AbstractEurekaServer {

    @Inject
    public EurekaWriteServer(Injector injector) {
        super(injector);
    }

    public EurekaRegistry<InstanceInfo> getEurekaServerRegistry() {
        return injector.getInstance(EurekaRegistry.class);
    }
}
