package io.phaas.viewmanager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TestViewManager extends AbstractViewManager<TestEntity, String> {

	private static final String jdbcSelectByGroupId = "select ID, version, serialized_data, item_id, key, other_key FROM test.test_view_entity WHERE ITEM_ID = ?";

	public TestViewManager(DataSource dataSource, ObjectMapper objectMapper) {
		super(dataSource, objectMapper, "TEST.TEST_VIEW_ENTITY", "ID", "VERSION", "SERIALIZED_DATA", "ITEM_ID", "KEY", "OTHER_KEY");
	}

	@Override
	protected Object[] getObjectValues(TestEntity e) {
		return new Object[] { e.getId(), e.getVersion(), e.getSerializedData(), e.getGroupId(), e.getKey(), e.getOtherKey() };
	}

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

	public List<TestEntity> findByGroupId(String groupId) {
		List<TestEntity> dbResults = getJdbc().query(jdbcSelectByGroupId, this, groupId);
		return mergeObjectsWithSession(dbResults, e -> groupId.equals(e.getGroupId()));
	}

}
