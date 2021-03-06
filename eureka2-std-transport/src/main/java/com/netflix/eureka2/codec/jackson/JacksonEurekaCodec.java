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

package com.netflix.eureka2.codec.jackson;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netflix.eureka2.codec.jackson.mixin.DataCenterInfoMixIn;
import com.netflix.eureka2.model.channel.*;
import com.netflix.eureka2.model.datacenter.DataCenterInfo;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.StdInstanceInfo;
import com.netflix.eureka2.model.transport.StdAcknowledgement;
import com.netflix.eureka2.model.transport.StdGoAway;
import com.netflix.eureka2.model.transport.StdInterestRegistration;
import com.netflix.eureka2.model.transport.notification.StdUpdateInstanceInfo;
import com.netflix.eureka2.model.transport.notification.StdAddInstance;
import com.netflix.eureka2.model.transport.notification.StdDeleteInstance;
import com.netflix.eureka2.model.transport.notification.StdStreamStateUpdate;
import com.netflix.eureka2.spi.codec.EurekaCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
public class JacksonEurekaCodec extends EurekaCodec {

    private static final Logger logger = LoggerFactory.getLogger(JacksonEurekaCodec.class);

    static final Class<?>[] REGISTRATION_PROTOCOL_MODEL = {
            StdInstanceInfo.class, StdHeartbeat.class, StdAcknowledgement.class, StdGoAway.class, StdClientHello.class, StdServerHello.class
    };

    static final Class<?>[] REPLICATION_PROTOCOL_MODEL = {
            StdHeartbeat.class, StdReplicationClientHello.class, StdReplicationServerHello.class,
            StdAddInstance.class, StdDeleteInstance.class, StdStreamStateUpdate.class,
            StdAcknowledgement.class
    };

    static final Class<?>[] INTEREST_PROTOCOL_MODEL = {
            StdInterestRegistration.class, StdHeartbeat.class,
            StdAddInstance.class, StdDeleteInstance.class, StdUpdateInstanceInfo.class, StdStreamStateUpdate.class,
            StdAcknowledgement.class
    };

    static final Set<Class<?>> SUPPORTED_TYPES = new HashSet<>(combined(REGISTRATION_PROTOCOL_MODEL, REPLICATION_PROTOCOL_MODEL, INTEREST_PROTOCOL_MODEL));

    static final ObjectMapper MAPPER;

    static {
        MAPPER = new ObjectMapper();
        MAPPER.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
        MAPPER.setVisibility(PropertyAccessor.GETTER, Visibility.NONE);
        MAPPER.setVisibility(PropertyAccessor.IS_GETTER, Visibility.NONE);
        MAPPER.setVisibility(PropertyAccessor.SETTER, Visibility.NONE);
        MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        MAPPER.setSerializationInclusion(Include.NON_NULL);
        MAPPER.addMixIn(DataCenterInfo.class, DataCenterInfoMixIn.class); // For delta
    }

    private final Set<Class<?>> acceptedTypes;

    public JacksonEurekaCodec(Set<Class<?>> acceptedTypes) {
        this.acceptedTypes = acceptedTypes;
    }

    @Override
    public boolean accept(Class<?> valueType) {
        return acceptedTypes.contains(valueType);
    }

    @Override
    public <T> void encode(T value, OutputStream output) throws IOException {
        try {
            MAPPER.writeValue(output, value);
        } catch (Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Serialization error", e);
            }
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new IOException(e);
        }
    }

    @Override
    public <T> T decode(InputStream source, Class<T> valueType) throws IOException {
        Class<?> implType = valueType;
        if (valueType == InstanceInfo.class) {
            implType = StdInstanceInfo.class;
        }
        try {
            return MAPPER.readValue(source, (Class<T>) implType);
        } catch (Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Dserialization error", e);
            }
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new IOException(e);
        }
    }

    private static Set<Class<?>> combined(Class<?>[]... models) {
        Set<Class<?>> result = new HashSet<>();
        for (Class<?>[] model : models) {
            Collections.addAll(result, model);
        }
        return result;
    }
}
