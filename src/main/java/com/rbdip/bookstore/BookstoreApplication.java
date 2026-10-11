package com.rbdip.bookstore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Spring создаёт экземпляр конфигурационного класса, поэтому приватный
// конструктор "утилитного класса" здесь невозможен.
@SuppressWarnings("PMD.UseUtilityClass")
@SpringBootApplication
public class BookstoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookstoreApplication.class, args);
    }
}
