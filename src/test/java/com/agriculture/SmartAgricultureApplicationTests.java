package com.agriculture;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "mqtt.enabled=false")
class SmartAgricultureApplicationTests {

    @Test
    void contextLoads() {
    }
}
