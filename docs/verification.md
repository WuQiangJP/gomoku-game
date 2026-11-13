# 検証記録

> 本ファイルは中国語原版 `docs/verification.md` の和訳です。コードブロック内のメッセージや UI 文字列、**原版での実測値そのまま**を日本語に置き換えたものは「原版出力の和訳」であり、日本語版で再取得した出力ではありません。日本語版自身の再検証結果は末尾の「日本語版の再検証」に記載します。原版 `go_game/` は変更していません。

検証可能な証拠を記録し、外部監査が項目ごとに照合できるようにしたファイルです。コマンドはいずれもプロジェクト直下で実行（JDK 17 + Android SDK 34）。

## 単体テスト

```bash
./gradlew testDebugUnitTest --rerun-tasks
```

| テストクラス | ケース数 | カバレッジ |
| --- | --- | --- |
| `engine/BoardTest` | 8 | 着手、ハッシュ、五連判定、`setCells` によるハッシュ再計算 |
| `engine/EvaluatorTest` | 5 | 差分評価のドリフトなし、石の形の点数化 |
| `engine/SearcherTest` | 9 | 必勝／必ず受ける、四追いの正例、**相手に五連成立点がある場合は必勝読みを禁止**、制限時間の実効性 |
| `engine/AiEngineConcurrencyTest` | 2 | 並行呼び出し、キャンセルコールバック |
| `game/AiEngineTest` | 3 | 4 段階の難易度パラメータ |
| `game/GameStateTest` | 8 | 着手の正当性、勝敗、待った、復元 |
| `game/GameUiStateTest` | 4 | equals/hashCode 規約 |
| `game/GameViewModelTest` | 7 | 黒が先手、色を入れ替える、待ったでプレイヤーの手番に戻る、復元時に AI の一手を補う |
| `data/GameStoreTest` | 3 | 読み書きの往復、クリア、専用ディレクトリと権限ゼロ |
| `ui/BoardStatusTest` | 3 | アクセシビリティ説明文 |
| 合計 | **52** | failures=0 errors=0 |

## 逆方向の検証：新しいケースは旧不具合を検出できるか

追加したケースを未変更の旧コードのコピーに載せて実行した（コピーにはテストとテスト用の継ぎ目のみ追加し、挙動は変更していない）。結果は **9 ケースが失敗**：

```
[AiEngineConcurrencyTest] 並行探索で例外が出てはならない。実際の最初の例外: java.lang.IllegalArgumentException: 位置 113 には既に石がある
[SearcherTest] HARD は (10,8) に打って白の五連を止めるべき。実際: 93
[SearcherTest] 相手が一手で五連を作れる場合、連続した四による攻めは成立しない expected:<-1> but was:<93>
[GameUiStateTest] 内容が同じ状態は等しくハッシュも一致する -> アサーション失敗
[GameViewModelTest] 色を入れ替えても黒が先手 -> 黒が先に一手打つべき expected:<1> but was:<0>
[GameViewModelTest] 白を持つ場合の待ったはプレイヤーの手番に戻る -> expected:<1> but was:<0>
[GameViewModelTest] 新規対局後は黒が先手で盤が空 -> 黒が先に一手打つべき expected:<1> but was:<0>
[GameViewModelTest] セーブデータ復元時に AI の手番なら自動で打つ -> expected:<3> but was:<2>
[GameViewModelTest] 思考中にやり直しても着手が衝突しない -> expected:<1> but was:<0>
```

> 注：上記は**原版の中国語テスト名とアサーションメッセージの和訳**です。日本語版ではテスト名もメッセージも日本語に置き換えているため、実際の出力文字列は異なります（後述）。数値と例外型は原版の実測値です。

同じケース群が修正後のコードではすべて通過しており、これらが対応する挙動を実際に拘束している（常に真のアサーションではない）ことを示している。

## エミュレータ実測（Android 9、API 28 x86、1080×1920 @420dpi、ハードウェアアクセラレーションなし）

```
adb install -r dist/gomoku-1.0.0-debug.apk        # Success
adb shell am start -n darkdragon.AustinWu.gomoku.debug/darkdragon.AustinWu.gomoku.MainActivity
adb shell uiautomator dump /sdcard/u1.xml
```

各段階の `uiautomator dump` の主要ノード（`text` / `content-desc`）：

| 段階 | 状態行 | 盤面のセマンティクス | コントロール |
| --- | --- | --- | --- |
| コールドスタート | `難易度：中級　黒を持つ　第 0 手` | `盤面、黒石 0 枚、白石 0 枚` | `難易度：中級`、`新規対局`、`待った`、`色を入れ替える（白にする）` |
| 黒で (7,7) に打った後 | `難易度：中級　黒を持つ　第 2 手` | `盤面、黒石 1 枚、白石 1 枚` | 同上 |
| 「色を入れ替える」をタップ | `難易度：中級　白を持つ　第 1 手` | `盤面、黒石 1 枚、白石 0 枚` | `色を入れ替える（黒にする）` |
| 白が打ち AI が受ける | `難易度：中級　白を持つ　第 3 手` | `盤面、黒石 2 枚、白石 1 枚` | 同上 |
| 「待った」をタップ | `難易度：中級　白を持つ　第 1 手` | `盤面、黒石 1 枚、白石 0 枚` | 同上 |
| `force-stop` 後に再起動 | `難易度：中級　白を持つ　第 7 手` | `盤面、黒石 4 枚、白石 3 枚` | セーブデータが完全に復元 |

> 注：上表の文字列は**原版の UI 出力（中国語）の和訳**です。日本語版の UI 文字列は後述のとおり差し替えています。

「色を入れ替える」の行は、白を持つ側に替わった後に AI が黒で先に一手打ち（黒 1 白 0）、プレイヤーが自分の手番に戻ることを示している。「待った」の行は、白を持つ場合に 1 回で 2 手を取り消してプレイヤーの手番を返すことを示している。

盤面の幾何はピクセル逆算でも独立に照合し、セマンティクスノードと一致した。盤面の外接矩形は左上 `(140,490)`、一辺 `798px`、交点間隔 `49.9px` で、読み取った黒白石の位置はカウンタと一致した。

## アンインストール後の残存

```
adb uninstall darkdragon.AustinWu.gomoku.debug
adb shell pm list packages | grep -i gogame          # 空
adb shell ls /data/data | grep -i gogame             # 空
adb shell find /data /sdcard -iname '*gogame*'       # 空
adb reboot                                           # 再起動後の再確認も空
```

アンインストールの直後にシステム側が作る項目が現れる場合がある：

- `/data/system/package_cache/<n>/darkdragon.AustinWu.gomoku.debug-<hash>`：PackageManager の解析マニフェストのキャッシュ
- `/data/system/graphicsstats/<ts>/darkdragon.AustinWu.gomoku.debug`：システムのフレーム統計

いずれも所有者は `system:system`、システム専用ディレクトリにあり、アプリからは書き込めず、再起動後に消去される。アプリ自身は残存を一切生成しない。

## 画面スクリーンショット

`docs/screenshot.png`（1080×1920）はローカルのエミュレータで撮影したもので、同一フレーム内にタイトル、状態カード、盤面、4 つのコントロール（難易度 / 新規対局 / 待った / 色を入れ替える）がすべて見えており、スクロールは不要。

## 日本語版の再検証

和訳にあたり、ユーザー向け文字列とテストのアサーションメッセージを日本語に統一した。コードの挙動・数値は原版と同一である。以下は `go_game_ja/` で実際に実行した結果（原版の記録ではなく、日本語版自身の再検証）。

- 2026-11-12 22:24 `./gradlew clean assembleDebug testDebugUnitTest`：`BUILD SUCCESSFUL`、52 ケースすべて通過（`TESTS=52 FAILURES=0 ERRORS=0`）。APK 8,752,205 バイト、SHA-256 `22c9168ef3323094f49f4f59eb83041201ce49928d7754a72e7881f8311f7020`（第 3 回監査で実物との一致を確認済みの実測値）
- プロジェクト内の全 `.kt` 23 ファイルについて、コメント・文字列リテラル・バッククォート識別子を除去した「コードスケルトン」を原版と比較し、22 ファイルが完全一致。残り 1 ファイル（`ui/GameScreen.kt`）の差は UI 文字列の中国語正字「黑」→ 日本語正字「黒」への置換 2 箇所（`GameScreen.kt` 95 行目・171 行目）のみで、ロジックの差はない
- UI 文字列は `ui/BoardStatusTest.kt` の期待値と日本語で一致する。`GameViewModel` の状態文言（`盤が埋まり、引き分けです` など）は `game/GameViewModelTest.kt` では検証対象外（状態文言を検証する断言は一切なく、`message` および当該文字列への参照は 0 件）であり、文言変更がテストに影響しない。上記 52 ケースの通過はロジックに回帰がないことの裏付けとなる
- 原版 `go_game/` は翻訳前の SHA-256 スナップショットと 56 ファイルすべてがバイト単位で一致（変更ゼロ）
- 2026-11-12 22:48 日本語版をエミュレータ（API 28 x86、1080×1920）に導入し実測：`uiautomator dump` で「石を打ってください」「難易度：中級　黒を持つ　第 0 手」「盤面、黒石 0 枚、白石 0 枚」「新規対局」「待った」「色を入れ替える（白にする）」「五目並べ」を確認。交点 (7,7) に着手後は「第 2 手／盤面、黒石 1 枚、白石 1 枚」となり AI の受けとアクセシビリティ記述が一致。`docs/screenshot.png` はこの日本語版の画面です（1080×1920）。アンインストール後はパッケージ一覧と `/data/data` が空
- 2026-11-12 22:51 独立した日本語校正（用語・文法・UI/テスト整合性）の指摘 8 件を反映：中国語からの直訳表現、漢字造語、用語表と一致しない表記、助詞の重複をそれぞれ標準日本語へ統一。修正後も `./gradlew assembleDebug testDebugUnitTest` は `BUILD SUCCESSFUL`、52 ケース `FAILURES=0 ERRORS=0`。この修正は注釈のみで APK の内容は変わらず、配布 APK は 22:24 のビルドと同一（8,752,205 バイト、SHA-256 `22c9168ef3323094f49f4f59eb83041201ce49928d7754a72e7881f8311f7020`）

- 2026-11-13 01:14 第 3 回監査（条件付き承認）の指摘 C-1～C-3 を反映した後の最終検証：`./gradlew clean assembleDebug testDebugUnitTest` は `BUILD SUCCESSFUL`、52 ケース `FAILURES=0 ERRORS=0`。`GameViewModel` の UI 文字列（`盤が埋まり、引き分けです`）を変更したため APK を作り直し、`dist/gomoku-1.0.0-debug.apk` は 8,719,859 バイト、SHA-256 `2a7b1d8ba80afd1f031d0e059b4d8fbcaa477f16635718a72b846053d5863868`。日本語版の画面文字列は実機で再確認済みではあるが、本修正は文言のみであり挙動は不変
- 2026-11-14 02:06 包名変更（`com.wuqing.gogame` → `darkdragon.AustinWu.gomoku`）に伴う再検証：`./gradlew clean assembleDebug testDebugUnitTest` は `BUILD SUCCESSFUL`、52 ケース `FAILURES=0 ERRORS=0`。新パッケージ名で APK を作り直し、`dist/gomoku-1.0.0-debug.apk` は 8,721,687 バイト、SHA-256 `8772a2f9f2be1d6ec5be3483d5e3844eae8b3209a85edcb44919014d7e5431e1`。APK 内の二進数スキャン（本機パス・旧パッケージ名・メールアドレス）は 0 件。`aapt dump badging` で `package: name='darkdragon.AustinWu.gomoku.debug'` を確認
- 2026-11-13 02:37 第 4 回監査の指摘に基づく文書訂正（**再ビルド・再実測は行っていない**）：22:24 の APK 記録を第 3 回監査確認済みの実測値へ復元し、22:51 の記録に、修正が注釈のみで APK の内容が変わらない旨を明記。`GameViewModelTest` の検証内容、`GameScreen.kt` の置換箇所数（2 箇所）、中国語の口語形による表記ゆれを「中国語」へ統一。コード・APK への変更はない