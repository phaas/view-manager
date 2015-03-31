package io.phaas.viewmanager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.persistence.EntityNotFoundException;

import org.hibernate.ObjectDeletedException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A view manager is an EntityManager-like layer for managing "Document" objects. The view manager optimizes repeated access to the same
 * document object(s) by caching all of the documents accessed in a transaction.
 * 
 * <em>Document objects</em> generally map to a single table, have a unique ID, wrap a JSON-serialized payload and have some additional
 * (optional) attributes for lookup.
 *
 * @param <E>
 * @param <I>
 * 
 * @author Patrick Haas
 */
public abstract class AbstractViewManager<E extends ViewEntity<?>, I, P extends PersistenceAdapter<E>> implements ViewManager<E, I> {

	private final class ViewManagerTransactionSynchronization extends TransactionSynchronizationAdapter {
		private ViewManagerSession<E> session;

		public ViewManagerTransactionSynchronization(ViewManagerSession<E> session) {
			this.session = session;
		}

		@Override
		public void beforeCommit(boolean readOnly) {
			session.commit();
		}

		@Override
		public void afterCompletion(int status) {
			threadSession.remove();
		}
	}

	// private final JdbcTemplate jdbc;
	private final ObjectMapper objectMapper;

	private final ThreadLocal<ViewManagerSession<E>> threadSession = new ThreadLocal<>();
	private final P persistence;

	public AbstractViewManager(ObjectMapper objectMapper, P persistence) {
		this.objectMapper = objectMapper;
		this.persistence = persistence;
	}

	@Override
	public E require(I id) throws EntityNotFoundException {
		ViewManagerSession<E> session = getSession();

		try {
			E result = session.find(id.toString());
			if (result != null) {
				return result;
			}

			result = persistence.select(id.toString());

			initializeObject(result);
			session.addLoadedObject(result, result.getVersion());
			return result;
		} catch (EmptyResultDataAccessException | ObjectDeletedException e) {
			throw new EntityNotFoundException(e.getMessage());
		}
	}

	@Override
	public E load(I id) {
		try {
			return require(id);
		} catch (EntityNotFoundException e) {
			return null;
		}
	}

	@Override
	public void remove(I id) {
		ViewManagerSession<E> session = getSession();
		session.addRemovedObject(id.toString());
	}

	@Override
	public void deleteAll() {
		persistence.deleteAll();
	}

	/**
	 * Merge a list of entities loaded from the database with the session.
	 * 
	 * <ul>
	 * <li>All entities in the session that match the predicate will be added to the result
	 * <li>All entities loaded from the database that also exist in the session will be replaced with the attached entities.
	 * <li>All entities loaded from the database that also exist in the session but no longer qualify for the predicate will be removed.
	 * </ul>
	 * 
	 * @param databaseResults
	 *            entities found in the database
	 * @param matcher
	 *            a Predicate equivalent to the query that selected the database results
	 * @return
	 */
	protected List<E> mergeObjectsWithSession(List<E> databaseResults, Predicate<E> matcher) {
		ViewManagerSession<E> session = getSession();

		List<E> sessionResults = session.find(matcher);
		List<E> result = new ArrayList<>(databaseResults.size() + sessionResults.size());
		result.addAll(sessionResults);

		Set<String> ids = sessionResults.stream().map(e -> e.getId()).collect(Collectors.toSet());

		for (E item : databaseResults) {
			if (ids.contains(item.getId())) {
				// Item was found in the database and the session. The object attached to the session
				// is already part of the result.
				continue;
			}

			try {
				E existing = session.find(item.getId());
				if (existing != null) {
					// Object in database matched the predicate but the entity in session has been modified
					// and no longer meets the predicate
				} else {
					result.add(initializeObject(item));
					session.addLoadedObject(item, item.getVersion());
				}
			} catch (ObjectDeletedException e) {
				// Object in the database matched the predicate but the entity has since been
				// queued for removal
				continue;
			}
		}

		return result;
	}

	protected E initializeObject(E entity) {
		entity.setObjectMapper(objectMapper);
		return entity;
	}

	protected List<E> initialize(List<E> items) {
		items.forEach(item -> initializeObject(item));
		return items;
	}

	@Override
	public void persist(E entity) {
		initializeObject(entity);
		ViewManagerSession<E> session = getSession();
		session.add(entity);
	}

	private ViewManagerSession<E> getSession() {
		ViewManagerSession<E> session = threadSession.get();
		if (session == null) {
			session = new ViewManagerSession<>(this);
			threadSession.set(session);
			TransactionSynchronizationManager.registerSynchronization(new ViewManagerTransactionSynchronization(session));
		}
		return session;
	}

	protected void insert(E object) {
		persistence.insert(getObjectValues(object));
	}

	protected void update(E object, long version) {
		persistence.update(getObjectValues(object), version);
	}

	protected void delete(Set<String> ids) {
		persistence.delete(ids);
	}

	protected ObjectMapper getObjectMapper() {
		return objectMapper;
	}

	protected P getPersistence() {
		return persistence;
	}

	protected abstract Object[] getObjectValues(E entity);

	protected abstract void incrementVersion(E entity);

	protected abstract boolean isModified(E entity);
}
