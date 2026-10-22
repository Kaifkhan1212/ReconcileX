package com.reconcilex;

import org.springframework.boot.SpringApplication;

public class TestReconcilexApplication {

	public static void main(String[] args) {
		SpringApplication.from(ReconcilexApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
