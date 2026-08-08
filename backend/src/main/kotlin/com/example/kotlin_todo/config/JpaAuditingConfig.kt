package com.example.kotlin_todo.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

/**
 * JpaAuditingの設定クラス。
 */
@Configuration
@EnableJpaAuditing
class JpaAuditingConfig
