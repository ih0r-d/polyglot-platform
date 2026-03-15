package io.github.ih0rd.examples;

import io.github.ih0rd.polyglot.spring.client.EnablePolyglotClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnablePolyglotClients(
        basePackages = "io.github.ih0rd.examples.contracts"
)
public class PolyglotSpringbootExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolyglotSpringbootExampleApplication.class, args);
    }

}
