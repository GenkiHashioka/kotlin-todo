# Android 16 - READMEにAndroidのスクリーンショットを載せる

**記録日**: 2026-11-21  
**対応Issue**: [#80](https://github.com/GenkiHashioka/kotlin-todo/issues/80)  
**ブランチ**: `docs/readme-screenshots`  
**設計メモ**: なし（コードの変更を伴わない撮影作業のため）

本書では、コマンドや設定を「日本語の名前（`コード上の名前`）」の形で書く。

## 結果

README の「スクリーンショット」節のコメントアウトを外し、`docs/images/android/` に次の 8 点を置いた。アプリのコードは変えていない。

| ファイル | 内容 |
|---|---|
| `01-list.png` | Todo 一覧（サンプル 5 件） |
| `02-detail.png` | Todo 詳細（説明・期日・カテゴリまで入った 1 件） |
| `03-create.png` | Todo 登録（入力済み、キーボードは閉じた状態） |
| `04-edit.png` | Todo 編集（既存の値が入った状態） |
| `05-delete-dialog.png` | 削除の確認ダイアログ |
| `06-list-error.png` | 一覧の取得失敗と再試行ボタン |
| `07-create-error.png` | 登録失敗後も入力が残った登録画面 |
| `08-create-flow.gif` | 登録して一覧へ戻り、Snackbar が出るまで（約 8 秒、幅 360px、約 200KB） |

PNG は 1080×2400 のエミュレーターで撮った原寸のまま置き、README では幅 180px で表示する。エミュレーターはダークテーマのまま、8 点すべてを同じテーマで撮った。

## 撮影の手順

撮り直すときに同じ状態を再現できるよう、手順を残す。WSL から Windows 側の `adb.exe` を呼ぶ前提で、次の変数を使う。

```bash
ADB=/mnt/c/Users/gensh/AppData/Local/Android/Sdk/platform-tools/adb.exe
```

### 1. サンプルデータを入れる

動作確認で入れたデータを、README に載せられる内容に入れ替えた。

1. 既存の todos と categories を、データの書き出し（`pg_dump --data-only -t todos -t categories`）でリポジトリの外のファイルに退避した
2. todos → categories の順に全件削除した。Todo がカテゴリを参照しているので、参照する側から消す
3. カテゴリ「学習」を SQL で 1 件入れた。カテゴリを作る API がまだないため
4. Todo 5 件を `curl` で `POST /todos` に送って作った。SQL で直接入れず API を使ったのは、入力チェックと所有者 ID の決定を含めて、本番と同じ経路を通すため

| タイトル | 優先度 | 状態 | 期日 | カテゴリ |
|---|---|---|---|---|
| Kotlin Coroutines の公式ガイドを読む | HIGH | IN_PROGRESS | 2026-11-20 | 学習 |
| Compose の状態管理をサンプルで試す | MEDIUM | NOT_STARTED | 2026-11-30 | 学習 |
| ポートフォリオの README を仕上げる | HIGH | NOT_STARTED | 2026-11-22 | なし |
| 技術ブログの下書きを書く | LOW | NOT_STARTED | なし | なし |
| Ktor の API テストを追加する | MEDIUM | DONE | 2026-11-10 | 学習 |

1 件目と 2 件目には説明文も入れた。1 件目は詳細画面（02）と編集画面（04）に使った。

### 2. ステータスバーを固定する（デモモード）

システム UI のデモモード（ステータスバーの時刻・電池・電波を固定の表示に差し替える開発者向けの機能）を使う。撮影のたびに時刻や電池残量が変わらず、通知アイコンも写らない。

デモモードは、ブロードキャスト（Android 内で一斉に送る合図）を `adb` から送って操作する。今回はリポジトリの外の bat ファイル（`C:\Users\gensh\demo-mode-on.bat` / `demo-mode-off.bat`）にまとめた。中身は次のとおり。

```bat
"%ADB%" shell settings put global sysui_demo_allowed 1
"%ADB%" shell am broadcast -a com.android.systemui.demo -e command enter
"%ADB%" shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1000
"%ADB%" shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
"%ADB%" shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e fully true
"%ADB%" shell am broadcast -a com.android.systemui.demo -e command network -e mobile hide
"%ADB%" shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
```

| 行 | 意味 |
|---|---|
| `sysui_demo_allowed 1` | デモモードの合図を受け付けるようにする。許可しないと合図は無視される |
| `enter` | デモモードに入る |
| `clock -e hhmm 1000` | 時計を 10:00 に固定する |
| `battery -e level 100 -e plugged false` | 電池を 100%、充電中の表示なしにする |
| `network -e wifi show -e level 4 -e fully true` | Wi-Fi を電波最大で表示する。`fully true` を付けないと、インターネット接続を確認できていない印（「!」）が付く |
| `network -e mobile hide` | モバイル回線のアイコンを隠す |
| `notifications -e visible false` | 通知アイコンを隠す |

終えるときは `-e command exit` を送る。bat はコマンドプロンプトが Shift-JIS で読むため、コメントは英語で書いた。

### 3. PNG を撮る

```bash
"$ADB" exec-out screencap -p > docs/images/android/01-list.png
```

- 端末内の画面撮影コマンド（`screencap`）で、`-p` を付けて PNG 形式で出力する
- 出力をそのまま PC へ流すために、`exec-out` を使う。`shell` は改行の文字を書き換えることがあり、PNG のようなバイナリが壊れる
- WSL のリポジトリへ直接保存できるので、ファイル名を付けて撮るだけで済む

エラー状態の 06・07 は、バックエンド（`./gradlew run`）を止めて撮った。DB を止めると #29 によりリクエストが 30 秒ほど待たされるため、DB は止めない。一覧の取得は ViewModel が作られたとき（`init`）に走るので、06 はアプリを終了してから起動し直して撮った。07 の失敗の文は画面内の文字（`Text`）で、Snackbar のように消えないため、落ち着いて撮れた。

### 4. GIF を撮る

Android には GIF を直接撮る機能がないため、動画を撮って変換した。

```bash
# 押した位置を画面に表示する（終わったら 0 に戻す）
"$ADB" shell settings put system show_touches 1

# 入力済みの登録画面を開いてから、12 秒で自動停止する録画を始める
"$ADB" shell screenrecord --time-limit 12 /sdcard/08-create-flow.mp4

# 動画を WSL に取り出す
"$ADB" exec-out cat /sdcard/08-create-flow.mp4 > ~/08-create-flow.mp4

# GIF に変換する
ffmpeg -i ~/08-create-flow.mp4 -vf "fps=10,scale=360:-1:flags=lanczos,split[a][b];[a]palettegen[p];[b][p]paletteuse" -loop 0 docs/images/android/08-create-flow.gif
```

- 録画を `Ctrl + C` で止めると、WSL → `adb.exe` → 端末と停止の合図が伝わる途中で途切れ、動画が壊れることがある。そのため時間で止める（`--time-limit`）
- `adb pull` は Windows のプログラムなので、WSL のパス（`/home/...`）を保存先にできない。そのため `exec-out cat` で流して保存した
- `fps=10`：GIF は 1 コマごとに画像を丸ごと持つので、コマ数を減らしてファイルを軽くする
- `scale=360:-1`：README の表示幅 180px の 2 倍にして、高解像度の画面でもぼやけないようにする。`-1` は縦横比を保つ指定
- `palettegen` / `paletteuse`：GIF は 256 色までしか使えないため、動画に合った 256 色を選んでから描き直す。省くと淡い色がまだらになる
- `-loop 0`：無限に繰り返し再生する

撮影後は、押した位置の表示をオフに戻し、端末内と WSL の動画を消し、登録した 6 件目の Todo をアプリから削除して 5 件に戻した。

## 学び

### アプリの外側の表示も `adb` から操作できる

ステータスバーの表示や、押した位置の表示は、アプリのコードではなく端末の設定とシステム UI の機能で変えられる。`adb shell settings put` で設定を変え、`am broadcast` で合図を送る、という 2 つの使い方を覚えた。

### バイナリを取り出すときは `exec-out` を使う

`adb shell` の出力は端末側で改行が書き換わることがあり、PNG や MP4 が壊れる。手を加えずに流す `exec-out` を使うと、撮った画像や動画をそのままファイルに保存できる。

### `screenrecord` は画面が変わったときだけコマを書く

録画の上限を 12 秒にしたが、動画は約 8 秒で終わった。最後に画面が止まっていた時間は動画に入らない。GIF は Snackbar が出たままの画面で終わり、最初に戻って繰り返す。

## 検証

| 確認したこと | 確認した人 |
|---|---|
| 8 点のファイルが README のパスどおりに存在する | Claude（ファイルの存在を確認） |
| 各画像の中身（ステータスバーの固定、写っている状態、キーボードや通知がない） | Claude（画像を読んで確認） |
| GIF の大きさ・コマ数と、登録 → 送信中 → 一覧 → Snackbar の流れ | Claude（`ffprobe` とコマの書き出しで確認） |
| 撮影後に Todo が 5 件に戻り、動画ファイルと押した位置の表示が片付いている | Claude（API と `adb` で確認） |
| デモモードが想定どおりに表示される | 本人 |
| 06 の後、バックエンド起動後に「再試行」で一覧が表示される | 本人 |
| VS Code のプレビューで、README の表が崩れずに表示される | 本人 |
| GitHub 上で README の画像が表示される | 本人（ブランチ `docs/readme-screenshots` の README で確認） |
| Codex のレビュー（`/codex:review --base main`） | 指摘なし |

## 残る課題と次の作業

- 詳細画面の作成日時・更新日時が API の形式（`2026-11-16T00:24:49.721526`）のまま表示される：[#81](https://github.com/GenkiHashioka/kotlin-todo/issues/81)。直した後に `02-detail.png` を撮り直す
- 全画面のタイトルバー（`TopAppBar`）：別 Issue で扱う。導入後に、タイトルバーが写る画像をまとめて撮り直す
- 03 と 07 のタイトルは空白の有無が違い（`AndroidのUIテストを書く` / `Android の UI テストを書く`）、08 の説明欄も 03・07 と違う。README の表示幅では目立たないため、撮り直していない。次の撮り直しで揃える
