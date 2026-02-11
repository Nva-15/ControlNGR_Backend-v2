package com.example.ControlNGR;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.TimeZone;

import jakarta.annotation.PostConstruct;

@SpringBootApplication
@EnableAsync
public class ControlNgrApplication {

	public static void main(String[] args) {
		SpringApplication.run(ControlNgrApplication.class, args);
	}

	@PostConstruct
	public void init() {
		TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"));
	}

}
