package io.phaas.viewmanager;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ViewManagerSession<E extends ViewEntity<?>> {

	private final AbstractViewManager<E, ?, ?> vm;
	private final Map<String, E> addedObjects = new TreeMap<>();
	private final Map<String, E> loadedObjects = new TreeMap<>();
	private final Set<String> removedObjects = new TreeSet<>();

	public ViewManagerSession(AbstractViewManager<E, ?, ?> vm) {
		this.vm = vm;
	}

	public void add(E e) {
		addedObjects.put(e.getId(), e);
	}

	public void addLoadedObject(E e) {
		addedObjects.remove(e.getId());
		loadedObjects.put(e.getId(), e);
	}

	public void addRemovedObject(String id) {
		addedObjects.remove(id);
		loadedObjects.remove(id);
		removedObjects.add(id);
	}

	public E find(String id) {
		E result = addedObjects.get(id);
		if (result == null) {
			result = loadedObjects.get(id);
		}
		if (result == null && removedObjects.contains(id)) {
			throw new ObjectDeletedException("Cannot load item " + id + " as it has been removed");
		}
		return result;
	}

	public List<E> find(Predicate<E> matcher) {
		Stream<E> items = Stream.concat(addedObjects.values().stream(), loadedObjects.values().stream());
		return items.filter(matcher).collect(Collectors.toList());
	}

	public void commit() {
		vm.delete(removedObjects);
		for (E x : loadedObjects.values()) {
			vm.update(x);
		}
		for (E x : addedObjects.values()) {
			vm.insert(x);
		}
	}
}
