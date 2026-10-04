package com.ilucatero.springai.chat.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/** Entry point for the Getting Started module. */
@SpringBootApplication
@ComponentScan(basePackages = "com.example.springai")
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}