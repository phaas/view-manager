package io.phaas.viewmanager;

import java.util.Set;

public interface PersistenceAdapter<E> {

	E select(String id);

	void insert(Object[] params);

	void update(Object[] params);

	void delete(Set<String> ids);

	void deleteAll();

}
