package com.chatapp.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "eureka.client.enabled=false")
class CloudGatewayApplicationTests {

		@Test
		void contextLoads() {
		}

}
