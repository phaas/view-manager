package io.phaas.viewmanager;

import com.fasterxml.jackson.core.type.TypeReference;

import io.phaas.viewmanager.model.TestObject;

public class TestEntity extends ViewEntity<TestObject> {
	private static final TypeReference<TestObject> TYPE_REFERENCE = new TypeReference<TestObject>() {
	};

	private String groupId;
	private String key;
	private String otherKey;

	public TestEntity(String id, TestObject json) {
		setId(id);
		setObject(json);
		setGroupId(json.groupId);
		setKey(json.key);
		setOtherKey(json.otherKey);
	}

	public TestEntity() {
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getOtherKey() {
		return otherKey;
	}

	public void setOtherKey(String otherKey) {
		this.otherKey = otherKey;
	}

	@Override
	protected TypeReference<TestObject> typeReference() {
		return TYPE_REFERENCE;
	}

	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

}
