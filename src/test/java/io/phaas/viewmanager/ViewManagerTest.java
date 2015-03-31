package io.phaas.viewmanager;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import io.phaas.viewmanager.configuration.TestConfiguration;
import io.phaas.viewmanager.model.TestObject;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.PersistenceContext;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { TestConfiguration.class })
public class ViewManagerTest {

	@PersistenceContext
	private EntityManager em;

	@Resource
	private PlatformTransactionManager tm;

	@Resource
	private TestViewManager vm;

	@Resource
	private TransactionTemplate tx;

	@Before
	public void cleanup() {
		vm.deleteAll();
	}

	@Test
	public void testPersist() {
		TestEntity entity = new TestEntity("ID", new TestObject("GroupID", "Key", "OtherKey"));

		tx.execute(t -> {
			vm.persist(entity);
			return null;
		});

		TestEntity load = tx.execute(t -> {
			return vm.require("ID");
		});
		assertEquals(entity.getId(), load.getId());
		assertEquals(entity.getGroupId(), load.getGroupId());
		assertEquals(entity.getKey(), load.getKey());
		assertEquals(entity.getOtherKey(), load.getOtherKey());
	}

	@Test
	public void testUpdate() {
		TestEntity entity = new TestEntity("ID", new TestObject("GroupID", "Key", "OtherKey"));

		tx.execute(t -> {
			vm.persist(entity);
			return null;
		});

		tx.execute(t -> {
			vm.require("ID").write().count = 1;
			return null;
		});

		TestEntity result = tx.execute(t -> {
			return vm.require("ID");
		});

		assertEquals(1, result.read().count);
	}

	@Test
	public void testDelete() {
		TestEntity entity = new TestEntity("ID", new TestObject("GroupID", "Key", "OtherKey"));

		tx.execute(t -> {
			vm.persist(entity);
			return null;
		});

		tx.execute(t -> {
			vm.remove("ID");
			return null;
		});

		try {
			tx.execute(t -> {
				return vm.require("ID");
			});
			fail("EntityNotFoundException expected");
		} catch (EntityNotFoundException e) {
			/* expected */
		}
	}

	@Test
	public void testFindByGroupId() {
		tx.execute(t -> {
			vm.persist(new TestEntity("ID1", new TestObject("GroupID", "RED", "Color")));
			vm.persist(new TestEntity("ID2", new TestObject("GroupID", "BLUE", "Color")));
			return null;
		});

		List<TestEntity> result = tx.execute(t -> {
			return vm.findByGroupId("GroupID");
		});

		Optional<TestEntity> id1 = result.stream().filter(item -> "ID1".equals(item.getId())).findFirst();
		assertTrue(id1.isPresent());

		Optional<TestEntity> id2 = result.stream().filter(item -> "ID2".equals(item.getId())).findFirst();
		assertTrue(id2.isPresent());
	}

	@Test
	public void testFindAttachedEntity() {
		tx.execute(t -> {
			TestEntity entity = new TestEntity("ID1", new TestObject("GroupID", "RED", "Color"));
			vm.persist(entity);

			assertSame(entity, vm.require("ID1"));
			return null;
		});
	}

	@Test
	public void testFindAttachedEntityByGroupId() {
		tx.execute(t -> {
			vm.persist(new TestEntity("ID1", new TestObject("GroupID", "RED", "Color")));
			return null;
		});

		tx.execute(t -> {
			TestEntity entity = vm.require("ID1");
			List<TestEntity> resultList = vm.findByGroupId("GroupID");

			assertSame(entity, resultList.get(0));
			return null;
		});
	}

	@Test
	public void testFindAttachedModifiedEntityByGroupId() {
		tx.execute(t -> {
			vm.persist(new TestEntity("ID1", new TestObject("GroupID", "RED", "Color")));
			return null;
		});

		tx.execute(t -> {
			TestEntity entity = vm.require("ID1");
			entity.setGroupId("Group2");
			entity.write().groupId = "Group2";

			assertThat(vm.findByGroupId("GroupID"), hasSize(0));

			assertThat(vm.findByGroupId("Group2"), hasItem(entity));

			return null;
		});
	}

	@Test
	public void testUpdateEntitiesFoundByGroupId() {
		tx.execute(t -> {
			vm.persist(new TestEntity("ID1", new TestObject("GroupID", "RED", "Color")));
			vm.persist(new TestEntity("ID2", new TestObject("GroupID", "BLUE", "Color")));
			return null;
		});

		tx.execute(t -> {
			vm.findByGroupId("GroupID").forEach(item -> {
				TestObject json = item.write();
				json.count = 5;
				json.otherKey = "OTHER";
				item.setOtherKey("OTHER");
			});
			return null;
		});

		tx.execute(t -> {
			TestEntity id1 = vm.require("ID1");
			TestEntity id2 = vm.require("ID2");

			assertEquals(5, id1.read().count);
			assertEquals("OTHER", id1.getOtherKey());

			assertEquals(5, id2.read().count);
			assertEquals("OTHER", id2.getOtherKey());
			return null;
		});
	}

	@Test
	public void testConcurrentModification() {
		tx.execute(t -> {
			vm.persist(new TestEntity("ID1", new TestObject("GroupID", "RED", "Color")));
			return null;
		});

		final CountDownLatch start = new CountDownLatch(1);
		final CountDownLatch done = new CountDownLatch(1);
		new Thread(() -> {
			tx.execute(t -> {
				try {
					start.await();
				} catch (Exception e) {
					e.printStackTrace();
				}
				TestEntity entity = vm.require("ID1");
				entity.write().count++;
				return null;
			});
			done.countDown();
		}).start();

		try {
			tx.execute(t -> {
				TestEntity entity = vm.require("ID1");
				start.countDown();
				try {
					done.await();
				} catch (Exception e) {
					e.printStackTrace();
				}
				entity.write().count++;
				return null;
			});
			fail("Expected concurrent modification exception");
		} catch (OptimisticLockingFailureException e) {
			// success
		}
	}
}
