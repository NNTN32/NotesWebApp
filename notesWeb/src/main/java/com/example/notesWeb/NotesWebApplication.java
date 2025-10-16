package com.example.notesWeb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NotesWebApplication {

	public static void main(String[] args) {

		SpringApplication.run(
				NotesWebApplication.class, args);
	}

}
