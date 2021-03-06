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

package com.netflix.eureka2.server;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import com.netflix.eureka2.metric.server.SpectatorWriteServerMetricFactory;
import com.netflix.eureka2.metric.server.WriteServerMetricFactory;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.EurekaRegistryImpl;
import com.netflix.eureka2.registry.EurekaRegistryView;
import com.netflix.eureka2.server.audit.AuditServiceController;
import com.netflix.eureka2.server.config.EurekaInstanceInfoConfig;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;
import com.netflix.eureka2.server.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.server.registry.PreservableRegistrationProcessor;
import com.netflix.eureka2.server.registry.RegistrationChannelProcessorProvider;
import com.netflix.eureka2.server.rest.WriteServerRootResource;
import com.netflix.eureka2.server.service.EurekaWriteServerSelfInfoResolver;
import com.netflix.eureka2.server.service.EurekaWriteServerSelfRegistrationService;
import com.netflix.eureka2.server.service.SelfRegistrationService;
import com.netflix.eureka2.server.service.bootstrap.BackupClusterBootstrapService;
import com.netflix.eureka2.server.service.bootstrap.RegistryBootstrapCoordinator;
import com.netflix.eureka2.server.service.bootstrap.RegistryBootstrapService;
import com.netflix.eureka2.server.service.replication.ReplicationService;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.eureka2.server.spi.ExtensionContext;
import com.netflix.eureka2.server.transport.EurekaTransportServer;
import com.netflix.eureka2.spi.transport.EurekaClientTransportFactory;
import com.netflix.eureka2.spi.transport.EurekaServerTransportFactory;
import io.reactivex.netty.metrics.MetricEventsListenerFactory;
import io.reactivex.netty.spectator.SpectatorEventsListenerFactory;
import rx.schedulers.Schedulers;

import static com.netflix.eureka2.Names.EUREKA_SERVICE;

/**
 * @author Tomasz Bak
 */
public class EurekaWriteServerModule extends AbstractEurekaServerModule {

    @Override
    protected void configure() {
        bindBase();
        bindMetricFactories();
        bindSelfInfo();
        bindEurekaTransportServer();

        bindBootstrapComponents();

        bindInterestComponents();
        bindRegistrationComponents();
        bindReplicationComponents();

        bindRegistryComponents();

        // REST
        bind(WriteServerRootResource.class).asEagerSingleton();

        // write server specific stuff
        bind(ExtensionContext.class).in(Scopes.SINGLETON);
        bind(ServerType.class).toInstance(ServerType.Write);
        bind(AbstractEurekaServer.class).to(EurekaWriteServer.class);
        bind(WriteServerMetricFactory.class).to(SpectatorWriteServerMetricFactory.class).asEagerSingleton();
    }

    protected void bindSelfInfo() {
        bind(SelfInfoResolver.class).to(EurekaWriteServerSelfInfoResolver.class);
        bind(SelfRegistrationService.class).to(EurekaWriteServerSelfRegistrationService.class);
    }

    protected void bindRegistrationComponents() {
        bind(MetricEventsListenerFactory.class)
                .annotatedWith(Names.named(com.netflix.eureka2.Names.EUREKA_SERVICE))
                .toInstance(new SpectatorEventsListenerFactory("registration-rx-client-", "registration-rx-server-"));
        bind(EurekaClientTransportFactory.class).toInstance(EurekaClientTransportFactory.getDefaultFactory());
        bind(EurekaServerTransportFactory.class).toInstance(EurekaServerTransportFactory.getDefaultFactory());
        bind(AuditServiceController.class).asEagerSingleton();

        bind(EurekaRegistrationProcessor.class).to(PreservableRegistrationProcessor.class).in(Scopes.SINGLETON);
        bind(EurekaRegistrationProcessor.class)
                .annotatedWith(Names.named(EUREKA_SERVICE))
                .toProvider(RegistrationChannelProcessorProvider.class);
    }

    protected void bindReplicationComponents() {
        bind(MetricEventsListenerFactory.class)
                .annotatedWith(Names.named(com.netflix.eureka2.Names.REPLICATION))
                .toInstance(new SpectatorEventsListenerFactory("replication-rx-client-", "replication-rx-server-"));
        bind(ReplicationService.class).asEagerSingleton();
    }

    protected void bindRegistryComponents() {
        bind(EurekaRegistryView.class).to(EurekaRegistry.class);
        bind(EurekaRegistry.class).to(EurekaRegistryImpl.class).asEagerSingleton();
    }

    protected void bindBootstrapComponents() {
        bind(RegistryBootstrapCoordinator.class).asEagerSingleton();
        bind(RegistryBootstrapService.class).to(BackupClusterBootstrapService.class);
    }

    protected void bindEurekaTransportServer() {
        bind(EurekaTransportServer.class).toProvider(WriteServerTransportProvider.class);
    }


    @Singleton
    static class WriteServerTransportProvider implements Provider<EurekaTransportServer> {

        private final EurekaTransportServer transportServer;

        @Inject
        WriteServerTransportProvider(EurekaServerTransportFactory transportFactory,
                                     EurekaServerTransportConfig config,
                                     @Named(EUREKA_SERVICE) Provider<EurekaRegistrationProcessor> registrationProcessor,
                                     @Named(EUREKA_SERVICE) MetricEventsListenerFactory servoEventsListenerFactory,
                                     EurekaRegistry registry,
                                     EurekaRegistryView registryView,
                                     EurekaInstanceInfoConfig instanceInfoConfig) {
            this.transportServer = new EurekaTransportServer(transportFactory, config, registrationProcessor, servoEventsListenerFactory, registry, registryView, instanceInfoConfig, Schedulers.computation());
        }

        @Override
        public EurekaTransportServer get() {
            return transportServer;
        }
    }
}
