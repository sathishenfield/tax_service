package com.example.tax;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"com.example.tax", "com.core.lib"})
@EntityScan(basePackages = {"com.example.tax", "com.core.lib"})
@EnableJpaRepositories(basePackages = {"com.example.tax", "com.core.lib"})
public class TaxServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(TaxServiceApplication.class, args);
	}

}
