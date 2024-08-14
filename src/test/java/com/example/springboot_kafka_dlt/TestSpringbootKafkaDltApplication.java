package com.example.springboot_kafka_dlt;

import org.springframework.boot.SpringApplication;

public class TestSpringbootKafkaDltApplication {

	public static void main(String[] args) {
		SpringApplication.from(SpringbootKafkaDltApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
