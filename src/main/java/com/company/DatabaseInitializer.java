package com.company;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInitializer {

	@Autowired
	JdbcTemplate jdbcTemplate;

	// 因为 AppConfig.java 中的类 AppConfig前有注解 @ComponentScan,
	// 而该类 DatabaseInitializer 上有注解 @Component 和 函数 init 有注解 @PostConstruct
	// 所以在启动服务时就会走到该类的 init 方法
	@PostConstruct
	public void init() {
		jdbcTemplate.update("CREATE TABLE IF NOT EXISTS users (" //
				+ "id BIGINT IDENTITY NOT NULL PRIMARY KEY, " //
				+ "email VARCHAR(100) NOT NULL, " //
				+ "password VARCHAR(100) NOT NULL, " //
				+ "name VARCHAR(100) NOT NULL, " //
				+ "UNIQUE (email))");
	}
}
