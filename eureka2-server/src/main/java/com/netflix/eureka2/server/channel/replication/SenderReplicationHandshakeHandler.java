/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.eureka2.server.channel.replication;

import com.netflix.eureka2.channel.SourceIdGenerator;
import com.netflix.eureka2.channel.client.ClientHandshakeHandler;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ReplicationHandler;
import com.netflix.eureka2.spi.model.channel.ReplicationClientHello;
import com.netflix.eureka2.spi.model.ChannelModel;

/**
 */
public class SenderReplicationHandshakeHandler extends ClientHandshakeHandler<ChangeNotification<InstanceInfo>, Void> implements ReplicationHandler {

    private final Source clientSource;
    private final EurekaRegistry<InstanceInfo> eurekaRegistry;

    public SenderReplicationHandshakeHandler(Source clientSource, SourceIdGenerator serverIdGenerator, EurekaRegistry<InstanceInfo> eurekaRegistry) {
        super(serverIdGenerator);
        this.clientSource = clientSource;
        this.eurekaRegistry = eurekaRegistry;
    }

    @Override
    protected ChannelNotification<ChangeNotification<InstanceInfo>> createClientHello() {
        ReplicationClientHello clientHello = ChannelModel.getDefaultModel().newReplicationClientHello(clientSource, eurekaRegistry.size());
        return ChannelNotification.newHello(clientHello);
    }
}
