package io.phaas.viewmanager.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestObject {
	public String groupId;
	public String key;
	public String otherKey;
	public int count = 0;

	public TestObject() {
	}

	public TestObject(String groupId, String key, String otherKey) {
		this.groupId = groupId;
		this.key = key;
		this.otherKey = otherKey;
	}

}
