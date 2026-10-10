package com.ilucatero.springai.chat_cs.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the Getting Started module. */
@SpringBootApplication(scanBasePackages = "com.ilucatero.springai.chat_cs")
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}