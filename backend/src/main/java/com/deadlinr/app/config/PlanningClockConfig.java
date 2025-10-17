package com.deadlinr.app.config;
import java.time.Clock;
import org.springframework.context.annotation.*;

@Configuration public class PlanningClockConfig {
 @Bean public Clock planningClock(){return Clock.systemUTC();}
}
