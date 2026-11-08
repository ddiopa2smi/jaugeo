package sn.digitalperf.jaugeo;

import org.springframework.boot.SpringApplication;

public class TestJaugeoApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(JaugeoApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
