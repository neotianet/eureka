package com.netflix.eureka2.registry;

import java.util.Collection;

import com.netflix.eureka2.EurekaCloseable;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.notification.ChangeNotification;

/**
 * @author David Liu
 */
public interface MultiSourcedDataStore<T> extends EurekaCloseable {

    ChangeNotification<T>[] update(T data, Source source);

    ChangeNotification<T>[] remove(String dataId, Source source);

    Collection<MultiSourcedDataHolder<T>> values();

    MultiSourcedDataHolder<T> get(String id);

    int size();
}
