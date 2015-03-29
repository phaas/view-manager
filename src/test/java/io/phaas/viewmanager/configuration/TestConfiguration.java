package io.phaas.viewmanager.configuration;

import io.phaas.viewmanager.TestViewManager;
import io.phaas.viewmanager.jpa.JpaTestViewRepository;

import java.sql.SQLException;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.PreDestroy;
import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.hibernate.cfg.ImprovedNamingStrategy;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableTransactionManagement(mode = AdviceMode.PROXY, proxyTargetClass = true)
public class TestConfiguration {

	private static final String[] JPA_ENTITY_PACKAGES = { "io.phaas.viewmanager.jpa" };

	private boolean showSql = false;
	private String hibernateDialect = "org.hibernate.dialect.H2Dialect";

	@Bean
	public DataSource dataSource() {
		return new SimpleDriverDataSource( //
				new org.h2.Driver(), //
				"jdbc:h2:mem:db;MVCC=TRUE;MODE=DB2;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=TRUE",//
				"sa", "");
	}

	@Bean
	public DataSourceInitializer dataSourceInitializer() {
		DataSourceInitializer init = new DataSourceInitializer();
		init.setDataSource(dataSource());
		init.setDatabasePopulator(new ResourceDatabasePopulator(new ClassPathResource("init-db.sql")));
		return init;
	}

	@PreDestroy
	public void shutDown() throws SQLException {
		// dataSource().getConnection().createStatement().execute("SHUTDOWN");
	}

	@Bean(name = "entityManagerFactory")
	public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
		Map<String, Object> jpaProperties = new TreeMap<String, Object>();

		jpaProperties.put("hibernate.ejb.naming_strategy", ImprovedNamingStrategy.class.getCanonicalName());
		jpaProperties.put("hibernate.default_schema", "TEST");
		jpaProperties.put("hibernate.connection.charSet", "UTF-8");

		// Enable Jadira user types and configure date defaults
		jpaProperties.put("jadira.usertype.autoRegisterUserTypes", "true");
		jpaProperties.put("jadira.usertype.javaZone", "Z");
		jpaProperties.put("jadira.usertype.databaseZone", "Z");

		HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
		vendorAdapter.setDatabasePlatform(hibernateDialect);
		vendorAdapter.setShowSql(showSql);

		LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
		emf.setPackagesToScan(JPA_ENTITY_PACKAGES);
		emf.setPersistenceUnitName("hibernate");
		emf.setDataSource(dataSource());
		emf.setJpaPropertyMap(jpaProperties);
		emf.setJpaVendorAdapter(vendorAdapter);
		return emf;
	}

	@Bean
	public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
		return new JpaTransactionManager(emf);
	}

	@Bean
	public TransactionTemplate TransactionTemplate(PlatformTransactionManager tm) {
		return new TransactionTemplate(tm);
	}

	@Bean
	public JpaTestViewRepository jpaTestViewRepository() {
		return new JpaTestViewRepository(objectMapper());
	}

	@Bean
	public TestViewManager testViewManager() {
		return new TestViewManager(dataSource(), objectMapper());
	}

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}

}
