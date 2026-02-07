package com.example.notesWeb;

import io.github.cdimascio.dotenv.Dotenv;

public class envLoader {
    //Force the .env file to load before initializing the Spring Context.
    public static void loadEnv() {
        String profile = System.getProperty("spring.profiles.active", "local");

        if (!"local".equals(profile)) {
            return;
        }
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMalformed()
                .ignoreIfMissing() //no crash CI/prod
                .load();

        dotenv.entries().forEach(entry ->
                System.setProperty(entry.getKey(), entry.getValue())
        );
    }
}
