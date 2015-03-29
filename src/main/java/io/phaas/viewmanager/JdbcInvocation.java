package io.phaas.viewmanager;

import org.springframework.jdbc.core.PreparedStatementSetter;

public class JdbcInvocation {

	public final String statement;
	public final PreparedStatementSetter setter;

	public JdbcInvocation(String statement, PreparedStatementSetter setter) {
		this.statement = statement;
		this.setter = setter;
	}
}
