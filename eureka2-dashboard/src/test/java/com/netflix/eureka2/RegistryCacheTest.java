package com.netflix.eureka2;

import java.util.Map;

import com.netflix.eureka2.model.instance.InstanceInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class RegistryCacheTest extends RegistryTestBase {

    @Test(timeout = 60000)
    public void checkRegistryCount() {
        RegistryCache registryCache = new RegistryCache(interestClient);
        final Map<String, InstanceInfo> regCache = registryCache.getCache();
        assertTrue(regCache != null);
        assertTrue(regCache.size() == 4);
    }
}
