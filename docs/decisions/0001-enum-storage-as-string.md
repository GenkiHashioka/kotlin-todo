# 0001 - enumはORDINALではなくSTRINGでDB保存する

**ステータス**: 採用
**日付**: 2026-08-01

## Context（背景・何を解決したいか）

`Todo.priority`（`Priority`）と`Todo.status`（`TodoStatus`）はKotlinのenumで表現する。JPAの`@Enumerated`には2つの保存方式がある。

- `EnumType.ORDINAL`: enum定義順のインデックス（0, 1, 2...）を整数として保存
- `EnumType.STRING`: enum定数名（`"LOW"`, `"HIGH"`など）を文字列として保存

## Decision（何を決めたか）

`@Enumerated(EnumType.STRING)`を使う。

## Consequences（この決定によって何が得られ、何を犠牲にしたか）

- **得られるもの**: 将来enumの並び順を変えたり、途中に新しい値を挿入したりしても、既存データの意味が変わらない。DBの値を直接見ても`"HIGH"`のように人間が読める。
- **犠牲にするもの**: `ORDINAL`よりわずかにストレージ容量を使う（文字列 vs 整数）。今回の規模では無視できるレベル。
- **もし`ORDINAL`にしていたら**: 例えば`Priority { LOW, MEDIUM, HIGH }`に後から`URGENT`を先頭や中間に追加した場合、既存レコードの整数値が指す意味がずれてしまい、データ破損と同義になる。これを避けるため、実務でも`ORDINAL`はほぼ使われない。
