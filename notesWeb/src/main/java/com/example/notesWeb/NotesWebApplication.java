package com.example.notesWeb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;

@SpringBootApplication(scanBasePackages = "com.example.notesWeb")
@EnableScheduling
@EnableWebSocketMessageBroker
public class NotesWebApplication {

	public static void main(String[] args) {
		envLoader.loadEnv();
		SpringApplication.run(
				NotesWebApplication.class, args);
	}

}
