package io.phaas.viewmanager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.jdbc.core.RowMapper;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TestViewManager extends AbstractViewManager<TestEntity, String, TestViewManager.TestEntityJdbcAdapter> {

	protected static final class TestEntityJdbcAdapter extends JdbcPersistenceAdapter<TestEntity> {
		private static final String jdbcSelectByGroupId = "SELECT id, version, serialized_data, item_id, key, other_key "
				+ "FROM test.test_view_entity WHERE item_id = ?";

		protected TestEntityJdbcAdapter(DataSource dataSource) {
			super(dataSource, ROW_MAPPER, "TEST.TEST_VIEW_ENTITY", "ID", "VERSION", "SERIALIZED_DATA", "ITEM_ID", "KEY", "OTHER_KEY");
		}

		public List<TestEntity> findByGroupId(String groupId) {
			return getJdbc().query(jdbcSelectByGroupId, ROW_MAPPER, groupId);
		}
	}

	protected static final RowMapper<TestEntity> ROW_MAPPER = new RowMapper<TestEntity>() {
		@Override
		public TestEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
			TestEntity testEntity = new TestEntity();
			testEntity.setId(rs.getString("ID"));
			testEntity.setVersion(rs.getLong("VERSION"));
			testEntity.setGroupId(rs.getString("ITEM_ID"));
			testEntity.setKey(rs.getString("KEY"));
			testEntity.setOtherKey(rs.getString("OTHER_KEY"));
			testEntity.setSerializedData(rs.getBytes("SERIALIZED_DATA"));
			return testEntity;
		}
	};

	public TestViewManager(DataSource dataSource, ObjectMapper objectMapper) {
		super(objectMapper, new TestEntityJdbcAdapter(dataSource));
	}

	@Override
	protected Object[] getObjectValues(TestEntity e) {
		return new Object[] { e.getId(), e.getVersion(), e.getSerializedData(), e.getGroupId(), e.getKey(), e.getOtherKey() };
	}

	public List<TestEntity> findByGroupId(String groupId) {
		List<TestEntity> dbResults = getPersistence().findByGroupId(groupId);
		return mergeObjectsWithSession(dbResults, e -> groupId.equals(e.getGroupId()));
	}

	@Override
	protected void incrementVersion(TestEntity entity) {
		entity.setVersion(entity.getVersion() + 1);
	}

	@Override
	protected boolean isModified(TestEntity entity) {
		return true;
	}

}
