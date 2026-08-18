package pl.mkn.tdw;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class TeamDeliveryWorkspaceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeamDeliveryWorkspaceApplication.class, args);
    }

}
