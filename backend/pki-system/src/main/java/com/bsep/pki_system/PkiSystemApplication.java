package com.bsep.pki_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PkiSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(PkiSystemApplication.class, args);
	}

}
