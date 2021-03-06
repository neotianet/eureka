package com.netflix.eureka2.utils.functions;

import rx.Observable;
import rx.Observable.Transformer;
import rx.functions.Func1;
import rx.functions.Func2;

/**
 * @author Tomasz Bak
 */
public final class RxFunctions {

    private static final Func1<Object, Boolean> FILTER_NULL_VALUES_FUNC = new Func1<Object, Boolean>() {
        @Override
        public Boolean call(Object o) {
            return o != null;
        }
    };

    private static final Object COMBINE_MARKER = new String("combineWithOptional() operator's end of primary stream marker");
    private static final Observable<Object> COMBINE_MARKER_OBSERVABLE = Observable.just(COMBINE_MARKER);
    private static final Func1<Object, Boolean> COMBINE_MARKER_PREDICATE = new Func1<Object, Boolean>() {
        @Override
        public Boolean call(Object o) {
            return o != COMBINE_MARKER;
        }
    };

    private RxFunctions() {
    }

    public static <T> Func1<T, Boolean> filterNullValuesFunc() {
        return (Func1<T, Boolean>) FILTER_NULL_VALUES_FUNC;
    }

    /**
     * Convert onError to onCompleted. This is useful when merging multiple observables, where onError
     * would interrupt the whole stream.
     */
    public static <T> Transformer<T, T> swallowError() {
        return new Transformer<T, T>() {
            @Override
            public Observable<T> call(Observable<T> observable) {
                return observable.onErrorResumeNext(Observable.<T>empty());
            }
        };
    }

    /**
     * Combine operator that completes immediately when the first (primary) observable terminates.
     * Standard {@link Observable#combineLatest(Observable, Observable, Func2)} completes only when both
     * combined streams complete.
     */
    public static <T1, T2, R> Observable<R> combineWithOptional(Observable<? extends T1> main, Observable<? extends T2> optional, final Func2<? super T1, ? super T2, ? extends R> combineFunction) {
        Observable mainUntyped = main;
        return Observable.combineLatest(mainUntyped.concatWith(COMBINE_MARKER_OBSERVABLE), optional, new Func2<Object, T2, Object>() {
            @Override
            public Object call(Object t1, T2 t2) {
                if (t1 == COMBINE_MARKER) {
                    return COMBINE_MARKER;
                }
                return combineFunction.call((T1) t1, t2);
            }
        }).takeWhile(COMBINE_MARKER_PREDICATE);
    }
}
