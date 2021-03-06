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

package com.netflix.eureka2.server.audit.kafka;

import java.net.InetSocketAddress;

import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.netflix.archaius.ConfigProxyFactory;
import com.netflix.eureka2.server.audit.AuditService;
import com.netflix.eureka2.server.audit.AuditServiceController;
import com.netflix.eureka2.server.audit.kafka.config.KafkaAuditServiceConfig;
import com.netflix.eureka2.server.spi.ExtAbstractModule;
import com.netflix.eureka2.utils.StreamedDataCollector;
import com.netflix.governator.auto.annotations.ConditionalOnProfile;

import javax.inject.Singleton;

/**
 * Guice module for injecting {@link AuditService} with Kafka persistence.
 *
 * @author Tomasz Bak
 */
@ConditionalOnProfile(value = {ExtAbstractModule.WRITE_PROFILE, ExtAbstractModule.BRIDGE_PROFILE}, matchAll = false)
public class KafkaAuditServiceModule extends ExtAbstractModule {

    public static final String DEFAULT_CONFIG_PREFIX = "eureka.ext.audit.kafka";

    private static final TypeLiteral<StreamedDataCollector<InetSocketAddress>> STREAMED_DATA_COLLECTOR_TYPE_LITERAL =
            new TypeLiteral<StreamedDataCollector<InetSocketAddress>>() {
            };

    @Override
    protected void configure() {
        bind(STREAMED_DATA_COLLECTOR_TYPE_LITERAL).toProvider(KafkaServersProvider.class);
        bind(AuditServiceController.class).in(Scopes.SINGLETON);
        bind(AuditService.class).to(KafkaAuditService.class);
    }

    @Provides
    @Singleton
    public KafkaAuditServiceConfig getConfiguration(ConfigProxyFactory factory) {
        return factory.newProxy(KafkaAuditServiceConfig.class, DEFAULT_CONFIG_PREFIX);
    }

    public static Module fromConfig(final KafkaAuditServiceConfig config) {
        return new KafkaAuditServiceModule() {
            @Override
            @Provides
            @Singleton
            public KafkaAuditServiceConfig getConfiguration(ConfigProxyFactory factory) {
                return config;
            }
        };
    }
}
