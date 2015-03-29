package io.phaas.viewmanager.jpa;

import java.util.List;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.PersistenceContext;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.fasterxml.jackson.databind.ObjectMapper;

@Transactional
public abstract class AbstractViewModelRepository<E extends JpaViewEntity<?>, I> {

	@PersistenceContext
	private EntityManager entityManager;

	private final ObjectMapper objectMapper;

	private final Class<E> clazz;

	protected AbstractViewModelRepository(Class<E> clazz, ObjectMapper objectMapper) {
		this.clazz = clazz;
		this.objectMapper = objectMapper;
	}

	public E create(I id) {
		try {
			E entity = clazz.newInstance();
			entity.setId(getKey(id));
			entity.setObjectMapper(objectMapper);
			initializeNewObject(entity, id);
			return entity;
		} catch (EntityExistsException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("Programming error: can not instantiate class " + clazz, e);
		}
	}

	public void persist(E entity) {
		entityManager.persist(entity);
	}

	public E load(I id) {
		E entity = entityManager.find(clazz, getKey(id));
		return entity == null ? null : initialize(entity);
	}

	public E require(I id) {
		E entity = entityManager.find(clazz, getKey(id));
		if (entity == null) {
			throw new EntityNotFoundException("Could not find view with id " + getKey(id));
		}
		return initialize(entity);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void remove(I id) {
		E entity = entityManager.find(clazz, getKey(id));
		Assert.notNull(entity, "Entity not found");
		entityManager.remove(entity);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void deleteAll() {
		entityManager.createQuery("DELETE FROM " + clazz.getSimpleName()).executeUpdate();
	}

	/**
	 * Object key to string mapping. Must be implemented when the entities primary key
	 * 
	 * @param id
	 * @return
	 */
	protected abstract String getKey(I id);

	protected void initializeNewObject(E entity, I id) {
	}

	protected EntityManager getEntityManager() {
		return entityManager;
	}

	protected ObjectMapper getObjectMapper() {
		return objectMapper;
	}

	protected List<E> initialize(List<E> entities) {
		for (E entity : entities) {
			initialize(entity);
		}
		return entities;
	}

	protected E initialize(E entity) {
		entity.setObjectMapper(getObjectMapper());
		return entity;
	}
}
