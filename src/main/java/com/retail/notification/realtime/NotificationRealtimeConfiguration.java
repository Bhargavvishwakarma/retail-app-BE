package com.retail.notification.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Enables the small, module-local scheduled tasks used to keep notification
 * streams alive. Keeping this in the notification module makes the scheduling
 * dependency explicit and avoids coupling the application bootstrap class to
 * a delivery mechanism.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class NotificationRealtimeConfiguration {

    /**
     * Keeps polling and heartbeat work off request threads and avoids one slow
     * stream heartbeat preventing the outbox from being picked up promptly.
     */
    @Bean(name = "taskScheduler")
    ThreadPoolTaskScheduler notificationTaskScheduler(
            @Value("${app.notifications.scheduler-pool-size:2}") int poolSize) {
        if (poolSize < 2) {
            throw new IllegalArgumentException("Notification scheduler pool size must be at least 2");
        }
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("notification-scheduler-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        return scheduler;
    }
}
