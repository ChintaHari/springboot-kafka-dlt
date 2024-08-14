package com.example.springboot_kafka_dlt;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SpringbootKafkaDltApplicationTests {

	@Test
	void contextLoads() {
	}

}
