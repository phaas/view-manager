package io.phaas.viewmanager.performance;

import java.util.List;

import javax.persistence.FlushModeType;

import io.phaas.viewmanager.TestEntity;
import io.phaas.viewmanager.TestViewManager;
import io.phaas.viewmanager.jpa.TestViewEntity;
import io.phaas.viewmanager.jpa.JpaTestViewRepository;
import io.phaas.viewmanager.model.TestObject;

public interface PersistenceAdapter {

	void createItem(String id);

	void updateItemByItemId(String id);

	void updateItem(String id);

	public static class JpaAdapter implements PersistenceAdapter {

		private final JpaTestViewRepository manager;
		private final FlushModeType flushMode;

		public JpaAdapter(JpaTestViewRepository manager, FlushModeType flushMode) {
			this.manager = manager;
			this.flushMode = flushMode;
		}

		@Override
		public void updateItemByItemId(String id) {
			List<TestViewEntity> result = manager.findAll("item." + id, flushMode);
			for (TestViewEntity x : result) {
				x.write().count++;
			}
		}

		@Override
		public void updateItem(String currentItem) {
			manager.require(currentItem).write().count++;
		}

		@Override
		public void createItem(String id) {
			TestObject json = new TestObject();
			json.groupId = "item." + id;
			json.key = "GREEN";
			json.otherKey = "FOO";

			TestViewEntity item = manager.create(id);
			item.setItemId("item." + id);
			item.setKey("GREEN");
			item.setOtherKey("FOO");
			item.setObject(json);
			manager.persist(item);
		}
	}

	public class JdbcAdapter implements PersistenceAdapter {
		private final TestViewManager vm;

		public JdbcAdapter(TestViewManager vm) {
			this.vm = vm;
		}

		@Override
		public void createItem(String id) {
			vm.persist(new TestEntity(id, new TestObject("item." + id, "GREEN", "FOO")));
		}

		@Override
		public void updateItemByItemId(String id) {
			List<TestEntity> result = vm.findByGroupId("item." + id);
			for (TestEntity x : result) {
				x.write().count++;
			}
		}

		@Override
		public void updateItem(String id) {
			vm.require(id).write().count++;
		}
	}

}
