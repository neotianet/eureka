package com.netflix.eureka.client.service;

import com.netflix.eureka.client.transport.ServerConnection;
import com.netflix.eureka.client.transport.TransportClient;
import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.interests.ModifyNotification;
import com.netflix.eureka.protocol.discovery.AddInstance;
import com.netflix.eureka.protocol.discovery.DeleteInstance;
import com.netflix.eureka.protocol.discovery.InterestRegistration;
import com.netflix.eureka.protocol.discovery.InterestSetNotification;
import com.netflix.eureka.protocol.discovery.UnregisterInterestSet;
import com.netflix.eureka.protocol.discovery.UpdateInstanceInfo;
import com.netflix.eureka.registry.Delta;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.InterestChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Func1;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * An implementation of {@link com.netflix.eureka.service.InterestChannel}
 *
 * @author Nitesh Kant
 */
public class InterestChannelImpl extends AbstractChannel<InterestChannelImpl.STATES> implements InterestChannel {

    private static final Logger logger = LoggerFactory.getLogger(InterestChannelImpl.class);

    private static final IllegalStateException INTEREST_ALREADY_REGISTERED_EXCEPTION =
            new IllegalStateException("An interest is already registered. You must upgrade interest instead.");

    private static final IllegalStateException INTEREST_NOT_REGISTERED_EXCEPTION =
            new IllegalStateException("No interest is registered on this channel.");

    protected enum STATES {Idle, Registered, Closed}

    /**
     * A local copy of instances received by this channel from the server. This is used for:
     *
     * <ul>
        <li><i>Updates on the wire</i>: Since we only get the delta on the wire, we use this map to get the last seen
     {@link InstanceInfo} and apply the delta on it to get the new {@link InstanceInfo}</li>
        <li><i>Deletes on the wire</i>: Since we only get the identifier for the instance deleted, we use this map to
     get the last seen {@link InstanceInfo}</li>
     </ul>
     *
     * <h2>Thread safety</h2>
     *
     * Since this channel directly leverages the underlying {@link ServerConnection} and our underlying stack guarantees
     * that there are not concurrent updates sent to the input reader, we can safely assume that this code is single
     * threaded.
     */
    private final Map<String, InstanceInfo> idVsInstance = new HashMap<String, InstanceInfo>();

    public InterestChannelImpl(TransportClient client) {
        super(STATES.Idle, client, 30000);
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> register(final Interest<InstanceInfo> interest) {
        if (!state.compareAndSet(STATES.Idle, STATES.Registered)) {// State check. Only start registration if the state is Idle.
            STATES currentState = state.get();
            switch (currentState) {
                case Registered:
                    return Observable.error(INTEREST_ALREADY_REGISTERED_EXCEPTION);
                case Closed:
                    return Observable.error(CHANNEL_CLOSED_EXCEPTION);
            }
        }

        //TODO: Need to serialize as register -> upgrade (if any) -> unregister. With this code they can be interleaved
        return connect().switchMap(new Func1<ServerConnection, Observable<? extends ChangeNotification<InstanceInfo>>>() {
            @Override
            public Observable<? extends ChangeNotification<InstanceInfo>> call(ServerConnection serverConnection) {
                @SuppressWarnings("rawtypes")
                Observable sendAck = serverConnection.send(new InterestRegistration(interest));

                @SuppressWarnings("unchecked")
                Observable<ChangeNotification<InstanceInfo>> toReturn = Observable.concat(sendAck, createInterestStream());

                return toReturn;
            }
        });
    }

    @Override
    public Observable<Void> upgrade(Interest<InstanceInfo> newInterest) {
        STATES currentState = state.get();
        switch (currentState) {
            case Idle:
                return Observable.error(INTEREST_NOT_REGISTERED_EXCEPTION);
            case Registered:
                /**
                 * TODO: Upgrades have two states:
                 * 1) Every interest is unique: Server re-runs from the start for every interest.
                 * 2) Overlapping interests: This becomes complex since we have to re-run the old notifications for a new
                 * subscriber.
                 */
                return Observable.error(new UnsupportedOperationException("Upgrade not yet implemented."));
            case Closed:
                return Observable.error(CHANNEL_CLOSED_EXCEPTION);
            default:
                return Observable.error(new IllegalStateException("Unrecognized channel state: " + currentState));
        }
    }

    @Override
    public Observable<Void> unregister() {
        if (!state.compareAndSet(STATES.Registered, STATES.Idle)) {
            final STATES currentState = state.get();
            switch (currentState) {
                case Idle:
                    return Observable.error(INTEREST_NOT_REGISTERED_EXCEPTION);
                case Closed:
                    return Observable.error(CHANNEL_CLOSED_EXCEPTION);
                default:
                    return Observable.error(new IllegalStateException("Unrecognized channel state: " + currentState));
            }
        }

        //TODO: Need to serialize as register -> upgrade (if any) -> unregister. With this code both can be interleaved
        return connect().switchMap(new Func1<ServerConnection, Observable<Void>>() {
            @Override
            public Observable<Void> call(ServerConnection connection) {
                return connection.send(UnregisterInterestSet.INSTANCE);
            }
        }); // Connect is idempotent and does not connect on every call.
    }

    @Override
    protected void _close() {
        super._close();
        idVsInstance.clear();
    }

    private Observable<ChangeNotification<InstanceInfo>> createInterestStream() {

        return connect().switchMap(new Func1<ServerConnection, Observable<? extends ChangeNotification<InstanceInfo>>>() {
            @Override
            public Observable<? extends ChangeNotification<InstanceInfo>> call(final ServerConnection connection) {
                return connection.getInput().filter(new Func1<Object, Boolean>() {
                    @Override
                    public Boolean call(Object message) {
                        boolean isKnown = message instanceof InterestSetNotification;
                        if (!isKnown) {
                            logger.warn("Unrecognized discovery protocol message of type " + message.getClass());
                        }
                        return isKnown;
                    }
                }).map(new Func1<Object, ChangeNotification<InstanceInfo>>() {
                    @Override
                    public ChangeNotification<InstanceInfo> call(Object message) {
                        InterestSetNotification notification = (InterestSetNotification) message;
                        if (notification instanceof AddInstance) {
                            InstanceInfo instanceInfo = ((AddInstance) notification).getInstanceInfo();
                            idVsInstance.put(instanceInfo.getId(), instanceInfo);
                            sendAckOnConnection(connection);
                            return new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Add, instanceInfo);
                        } else if (notification instanceof UpdateInstanceInfo) {
                            Delta delta = ((UpdateInstanceInfo) notification).getDelta();
                            InstanceInfo oldInfo = idVsInstance.get(delta.getId());
                            if (oldInfo != null) {
                                InstanceInfo updatedInfo = oldInfo.applyDelta(delta);
                                idVsInstance.put(updatedInfo.getId(), updatedInfo);
                                sendAckOnConnection(connection);
                                @SuppressWarnings("unchecked")
                                ModifyNotification<InstanceInfo> modify =
                                        new ModifyNotification(updatedInfo, Collections.singleton(delta));
                                return modify;
                            }

                            sendAckOnConnection(connection); // Non-existent instance update isn't an error.

                            if (logger.isWarnEnabled()) {
                                logger.warn("Update notification received for non-existent instance id " + delta.getId());
                            }
                            return null;
                        } else if (notification instanceof DeleteInstance) {
                            String instanceId = ((DeleteInstance) notification).getInstanceId();
                            InstanceInfo removedInstance = idVsInstance.remove(instanceId);
                            sendAckOnConnection(connection);
                            if (removedInstance != null) {
                                return new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Delete,
                                                                            removedInstance);
                            }
                            sendAckOnConnection(connection); // Non-existent instance delete isn't an error.
                            if (logger.isWarnEnabled()) {
                                logger.warn("Delete notification received for non-existent instance id:" + instanceId);
                            }
                            return null;
                        } else {
                            throw new IllegalArgumentException("Unknown message received on the interest channel. Type: "
                                                               + message.getClass().getName());
                        }
                    }
                }).filter(new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
                    @Override
                    public Boolean call(ChangeNotification<InstanceInfo> notification) {
                        return null != notification;
                    }
                });
            }
        });
    }
}