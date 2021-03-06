package com.netflix.eureka2.utils.rx;

import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Scheduler;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.schedulers.Schedulers;

/**
 * A Func1 with retry options for use in .retryWhen(new RetryStrategyFunc())
 *
 * @author David Liu
 */
public class RetryStrategyFunc implements Func1<Observable<? extends Throwable>, Observable<Long>> {
    private final long retryIntervalMillis;
    private final int numRetries;
    private final boolean backoffRetry;
    private final Scheduler scheduler;

    /**
     * Create a retry strategy that retries infinitely with the retryInterval as the set delay between retries
     *
     * @param retryInterval the initial wait between retries
     * @param timeUnit the timeUnit of the retryInterval
     */
    public RetryStrategyFunc(long retryInterval, TimeUnit timeUnit) {
        this(TimeUnit.MILLISECONDS.convert(retryInterval, timeUnit), -1, false);
    }

    public RetryStrategyFunc(long retryIntervalMillis, Scheduler scheduler) {
        this(retryIntervalMillis, -1, false, scheduler);
    }

    public RetryStrategyFunc(long retryIntervalMillis, int totalRetries, boolean exponentialBackoff) {
        this(retryIntervalMillis, totalRetries, exponentialBackoff, Schedulers.computation());
    }

    /**
     * @param retryIntervalMillis the initial wait between retries in milliseconds
     * @param totalRetries max number of retries to attempt
     * @param exponentialBackoff boolean to denote whether to use exponential backoff
     */
    public RetryStrategyFunc(long retryIntervalMillis, int totalRetries, boolean exponentialBackoff, Scheduler scheduler) {
        this.retryIntervalMillis = retryIntervalMillis;
        this.numRetries = totalRetries;
        this.backoffRetry = exponentialBackoff;
        this.scheduler = scheduler;
    }

    @Override
    public Observable<Long> call(Observable<? extends Throwable> observable) {
        Observable<Integer> ticker;
        if (numRetries > 0) {
            ticker = Observable.range(1, numRetries);
        } else {
            ticker = Observable.just(1).repeat();
        }

        return observable.zipWith(ticker, new Func2<Throwable, Integer, Long>() {
            @Override
            public Long call(Throwable n, Integer i) {
                if (backoffRetry) {
                    return (long) Math.pow(2, i) * retryIntervalMillis;
                } else {
                    return retryIntervalMillis;
                }
            }
        }).flatMap(new Func1<Long, Observable<Long>>() {
            @Override
            public Observable<Long> call(Long i) {
                return Observable.timer(i, TimeUnit.MILLISECONDS, scheduler);
            }
        });
    }
}
