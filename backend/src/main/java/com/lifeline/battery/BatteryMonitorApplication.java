package com.lifeline.battery;

import com.lifeline.battery.config.LifelineProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(LifelineProperties.class)
public class BatteryMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatteryMonitorApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
