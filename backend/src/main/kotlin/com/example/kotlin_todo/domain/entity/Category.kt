package com.example.kotlin_todo.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction

/**
 * TODOカテゴリのエンティティクラス
 */
@Entity
@Table(
    name = "categories",
    uniqueConstraints = [UniqueConstraint(columnNames = ["owner_id", "name"])]
)
class Category(
    /** カテゴリID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /** カテゴリ名 */
    @Column(nullable = false, length = 100)
    var name: String,

    /** オーナーID */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: User,
)