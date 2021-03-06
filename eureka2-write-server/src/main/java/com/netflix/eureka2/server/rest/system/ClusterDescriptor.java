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

package com.netflix.eureka2.server.rest.system;

import java.util.List;

import com.netflix.eureka2.model.instance.InstanceInfo;

/**
 * @author Tomasz Bak
 */
public class ClusterDescriptor {

    private final String clusterId;
    private final List<InstanceInfo> servers;

    public ClusterDescriptor(String clusterId, List<InstanceInfo> servers) {
        this.clusterId = clusterId;
        this.servers = servers;
    }

    public String getClusterId() {
        return clusterId;
    }

    public List<InstanceInfo> getServers() {
        return servers;
    }
}
