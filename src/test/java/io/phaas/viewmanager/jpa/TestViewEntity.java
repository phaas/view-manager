package io.phaas.viewmanager.jpa;

import io.phaas.viewmanager.model.TestObject;

import javax.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.core.type.TypeReference;

@Entity
@JsonAutoDetect(getterVisibility = Visibility.NONE)
public class TestViewEntity extends JpaViewEntity<TestObject> {

	private static final TypeReference<TestObject> TYPE_REFERENCE = new TypeReference<TestObject>() {
	};

	private String itemId;
	private String key;
	private String otherKey;

	public TestViewEntity() {
	}

	@Override
	protected TypeReference<TestObject> typeReference() {
		return TYPE_REFERENCE;
	}

	public String getItemId() {
		return itemId;
	}

	public void setItemId(String itemId) {
		this.itemId = itemId;
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

}
