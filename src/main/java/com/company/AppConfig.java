package com.company;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

import com.company.service.User;
import com.company.service.UserService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
@ComponentScan
@PropertySource("jdbc.properties")
public class AppConfig {
	/**
	 * Spring开发--访问数据库
	 * Java程序访问数据库的标准接口JDBC，它的实现方式非常简洁，即：Java标准库定义接口，各数据库厂商以“驱动”的形式实现接口。
	 * 应用程序要使用哪个数据库，就把该数据库厂商的驱动以jar包形式引入进来，同时自身仅使用JDBC接口，编译期并不需要特定厂商的驱动。
	 *
	 * 访问数据库--使用 JDBC
	 * 在Spring使用JDBC，
	 * 首先我们通过IoC容器创建并管理一个DataSource实例，
	 * 然后，Spring提供了一个JdbcTemplate，可以方便地让我们操作JDBC，
	 * 因此，通常情况下，我们会实例化一个JdbcTemplate。顾名思义，这个类主要使用了Template模式。
	 * Spring提供的 JdbcTemplate 采用Template模式，提供了一系列以回调为特点的工具方法，目的是避免繁琐的try...catch语句。
	 * 		public User getUserById(long id) {
	 *  	   // 注意传入的是ConnectionCallback:
	 *  	   return jdbcTemplate.execute((Connection conn) -> {
	 *  	       // 可以直接使用conn实例，不要释放它，回调结束后JdbcTemplate自动释放:
	 *  	       // 在内部手动创建的PreparedStatement、ResultSet必须用try(...)释放:
	 *  	       try (var ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
	 *  	           ps.setObject(1, id);
	 *  	           try (var rs = ps.executeQuery()) {
	 *  	               if (rs.next()) {
	 *  	                   return new User( // new User object:
	 *  	                           rs.getLong("id"), // id
	 *  	                           rs.getString("email"), // email
	 *  	                           rs.getString("password"), // password
	 *  	                           rs.getString("name")); // name
	 *  	               }
	 *  	               throw new RuntimeException("user not found by id.");
	 *  	           }
	 *  	       }
	 *  	   });
	 * 		}
	 * 也就是说，上述回调方法允许获取Connection，然后做任何基于Connection的操作。
	 *
	 * 访问数据库--使用声明式事务
	 * 如果要在Spring中操作事务，没必要手写JDBC事务，可以使用Spring提供的高级接口来操作事务。
	 * Spring提供了一个 PlatformTransactionManager 来表示事务管理器，所有的事务都由它负责管理。而事务由 TransactionStatus 表示。
	 * Spring为啥要抽象出 PlatformTransactionManager 和 TransactionStatus ？
	 * 原因是JavaEE除了提供JDBC事务外，它还支持分布式事务JTA（Java Transaction API）。
	 * 因为我们的代码只需要JDBC事务，因此，在AppConfig中，需要再定义一个PlatformTransactionManager对应的Bean，它的实际类型是 DataSourceTransactionManager：
	 *
	 * 使用编程的方式使用Spring事务仍然比较繁琐，更好的方式是通过声明式事务来实现。使用声明式事务非常简单，
	 * 		1. 在 AppConfig 中追加一个上述定义的 PlatformTransactionManager ，
	 * 		2. @EnableTransactionManagement 就可以启用声明式事务。
	 * 		3. 对需要事务支持的方法，加一个@Transactional注解：
	 * 		注意：声明了@EnableTransactionManagement后，不必额外添加@EnableAspectJAutoProxy。
	 * Spring对一个声明式事务的方法，如何开启事务支持？原理仍然是AOP代理，即通过自动创建Bean的Proxy实现
	 *
	 *
	 * 使用声明式事务的回滚事务：
	 * 默认情况下，如果发生了RuntimeException，Spring的声明式事务将自动回滚。
	 * 在一个事务方法中，如果程序判断需要回滚事务，只需抛出RuntimeException，例如：
	 * 		@Transactional
	 * 		public buyProducts(long productId, int num) {
	 * 		    ...
	 * 		    if (store < num) {
	 * 		        // 库存不够，购买失败:
	 * 		        throw new IllegalArgumentException("No enough products");
	 * 		    }
	 * 		    ...
	 * 		}
	 * 如果要针对Checked Exception回滚事务，需要在@Transactional注解中写出来：
	 * 		@Transactional(rollbackFor = {RuntimeException.class, IOException.class})
	 * 		public buyProducts(long productId, int num) throws IOException {
	 * 		    ...
	 * 		}
	 * 上述代码表示在抛出RuntimeException或IOException时，事务将回滚。
	 *
	 * 使用声明式事务的事务边界：
	 * 在使用事务的时候，明确事务边界非常重要。对于声明式事务，例如，下面的register()方法：
	 * 		@Component
	 * 		public class UserService {
	 * 		    @Transactional
	 * 		    public User register(String email, String password, String name) { // 事务开始
	 * 		       ...
	 * 		    } // 事务结束
	 * 		}
	 * 它的事务边界就是register()方法开始和结束。
	 *
	 * 使用声明式事务的事务传播：
	 * 		默认的事务传播级别是REQUIRED，它满足绝大部分的需求。还有一些其他的传播级别：
	 * 		SUPPORTS：表示如果有事务，就加入到当前事务，如果没有，那也不开启事务执行。这种传播级别可用于查询方法，因为SELECT语句既可以在事务内执行，也可以不需要事务；
	 * 		MANDATORY：表示必须要存在当前事务并加入执行，否则将抛出异常。这种传播级别可用于核心更新逻辑，比如用户余额变更，它总是被其他事务方法调用，不能直接由非事务方法调用；
	 * 		REQUIRES_NEW：表示不管当前有没有事务，都必须开启一个新的事务执行。如果当前已经有事务，那么当前事务会挂起，等新事务完成后，再恢复执行；
	 * 		NOT_SUPPORTED：表示不支持事务，如果当前有事务，那么当前事务会挂起，等这个方法执行完成后，再恢复执行；
	 * 		NEVER：和NOT_SUPPORTED相比，它不但不支持事务，而且在监测到当前有事务时，会抛出异常拒绝执行；
	 * 		NESTED：表示如果当前有事务，则开启一个嵌套级别事务，如果当前没有事务，则开启一个新事务。
	 * 上面这么多种事务的传播级别，其实默认的REQUIRED已经满足绝大部分需求，SUPPORTS和REQUIRES_NEW在少数情况下会用到，
	 * 其他基本不会用到，因为把事务搞得越复杂，不仅逻辑跟着复杂，而且速度也会越慢。
	 *
	 * Spring是如何传播事务的？
	 * 我们在JDBC中使用事务的时候，是这么个写法：
	 * 		Connection conn = openConnection();
	 * 		try {
	 * 		    // 关闭自动提交:
	 * 		    conn.setAutoCommit(false);
	 * 		    // 执行多条SQL语句:
	 * 		    insert(); update(); delete();
	 * 		    // 提交事务:
	 * 		    conn.commit();
	 * 		} catch (SQLException e) {
	 * 		    // 回滚事务:
	 * 		    conn.rollback();
	 * 		} finally {
	 * 		    conn.setAutoCommit(true);
	 * 		    conn.close();
	 * 		}
	 * Spring 使用声明式事务，最终也是通过执行JDBC事务来实现功能的，那么，一个事务方法，如何获知当前是否存在事务？
	 * 答案是使用 ThreadLocal。===理解这个很重要===
	 * Spring总是把JDBC相关的 Connection 和 TransactionStatus 实例绑定到 ThreadLocal。
	 * 如果一个事务方法从ThreadLocal未取到事务，那么它会打开一个新的JDBC连接，同时开启一个新的事务，
	 * 否则，它就直接使用从ThreadLocal获取的JDBC连接以及TransactionStatus。
	 * 因此，事务能正确传播的前提是，方法调用是在一个线程内才行。如果像下面这样写：
	 * 		@Transactional
	 * 		public User register(String email, String password, String name) { // BEGIN TX-A
	 * 		    User user = jdbcTemplate.insert("...");
	 * 		    new Thread(() -> {
	 * 		        // BEGIN TX-B:
	 * 		        bonusService.addBonus(user.id, 100);
	 * 		        // END TX-B
	 * 		    }).start();
	 * 		} // END TX-A
	 * 在另一个线程中调用BonusService.addBonus()，它根本获取不到当前事务，
	 * 因此，UserService.register()和BonusService.addBonus()两个方法，将分别开启两个完全独立的事务。
	 * 换句话说，事务只能在当前线程传播，无法跨线程传播。
	 */
	@Value("${jdbc.url}")
	String jdbcUrl;

	@Value("${jdbc.username}")
	String jdbcUsername;

	@Value("${jdbc.password}")
	String jdbcPassword;

	@Bean
	DataSource createDataSource() {
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(jdbcUrl);
		config.setUsername(jdbcUsername);
		config.setPassword(jdbcPassword);
		config.addDataSourceProperty("autoCommit", "true");
		config.addDataSourceProperty("connectionTimeout", "5");
		config.addDataSourceProperty("idleTimeout", "60");
		return new HikariDataSource(config);
	}

	@Bean
	JdbcTemplate createJdbcTemplate(@Autowired DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}

	public static void main(String[] args) {
		ApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
		UserService userService = context.getBean(UserService.class);
		// userService.register("bob@example.com", "password1", "Bob");
		// userService.register("alice@example.com", "password2", "Alice");
		User bob = userService.getUserByName("Bob");
		System.out.println(bob);
		// User tom = userService.register("tom@example.com", "password3", "Tom");
		// System.out.println(tom);
		System.out.println("Total: " + userService.getUsers());
		for (User u : userService.getUsers(1)) {
			System.out.println(u);
		}
		((ConfigurableApplicationContext) context).close();
	}
}
