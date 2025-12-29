package com.example.wallet_service;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WalletServiceApplication {

	public static void main(String[] args) {
		// Load .env file for local development
		try {
			Dotenv dotenv = Dotenv.configure()
					.ignoreIfMissing()
					.load();
			dotenv.entries().forEach(entry -> {
				System.setProperty(entry.getKey(), entry.getValue());
			});
		} catch (Exception e) {
			// .env file is optional, continue without it
			System.out.println("Note: .env file not found, using default values or system environment variables");
		}
		
		SpringApplication.run(WalletServiceApplication.class, args);
	}

}
