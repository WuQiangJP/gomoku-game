# AGENTS.md

> 本書は中国語版 `AGENTS.md` の日本語訳です。原版（`go_game/`）は変更していません。用語の基準は [`docs/glossary-ja.md`](docs/glossary-ja.md) を参照してください。

## プロジェクトの位置づけ

Android ネイティブの五目並べアプリ。Kotlin + Jetpack Compose の単一 Activity 構成で、GitHub での公開配布を想定している。棋力は自作の α-β 探索が担い、サードパーティ製 AI ライブラリ、モデルファイル、ネットワーク API は導入しない。

## ディレクトリと役割

```
app/src/main/java/darkdragon/AustinWu/gomoku/
├── MainActivity.kt        唯一の Activity。Compose の内容を保持する
├── engine/                純 Kotlin の思考エンジン。Android 依存なしで JVM 単体テスト可能
│   ├── Board.kt           盤面の状態、Zobrist ハッシュ、五連判定
│   ├── LineTable.kt       4 方向の行・列・斜めのインデックス表
│   ├── Evaluator.kt       差分更新による局面評価
│   └── Searcher.kt        negamax + α-β + 置換表 + 反復深化 + 四追い（VCF）
├── game/                  対局層。engine と Android の基本クラスのみに依存
│   ├── GameState.kt       着手、勝敗、待った、局面復元
│   ├── AiEngine.kt        難易度パラメータのまとめ
│   └── GameViewModel.kt   UI 状態、コルーチンによる AI 制御、セーブデータ
├── data/GameStore.kt      アプリ専用 SharedPreferences によるセーブデータ
└── ui/                    Compose 画面：Theme / BoardView（Canvas）/ GameScreen
app/src/test/java/...      JVM 単体テスト（52 件、Robolectric による対局層・保存層のカバレッジを含む）
```

レイヤリング原則：`engine` に `android.*` の import を書かない。`game` に Compose の型を書かない。`ui` で棋力計算をしない。

## 作業フロー

1. 変更前に `README.md` で外部への約束を、`ROADMAP.md` で現在の進捗を確認する。
2. 棋力に関する変更には `app/src/test/.../engine/` のケースを、対局フローの変更には `game/GameViewModelTest` を、セーブデータの変更には `data/GameStoreTest` を、画面文言・セマンティクスの変更には `ui/` のケースを必ず追加する。
   棋力や対局ルールを変更したら、まず未変更の旧コードのコピーで新しいケースを実行し、そのケースが実際に不具合を検出できることを確認する（常に真になるアサーションを書かないため）。
3. 検証コマンド（いずれも必須）：
   ```bash
   ./gradlew testDebugUnitTest     # JVM 単体テスト全体
   ./gradlew assembleDebug         # コンパイルとパッケージング
   ```
4. 画面・ジェスチャ・セーブデータに関わる場合は、エミュレータでの実測も行う：インストール → 起動 → 数手打つ → プロセス終了 → 再起動して復元 → アンインストール後に残存がないことを確認。

## 実装上の取り決め

- `local.properties` はローカル SDK を指す。リポジトリには登録しない。
- デバッグ版のパッケージ名には `.debug` が付く（`applicationIdSuffix`）。実機・エミュレータでの検証はいずれも `darkdragon.AustinWu.gomoku.debug` を使う。
- 盤面 UI の描画とタップ判定は同じ幾何（`padding = width / (size + 1)`）を共有しなければならない。片方だけを変えると着手位置がずれるため、必ず両方を同期する。
- `BoardView` の `contentDescription` は `ui/BoardStatus.kt` の純関数が生成し、石の数と勝敗を公開する。自動検証が依存するため削除しない。
- **先手は常に黒**：`GameState.reset(firstPlayer)` に黒以外を渡すのはテストまたは特殊な局面のみ。持ち色（`humanColor`）は操作する側を決めるだけで、先手を変えてはならない。
- **`Searcher` は共有しない**：可変な盤面と置換表を保持する。`AiEngine.bestMove` の呼び出しごとに新しいインスタンスを生成し、読み取り専用の `LineTable` だけを共有する。共有すると並行実行やキャンセル時に状態が衝突する。
- **四追い（VCF）の前に相手の五連成立点を除外する**：連続した四で攻めるには相手が毎手受けることが前提。`vcfSearch` のノードチェックを削除すると、上級・最上級が自ら敗着を選ぶ。
- **操作ボタンを最初の画面から追い出さない**：盤面は `weight(1f)` + `BoxWithConstraints` で残り高さに追従させる。コントロールを追加する前に、1080×1920 と 720×1280 の両方でスクロール不要か確認する。
- 対局層のテスト用継ぎ目：`GameViewModel.computeAiMove` と `aiDispatcher` はテストで決定的な実装に差し替えられる。本番経路はそのまま維持し、削除しない。

## プロジェクトのレッドライン（禁止事項）

- **`<uses-permission>` を一切追加しない**：本アプリは権限不要を約束しており、権限を増やすとプライバシー上の約束と「アンインストール後に残さない」前提が崩れる。
- **外部ストレージや共有ディレクトリに書き込まない**：対局データはアプリ専用ディレクトリのみに置く。そうしないと、アンインストール後にファイルが残る。
- **ネットワーク通信、分析 SDK、広告 SDK を導入しない**。
- **ライセンス不明または GPL のコードを複製しない**：本プロジェクトは MIT で公開する。着想の参考は良いが、実装は自作でなければならない。
- **`local.properties`、keystore、APK などのビルド生成物をコミットしない**。

## よく使うコマンド

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew assembleDebug testDebugUnitTest
$ANDROID_SDK_ROOT/platform-tools/adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
$ANDROID_SDK_ROOT/platform-tools/adb -s emulator-5554 shell am start -n darkdragon.AustinWu.gomoku.debug/darkdragon.AustinWu.gomoku.MainActivity
```
