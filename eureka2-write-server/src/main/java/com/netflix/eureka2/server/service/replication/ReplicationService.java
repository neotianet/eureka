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

package com.netflix.eureka2.server.service.replication;

import com.netflix.eureka2.channel.SourceIdGenerator;
import com.netflix.eureka2.channel.client.ClientHeartbeatHandler;
import com.netflix.eureka2.metric.server.WriteServerMetricFactory;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.server.ReplicationPeerAddressesProvider;
import com.netflix.eureka2.server.channel.replication.ReplicationLoopException;
import com.netflix.eureka2.server.channel.replication.SenderReplicationHandshakeHandler;
import com.netflix.eureka2.server.channel.replication.SenderReplicationLoopDetectorHandler;
import com.netflix.eureka2.server.channel.replication.SenderRetryableReplicationHandler;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ChannelPipeline;
import com.netflix.eureka2.spi.transport.EurekaClientTransportFactory;
import com.netflix.eureka2.utils.rx.RetryStrategyFunc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Tomasz Bak
 */
@Singleton
public class ReplicationService {

    private static final long HEARTBEAT_INTERVAL_MS = 30 * 1000; // FIXME read from configuration

    enum STATE {Idle, Connected, Closed}

    private static final Logger logger = LoggerFactory.getLogger(ReplicationService.class);

    private final SourceIdGenerator idGenerator = new SourceIdGenerator();

    private final Scheduler scheduler;
    private final AtomicReference<STATE> state = new AtomicReference<>(STATE.Idle);
    private final WriteServerConfig config;
    private final EurekaRegistry<InstanceInfo> eurekaRegistry;
    private final SelfInfoResolver selfInfoResolver;
    private final ReplicationPeerAddressesProvider peerAddressesProvider;
    private final WriteServerMetricFactory metricFactory;
    private final EurekaClientTransportFactory transportFactory;

    protected final Map<Server, Subscription> addressVsPipelineSubscription;

    private InstanceInfo ownInstanceInfo;
    private Source clientSource;

    private Subscription resolverSubscription;

    @Inject
    public ReplicationService(WriteServerConfig config,
                              EurekaRegistry eurekaRegistry,
                              SelfInfoResolver selfInfoResolver,
                              ReplicationPeerAddressesProvider peerAddressesProvider,
                              WriteServerMetricFactory metricFactory,
                              EurekaClientTransportFactory transportFactory) {
        this.config = config;
        this.eurekaRegistry = eurekaRegistry;
        this.selfInfoResolver = selfInfoResolver;
        this.peerAddressesProvider = peerAddressesProvider;
        this.metricFactory = metricFactory;
        this.transportFactory = transportFactory;
        this.addressVsPipelineSubscription = new HashMap<>();
        this.scheduler = Schedulers.computation();
    }

    @PostConstruct
    public void connect() {
        logger.info("Starting replication service");
        if (!state.compareAndSet(STATE.Idle, STATE.Connected)) {
            if (state.get() == STATE.Connected) {
                logger.info("Replication service already started; ignoring subsequent connect");
                return;
            }
            throw new IllegalStateException("ReplicationService already closed");
        }

        resolverSubscription = selfInfoResolver.resolve().take(1)
                .switchMap(new Func1<InstanceInfo, Observable<ChangeNotification<Server>>>() {
                    @Override
                    public Observable<ChangeNotification<Server>> call(InstanceInfo instanceInfo) {
                        ownInstanceInfo = instanceInfo;
                        clientSource = InstanceModel.getDefaultModel().createSource(Source.Origin.REPLICATED, ownInstanceInfo.getId());
                        return peerAddressesProvider.get();
                    }
                })
                .retryWhen(new RetryStrategyFunc(1, TimeUnit.SECONDS))
                .subscribe(new Subscriber<ChangeNotification<Server>>() {
                    @Override
                    public void onCompleted() {
                        logger.debug("Replication server resolver stream completed - write cluster server list will no longer be updated");
                    }

                    @Override
                    public void onError(Throwable e) {
                        logger.error("Replication server resolver stream error - write cluster server list will no longer be updated", e);
                    }

                    @Override
                    public void onNext(ChangeNotification<Server> serverNotif) {
                        Server address = serverNotif.getData();
                        switch (serverNotif.getKind()) {
                            case Add:
                                addServer(address);
                                break;
                            case Delete:
                                removeServer(address);
                        }
                    }
                });
    }

    private void addServer(final Server address) {
        if (state.get() == STATE.Closed) {
            logger.info("Not adding server as the service is already shutdown");
            return;
        }

        if (!addressVsPipelineSubscription.containsKey(address)) {
            logger.info("Adding replication channel to server {}", address);

            Observable<ChangeNotification<InstanceInfo>> localUpdates = eurekaRegistry.forInterest(Interests.forFullRegistry(), Source.matcherFor(Source.Origin.LOCAL));
            Subscription pipelineSubscription = createReplicationPipeline(address).getFirst()
                    .handle(localUpdates.map(update -> ChannelNotification.newData(update)))
                    .subscribe(
                            next -> {
                                // Void
                            },
                            e -> {
                                if (e instanceof ReplicationLoopException) {
                                    logger.info("Removing own address {} from replication pool", address);
                                } else {
                                    logger.error("Replication pipeline for server {} disconnect with an error and will not be retried", address);
                                }
                                removeServer(address);
                            },
                            () -> logger.info("Replication pipeline for server {} onCompleted", address)
                    );

            addressVsPipelineSubscription.put(address, pipelineSubscription);
        }
    }

    private void removeServer(Server address) {
        Subscription subscription = addressVsPipelineSubscription.remove(address);
        if (subscription != null) {
            logger.info("Removing replication target {}", address);
            subscription.unsubscribe();
        }
    }

    @PreDestroy
    public void close() {
        logger.info("Closing replication service");
        STATE prev = state.getAndSet(STATE.Closed);
        if (STATE.Connected == prev) {  // only need to perform shutdown if was previously connected
            resolverSubscription.unsubscribe();
            for (Map.Entry<Server, Subscription> entry : addressVsPipelineSubscription.entrySet()) {
                logger.info("Unsubscribing replication pipeline to server {}", entry.getKey());
                entry.getValue().unsubscribe();
            }
            addressVsPipelineSubscription.clear();
        }
    }

    private ChannelPipeline<ChangeNotification<InstanceInfo>, Void> createReplicationPipeline(Server address) {
        SenderRetryableReplicationHandler retryableHandler = new SenderRetryableReplicationHandler(() -> {
            return Observable.just(new ChannelPipeline<>("replicationInternalPipeline",
                    new SenderReplicationHandshakeHandler(clientSource, idGenerator, eurekaRegistry),
                    new SenderReplicationLoopDetectorHandler(clientSource),
                    new ClientHeartbeatHandler<ChangeNotification<InstanceInfo>, Void>(HEARTBEAT_INTERVAL_MS, scheduler),
                    transportFactory.newReplicationTransport(address)
            ));
        }, config.getReplicationReconnectDelayMs(), scheduler);

        return new ChannelPipeline<>("replicationPipeline", retryableHandler);
    }
}
