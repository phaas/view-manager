package io.phaas.viewmanager;

import io.phaas.viewmanager.ViewManagerSession.EntityStatus.Status;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ViewManagerSession<E extends ViewEntity<?>> {

	static class EntityStatus<E> {
		enum Status {
			ADDED, LOADED, REMOVED;
		}

		public final String id;
		public final E item;
		public final long version;
		public Status status;

		public EntityStatus(String id, E item, long version, Status status) {
			this.id = id;
			this.item = item;
			this.version = version;
			this.status = status;
		}
	}

	private final AbstractViewManager<E, ?, ?> vm;
	private final Map<String, EntityStatus<E>> entities = new TreeMap<>();

	public ViewManagerSession(AbstractViewManager<E, ?, ?> vm) {
		this.vm = vm;
	}

	public void add(E entity) {
		entities.put(entity.getId(), new EntityStatus<E>(entity.getId(), entity, 0, Status.ADDED));
	}

	public void addLoadedObject(E entity, long version) {
		entities.put(entity.getId(), new EntityStatus<E>(entity.getId(), entity, entity.getVersion(), Status.LOADED));
	}

	public void addRemovedObject(String id) {
		EntityStatus<E> entityStatus = entities.get(id);
		if (entityStatus != null) {
			entityStatus.status = Status.REMOVED;
		} else {
			entities.put(id, new EntityStatus<E>(id, null, 0, Status.REMOVED));
		}
	}

	public E find(String id) {
		EntityStatus<E> entityStatus = entities.get(id);
		if (entityStatus == null) {
			return null;
		}
		if (entityStatus.status != Status.REMOVED) {
			return entityStatus.item;
		} else {
			throw new ObjectDeletedException("Cannot load item " + id + " as it has been removed");
		}
	}

	public List<E> find(Predicate<E> matcher) {
		return entities.values().stream().filter(es -> es.status != Status.REMOVED).map(es -> es.item)//
				.filter(matcher).collect(Collectors.toList());
	}

	public void commit() {
		Set<String> removedObjects = entities.values().stream()//
				.filter(es -> es.status == Status.REMOVED).map(es -> es.id).collect(Collectors.toSet());
		vm.delete(removedObjects);

		entities.values().stream().filter(es -> es.status == Status.LOADED).forEach(es -> {
			if (vm.isModified(es.item)) {
				vm.incrementVersion(es.item);
				vm.update(es.item, es.version);
			}
		});
		entities.values().stream().filter(es -> es.status == Status.ADDED).forEach(es -> {
			vm.insert(es.item);
		});
	}
}
