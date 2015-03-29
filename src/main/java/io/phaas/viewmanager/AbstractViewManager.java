package io.phaas.viewmanager;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.persistence.EntityNotFoundException;
import javax.sql.DataSource;

import org.hibernate.ObjectDeletedException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
public abstract class AbstractViewManager<E extends ViewEntity<?>, I> implements ViewManager<E, I>, RowMapper<E> {

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

	private final JdbcTemplate jdbc;
	private final ObjectMapper objectMapper;

	private final ThreadLocal<ViewManagerSession<E>> threadSession = new ThreadLocal<>();
	private final String jdbcInsert;
	private final String jdbcSelect;
	private final String jdbcUpdate;
	private final String tableName;
	private final String idColumn;

	public AbstractViewManager(DataSource dataSource, ObjectMapper objectMapper, String tableName, String idColumn, String versionColumn,
			String... otherColumns) {
		this.objectMapper = objectMapper;
		this.tableName = tableName;
		this.idColumn = idColumn;
		this.jdbc = new JdbcTemplate(dataSource);

		jdbcInsert = buildInsertStatement(tableName, idColumn, otherColumns);
		jdbcSelect = buildSelectStatement(tableName, idColumn, otherColumns);
		jdbcUpdate = buildUpdateStatement(tableName, idColumn, otherColumns);
	}

	@Override
	public E require(I id) throws EntityNotFoundException {
		ViewManagerSession<E> session = getSession();

		try {
			E result = session.find(id.toString());
			if (result != null) {
				return result;
			}

			result = jdbc.queryForObject(jdbcSelect, this, id);
			initializeObject(result);
			session.addLoadedObject(result);
			return result;
		} catch (EmptyResultDataAccessException | ObjectDeletedException e) {
			throw new EntityNotFoundException(e.getMessage());
		}
	}

	@Override
	public E load(I id) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public E create(I id) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void remove(I id) {
		ViewManagerSession<E> session = getSession();
		session.addRemovedObject(id.toString());
	}

	@Override
	public void deleteAll() {
		jdbc.execute("truncate table " + tableName);
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
					session.addLoadedObject(item);
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
		jdbc.update(jdbcInsert, getObjectValues(object));
	}

	protected void update(E object) {
		Object[] objectValues = getObjectValues(object);
		int rows = jdbc.update(jdbcUpdate, new PreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement ps) throws SQLException {
				// Assign values 2..n to parameters 1..n-1
				for (int i = 1; i < objectValues.length; i++) {
					ps.setObject(i, objectValues[i]);
				}
				// ID is the last parameter
				ps.setObject(objectValues.length, objectValues[0]);
				// TODO version
			}
		});
		if (rows != 1) {
			throw new ObjectOptimisticLockingFailureException(object.getClass(), object.getId());
		}
	}

	protected void delete(Set<String> ids) {
		StringBuilder sql = new StringBuilder("DELETE FROM ").append(tableName).append(" WHERE ").append(idColumn);
		sql.append(" in (").append(params(ids.size())).append(")");

		int count = jdbc.update(sql.toString(), ids.toArray());
		if (count != ids.size()) {
			String msg = String.format("Expected %d deletions but affected %d rows (%s)", ids.size(), count, ids);
			throw new OptimisticLockingFailureException(msg);
		}
	}

	protected abstract Object[] getObjectValues(E object);

	protected String buildInsertStatement(String tableName, String idColumn, String[] otherColumns) {
		return String.format("INSERT INTO %s ( %s) values (%s)", tableName, columns(idColumn, otherColumns),
				params(1 + otherColumns.length));
	}

	protected String buildUpdateStatement(String tableName, String idColumn, String[] otherColumns) {
		StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" SET ");

		boolean first = true;
		for (String col : otherColumns) {
			if (first) {
				first = false;
			} else {
				sql.append(", ");
			}
			sql.append(col).append("=?");
		}
		sql.append(" WHERE ").append(idColumn).append("=?");
		// TODO "and version=?

		return sql.toString();
	}

	protected String buildSelectStatement(String tableName, String idColumn, String[] otherColumns) {
		return String.format("SELECT %s FROM %s WHERE %s = ?", columns(idColumn, otherColumns), tableName, idColumn);
	}

	protected static String columns(String idColumn, String[] otherColumns) {
		StringBuilder sb = new StringBuilder(idColumn);
		for (String s : otherColumns) {
			sb.append(",").append(s);
		}
		return sb.toString();
	}

	protected static String params(int count) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < count; i++) {
			if (sb.length() > 0) {
				sb.append(",");
			}
			sb.append("?");
		}
		return sb.toString();
	}

	protected JdbcTemplate getJdbc() {
		return jdbc;
	}

	protected ObjectMapper getObjectMapper() {
		return objectMapper;
	}

}
