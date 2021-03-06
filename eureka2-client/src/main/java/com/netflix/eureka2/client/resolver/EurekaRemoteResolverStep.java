package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.instance.InstanceInfo;

/**
 * @author David Liu
 */
public interface EurekaRemoteResolverStep extends ServerResolverStep {

    ServerResolver forInterest(Interest<InstanceInfo> interest);

}
