package io.phaas.viewmanager.jpa;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.persistence.Basic;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;
import javax.persistence.Version;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@MappedSuperclass
public abstract class JpaViewEntity<T> {

	private transient boolean dirty;
	private transient T object;
	private transient ObjectMapper objectMapper;

	private String id;
	private byte[] serializedData;
	private long version;
	public static int serialization;

	@Transient
	public T read() {
		if (object == null && serializedData != null) {
			TypeReference<T> typeReference = null;
			try {
				typeReference = typeReference();
				object = objectMapper.reader(typeReference).readValue(serializedData);
			} catch (Exception e) {
				throw new RuntimeException(String.format("Can't deserialize %s",
						typeReference == null ? "unknown" : typeReference.getType()), e);
			}
		}
		return object;
	}

	@Transient
	public T write() {
		dirty = true;
		return read();
	}

	@Lob
	public byte[] getSerializedData() {
		updateSerializedData();
		return serializedData;
	}

	@JsonValue
	@JsonRawValue
	public String generateRawJsonString() {
		return new String(getSerializedData(), StandardCharsets.UTF_8);
	}

	@Basic
	@Id
	public String getId() {
		return id;
	}

	public void setObject(T object) {
		this.dirty = true;
		this.object = object;
	}

	public void setObjectMapper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void setSerializedData(byte[] serializedData) {
		this.serializedData = serializedData;
	}

	public void setId(String svcOrderId) {
		this.id = svcOrderId;
	}

	private void updateSerializedData() {
		try {
			if (dirty) {
				serializedData = object == null ? null : objectMapper.writeValueAsBytes(object);
				// Once an entity is dirty, it must remain dirty while it's attached to the entity manager
				// Otherwise changes made after an EntityManager.flush() will be lost.
				serialization++;
				dirty = false;
			}
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	@Version
	public long getVersion() {
		return version;
	}

	public void setVersion(long version) {
		this.version = version;
	}

	@Transient
	protected abstract TypeReference<T> typeReference();

	public void writeTo(JsonGenerator json) throws JsonGenerationException, IOException {
		json.writeRawValue(generateRawJsonString());
	}

	@Transient
	protected ObjectMapper getObjectMapper() {
		return objectMapper;
	}

}
