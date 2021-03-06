package com.netflix.eureka2.registry;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.utils.functions.ChangeNotifications;
import com.netflix.eureka2.metric.EurekaRegistryMetrics;
import com.netflix.eureka2.model.Source;
import rx.Observable;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Not thread safe, assume concurrency is taken care of by external wrappers
 *
 * @author David Liu
 */
public class SimpleInstanceInfoDataStore implements MultiSourcedDataStore<InstanceInfo> {

    protected final Map<String, MultiSourcedDataHolder<InstanceInfo>> dataMap;
    protected final EurekaRegistryMetrics metrics;

    public SimpleInstanceInfoDataStore(EurekaRegistryMetrics metrics) {
        this.dataMap = new ConcurrentHashMap<>();  // concurrentHashMap as we have concurrent reads
        this.metrics = metrics;
    }

    @Override
    public ChangeNotification<InstanceInfo>[] update(InstanceInfo instanceInfo, Source source) {
        String id = instanceInfo.getId();

        MultiSourcedDataHolder<InstanceInfo> currHolder = dataMap.get(id);
        if (currHolder == null) {
            currHolder = new MultiSourcedInstanceInfoHolder(id, metrics);
            dataMap.put(id, currHolder);
            return currHolder.update(source, instanceInfo);
        } else {
            return currHolder.update(source, instanceInfo);
        }
    }

    @Override
    public ChangeNotification<InstanceInfo>[] remove(String id, Source source) {
        MultiSourcedDataHolder<InstanceInfo> currHolder = dataMap.get(id);
        if (currHolder == null) {
            return ChangeNotifications.emptyChangeNotifications();
        } else {
            ChangeNotification<InstanceInfo>[] notifications = currHolder.remove(source);
            if (currHolder.isEmpty()) {
                dataMap.remove(id);
            }
            return notifications;
        }
    }

    @Override
    public Collection<MultiSourcedDataHolder<InstanceInfo>> values() {
        return dataMap.values();
    }

    @Override
    public MultiSourcedDataHolder<InstanceInfo> get(String id) {
        return dataMap.get(id);
    }

    @Override
    public int size() {
        return dataMap.size();
    }

    @Override
    public Observable<Void> shutdown() {
        dataMap.clear();
        return Observable.empty();
    }

    @Override
    public Observable<Void> shutdown(Throwable cause) {
        dataMap.clear();
        return Observable.error(cause);
    }
}
