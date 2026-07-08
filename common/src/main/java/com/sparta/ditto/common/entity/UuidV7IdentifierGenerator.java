package com.sparta.ditto.common.entity;

import com.sparta.ditto.common.util.UuidV7Generator;
import java.lang.reflect.Field;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;
import org.springframework.util.ReflectionUtils;

/** BaseEntity를 포함한 모든 엔티티의 PK를 UuidV7Generator로 채번하는 Hibernate 생성기 */
public class UuidV7IdentifierGenerator implements IdentifierGenerator {

    private final UuidV7Generator uuidV7Generator = new UuidV7Generator();

    @Override
    public Object generate(SharedSessionContractImplementor session, Object object) {
        Object existingId = readExistingId(object);
        if (existingId != null) {
            return existingId;
        }
        return uuidV7Generator.generate();
    }

    /** id가 이미 수동으로 세팅되어 있으면 덮어쓰지 않고 그대로 사용한다 */
    private Object readExistingId(Object object) {
        Field idField = ReflectionUtils.findField(object.getClass(), "id");
        if (idField == null) {
            return null;
        }
        ReflectionUtils.makeAccessible(idField);
        return ReflectionUtils.getField(idField, object);
    }
}
