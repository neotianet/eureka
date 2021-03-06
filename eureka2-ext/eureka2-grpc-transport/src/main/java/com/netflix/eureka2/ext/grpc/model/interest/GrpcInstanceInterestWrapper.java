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

package com.netflix.eureka2.ext.grpc.model.interest;

import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;

/**
 */
public class GrpcInstanceInterestWrapper extends GrpcPatternInterestWrapper {

    public GrpcInstanceInterestWrapper(String instanceId, Interest.Operator operator) {
        super(Eureka2.GrpcInterest.newBuilder().setInstance(
                Eureka2.GrpcInterest.GrpcInstanceInterest.newBuilder()
                        .setOperator(toGrpcOpertor(operator))
                        .setPattern(instanceId)
                        .build()
                ).build()
        );
    }

    public GrpcInstanceInterestWrapper(Eureka2.GrpcInterest grpcInterest) {
        super(grpcInterest);
    }

    public static GrpcInterestWrapper getInstance(Eureka2.GrpcInterest grpcInterest) {
        return new GrpcInstanceInterestWrapper(grpcInterest);
    }

    @Override
    public QueryType getQueryType() {
        return QueryType.Instance;
    }

    @Override
    public Operator getOperator() {
        return toOpertor(getGrpcObject().getInstance().getOperator());
    }

    @Override
    public String getPattern() {
        return getGrpcObject().getInstance().getPattern();
    }

    @Override
    protected String getValue(InstanceInfo data) {
        return data.getId();
    }

}
