package com.example.chat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;

@Configuration
@EnableReactiveMongoAuditing
public class MongoConfig {
    // MongoDB 설정은 application.properties에서 자동으로 처리됨
}