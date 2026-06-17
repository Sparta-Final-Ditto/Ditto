package com.sparta.ditto.chat.infrastructure.mongo;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/*
 MongoDB 설정
 - 메시지 원본 저장소 (chat_messages_mongo)
 - Repository scan 범위 지정
*/

@Configuration
@EnableMongoRepositories(basePackages = "com.sparta.ditto.chat.infrastructure.mongo")
public class MongoConfig {
}