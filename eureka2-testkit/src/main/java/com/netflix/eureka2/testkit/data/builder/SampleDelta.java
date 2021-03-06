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

package com.netflix.eureka2.testkit.data.builder;

import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.instance.Delta;
import com.netflix.eureka2.model.instance.DeltaBuilder;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfo.Status;
import com.netflix.eureka2.model.instance.InstanceInfoField;

/**
 * @author Tomasz Bak
 */
public enum SampleDelta {

    StatusUp() {
        @Override
        public DeltaBuilder builder() {
            return newBuilder().withDelta(InstanceInfoField.STATUS, Status.UP);
        }
    },
    StatusDown() {
        @Override
        public DeltaBuilder builder() {
            return newBuilder().withDelta(InstanceInfoField.STATUS, Status.DOWN);
        }
    };

    final InstanceInfo baseInstanceInfo;

    SampleDelta() {
        this(SampleInstanceInfo.DiscoveryServer.build());
    }

    SampleDelta(InstanceInfo baseInstanceInfo) {
        this.baseInstanceInfo = baseInstanceInfo;
    }

    public abstract DeltaBuilder builder();

    public <T> Delta<T> build() {
        return (Delta<T>) builder().build();
    }

    DeltaBuilder newBuilder() {
        return InstanceModel.getDefaultModel().newDelta().withId(this.baseInstanceInfo.getId());
    }
}
