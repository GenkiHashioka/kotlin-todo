package com.genkihashioka.kotlintodo.ui.todo

// 改行コード（CRLF、CR、LF）
private val lineBreakRegex = Regex("\r\n|\r|\n")

/**
 * タイトルを1行入力用に整える。改行は半角空白に置換する。
 */
fun normalizeTodoTitle(title: String): String = title.replace(lineBreakRegex, " ")
