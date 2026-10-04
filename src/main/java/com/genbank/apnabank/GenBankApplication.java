package com.genbank.apnabank;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class GenBankApplication {

	@Value("${genbank.auto-open-browser:false}")
	private boolean autoOpenBrowser;

	public static void main(String[] args) {
		SpringApplication.run(GenBankApplication.class, args);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onApplicationReady() {
		if (!autoOpenBrowser) return;
		try {
			String os = System.getProperty("os.name", "").toLowerCase();
			if (os.contains("win")) {
				Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "http://localhost:8080/"});
			}
		} catch (Exception ignored) {
		}
	}
}
