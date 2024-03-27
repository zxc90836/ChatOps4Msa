package example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication // 標記為 Spring Boot 應用的入口
public class TrelloApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrelloApplication.class, args); // 啟動應用
    }
}