# Dr.K 要件定義・実装TODO（決定版）

本書は、Android向けラン計測アプリ「Dr.K」の最小実用（MVP）を迷わず実装できる粒度で定義した要件・仕様・実装手順をまとめたものです。初期リリースはローカル完結（サーバ不要）とし、GitHub ActionsでAPKを自動生成して配布します。

## 0. 概要
- 目的: 走行の距離・時間・ペースを前景/背景で継続計測し、終了後に結果・XP・レベル・称号を演出表示する。
- プラットフォーム: Android専用、Kotlin + Jetpack Compose。
- 位置: FusedLocationProvider（Google Play Services）。
- 地図: Google Maps SDK（Maps Compose）。
- DB: Room。設定は DataStore。
- アニメ: Lottie（lottie-compose）。
- ビルド配布: GitHub Actions（assembleDebug → Artifact）。

## 1. スコープ（MVP）
必須機能のみで最短で価値を提供する。
- 計測（開始/停止、前景/背景継続、通知常駐）
- 距離・時間・平均/現在ペース算出（ジッター抑制）
- 走行中ルート描画・結果画面で再表示
- カレンダー（日別合計距離/時間・色分け・当日反映）
- XP、レベルアップ、称号獲得の演出（Lottie + 短いSE）
- ローカル保存（履歴/集計）

非スコープ（MVP外）
- クラウド同期・共有、SNS連携、Webバックエンド
- オートポーズ、高度なトレーニング機能、Wear OS 連携

## 2. 機能要件
### 2.1 計測
- 操作: ホームから計測開始/終了。初回開始時に権限フローを提示。
- サービス: Foreground Service + 通知常駐。アプリ終了や画面OFFでも継続。
- 更新頻度: 1〜3秒または3〜10mの短い方（端末状況で最適化）。
- セッション: 開始時にSessionを生成、終了時に確定。ラップは任意ON/OFF（1km毎）。

### 2.2 算出
- 距離: 連続有効サンプル間のHaversine合計。移動3m未満は0扱い。
- 時間: 計測開始からの経過秒（停車中も加算）。
- ペース: 平均ペース（距離>0の間）と現在ペース（直近ウィンドウ）を表示。
- 外れ値除去: `accuracy > 40m` または速度が不自然に跳ねる点を無効化。
- 平滑化: 必要に応じ軽いEMA（過度に曲率を殺さない）。

### 2.3 地図
- 走行中: 現在地フォロー、取得済みルートをポリライン描画。
- 結果表示: 該当セッションのルートを再描画、ズームは自動フィット。

### 2.4 カレンダー
- 日別集計: 合計距離・時間・XPを表示。量に応じ色強度を変化。
- 詳細: タップで当日のセッション一覧→選択でルート表示。

### 2.5 ゲーミフィケーション
- XP付与（初期設定）: 距離1km=10XP、時間10分=5XP。
- レベル: 必要XP = `現在レベル × 100`。到達時にレベルアップ演出。
- 称号: 条件例（初回1km、累積100km、7日連続）。到達で称号演出。

### 2.6 保存/履歴
- ローカルのみ（Room）。履歴一覧、各セッションのルート・数値・XP。
- Daily集計を持ち、開始日付のタイムゾーン境界で集約。

## 3. 非機能要件
- 精度: 屋外で1km走に対し±2〜3%以内を目標。
- 消費電力: 更新間隔の間引き、Foreground Service、電池最適化除外の案内。
- オフライン: 計測・保存は通信不要（地図タイルはオンライン前提）。
- 起動/応答: 主要画面の初期描画1秒以内を目安。
- プライバシー: 位置は端末内のみ保存・共有なし。
- アクセシビリティ: 主要ボタンに十分なタップ領域、色弱配慮の配色。

## 4. 画面/UX
- ホーム: 開始/終了、距離、時間、平均ペース、当日合計サマリ。
- 計測: 地図、現在地フォロー、ルート、任意ラップ表示。
- リザルト: 本日距離/時間/平均ペース、セッション距離、XP、レベル、称号。演出再生。
- カレンダー: 月表示、色分け、当日反映、タップで詳細→ルート。
- 称号/プロフィール: 獲得履歴と次の目標。
- 設定: 単位（km/mi）、XPレート、通知、電池最適化除外案内。

ナビゲーション: Bottom Nav（ホーム/カレンダー/称号/設定）またはDrawer。MVPはBottom Navを推奨。

## 5. データモデル（Room）
```
Session(
  id: Long PK,
  startAt: Instant,
  endAt: Instant?,
  distanceM: Double,
  durationS: Long,
  avgPaceSecPerKm: Int?,
  pointsCount: Int,
  note: String?
)

TrackPoint(
  id: Long PK,
  sessionId: Long FK,
  t: Instant,
  lat: Double,
  lon: Double,
  accM: Float?,
  speedMps: Float?,
  cumDistanceM: Double
)

DailyStat(
  date: LocalDate,
  totalDistanceM: Double,
  totalDurationS: Long,
  earnedXp: Int,
  earnedTitlesCsv: String
)

PlayerState(
  id: Int PK=0,
  totalXp: Int,
  level: Int,
  nextLevelXp: Int,
  titlesCsv: String,
  streakDays: Int,
  lastActiveDate: LocalDate?
)

TitleDef(
  key: String PK,
  name: String,
  conditionType: Enum,
  threshold: Long
)
```

注意点
- 日付集計は端末の`ZoneId.systemDefault()`を用いる。日跨ぎセッションは開始日の`DailyStat`へ加算。
- `TitleDef`は初回起動時にプリセット投入（初回1km、累積100km、7日連続 など）。

## 6. 測位・距離算出仕様
- リクエスト: `PRIORITY_HIGH_ACCURACY` 相当、`interval=1–3s`、`minDistance=3–10m` 目安。
- 無効判定: `accuracy>40m`、速度が直前の2倍超でかつ距離/時間が物理的に不自然、GPS初期化直後の数点。
- 平滑化: 位置のEMA（係数0.2前後）、速度は生値を尊重。
- 距離: 有効点間でHaversine、3m未満は0。トンネル等で欠測時は補間しない。
- 現在ペース: 直近200–400mウィンドウの移動平均で算出（距離が足りない場合は直近全量）。
- タイムスタンプ: 端末時刻（モノトニック時計で経過時間を補助）。

## 7. 権限・OS要件
- Android 10+: BACKGROUND LOCATIONは別フローで取得。
- Android 13+: POST_NOTIFICATIONS が必要。
- Android 14+: `foregroundServiceType="location"` を宣言。
- 主権限: ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION / ACCESS_BACKGROUND_LOCATION / FOREGROUND_SERVICE / FOREGROUND_SERVICE_LOCATION / POST_NOTIFICATIONS。
- 通知: Channel `tracking`。文言「Dr.Kが距離と時間を計測中」。
- 電池最適化: 除外の設定画面へ誘導（デバイス別の注意文言）。

## 8. アーキテクチャ
- UI: Jetpack Compose（Maps Compose）。
- 状態管理: MVVM（ViewModel + StateFlow）。
- 位置層: ForegroundService + FusedLocationProviderClient + Repository（単一ソース・オブ・トゥルース）。
- DB: Room（DAO）。
- DI: Hilt（任意、MVPは簡易モジュールでも可）。
- アニメ: lottie-compose、演出JSONは`res/raw`。
- 通知: NotificationCompat + Channel管理ユーティリティ。
- 設定: DataStore（単位、XPレート、表示ON/OFFなど）。

スレッド/ライフサイクル
- 位置ストリームはServiceで収集→Repository→DB/StateFlowに配信。
- UIはStateFlowをCollectし、必要に応じてDBを読み出し。

## 9. GitHub/CI
- リポジトリはAPIキーを含めない。Mapsキーは`local.properties`またはCI Secretsから注入。
- GitHub Actions: push（main）で`./gradlew assembleDebug`、Artifactに`app-debug.apk`をアップロード。

## 10. 受け入れ基準
- 画面OFFで1時間連続計測し、復帰後も欠損なし。
- 1kmコース×3回で距離誤差が±3%以内。
- リザルトでXPが正しく加算、閾値到達でレベルアップ演出再生。
- 称号（初回1km、累積100km、7日連続）が条件達成時に確実に表示。
- カレンダーに当日合計が即時反映、詳細→ルート表示が可能。

## 11. 実装TODO（優先度順・チェックリスト）
1) プロジェクト初期化（Kotlin/Compose、minSdk・targetSdk定義、Java 17）
2) Manifest: 権限宣言、Android 14の`foregroundServiceType=location`追加
3) Foreground Service雛形（通知チャンネル`tracking`作成、開始/停止API）
4) FusedLocationProvider: 前景/背景で共通の測位ストリーム確立
5) 距離/時間/ペース算出（Haversine、3m閾値、軽いEMA）
6) Room導入: Schema/DAO（Session/TrackPoint/DailyStat/PlayerState/TitleDef）
7) ホームUI（Compose）: 開始/終了、距離、時間、平均ペース、当日サマリ
8) Maps Compose: 現在地追従、ポリライン描画、結果画面で再描画
9) 終了処理: Session確定→DailyStat更新→XP加算→レベル/称号判定
10) Lottie演出: レベル/称号のJSON配置、再生制御＋短いSE
11) リザルト画面: 数値＋演出表示→OKでホームへ戻る
12) カレンダー画面: 月表示、色分け、当日反映、詳細→ルート
13) 設定画面: 単位、XPレート、通知、電池最適化除外案内
14) GitHub Actions: assembleDebug→Artifactアップロード（SecretsでMapsキー注入は任意）
15) 実機テスト: 公園/ビル街/トンネル手前で誤差と安定性確認→閾値微調整

## 12. 設定値（初期デフォルト）
- 位置更新: 1.5秒 / 5m（状況で可変）
- 最小距離閾値: 3m
- 外れ値`accuracy`: 40m
- 現在ペース窓: 300m
- XPレート: 1km=10XP、10分=5XP

## 13. リスクと対策
- 都市峡谷・樹木密集でのドリフト: `accuracy`/最小距離閾値、短区間の異常点除外。
- サービス停止（電池最適化）: 初回に除外案内、再起動時は通知で明示。
- 権限取得の難易度: 段階的取得（使用中→常時）。理由説明UIを用意。
- 地図キー漏えい: Secrets管理、リポジトリには直書きしない。

## 14. テスト計画（要点）
- 単体: 距離計算（Haversine/閾値）、XP/レベル/称号ロジック。
- 計測E2E: バックグラウンド1時間、画面復帰、通知遷移。
- 実地: 1kmコース反復、静止時の距離誤加算ゼロ確認。

## 15. オープン項目（合意待ち）
- ラップUIの既定値（ON/OFF）
- 現在ペースの更新頻度と表示フォーマット
- Lottie/SEの具体的アセット選定（無償/自作）
- 称号プリセットの最終ラインナップ

## 16. 運用手順（APK入手）
- `main`へpush→GitHub Actionsが`assembleDebug`→`app-debug.apk`をArtifactから取得→Androidへサイドロード。

以上。

