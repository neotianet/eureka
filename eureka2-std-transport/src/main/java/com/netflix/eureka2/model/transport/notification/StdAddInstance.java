/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.eureka2.model.transport.notification;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.StdInstanceInfo;
import com.netflix.eureka2.spi.model.transport.notification.AddInstance;

/**
 */
public class StdAddInstance implements AddInstance {

    private final StdInstanceInfo instanceInfo;

    // For serialization frameworks
    protected StdAddInstance() {
        instanceInfo = null;
    }

    public StdAddInstance(StdInstanceInfo instanceInfo) {
        this.instanceInfo = instanceInfo;
    }

    public InstanceInfo getInstanceInfo() {
        return instanceInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        StdAddInstance that = (StdAddInstance) o;

        return !(instanceInfo != null ? !instanceInfo.equals(that.instanceInfo) : that.instanceInfo != null);
    }

    @Override
    public int hashCode() {
        return instanceInfo != null ? instanceInfo.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "StdAddInstance{instanceInfo=" + instanceInfo + '}';
    }
}
