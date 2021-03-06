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

package com.netflix.eureka2.model;

import com.netflix.eureka2.model.channel.StdClientHello;
import com.netflix.eureka2.model.channel.StdReplicationClientHello;
import com.netflix.eureka2.model.channel.StdReplicationServerHello;
import com.netflix.eureka2.model.channel.StdServerHello;
import com.netflix.eureka2.model.channel.StdHeartbeat;
import com.netflix.eureka2.spi.model.*;
import com.netflix.eureka2.spi.model.channel.*;

/**
 */
public class StdChannelModel extends ChannelModel {

    private static final ChannelModel INSTANCE = new StdChannelModel();

    @Override
    public Heartbeat newHeartbeat() {
        return StdHeartbeat.INSTANCE;
    }

    @Override
    public ClientHello newClientHello(Source clientSource) {
        return new StdClientHello(clientSource);
    }

    @Override
    public ReplicationClientHello newReplicationClientHello(Source clientSource, int registrySize) {
        return new StdReplicationClientHello(clientSource, registrySize);
    }

    @Override
    public ServerHello newServerHello(Source serverSource) {
        return new StdServerHello(serverSource);
    }

    @Override
    public ReplicationServerHello newReplicationServerHello(Source serverSource) {
        return new StdReplicationServerHello(serverSource);
    }

    public static ChannelModel getStdModel() {
        return INSTANCE;
    }
}
