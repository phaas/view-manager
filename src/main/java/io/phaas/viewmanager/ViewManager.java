package io.phaas.viewmanager;

import javax.persistence.EntityNotFoundException;

public interface ViewManager<E extends ViewEntity<?>, I> {

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
	 * Create a new entity with this id. The returned entity is attached to the entity manager.
	 * 
	 * @param id
	 * @return
	 */
	E create(I id);

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

	void persist(E entity);
}