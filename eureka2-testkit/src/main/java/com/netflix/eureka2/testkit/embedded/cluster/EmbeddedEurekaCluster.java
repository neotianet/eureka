package com.netflix.eureka2.testkit.embedded.cluster;

import java.util.ArrayList;
import java.util.List;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ChangeNotification.Kind;
import com.netflix.eureka2.registry.EurekaRegistryView;
import com.netflix.eureka2.server.AbstractEurekaServer;
import rx.Observable;
import rx.subjects.PublishSubject;

/**
 * @author Tomasz Bak
 */
public abstract class EmbeddedEurekaCluster<S extends AbstractEurekaServer, A, R> {

    private final String clusterVip;

    protected final List<S> servers = new ArrayList<>();

    private final List<ChangeNotification<A>> clusterAddresses = new ArrayList<>();
    private final PublishSubject<ChangeNotification<A>> clusterAddressUpdates = PublishSubject.create();

    private int nextServerIdx;

    protected EmbeddedEurekaCluster(String clusterVip) {
        this.clusterVip = clusterVip;
    }

    public int scaleUpByOne(S newServer, A newAddress) {
        servers.add(newServer);
        addServerAddress(newAddress);
        return servers.size() - 1;
    }

    public abstract int scaleUpByOne();

    public int scaleUpBy(int count) {
        for (int i = 0; i < count; i++) {
            scaleUpByOne();
        }
        return servers.size() - 1;
    }

    public void scaleDownByOne() {
        scaleDownByOne(servers.size() - 1);
    }

    public void scaleDownByOne(int idx) {
        removeServerAddress(idx);
        S server = servers.remove(idx);
        server.shutdown();
    }

    public void scaleDownBy(int count) {
        for (int i = 0; i < count; i++) {
            scaleDownByOne();
        }
    }

    public void shutdown() {
        for (S server : servers) {
            server.shutdown();
        }
    }

    public String getVip() {
        return clusterVip;
    }

    public S getServer(int idx) {
        return servers.get(idx);
    }

    public List<S> getServers() {
        return servers;
    }

    public EurekaRegistryView<InstanceInfo> getEurekaRegistryView(int idx) {
        return servers.get(idx).getEurekaRegistryView();
    }

    public abstract R clusterReport();

    protected String nextAvailableServerId() {
        return clusterVip + '#' + nextServerIdx++;
    }

    protected Observable<ChangeNotification<A>> clusterChangeObservable() {
        ChangeNotification<A> sentinel = ChangeNotification.bufferSentinel();
        return Observable.from(clusterAddresses)
                .concatWith(Observable.just(sentinel))
                .concatWith(clusterAddressUpdates);
    }

    private void addServerAddress(A serverAddress) {
        clusterAddresses.add(new ChangeNotification<A>(Kind.Add, serverAddress));
        clusterAddressUpdates.onNext(new ChangeNotification<A>(Kind.Add, serverAddress));
    }

    private void removeServerAddress(int idx) {
        ChangeNotification<A> addChange = clusterAddresses.remove(idx);
        clusterAddressUpdates.onNext(new ChangeNotification<A>(Kind.Delete, addChange.getData()));
    }
}
