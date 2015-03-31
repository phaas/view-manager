package io.phaas.viewmanager;

import javax.persistence.EntityNotFoundException;

public interface ViewManager<E extends ViewEntity<?>, I> {

	/**
	 * Persist a new view.
	 */
	void persist(E entity);

	/**
	 * Find an existing view with this id, and throw an error if it doesn't exist.
	 * 
	 * @param id
	 * @return the view
	 * @throws EntityNotFoundException
	 */
	E require(I id) throws EntityNotFoundException;

	/**
	 * Find an existing view with this id.
	 * 
	 * @param id
	 * @return the view, or null if not found
	 */
	E load(I id);

	/**
	 * Delete the view with this id.
	 * 
	 * @param id
	 */
	void remove(I id);

	/**
	 * Remove all entities
	 */
	void deleteAll();

}