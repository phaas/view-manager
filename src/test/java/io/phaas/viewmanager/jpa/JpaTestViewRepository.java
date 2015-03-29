package io.phaas.viewmanager.jpa;

import java.util.List;

import javax.persistence.FlushModeType;
import javax.persistence.TypedQuery;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JpaTestViewRepository extends AbstractViewModelRepository<TestViewEntity, String> {

	public JpaTestViewRepository(ObjectMapper objectMapper) {
		super(TestViewEntity.class, objectMapper);
	}

	@Override
	protected String getKey(String id) {
		return id;
	}

	private static final String JPQL_FIND_BY_ITEM_ID = "from " + TestViewEntity.class.getCanonicalName() + " where itemId = :id";

	public List<TestViewEntity> findAll(String id, FlushModeType flushModeType) {
		TypedQuery<TestViewEntity> query = getEntityManager().createQuery(JPQL_FIND_BY_ITEM_ID, TestViewEntity.class);
		query.setFlushMode(flushModeType);
		List<TestViewEntity> result = query.setParameter("id", id).getResultList();
		return initialize(result);
	}

}
