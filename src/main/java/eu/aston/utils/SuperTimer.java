package eu.aston.utils;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SuperTimer {

    private static final Logger logger = LoggerFactory.getLogger(SuperTimer.class);

    private final Timer timer;
    private final Executor executor;

    public SuperTimer(Executor executor) {
        this.timer = new Timer("next-step-timeout", true);
        this.executor = executor;
    }

    public void schedule(long add, Runnable r) {
        schedule(new Date(System.currentTimeMillis() + add), r);
    }

    public void schedule(Date date, Runnable r) {
        timer.schedule(timerTask(r), date);
    }

    public <T> void schedule(long add, T id, Consumer<T> consumer) {
        schedule(new Date(System.currentTimeMillis() + add), id, consumer);
    }

    public <T> void schedule(Date date, T id, Consumer<T> consumer) {
        timer.schedule(timerTask(()->consumer.accept(id)), date);
    }

    public void schedulePeriod(long period, Runnable r) {
        timer.schedule(timerTask(r), period, period);
    }

    public void schedulePeriodTaskCreator(long period, Consumer<Consumer<Runnable>> taskCreator) {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try{
                    taskCreator.accept(executor::execute);
                }catch (Exception e){
                    logException(e);
                }
            }
        }, period, period);
    }

    public void execute(Runnable r){
        executor.execute(r);
    }

    private TimerTask timerTask(Runnable r){
        return new TimerTask() {
            @Override
            public void run() {
                executor.execute(()->{
                    try {
                        r.run();
                    }catch (Exception e){
                        logException(e);
                    }
                });
            }
        };
    }

    private static void logException(Throwable thr) {
        String m = thr.getMessage();
        try {
            StackTraceElement[] arr = thr.getStackTrace();
            if (arr != null && arr.length > 0) {
                m = m + " " + arr[0].toString();
            }
        } catch (Exception ignore) {
        }
        logger.warn("timer task error {}", m);
    }
}
