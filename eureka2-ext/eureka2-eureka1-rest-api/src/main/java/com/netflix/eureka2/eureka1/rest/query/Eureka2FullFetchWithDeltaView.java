package com.netflix.eureka2.eureka1.rest.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.appinfo.InstanceInfo.ActionType;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.utils.functions.ChangeNotifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscription;
import rx.functions.Func1;
import rx.subjects.BehaviorSubject;

import static com.netflix.eureka2.eureka1.utils.Eureka1ModelConverters.toEureka1xApplications;
import static com.netflix.eureka2.eureka1.utils.Eureka1ModelConverters.toEureka1xInstanceInfo;
import static com.netflix.eureka2.eureka1.utils.Eureka1ModelConverters.toEureka1xInstanceInfos;
import static com.netflix.eureka2.eureka1.utils.Eureka1ModelConverters.v1InstanceIdentityComparator;
import static com.netflix.eureka2.utils.functions.ChangeNotifications.collapseAndExtract;
import static com.netflix.eureka2.utils.functions.ChangeNotifications.emitAndAggregateChanges;
import static com.netflix.eureka2.utils.functions.ChangeNotifications.instanceInfoIdentity;

/**
 * A special view of full registry, which provides both full {@link Applications} data
 * structure, and delta changes that need to be applied to the previous copy of the
 * {@link Applications} object to transition to the current value.
 *
 * @author Tomasz Bak
 */
public class Eureka2FullFetchWithDeltaView {

    private static final Logger logger = LoggerFactory.getLogger(Eureka2FullFetchWithDeltaView.class);

    private final Observable<ChangeNotification<InstanceInfo>> notifications;
    private final long refreshIntervalMs;
    private final Scheduler scheduler;

    private final AtomicReference<RegistryFetch> latestCopy = new AtomicReference<>();
    private final BehaviorSubject<RegistryFetch> latestCopySubject = BehaviorSubject.create();
    private Subscription subscription;


    public Eureka2FullFetchWithDeltaView(Observable<ChangeNotification<InstanceInfo>> notifications,
                                         long refreshIntervalMs,
                                         Scheduler scheduler) {

        this.notifications = notifications;
        this.refreshIntervalMs = refreshIntervalMs;
        this.scheduler = scheduler;
        connect();
    }

    /**
     * Connect happens immediately after child object is constructed.
     */
    private void connect() {
        subscription = notifications
                .compose(ChangeNotifications.<InstanceInfo>delineatedBuffers())
                .compose(emitAndAggregateChanges(
                        instanceInfoIdentity(),
                        refreshIntervalMs,
                        TimeUnit.MILLISECONDS,
                        scheduler
                ))
                .map(new Func1<List<ChangeNotification<InstanceInfo>>, RegistryFetch>() {
                    @Override
                    public RegistryFetch call(List<ChangeNotification<InstanceInfo>> instanceInfos) {
                        return updateSnapshot(instanceInfos);
                    }
                })
                .subscribe(latestCopySubject);
    }

    public void close() {
        subscription.unsubscribe();
    }

    public Observable<RegistryFetch> latestCopy() {
        return latestCopySubject.take(1);
    }

    private RegistryFetch updateSnapshot(List<ChangeNotification<InstanceInfo>> latestUpdates) {
        RegistryFetch newCopy;
        if (latestCopy.get() == null) {
            LinkedHashSet<InstanceInfo> allInstances = collapseAndExtract(latestUpdates, instanceInfoIdentity());
            newCopy = new RegistryFetch(allInstances);
        } else {
            newCopy = latestCopy.get().apply(latestUpdates);
        }
        latestCopy.set(newCopy);
        return newCopy;
    }

    public static class RegistryFetch {
        private final SortedSet<com.netflix.appinfo.InstanceInfo> allInstances;
        private final Applications applications;
        private final Applications deltaChanges;

        RegistryFetch(Collection<InstanceInfo> v2Instances) {
            this.allInstances = new TreeSet<>(v1InstanceIdentityComparator());
            this.allInstances.addAll(toEureka1xInstanceInfos(v2Instances));

            this.applications = toEureka1xApplications(this.allInstances);
            this.deltaChanges = new Applications();
            this.deltaChanges.setAppsHashCode(this.applications.getAppsHashCode());
        }

        RegistryFetch(SortedSet<com.netflix.appinfo.InstanceInfo> allInstances, Collection<com.netflix.appinfo.InstanceInfo> deltaChanges) {
            this.allInstances = allInstances;
            this.applications = toEureka1xApplications(allInstances);
            this.deltaChanges = toEureka1xApplications(deltaChanges);
            this.deltaChanges.setAppsHashCode(this.applications.getAppsHashCode());
        }

        public Applications getApplications() {
            return applications;
        }

        public Applications getDeltaChanges() {
            return deltaChanges;
        }

        /**
         * The assumption is that this method is called with a compacted list, so there is exactly one
         * change notification for each instance, either Add, Modify or Delete.
         */
        public RegistryFetch apply(List<ChangeNotification<InstanceInfo>> updates) {
            TreeSet<com.netflix.appinfo.InstanceInfo> newAllInstances = new TreeSet<com.netflix.appinfo.InstanceInfo>(v1InstanceIdentityComparator());
            newAllInstances.addAll(allInstances);
            List<com.netflix.appinfo.InstanceInfo> deltaChanges = new ArrayList<>(updates.size());

            for (ChangeNotification<InstanceInfo> update : updates) {
                com.netflix.appinfo.InstanceInfo v1Update = toEureka1xInstanceInfo(update.getData());
                if (v1Update == null) {
                    continue;  // skip ahead
                }
                com.netflix.appinfo.InstanceInfo v1Info;
                switch (update.getKind()) {
                    case Add:
                        newAllInstances.remove(v1Update);
                        newAllInstances.add(v1Update);
                        v1Info = toEureka1xInstanceInfo(update.getData(), ActionType.ADDED);
                        if (v1Info != null) {
                            deltaChanges.add(v1Info);
                        }
                        break;
                    case Modify:
                        newAllInstances.add(v1Update);
                        v1Info = toEureka1xInstanceInfo(update.getData(), ActionType.MODIFIED);
                        if (v1Info != null) {
                            deltaChanges.add(v1Info);
                        }
                        break;
                    case Delete:
                        newAllInstances.remove(v1Update);
                        v1Info = toEureka1xInstanceInfo(update.getData(), ActionType.DELETED);
                        if (v1Info != null) {
                            deltaChanges.add(v1Info);
                        }
                        break;
                    default:
                        logger.error("Unexpected change notification type {}", update.getKind());
                }
            }

            return new RegistryFetch(newAllInstances, deltaChanges);
        }
    }
}
