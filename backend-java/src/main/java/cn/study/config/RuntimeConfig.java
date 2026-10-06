package cn.study.config;

import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class RuntimeConfig {
    @Bean public Clock clock(@Value("${app.timezone}") String zone) { return Clock.system(ZoneId.of(zone)); }
    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler taskScheduler() {
        var scheduler=new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();scheduler.setPoolSize(3);scheduler.setThreadNamePrefix("study-scheduler-");
        scheduler.setErrorHandler(error->org.slf4j.LoggerFactory.getLogger("scheduler").error("scheduled_failure type={}",error.getClass().getSimpleName()));return scheduler;
    }
}
