package io.phaas.viewmanager.performance;

import static org.junit.Assert.assertEquals;
import io.phaas.viewmanager.TestViewManager;
import io.phaas.viewmanager.ViewEntity;
import io.phaas.viewmanager.configuration.TestConfiguration;
import io.phaas.viewmanager.jpa.JpaTestViewRepository;
import io.phaas.viewmanager.jpa.JpaViewEntity;
import io.phaas.viewmanager.jpa.TestViewEntity;

import java.sql.SQLException;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.persistence.FlushModeType;
import javax.persistence.PersistenceContext;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StopWatch;

/**
 * Compare JPA/Hibernate based view management with JDBC/ViewManager implementation.
 * 
 * @author Patrick Haas
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { TestConfiguration.class })
public class TestPerformance {

	private static final int UPDATE_COUNT = 10;
	private static final int ITEM_COUNT = 1000;
	private static final int LOOPS = 5;

	@PersistenceContext
	private EntityManager em;

	@Resource
	private PlatformTransactionManager tm;

	@Resource
	private JpaTestViewRepository jpaRepository;

	@Resource
	private TestViewManager jdbcViewManager;

	@Resource
	private TransactionTemplate tx;

	@Before
	public void cleanup() {
		jpaRepository.deleteAll();
	}

	@Test
	public void jpaBaseline() throws SQLException {
		PersistenceAdapter jpa = new PersistenceAdapter.JpaAdapter(jpaRepository, FlushModeType.COMMIT);
		tx.execute(t -> {
			jpa.createItem("1");
			jpa.updateItem("1");
			return null;
		});
		printSerializationStats();

		TestViewEntity load = tx.execute(t -> {
			return jpaRepository.require("1");
		});
		printSerializationStats();

		assertEquals("1", load.getId());
		assertEquals(1, load.read().count);
	}

	@Test
	public void testCreation() {
		PersistenceAdapter jpa = new PersistenceAdapter.JpaAdapter(jpaRepository, FlushModeType.COMMIT);
		PersistenceAdapter jdbc = new PersistenceAdapter.JdbcAdapter(jdbcViewManager);
		jdbcViewManager.deleteAll();
		createAndUpdateObjectsInSingleTransaction(jpa);
		jdbcViewManager.deleteAll();
		createAndUpdateObjectsInSingleTransaction(jdbc);
	}

	@Test
	public void testInterleavedCreation() {
		PersistenceAdapter jpa = new PersistenceAdapter.JpaAdapter(jpaRepository, FlushModeType.COMMIT);
		PersistenceAdapter jdbc = new PersistenceAdapter.JdbcAdapter(jdbcViewManager);

		jdbcViewManager.deleteAll();
		createAllObjectsThenFindAndUpdateObjects(jpa);
		jdbcViewManager.deleteAll();
		createAllObjectsThenFindAndUpdateObjects(jdbc);
	}

	@Test
	public void testFindByCriteriaForUpdate() {
		PersistenceAdapter jpa = new PersistenceAdapter.JpaAdapter(jpaRepository, FlushModeType.COMMIT);
		PersistenceAdapter jdbc = new PersistenceAdapter.JdbcAdapter(jdbcViewManager);

		jdbcViewManager.deleteAll();
		createObjectsThenFindObjectsByQueryInNewTransaction(jpa);
		jdbcViewManager.deleteAll();
		createObjectsThenFindObjectsByQueryInNewTransaction(jdbc);
	}

	public void createAndUpdateObjectsInSingleTransaction(PersistenceAdapter adapter) {
		for (int i = 0; i < LOOPS; i++) {
			final int iteration = i;
			StopWatch sw = new StopWatch("testCreation" + adapter.getClass().getSimpleName());
			sw.start("CreateAndUpdate");
			tx.execute(t -> {
				for (int itemId = 0; itemId < ITEM_COUNT; itemId++) {
					String currentItem = iteration + "-" + itemId;
					adapter.createItem(currentItem);

					for (int update = 0; update < UPDATE_COUNT; update++) {
						adapter.updateItem(currentItem);
					}
				}
				return null;
			});
			sw.stop();
			System.out.println(sw);
			printSerializationStats();
		}
	}

	public void createAllObjectsThenFindAndUpdateObjects(PersistenceAdapter adapter) {
		for (int testRun = 0; testRun < LOOPS; testRun++) {
			final int iteration = testRun;
			StopWatch sw = new StopWatch("testInterleavedCreation" + adapter.getClass().getSimpleName());
			sw.start("CreateThenUpdate");
			tx.execute(t -> {
				for (int itemId = 0; itemId < ITEM_COUNT; itemId++) {
					String currentItem = iteration + "-" + itemId;
					adapter.createItem(currentItem);
				}
				for (int itemId = 0; itemId < ITEM_COUNT; itemId++) {
					String currentItem = iteration + "-" + itemId;
					for (int update = 0; update < UPDATE_COUNT; update++) {
						adapter.updateItem(currentItem);
					}
				}
				return null;
			});
			sw.stop();
			System.out.println(sw);
			printSerializationStats();
		}
	}

	public void createObjectsThenFindObjectsByQueryInNewTransaction(PersistenceAdapter adapter) {
		for (int testRun = 0; testRun < LOOPS; testRun++) {
			final int iteration = testRun;

			StopWatch sw = new StopWatch("testFindByCriteriaForUpdate" + adapter.getClass().getSimpleName());
			sw.start("creation");
			tx.execute(t -> {
				for (int itemId = 0; itemId < ITEM_COUNT; itemId++) {
					String currentItem = iteration + "-" + itemId;
					adapter.createItem(currentItem);
				}
				return null;
			});
			sw.stop();

			sw.start("updates");
			tx.execute(t -> {
				for (int itemId = 0; itemId < ITEM_COUNT; itemId++) {
					String currentItem = iteration + "-" + itemId;
					for (int update = 0; update < UPDATE_COUNT; update++) {
						adapter.updateItemByItemId(currentItem);
					}
				}
				return null;
			});

			sw.stop();
			System.out.println(sw);
			printSerializationStats();
		}
	}

	public void printSerializationStats() {
		System.out.println("Serialization count: " + JpaViewEntity.serialization + "/" + ViewEntity.serialization);
	}
}
