package com.netflix.eureka2.server.config.bean;

import com.netflix.eureka2.server.config.BootstrapConfig;
import com.netflix.eureka2.server.config.EurekaClusterDiscoveryConfig;
import com.netflix.eureka2.server.config.EurekaInstanceInfoConfig;
import com.netflix.eureka2.server.config.EurekaServerRegistryConfig;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;
import com.netflix.eureka2.server.config.WriteServerConfig;

import static com.netflix.eureka2.server.config.bean.EurekaClusterDiscoveryConfigBean.anEurekaClusterDiscoveryConfig;
import static com.netflix.eureka2.server.config.bean.EurekaInstanceInfoConfigBean.anEurekaInstanceInfoConfig;
import static com.netflix.eureka2.server.config.bean.EurekaServerRegistryConfigBean.anEurekaServerRegistryConfig;
import static com.netflix.eureka2.server.config.bean.EurekaServerTransportConfigBean.anEurekaServerTransportConfig;

/**
 * @author Tomasz Bak
 */
public class WriteServerConfigBean extends EurekaServerConfigBean implements WriteServerConfig {

    private final BootstrapConfig bootstrapConfig;
    private final long replicationReconnectDelayMillis;

    public WriteServerConfigBean(EurekaClusterDiscoveryConfig clusterDiscoveryConfig, EurekaInstanceInfoConfig instanceInfoConfig,
                                 EurekaServerTransportConfig transportConfig, EurekaServerRegistryConfig registryConfig,
                                 BootstrapConfig bootstrapConfig, long replicationReconnectDelayMillis) {
        super(clusterDiscoveryConfig, instanceInfoConfig, transportConfig, registryConfig);
        this.bootstrapConfig = bootstrapConfig;
        this.replicationReconnectDelayMillis = replicationReconnectDelayMillis;
    }

    @Override
    public long getReplicationReconnectDelayMs() {
        return replicationReconnectDelayMillis;
    }

    @Override
    public BootstrapConfig getBootstrap() {
        return bootstrapConfig;
    }

    public static Builder aWriteServerConfig() {
        return new Builder();
    }

    public static class Builder {
        private EurekaClusterDiscoveryConfig clusterDiscoveryConfig = anEurekaClusterDiscoveryConfig().build();
        private EurekaInstanceInfoConfig instanceInfoConfig = anEurekaInstanceInfoConfig().build();
        private EurekaServerTransportConfig transportConfig = anEurekaServerTransportConfig().build();
        private EurekaServerRegistryConfig registryConfig = anEurekaServerRegistryConfig().build();
        private BootstrapConfig bootstrapConfig = BootstrapConfigBean.aBootstrapConfig().build();
        private long replicationReconnectDelayMillis = DEFAULT_REPLICATION_RECONNECT_DELAY_MS;

        private Builder() {
        }

        public Builder withClusterDiscoveryConfig(EurekaClusterDiscoveryConfig clusterDiscoveryConfig) {
            this.clusterDiscoveryConfig = clusterDiscoveryConfig;
            return this;
        }

        public Builder withInstanceInfoConfig(EurekaInstanceInfoConfig instanceInfoConfig) {
            this.instanceInfoConfig = instanceInfoConfig;
            return this;
        }

        public Builder withTransportConfig(EurekaServerTransportConfig transportConfig) {
            this.transportConfig = transportConfig;
            return this;
        }

        public Builder withRegistryConfig(EurekaServerRegistryConfig registryConfig) {
            this.registryConfig = registryConfig;
            return this;
        }

        public Builder withReplicationReconnectDelayMillis(long replicationReconnectDelayMillis) {
            this.replicationReconnectDelayMillis = replicationReconnectDelayMillis;
            return this;
        }

        public Builder withBootstrapConfig(BootstrapConfig bootstrapConfig) {
            this.bootstrapConfig = bootstrapConfig;
            return this;
        }

        public Builder but() {
            return aWriteServerConfig()
                    .withClusterDiscoveryConfig(clusterDiscoveryConfig)
                    .withInstanceInfoConfig(instanceInfoConfig)
                    .withTransportConfig(transportConfig)
                    .withRegistryConfig(registryConfig)
                    .withReplicationReconnectDelayMillis(replicationReconnectDelayMillis)
                    .withBootstrapConfig(bootstrapConfig);
        }

        public WriteServerConfigBean build() {
            return new WriteServerConfigBean(
                    clusterDiscoveryConfig, instanceInfoConfig, transportConfig, registryConfig,
                    bootstrapConfig, replicationReconnectDelayMillis);
        }
    }
}
