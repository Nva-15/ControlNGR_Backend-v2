package com.example.ControlNGR;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class ControlNgrApplication {

	public static void main(String[] args) {
		// Toda la aplicacion trabaja en hora de Lima (fechas de marcacion, feriados, aniversarios)
		TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"));
		SpringApplication.run(ControlNgrApplication.class, args);
	}
}
