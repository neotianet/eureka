package com.netflix.eureka2.registry.index;

import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.MultipleInterests;
import com.netflix.eureka2.registry.EurekaRegistry;
import rx.Observable;

/**
 * @author Nitesh Kant
 */
public interface IndexRegistry<T> {

    /**
     * The interest for this call is required to be an atomic interest and not a {@link MultipleInterests}
     */
    Observable<ChangeNotification<T>> forInterest(Interest<T> interest,
                                                  Observable<ChangeNotification<T>> dataSource,
                                                  Index.InitStateHolder<T> initStateHolder);

    Observable<ChangeNotification<T>> forCompositeInterest(MultipleInterests<T> interest, EurekaRegistry<T> registry);

    Observable<Void> shutdown();

    Observable<Void> shutdown(Throwable cause);
}
