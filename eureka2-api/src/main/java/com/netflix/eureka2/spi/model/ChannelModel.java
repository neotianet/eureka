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

package com.netflix.eureka2.spi.model;

import com.netflix.eureka2.internal.util.ExtLoader;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.spi.model.channel.*;

/**
 */
public abstract class ChannelModel {

    private static volatile ChannelModel defaultModel;

    public abstract Heartbeat newHeartbeat();

    public abstract ClientHello newClientHello(Source clientSource);

    public abstract ReplicationClientHello newReplicationClientHello(Source clientSource, int registrySize);

    public abstract ServerHello newServerHello(Source serverSource);

    public abstract ReplicationServerHello newReplicationServerHello(Source serverSource);

    public static ChannelModel getDefaultModel() {
        if(defaultModel == null) {
            return ExtLoader.resolveDefaultModel().getChannelModel();
        }
        return defaultModel;
    }

    public static ChannelModel setDefaultModel(ChannelModel newModel) {
        ChannelModel previous = defaultModel;
        defaultModel = newModel;
        return previous;
    }
}
