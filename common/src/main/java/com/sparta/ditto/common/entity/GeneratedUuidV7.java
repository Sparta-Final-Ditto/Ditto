package com.sparta.ditto.common.entity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.hibernate.annotations.IdGeneratorType;

/** UUID PK 필드에 붙여 UuidV7IdentifierGenerator로 채번하도록 지정하는 어노테이션 */
@IdGeneratorType(UuidV7IdentifierGenerator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface GeneratedUuidV7 {
}
