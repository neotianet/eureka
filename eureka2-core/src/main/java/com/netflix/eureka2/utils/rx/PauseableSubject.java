package com.netflix.eureka2.utils.rx;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Subscriber;
import rx.observers.SafeSubscriber;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

/**
 * A special {@link Subject} implementation that has the capability to optionally
 * start/stop caching on demand, i.e. publish of data to the subscribers will be paused after calling the method
 * {@link #pause()} and the same can be resumed after calling the method {@link #resume()}
 *
 * @author Nitesh Kant
 */
public class PauseableSubject<T> extends Subject<T, T> {

    public enum ResumeResult {NotPaused, DuplicateResume, Resumed}

    private enum ResumeState {NotPaused, Resuming, Error}

    private static final Logger logger = LoggerFactory.getLogger(PauseableSubject.class);

    private final AtomicInteger resumeState = new AtomicInteger(ResumeState.NotPaused.ordinal());

    private final AtomicBoolean paused;
    private final Subject<T, T> notificationSubject;
    private final NotificationsSubjectSubscriber subscriber;
    private final ConcurrentLinkedQueue<T> notificationsWhenPaused; // TODO: See if this should be bounded.
    private volatile boolean completedWhenPaused;
    private volatile Throwable errorWhenPaused;

    protected PauseableSubject(OnSubscribe<T> onSubscribe,
                               Subject<T, T> notificationSubject) {
        super(onSubscribe);
        subscriber = new NotificationsSubjectSubscriber();
        this.notificationSubject = notificationSubject;
        notificationsWhenPaused = new ConcurrentLinkedQueue<>();
        paused = new AtomicBoolean();
    }

    public static <T> PauseableSubject<T> create() {
        final Subject<T, T> notificationSubject = PublishSubject.create();
        return new PauseableSubject<T>(new OnSubscribe<T>() {
            @Override
            public void call(Subscriber<? super T> subscriber) {
                notificationSubject.subscribe(subscriber);
            }
        }, notificationSubject);
    }

    public boolean isPaused() {
        return paused.get();
    }

    public void pause() {
        paused.set(true);
    }

    @Override
    public boolean hasObservers() {
        return notificationSubject.hasObservers();
    }

    public ResumeResult resume() {
        if (isPaused()) {
            if (resumeState.compareAndSet(ResumeState.NotPaused.ordinal(), ResumeState.Resuming.ordinal())) {
                try {
                    drainBuffer();

                    // We have here a race condition, as onNext could be called just before paused is set to false,
                    // but after the while loop above. That is why we need to drain the buffer, before each onNext.
                    paused.set(false);

                    if (completedWhenPaused) {
                        onCompleted();
                    } else if (null != errorWhenPaused) {
                        onError(errorWhenPaused);
                    }

                    return ResumeResult.Resumed;
                } catch (Exception e) {
                    logger.error("Error while resuming notifications subject.", e);
                    resumeState.compareAndSet(ResumeState.Resuming.ordinal(), ResumeState.Error.ordinal());
                    onError(e);
                    return ResumeResult.Resumed;
                } finally {
                    resumeState.compareAndSet(ResumeState.Resuming.ordinal(), ResumeState.NotPaused.ordinal());
                }
            } else {
                return ResumeResult.DuplicateResume;
            }
        } else {
            return ResumeResult.NotPaused;
        }
    }

    /**
     * We drain the queue on the resume invocation (while still in paused state), or
     * on each oNext, if paused is false. It is suboptimal but fixes the race condition issue.
     * We can optimize it later by introducing FSM for state management.
     */
    private void drainBuffer() {
        T nextPolled;
        while ((nextPolled = notificationsWhenPaused.poll()) != null) {
            notificationSubject.onNext(nextPolled);
        }
    }

    @Override
    public void onCompleted() {
        subscriber.onCompleted();
    }

    @Override
    public void onError(Throwable e) {
        subscriber.onError(e);
    }

    @Override
    public void onNext(T next) {
        subscriber.onNext(next);
    }

    /**
     * This makes sure that the {@link PauseableSubject} follows Rx contracts i.e.
     * it does not honor onNext() after termination and onNext() error causes onError().
     */
    private class NotificationsSubjectSubscriber extends SafeSubscriber<T> {

        public NotificationsSubjectSubscriber() {
            super(new Subscriber<T>() {
                @Override
                public void onCompleted() {
                    if (paused.get()) {
                        completedWhenPaused = true;
                    } else {
                        notificationSubject.onCompleted();
                    }
                }

                @Override
                public void onError(Throwable e) {
                    if (paused.get()) {
                        errorWhenPaused = e;
                    } else {
                        notificationSubject.onError(e);
                    }
                }

                @Override
                public void onNext(T notification) {
                    if (paused.get()) {
                        notificationsWhenPaused.add(notification);
                    } else {
                        // Need to drain each time, as may not be emptied by resume() call.
                        drainBuffer();
                        notificationSubject.onNext(notification);
                    }
                }
            });
        }
    }
}
