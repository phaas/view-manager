package io.phaas.viewmanager;

import org.springframework.dao.InvalidDataAccessApiUsageException;

@SuppressWarnings("serial")
public class ObjectDeletedException extends InvalidDataAccessApiUsageException {

	public ObjectDeletedException(String msg) {
		super(msg);
	}

}
