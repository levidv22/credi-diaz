package upeu.edu.pe.credito;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.security.Security;

@SpringBootApplication
@EnableScheduling
public class CreditoApplication {

	public static void main(String[] args) {
        Security.addProvider(new BouncyCastleProvider());
		SpringApplication.run(CreditoApplication.class, args);
	}

}
