package cn.cenxt.cc.demo;

import cn.cenxt.cc.mrcp.EnableCenxtMrcp;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableCenxtMrcp
public class MrcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MrcpServerApplication.class, args);
    }

}
