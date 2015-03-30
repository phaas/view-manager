package io.phaas.viewmanager;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

public class JdbcPersistenceAdapter<E> implements PersistenceAdapter<E> {

	private final JdbcTemplate jdbc;
	private final String idColumn;
	private final String versionColumn;
	private final String[] otherColumns;

	private final String jdbcInsert;
	private final String jdbcSelect;
	private final String jdbcUpdate;
	private final String tableName;
	private final RowMapper<E> rowMapper;

	public JdbcPersistenceAdapter(DataSource dataSource, RowMapper<E> rowMapper, String tableName, String idColumn, String versionColumn,
			String... otherColumns) {
		this.rowMapper = rowMapper;
		this.tableName = tableName;
		this.idColumn = idColumn;
		this.versionColumn = versionColumn;
		this.otherColumns = otherColumns;
		this.jdbc = new JdbcTemplate(dataSource);
		jdbcInsert = buildInsertStatement(tableName, idColumn, versionColumn, otherColumns);
		jdbcSelect = buildSelectStatement(tableName, idColumn, versionColumn, otherColumns);
		jdbcUpdate = buildUpdateStatement(tableName, idColumn, versionColumn, otherColumns);
	}

	@Override
	public E select(String id) {
		return jdbc.queryForObject(jdbcSelect, rowMapper, id);
	}

	@Override
	public void insert(Object[] params) {
		jdbc.update(jdbcInsert, params);
	}

	@Override
	public void update(Object[] params, long version) {
		final Object[] objectValues = params;
		int rows = jdbc.update(jdbcUpdate, new PreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement ps) throws SQLException {
				final boolean hasVersion = versionColumn != null;
				int offset = 0;
				int lastValue = objectValues.length - 1;

				// Assign values 2..n to parameters 1..n-1
				for (int parameterIndex = 1; parameterIndex <= lastValue; parameterIndex++) {
					ps.setObject(parameterIndex, objectValues[parameterIndex - offset]);
				}
				// ID and VERSION are the final parameter(s)
				ps.setObject(lastValue + 1, objectValues[0]);
				if (hasVersion) {
					ps.setObject(lastValue + 2, version);
				}
			}
		});
		if (rows != 1) {
			throw new ObjectOptimisticLockingFailureException("??", params[0]);
		}
	}

	@Override
	public void delete(Set<String> ids) {
		StringBuilder sql = new StringBuilder("DELETE FROM ").append(tableName).append(" WHERE ").append(idColumn);
		sql.append(" in (").append(params(ids.size())).append(")");

		int count = jdbc.update(sql.toString(), ids.toArray());
		if (count != ids.size()) {
			String msg = String.format("Expected %d deletions but affected %d rows (%s)", ids.size(), count, ids);
			throw new OptimisticLockingFailureException(msg);
		}
	}

	@Override
	public void deleteAll() {
		jdbc.execute("truncate table " + tableName);
	}

	protected String buildInsertStatement(String tableName, String idColumn, String versionColumn, String... otherColumns) {
		return String.format("INSERT INTO %s (%s) values (%s)", tableName, //
				columns(idColumn, versionColumn, otherColumns), params(versionColumn == null ? 1 : 2 + otherColumns.length));
	}

	protected String buildUpdateStatement(String tableName, String idColumn, String versionColumn, String... otherColumns) {
		StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" SET ");

		boolean first = true;
		if (versionColumn != null) {
			sql.append(versionColumn).append("=?");
			first = false;
		}

		for (String col : otherColumns) {
			if (first) {
				first = false;
			} else {
				sql.append(", ");
			}
			sql.append(col).append("=?");
		}
		sql.append(" WHERE ").append(idColumn).append("=?");

		if (versionColumn != null) {
			sql.append(" AND ").append(versionColumn).append("=?");
		}

		return sql.toString();
	}

	protected String buildSelectStatement(String tableName, String idColumn, String versionColumn, String... otherColumns) {
		return String.format("SELECT %s FROM %s WHERE %s = ?", columns(idColumn, versionColumn, otherColumns), tableName, idColumn);
	}

	protected static String columns(String idColumn, String versionColumn, String[] otherColumns) {
		StringBuilder sb = new StringBuilder(idColumn);
		if (versionColumn != null) {
			sb.append(",").append(versionColumn);
		}

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

}
