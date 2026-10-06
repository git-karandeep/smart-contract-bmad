package ai.generalcontracts.startup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class GeneralContractApplication {

	public static void main(String[] args) {
		SpringApplication.run(GeneralContractApplication.class, args);
	}

}
