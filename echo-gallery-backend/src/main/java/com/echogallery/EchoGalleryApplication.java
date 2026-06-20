package com.echogallery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
// 💡 加上 exclude，暫時中斷資料庫自動配置
// @SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class EchoGalleryApplication {

	public static void main(String[] args) {
		SpringApplication.run(EchoGalleryApplication.class, args);
	}

}
