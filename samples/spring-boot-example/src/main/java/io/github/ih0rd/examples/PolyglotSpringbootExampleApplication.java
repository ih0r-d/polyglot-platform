package io.github.ih0rd.examples;

import io.github.ih0rd.polyglot.annotations.spring.client.EnablePolyglotClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnablePolyglotClients
public class PolyglotSpringbootExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolyglotSpringbootExampleApplication.class, args);
    }
}
