package cl.sanosysalvos.usuarios;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@SpringBootApplication
public class MicroUsuariosApplication {

    public static void main(String[] args) {
        SpringApplication.run(MicroUsuariosApplication.class, args);
    }
}
