package com.netflix.eureka2.testkit.data.builder;

import java.util.ArrayList;
import java.util.Collection;

import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.SourcedChangeNotification;
import rx.Observable;

/**
 * @author David Liu
 */
public enum SampleChangeNotification {

    ZuulAdd() {
        @Override
        public ChangeNotification<InstanceInfo> newNotification() {
            return newNotification(SampleInstanceInfo.ZuulServer.build());
        }

        @Override
        public ChangeNotification<InstanceInfo> newNotification(InstanceInfo seed) {
            return new ChangeNotification<>(ChangeNotification.Kind.Add, seed);
        }
    },
    ZuulDelete() {
        @Override
        public ChangeNotification<InstanceInfo> newNotification() {
            return newNotification(SampleInstanceInfo.ZuulServer.build());
        }

        @Override
        public ChangeNotification<InstanceInfo> newNotification(InstanceInfo seed) {
            return new ChangeNotification<>(ChangeNotification.Kind.Delete, seed);
        }
    },
    DiscoveryAdd() {
        @Override
        public ChangeNotification<InstanceInfo> newNotification() {
            return newNotification(SampleInstanceInfo.DiscoveryServer.build());
        }

        @Override
        public ChangeNotification<InstanceInfo> newNotification(InstanceInfo seed) {
            return new ChangeNotification<>(ChangeNotification.Kind.Add, seed);
        }
    },
    DiscoveryDelete() {
        @Override
        public ChangeNotification<InstanceInfo> newNotification() {
            return newNotification(SampleInstanceInfo.DiscoveryServer.build());
        }

        @Override
        public ChangeNotification<InstanceInfo> newNotification(InstanceInfo seed) {
            return new ChangeNotification<>(ChangeNotification.Kind.Delete, seed);
        }
    },
    CliAdd() {
        @Override
        public ChangeNotification<InstanceInfo> newNotification() {
            return newNotification(SampleInstanceInfo.CliServer.build());
        }

        @Override
        public ChangeNotification<InstanceInfo> newNotification(InstanceInfo seed) {
            return new ChangeNotification<>(ChangeNotification.Kind.Add, seed);
        }
    },
    CliDelete() {
        @Override
        public ChangeNotification<InstanceInfo> newNotification() {
            return newNotification(SampleInstanceInfo.CliServer.build());
        }

        @Override
        public ChangeNotification<InstanceInfo> newNotification(InstanceInfo seed) {
            return new ChangeNotification<>(ChangeNotification.Kind.Delete, seed);
        }
    };

    public abstract ChangeNotification<InstanceInfo> newNotification();

    public abstract ChangeNotification<InstanceInfo> newNotification(InstanceInfo seed);

    public ChangeNotification<InstanceInfo> newNotification(Source source) {
        return new SourcedChangeNotification<>(newNotification(), source);
    }

    public ChangeNotification<InstanceInfo> newNotification(InstanceInfo seed, Source source) {
        return new SourcedChangeNotification<>(newNotification(seed), source);
    }

    public static Observable<ChangeNotification<InstanceInfo>> newAddNotifications(SampleChangeNotification type, int n) {
        Collection<ChangeNotification<InstanceInfo>> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            result.add(type.newNotification());
        }
        return Observable.from(result);
    }
}
