package com.example.kotlin_todo.domain.entity

import com.example.kotlin_todo.domain.enums.Priority
import com.example.kotlin_todo.domain.enums.TodoStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * todoエンティティクラス
 */
@Entity
@Table(name = "todos")
@EntityListeners(AuditingEntityListener::class)
class Todo(
    /** TODOID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /** TODOのタイトル */
    @Column(nullable = false, length = 200)
    var title: String,

    /** TODOの情報 */
    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    /** 期日 */
    @Column(name = "due_date")
    var dueDate: LocalDate? = null,

    /** 優先度 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var priority: Priority,

    /** ステータス */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: TodoStatus,

    /** カテゴリ */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = true)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    var category: Category? = null,

    /** オーナー */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: User,

    /** 作成日 */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    /** 更新日 */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)