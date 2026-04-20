# 巻末付録FAQ 実装計画

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 古典的 DDD+クリーンアーキテクチャの典型だけを学んだ読者が本書を読むときに抱く16個の困惑を、巻末付録 FAQ として `books/ddd-detachment/appendix-faq.md` に追加し、`config.yaml` に組み込む。

**Architecture:** 単一 Markdown ファイル (`appendix-faq.md`) + `config.yaml` の `chapters:` 末尾の修正。各 FAQ 項目は `### Q<番号>. <問い>` の見出し + 本文 15〜30 行 + 参照リンク行。全16問を1問ずつ書き、章参照は相対パス Markdown リンクで記述する。全体を段階的に commit する (リード文 → 各問 → config 更新 → 全体仕上げ)。

**Tech Stack:** Markdown (Zenn 書籍フォーマット)、YAML (`config.yaml`)、Git (feature ブランチ `feature/faq-appendix` で作業中、`develop` 向け PR)。

**設計書:** `docs/superpowers/specs/2026-04-20-faq-appendix-design.md` を参照 (16問のリスト、Issue素材、方向性メモがまとまっている)。

**章タイトル対応表 (リンク化に使用):**

| ファイル | 本文中のタイトル | 短縮呼称 |
| --- | --- | --- |
| `part1-ch01-typical-spring-ddd.md` | 古典ドメインモデルパターン | 1章 |
| `part1-ch02-problems.md` | その構成の何が問題か | 2章 |
| `part2-ch03-bean-validation.md` | アウトサイドイン開発とBean Validation | 3章 |
| `part2-ch04-parse-dont-validate.md` | パースとバリデーションを同時に行う | 4章 |
| `part2-ch05-comparison.md` | 二つのアプローチの比較 | 5章 |
| `part3-ch06-always-valid-layer.md` | Always-Valid Layer という切り口 | 6章 |
| `part3-ch07-state-transition.md` | 未検証→検証済みという状態遷移 | 7章 |
| `part3-ch08-record-domain-model.md` | ドメインモデルから関心を分離する | 8章 |
| `part4-ch09-mapping-strategy.md` | データ詰め替え戦略の全体像 | 9章 |
| `part4-ch10-coupling-balance.md` | 結合強度と距離のバランス | 10章 |
| `part4-ch11-decoder-as-glue.md` | デコーダがレイヤーをつなぐ | 11章 |
| `part5-ch12-migration.md` | 既存コードへの導入 | 12章 |
| `part5-ch13-decision-criteria.md` | 設計の重さを測る判断基準 | 13章 |

---

## File Structure

- **Create:** `books/ddd-detachment/appendix-faq.md` — 付録本体 (フロントマター + リード文 + 16問 + 末尾の締め)
- **Modify:** `books/ddd-detachment/config.yaml` — `chapters:` リストの `appendix-raoh-api` の直前に `appendix-faq` を1行追加

---

## 執筆時の共通方針 (全タスク共通)

1. **文体**: 本書既存章と統一。断定形、丁寧、感情的修辞を抑制、「〜やすいです」を使わない。従来派を貶めない中立トーン
2. **長さ**: 各問の本文は 15〜30 行 (コード・表・図があればやや長くなってよい)
3. **Issue番号は本文に出さない**: 設計書の「主なIssue素材」は執筆素材。本文では「画面フィールドを赤く表示させたいケースで〜」のように**現場の典型場面**として自然に記述する
4. **章参照**: 各問の末尾に `→ 詳しくは [<章タイトル>](<ファイル名>.md) (関連: <短縮呼称>)` を1行で書く
5. **構成パターン**: 各問は「典型解説ではどう説明されているか(1〜2文)」→「本書の立場(本文中心)」→「必要に応じてコード例・表」→「参照リンク行」の順で書く
6. **コード例**: Java 25 の `record` / `sealed interface` を使う。コード例は最小限 (5〜15行) に留める
7. **見出しレベル**: ファイル内の H1 は無し (Zenn書籍はフロントマターで管理)、各問は `###` (H3)

---

## Task 1: 付録ファイルの雛形とフロントマター・リード文を作成

**Files:**
- Create: `books/ddd-detachment/appendix-faq.md`

- [ ] **Step 1: 付録ファイルを作成する**

以下の内容で `books/ddd-detachment/appendix-faq.md` を新規作成する。`title:` は Zenn 書籍のページタイトル。リード文は対象読者と本付録の目的を明示する。

```markdown
---
title: "FAQ: 従来のDDD解説との違い"
---

本書を読み進める過程で、従来広く解説されている「DDD + クリーンアーキテクチャ」の典型——Controller / Service / Repository の3層、`@Entity` をドメインモデルとして使う、Bean Validation でドメインモデルを守る、レイヤーごとに DTO を詰め替える、Aggregate / Repository / UseCase の語彙——と、本書の主張が食い違う場面に何度も出くわすはずです。この付録はその食い違いを 16 個の問いに整理し、本書の立場を簡潔に示します。

各問いは、本書を読む順序で自然に浮かぶ並びにしました。本文の特定の章に対応する内容は、末尾の参照リンクから辿れます。

```

- [ ] **Step 2: 作成を確認する**

Run: `ls -l books/ddd-detachment/appendix-faq.md`
Expected: ファイルが存在し、サイズは約 500 バイト前後

- [ ] **Step 3: Commit**

```bash
git add books/ddd-detachment/appendix-faq.md
git commit -m "追加: 付録FAQのファイル雛形とリード文"
```

---

## Task 2: Q1 Form / Command / Entity を分けるのが正しい詰め替えでは?

**Files:**
- Modify: `books/ddd-detachment/appendix-faq.md` (末尾に追記)

**Issue素材 (参考):** #774 詰め替えが面倒 / #540 DTO温床 / #753 DTOの正しい認識 / #718 Parameter Object との違い / #86 DTOの置き場所 / #14 QueryService DTO / #8 DTO前提フレームワーク

**方向性:** 典型解説では「レイヤー間DTO分離=関心の分離の具体形」とされる。一方、1 カラム追加で複数クラスを修正する苦労 (2章の動機) や、レイヤーごとに入れ物クラスを作って詰め替える煩雑さが現場では頻出する。本書は**詰め替えの数ではなく、どのレイヤーがどの型の知識を持つべきか**で評価する。DTOが全くゼロになる主張ではない。外部 API レスポンス整形のような目的が違うDTOは9章の 1-way Mapping として残る。レイヤー間の「Form / Command」を重ねるタイプのDTO が不要になる。

- [ ] **Step 1: Q1 を末尾に追記する**

`books/ddd-detachment/appendix-faq.md` の末尾に以下を追記する。

```markdown
### Q1. Form / Command / Entity を分けるのが「正しい詰め替え」では?

典型的な DDD+クリーンアーキテクチャの解説では、HTTP リクエストを受ける `Form`、アプリケーション層が受け取る `Command`、ドメイン層の `Entity` をそれぞれ別クラスにして、境界ごとに詰め替える構成がよく示されます。この「レイヤー間DTOによる分離」は、関心の分離を形として示す具体例として定着しています。

本書の立場は、詰め替えの**数**を評価するのではなく、**どのレイヤーがどの型の知識を持つべきか**を評価する、というものです。`Form` と `Command` と `Entity` がほぼ同じ構造をしているなら、それは3クラスが同じ知識を重複して持っている状態です。2章で示したように、1カラムを追加するために複数クラスを修正することになり、修正漏れがコンパイルエラーにならないという実害も伴います。

本書はレイヤー間 DTO としての `Form` / `Command` を不要とします。境界のデコーダが HTTP リクエストを直接ドメイン型 (`OrderPlan` の `sealed interface` ヴァリアント) にパースし、以降は同じ型がアプリケーション層とドメイン層を流れます。

一方で、**すべての DTO がなくなるわけではありません**。外部 API のレスポンス JSON 構造など、ドメインモデルとは目的が異なる DTO は別物として残ります。9章では詰め替えを 2-way Mapping と 1-way Mapping に分け、前者は本書のアプローチで消せるが、後者は必要に応じて残すものと整理しています。

→ 詳しくは [9章 データ詰め替え戦略の全体像](part4-ch09-mapping-strategy.md) (関連: 10章 結合強度と距離のバランス、11章 デコーダがレイヤーをつなぐ)
```

- [ ] **Step 2: 追記を確認する**

Run: `grep -c '^### Q' books/ddd-detachment/appendix-faq.md`
Expected: `1`

- [ ] **Step 3: Commit**

```bash
git add books/ddd-detachment/appendix-faq.md
git commit -m "追加: FAQ Q1 Form/Command/Entity分離への回答"
```

---

## Task 3: Q2 デコーダとバリデーションの違い (ID存在確認・複数エラー集約を含む)

**Files:**
- Modify: `books/ddd-detachment/appendix-faq.md` (末尾に追記)

**Issue素材:** #763 ユースケース前バリデーション / #727 コンストラクタ例外でUI赤枠が困難 / #708 VO例外のプレゼン層ハンドリング / #713 例外クラスの設計 / #141 VOバリデーション重複 / #104 バリデーションの層分け / #552 コンストラクタバリデーション過剰 / #473 機能別バリデーション差 / #710 インフラエラーとDomainException

**方向性:** バリデーションは「検査して真偽を返す」、デコーダは「パースして型を確定させる」。決定的な違いは**失敗の扱い**。バリデーションは例外や boolean で伝えるが、デコーダは `ValidationFailure` を**値として集約**する。これにより (a) 画面の複数フィールドを一度に赤くする UI 要件を満たせる、(b) 例外キャッチ層設計の悩みがなくなる、(c) ドメインモデルは常に有効な値で生成される。ID 存在確認も形式的正当性の一部としてデコーダに置ける (6章の表)。

- [ ] **Step 1: Q2 を末尾に追記する**

```markdown
### Q2. デコーダは結局バリデーションと何が違うのか

「デコーダはバリデーションに型変換が付いたもの」と受け取られることがあります。しかし本書が強調したいのは、**失敗の扱い方が決定的に違う**という点です。

バリデーションは「検査して真偽を返す」仕組みです。`@NotNull` や `isValid()` のような API は、ある値が正しいかどうかだけを判定します。したがって失敗は例外や boolean として扱われ、呼び出し側は try-catch でハンドリングするか、再度入力の形を確認することになります。

デコーダは「入力をパースして型を確定させる」仕組みです。失敗は例外ではなく `ValidationFailure` という**値**で表現され、複数の失敗を集約できます。これにより、画面上の複数フィールドを一度に赤く表示する UI 要件にも、個別に例外を拾って再構成するような手間をかけずに対応できます。

```java
// デコーダの戻り値は「成功した record」か「失敗の集合」の sealed 値
sealed interface DecodeResult<T> {
    record Success<T>(T value) implements DecodeResult<T> {}
    record Failure<T>(List<ValidationFailure> errors) implements DecodeResult<T> {}
}
```

もう一つよく聞かれるのが「ID の存在確認はデータベースを叩くのでビジネスロジックでは?」という疑問です。本書はこれを**形式的正当性**の一部と位置づけ、デコーダに置いてよいとします。6章の「形式的正当性 / 業務的正当性」の表で、存在確認や値域チェックは前者、在庫や与信枠のような業務判断は後者、と整理しています。

→ 詳しくは [4章 パースとバリデーションを同時に行う](part2-ch04-parse-dont-validate.md) (関連: 3章 アウトサイドイン開発とBean Validation、5章 二つのアプローチの比較、6章 Always-Valid Layer という切り口)
```

- [ ] **Step 2: Commit**

```bash
git add books/ddd-detachment/appendix-faq.md
git commit -m "追加: FAQ Q2 デコーダとバリデーションの違い"
```

---

## Task 4: Q3 ドメインEntity と ORM Entity は分けるべきか

**Files:**
- Modify: `books/ddd-detachment/appendix-faq.md` (末尾に追記)

**Issue素材:** #728 ドメインEntityとORM Entity / #597 変換処理の置き場 / #299 混乱する根 / #55 別物か / #236 Laravel/Eloquent / #629 テーブル対応 / #628 データモデルクラス / #105 select結果の直接マップ / #470 クラス名衝突

**方向性:** 典型 Spring Boot 構成では `@Entity` がドメインを兼ねる。一方 DDD 書籍は「分けるべき」とも言い、実務で混線する。本書は**ORM Entity クラスを廃止**する。ドメインモデルは `record`、Repository 実装が `ResultSet` や jOOQ レコードを直接 record に変換する。詰め替え専用の中間クラスは不要。2章の「外側から作る」設計の逆流を防ぐ核心。

- [ ] **Step 1: Q3 を末尾に追記する**

```markdown
### Q3. ドメインEntity と ORM Entity は分けるべきか?

Spring Boot で JPA を使う典型構成では、`@Entity` アノテーションがついたクラスが**ドメインモデル**も**永続化の対応オブジェクト**も兼ねます。一方、DDD の解説書では「ドメインEntityとORM Entityは分けるべき」とも書かれ、現場でどちらに倒すべきか混乱する場面が頻繁にあります。

本書の立場は、ドメインモデルと ORM Entity を**どちらも分けた上で、ORM Entity クラス自体を廃止する**です。ドメインモデルは `record` + `sealed interface` で表現し、永続化の知識 (`@Entity`・`@Column`) はそこに混入させません。Repository の実装クラスが、jOOQ レコードや `ResultSet` を直接 record に変換します。

```java
public class SubscriptionRepositoryImpl implements SubscriptionRepository {
    private final DSLContext dsl;

    public Optional<Subscription> findById(SubscriptionId id) {
        return dsl.selectFrom(SUBSCRIPTIONS)
                .where(SUBSCRIPTIONS.ID.eq(id.value()))
                .fetchOptional()
                .map(this::toRecord);  // Jooqのレコード → ドメインの record へ直接変換
    }
}
```

中間に「ORM Entity クラス」を置かないため、詰め替えの段数は減ります。2章で示したアウトサイドイン開発——`@Entity` を起点にドメインモデルを後から作る——の弊害もここで解消されます。ドメインモデルが最初にあり、Repository 実装は「外側からドメインへの変換器」として設計されます。

→ 詳しくは [8章 ドメインモデルから関心を分離する](part3-ch08-record-domain-model.md) (関連: 9章 データ詰め替え戦略の全体像)
```

- [ ] **Step 2: Commit**

```bash
git add books/ddd-detachment/appendix-faq.md
git commit -m "追加: FAQ Q3 ドメインEntityとORM Entityの分離"
```

---

## Task 5: Q4 Optional/nullable な属性の表現

**Files:**
- Modify: `books/ddd-detachment/appendix-faq.md` (末尾に追記)

**Issue素材:** #760 改修後必須属性 / #736 必須だが未入力許容 / #531 VOのNull許可 / #359 未設定・未入力の表現 / #194 Entity内のVOをnullableに / #117 VO自体がnull / #34 AutoIncrement ID

**方向性:** 「必須だが旧データにない」「未入力を許容したい」という悩みは非常に多い。Always-Valid Layer の立場からは `null` 許容は避けたい。本書の立場: `Optional<T>` や null ではなく、**sealed interface でライフサイクル段階を型として分離**する。例: `Subscription.Draft` / `Subscription.Active` / `Subscription.Suspended`。

- [ ] **Step 1: Q4 を末尾に追記する**

```markdown
### Q4. Optional / nullable な属性や「既存データで値がない属性」をどう表現するか

「ドメイン上は必須属性だが、リリース前の既存データには値がないので NULL を許容するしかない」という場面は頻繁に起こります。`Optional<T>` で逃げたり、VO を nullable にしたりすることで解決を図るのが典型ですが、Always-Valid Layer の立場からは、同じ型が「値がある段階」と「ない段階」を両方表現してしまう問題が残ります。

本書の立場は、**段階ごとに型を分ける**——`sealed interface` を使ってライフサイクルの各段階を別の record ヴァリアントで表現する、というものです。

```java
public sealed interface Subscription {
    record Draft(SubscriptionId id, UserId userId)
            implements Subscription {}  // 本社所在地はまだ無い段階

    record Active(SubscriptionId id, UserId userId,
                  Location headquarters, OrderPlan plan,
                  LocalDate nextDeliveryDate)
            implements Subscription {}  // 必須属性がすべて揃った段階

    record Suspended(SubscriptionId id, UserId userId,
                     Location headquarters, OrderPlan plan)
            implements Subscription {}
}
```

`Active` の `headquarters` は `Optional<Location>` ではなく、直接 `Location` として持ちます。値が無い旧データは `Draft` として読み込み、必須属性が揃った時点で `Active` に遷移する振る舞いを Behavior に書く設計になります。これにより、呼び出し側が「必須と言いながら null が来るかもしれない」と身構える必要がなくなります。

7章の状態遷移モデルの延長として位置づけられ、既存データの段階的な埋め戻しも型の切り替えとして自然に表現できます。

→ 詳しくは [7章 未検証→検証済みという状態遷移](part3-ch07-state-transition.md) (関連: 8章 ドメインモデルから関心を分離する)
```

- [ ] **Step 2: Commit**

```bash
git add books/ddd-detachment/appendix-faq.md
git commit -m "追加: FAQ Q4 nullable属性をsealed interfaceで表現する"
```

---

## Task 6: Q5 record のドメインモデルは「振る舞いを持つ」原則に反するのでは?

**Files:**
- Modify: `books/ddd-detachment/appendix-faq.md` (末尾に追記)

**Issue素材:** #634 ドメインサービスのメソッドをクラスメソッドにすれば済む

**方向性:** 「ドメインモデル=データ+振る舞いを一体にしたクラス」の直感への対応。本書は振る舞いを Behavior クラスに分離することで、**データの変更理由と振る舞いの変更理由を独立**させる。関心の分離の一形態。

- [ ] **Step 1: Q5 を末尾に追記する**

```markdown
### Q5. `record` のドメインモデルは「振る舞いを持つ」原則に反するのでは?

DDD の解説では「ドメインモデルはデータと振る舞いを一体にしたクラスであるべき」「貧血ドメインモデルは避けるべき」と書かれることが多く、`record` のようなデータのみの型に振る舞いを持たせないことは、この原則に反するように見えます。

本書の立場は、**振る舞いを別クラス (Behavior) に分離することで関心を分離する**、というものです。これは「貧血モデル」とは性質が異なります。貧血モデルの問題は、振る舞いが「どこにあるのかわからない」ことにあります。Controller や Service にばらばらに散らばり、同じ業務ルールが複数箇所に重複する状態が典型的な症状です。本書の構成では、`record` に対応する Behavior クラスが振る舞いの唯一の置き場として明示されます。

```java
public record Subscription(...) {}  // 状態のみを持つ

public class SubscriptionBehavior {
    // 状態遷移・業務ルール検査はここに集約される
    public Subscription.Active activate(Subscription.Draft draft, ...) { ... }
    public Subscription.Suspended suspend(Subscription.Active active) { ... }
}
```

データと振る舞いを別クラスにする理由は、**変更理由が別だから**です。データ構造が変わる理由 (カラムを増やす・型を変える) と、業務ルールが変わる理由 (検査条件の追加・遷移の変更) は独立しており、それらを同じクラスに置くと、どちらの変更も相手に影響を及ぼします。関心の分離は「振る舞いをどこかに置くか」ではなく、「何の理由で変更されるかでクラスを分けるか」で評価すべきだ、というのが本書の主張です。

→ 詳しくは [8章 ドメインモデルから関心を分離する](part3-ch08-record-domain-model.md) (関連: 7章 未検証→検証済みという状態遷移)
```

- [ ] **Step 2: Commit**

```bash
git add books/ddd-detachment/appendix-faq.md
git commit -m "追加: FAQ Q5 recordと貧血モデルの違い"
```

---

## Task 7: Q6 sealed interface vs State パターン・enum 分岐

**Files:**
- Modify: `books/ddd-detachment/appendix-faq.md` (末尾に追記)

**Issue素材:** #693 タスクのステータス / #472 登録と検索で状態差 / #47, #48 仮登録/本登録 / #98 組み込み制御の状態パターン / #690 Kotlinイミュータブル / #455 User状態 / #329 イミュータブル属性変更 / #221 タスク作成時ステータス

**方向性:** State パターンは「現在の状態を切り替える」、sealed interface は「状態ごとに型が異なる」。enum+if 分岐は網羅性チェックが弱く、状態追加で抜けが生まれやすい。sealed interface はコンパイル時網羅性を保証する。

- [ ] **Step 1: Q6 を末尾に追記する**

```markdown
### Q6. sealed interface での状態遷移は State パターンや enum 分岐と何が違うのか

ステータスを表現する典型手段は enum + if-else、あるいは GoF の State パターンです。「仮登録 / 本登録」「未着手 / 着手中 / 完了」「停止中 / 稼働中」など、実務では多くのモデルが状態を持ちます。これらを `sealed interface` で書くのと、enum や State パターンで書くのは何が違うのか、という問いです。

違いは三つあります。

**第一に、状態ごとに持つ属性が違う**ことを型で表現できます。enum だと Status と属性は別フィールドのため、「Suspended のときは nextDeliveryDate を null にする」といった決まりがコメントと検査コードで維持されます。sealed interface では、`Suspended` record に `nextDeliveryDate` フィールドがそもそも存在しないため、この決まりが型で保証されます。

**第二に、網羅性チェックがコンパイル時に効きます。** `switch` 式で `sealed interface` を分岐すると、すべてのヴァリアントを扱わない場合にコンパイルエラーになります。enum でも JDK 21 以降のパターンマッチングで同様のチェックは可能ですが、sealed interface は「状態の追加はクラスの追加」という形を取るため、新しい状態を増やしたときに関連するすべての処理を追跡しやすくなります。

**第三に、State パターンとの違いは「状態の入れ替え」ではなく「状態ごとに型が違う」**という点です。State パターンは 1 つのコンテキストオブジェクトが内部で状態オブジェクトを差し替えますが、sealed interface では遷移のたびに新しい record (新しい型) が生まれます。不変性と型安全性の両方を同時に得られます。

```java
Subscription next = switch (current) {
    case Subscription.Draft d   -> behavior.activate(d, userId, ...);
    case Subscription.Active a  -> behavior.suspend(a);
    case Subscription.Suspended s -> behavior.resume(s);
    // 新しい状態を追加したらここが未網羅でコンパイルエラーになる
};
```

→ 詳しくは [7章 未検証→検証済みという状態遷移](part3-ch07-state-transition.md)
```

- [ ] **Step 2: Commit**

```bash
git add books/ddd-detachment/appendix-faq.md
git commit -m "追加: FAQ Q6 sealed interfaceとStateパターン・enumの差"
```

---

## Task 8: Q7 振る舞いクラス vs Domain Service / UseCase

**Files:**
- Modify: `books/ddd-detachment/appendix-faq.md` (末尾に追記)

**Issue素材:** #745 UseCaseとDomainServiceの違い / #750 DomainServiceとApplicationServiceの境 / #634 ドメインメソッドに吸収 / #594 DomainService過剰利用 / #159 誰が呼ぶのか / #12 DomainServiceがRepository扱う / #25 命名 / #44 集約内補助処理

**方向性:** Domain Service は通常「複数 Entity にまたがるロジック置き場」として定義される。Behavior は特定 record と 1:1 で紐付く。責務を表で整理する。

- [ ] **Step 1: Q7 を末尾に追記する**

```markdown
### Q7. 振る舞いクラス (SubscriptionBehavior) は Domain Service と何が違うのか

本書に出てくる `SubscriptionBehavior` のようなクラスを見たとき、これは Domain Service のことではないか、あるいは ApplicationService のことではないか、と戸惑うことがあります。ユースケースとドメインサービスの境目が曖昧だ、という質問も実務で頻出します。

本書では、三つの概念を以下のように区別します。

| 概念 | 責務 | record との関係 |
| --- | --- | --- |
| Behavior クラス | 特定 record の状態遷移と業務ルール検査 | 対応する record と 1:1 |
| Domain Service | 複数集約にまたがる特殊な業務ロジックの置き場 | 特定 record に紐付かない |
| ApplicationService | トランザクション境界、Behavior の組み合わせ、横断処理 | record を直接扱うが振る舞いは持たない |

「ドメインサービスの肥大化」「ApplicationService との境が曖昧」という問題は、Behavior という第三の置き場がないときに起こります。**record と 1:1 で紐付くクラスを明示的に用意する**ことで、ドメインサービスに入れるべきロジックは「複数集約にまたがる特殊ケース」だけに絞られ、ApplicationService は組み立てに専念できます。

Domain Service を完全に廃止するわけではありません。たとえば「ユーザーの与信枠を調べて、複数集約の注文を合算する」ような処理は、特定 record に紐付けにくいため Domain Service に置きます。しかしそうしたケースは多くなく、日常の振る舞いの大半は Behavior に収まります。

→ 詳しくは [8章 ドメインモデルから関心を分離する](part3-ch08-record-domain-model.md) (関連: 7章 未検証→検証済みという状態遷移)
```

- [ ] **Step 2: Commit**

```bash
git add books/ddd-detachment/appendix-faq.md
git commit -m "追加: FAQ Q7 BehaviorとDomain Service/ApplicationServiceの責務分離"
```

---

## Task 9: Q8 権限・認可ロジックの配置

**Files:**
- Modify: `books/ddd-detachment/appendix-faq.md` (末尾に追記)

**Issue素材:** #787 プロジェクト管理の権限 / #220 Slack的チャンネル / #183 Admin削除 / #252 #251 #250 role-based重複 / #331 ゴミ箱移動 / #121 認可層配置 / #509 権限で取得範囲変化 / #68 権限とドメインモデル / #627 他人情報チェック

**方向性:** update ユースケースに認可コードが重複する、AOP で切り出すべきか、の悩み。本書の立場: **許可/不許可を sealed 戻り値として型で表す**。

- [ ] **Step 1: Q8 を末尾に追記する**

```markdown
### Q8. 権限・認可ロジックはドメイン層・UseCase層・Controllerのどこに書くか

「Owner しか削除できない」「自分のブログしか編集できない」のようなロールベースの認可ロジックは、実務で必ず登場します。ドメイン層に書くのか、UseCase 層に書くのか、Controller や AOP で横断的に扱うのか、判断に迷います。update 系のユースケースが増えるたびに同じ認可コードが重複する、という苦しみも典型的です。

本書の立場は、**認可の許可 / 不許可を Behavior の戻り値として型で表す**、というものです。認可は業務ルールの一部であり、横断的な関心事として外に出すのではなく、Behavior が sealed 戻り値として表現します。

```java
public sealed interface SubscriptionOperationResult {
    record Success(Subscription subscription) implements SubscriptionOperationResult {}
    record PermissionDenied(String reason) implements SubscriptionOperationResult {}
    record AlreadySuspended() implements SubscriptionOperationResult {}
}

public class SubscriptionBehavior {
    public SubscriptionOperationResult suspend(Subscription.Active target, UserId operatorId) {
        if (!target.userId().equals(operatorId)) {
            return new SubscriptionOperationResult.PermissionDenied("契約者本人以外は停止できません");
        }
        return new SubscriptionOperationResult.Success(...);
    }
}
```

Controller は戻り値を `switch` で受けて HTTP レスポンスに射影します。update ごとに if 文で認可を書くのではなく、Behavior に認可ロジックが集約されるため、重複が発生しません。AOP で横断処理として書く方法とは異なり、コンパイル時に「このユースケースは認可ロジックを通ったか」が型で追跡できます。

認可がユースケース全体で定型的なケース (「管理者でないと API 自体を呼べない」など) は Controller 側の gate で弾いてかまいません。Behavior に入れるのは、**業務ドメインの知識と絡む認可** ("この Subscription の契約者本人だけが停止できる") に限ります。

→ 詳しくは [8章 ドメインモデルから関心を分離する](part3-ch08-record-domain-model.md) (関連: 11章 デコーダがレイヤーをつなぐ)
```

- [ ] **Step 2: Commit**

```bash
git add books/ddd-detachment/appendix-faq.md
git commit -m "追加: FAQ Q8 認可ロジックをsealed戻り値で型表現する"
```

---

## Task 10: Q9 部分更新 (PATCH) の設計

**Files:**
- Modify: `books/ddd-detachment/appendix-faq.md` (末尾に追記)

**Issue素材:** #315 在庫数だけ更新 / #289 loginIdとpasswordで更新画面が違う / #310 一部field更新コスト / #288 CRUD update肥大化 / #466 update引数検査 / #506 REST updateに色々混ざる / #777 ブログupdate粒度

**方向性:** 属性セットごとに小さな Behavior を切る。record の `with` 的置換で差分適用。入力は必要な部分だけのデコーダで受ける。

- [ ] **Step 1: Q9 を末尾に追記する**

```markdown
### Q9. 部分更新 (PATCH) のようにフィールド単位で更新したい場合、どう設計するか

REST の PUT / PATCH で「一部フィールドだけ更新したい」という要求はよく出ます。loginId と password で更新画面が別、EC の在庫数だけ更新、ブログのタイトル編集と本文編集が別タイミング、などが典型例です。典型解説に従って「update ユースケース」を 1 つ作ると、引数にあらゆるフィールドが混ざり、「何が指定されたか」の検査が膨らみます。

本書の立場は、**属性セットごとに小さな Behavior (ユースケース) を切る**、というものです。1 つの巨大な `updateSubscription` ではなく、`changePlan` / `changeDeliveryDate` / `changeHeadquarters` のように、更新する属性セットごとにメソッドを分けます。

```java
public class SubscriptionBehavior {
    public Subscription.Active changePlan(Subscription.Active target, OrderPlan newPlan) {
        return new Subscription.Active(
                target.id(), target.userId(), target.headquarters(),
                newPlan,                  // ここだけ差し替え
                target.nextDeliveryDate());
    }

    public Subscription.Active changeNextDeliveryDate(Subscription.Active target, LocalDate date) {
        return new Subscription.Active(
                target.id(), target.userId(), target.headquarters(),
                target.plan(),
                date);                    // ここだけ差し替え
    }
}
```

入力を受けるデコーダも、部分更新用に別定義します。「プラン変更のリクエスト」と「配送日変更のリクエスト」は違う型であり、それぞれが必要な属性だけをパースします。1 つのリクエスト型で「指定されたら変える、なければ変えない」を検査で分けるのは、型の情報量を増やすほうに進むべきです。

Repository の保存単位は集約ルート全体ですが、ユースケースの入力契約は細かいまま保ちます。保存時に record 全体を置き換えるのは、詰め替えコストではなく、不変性と楽観ロックの整合に寄与します。

→ 詳しくは [8章 ドメインモデルから関心を分離する](part3-ch08-record-domain-model.md) (関連: 9章 データ詰め替え戦略の全体像)
```

- [ ] **Step 2: Commit**

```bash
git add books/ddd-detachment/appendix-faq.md
git commit -m "追加: FAQ Q9 部分更新は属性セットごとのBehaviorで分割する"
```

---

## Task 11: Q10 Aggregate はどこに行ったのか

**Files:**
- Modify: `books/ddd-detachment/appendix-faq.md` (末尾に追記)

**Issue素材:** #717 集約配置 / #755 集約間参照

**方向性:** 集約ルートを可変クラスにする典型表現は採らない。不変 record で状態を表現し、不変条件はデコーダと Behavior で担保する。概念自体は否定しない。

- [ ] **Step 1: Q10 を末尾に追記する**

```markdown
### Q10. Aggregate (集約) はどこに行ったのか

DDD の解説書では、Aggregate は「不変条件を保つ整合性の境界」として語られ、Aggregate Root という可変クラスがその境界の入口となります。本書の構成では Aggregate Root のような可変クラスが前面に出てこないため、集約の概念を放棄したのか、と疑問に思うかもしれません。

本書は**集約の概念自体は保持しつつ、表現方法を変えています**。集約が保証すべき整合性——「Subscription が Active なら nextDeliveryDate は必須」「カスタムプランの食材数は最大 20 品」——は、デコーダと Behavior で担保されます。

- **形式的正当性** (必須属性が揃う、型が正しい、値域に収まる) はデコーダがパース時に保証する
- **業務的正当性** (食材数上限、契約者本人のみ操作可) は Behavior が状態遷移時に検査する
- **不変性** によって、ひとたび生成された集約が不正な状態に陥ることはない

つまり、Aggregate Root というクラスを中心にした「メソッド呼び出しの都度 不変条件を守る」設計から、**型とパース時点で不変条件を確定する**設計に移行しています。境界 (集約の外縁) はデコーダと Repository の変換処理が引き受けます。

集約内の子エンティティや Value Object は、ほとんどの場合 `record` のフィールドとして表現されます。集約間の参照は ID で行い、同じ集約を複数箇所から書き換えることは起こりません。

→ 詳しくは [7章 未検証→検証済みという状態遷移](part3-ch07-state-transition.md) (関連: 8章 ドメインモデルから関心を分離する)
```

- [ ] **Step 2: Commit**

```bash
git add books/ddd-detachment/appendix-faq.md
git commit -m "追加: FAQ Q10 Aggregateの表現方法を型とパース時保証に移行"
```

---

## Task 12: Q11 Aggregate Root・Repository・トランザクション境界の対応

**Files:**
- Modify: `books/ddd-detachment/appendix-faq.md` (末尾に追記)

**Issue素材:** #790 Repository内トランザクション制御 / #794 削除できるかをドメインで表現 / #315 部分更新保存単位 / #506 RESTupdate

**方向性:** Repository は引き続き存在するが、不変 record を保存/復元する形に変わる。トランザクション境界は ApplicationService に残る。

- [ ] **Step 1: Q11 を末尾に追記する**

```markdown
### Q11. Aggregate Root・Repository・トランザクション境界の対応は?

Aggregate Root を可変クラスにしないなら、Repository やトランザクション境界はどうなるのか、という疑問が続きます。典型解説では Aggregate Root に対して Repository が 1:1 で対応し、ApplicationService がトランザクションを張る構成が示されます。

本書の対応は以下のとおりです。

| 概念 | 典型解説の表現 | 本書の表現 |
| --- | --- | --- |
| Aggregate Root | 可変クラス + メソッド | 不変 `record` + `sealed interface` |
| Repository | Root を保存/復元 | `record` を保存/復元 (同じ粒度) |
| トランザクション境界 | ApplicationService | ApplicationService (変わらず) |
| 集約の不変条件 | Root のメソッドで守る | デコーダとパース時、Behavior と状態遷移時に守る |

Repository のインターフェースは `findById(id) -> Optional<Subscription>` や `save(subscription)` のように、ドメイン型を引数と戻り値に使います。この点は典型解説と同じです。異なるのは、保存対象が**不変オブジェクト**であることと、復元時に**状態ヴァリアント (Draft / Active / Suspended)** を区別して返せることです。

```java
public interface SubscriptionRepository {
    Optional<Subscription> findById(SubscriptionId id);
    Subscription save(Subscription subscription);
    // 型が sealed interface なので、呼び出し側は switch で状態を安全に扱える
}
```

トランザクション境界は ApplicationService 層に置き、Behavior を複数呼び出したり他の集約を更新したりする場合にも、一貫して ApplicationService で制御します。Repository 自体にトランザクション制御を持ち込むことは本書でも避けます (利用側の合成が効かなくなるため)。

→ 詳しくは [7章 未検証→検証済みという状態遷移](part3-ch07-state-transition.md) (関連: 8章 ドメインモデルから関心を分離する、9章 データ詰め替え戦略の全体像、11章 デコーダがレイヤーをつなぐ)
```

- [ ] **Step 2: Commit**

```bash
git add books/ddd-detachment/appendix-faq.md
git commit -m "追加: FAQ Q11 Repository/トランザクション境界の対応"
```

---

## Task 13: Q12 Value Object は record に吸収されたのか

**Files:**
- Modify: `books/ddd-detachment/appendix-faq.md` (末尾に追記)

**Issue素材:** #726 EntityかVOか / #754 フィールドはVOにすべきか / #704 EnumでVO / #784 不変と交換可能

**方向性:** `record` は VO の性質 (不変・値ベース等価性) を最初から備える。従来「VO をクラスで書いていた」苦労が消える。値が決まっているケースでは `sealed interface` が Enum より適切なこともある。

- [ ] **Step 1: Q12 を末尾に追記する**

```markdown
### Q12. Value Object は `record` に吸収されたのか

DDD の Value Object には「不変である」「等価性が値によって決まる」「識別子を持たない」という性質があります。Java で書く場合、従来は `@EqualsAndHashCode` (Lombok) を付けた通常のクラスや、コンストラクタ + getter + equals/hashCode を手書きするクラスで表現してきました。

`record` はこれらの性質を**最初から備えます**。不変であり、等価性はコンポーネントの値で決まり、識別子を持つかどうかは設計者が決めます。したがって、従来 VO としてクラスを書いていた場面のほとんどは、`record` の 1 行で済みます。

```java
public record Amount(BigDecimal value, Currency currency) {
    public Amount {
        Objects.requireNonNull(value);
        Objects.requireNonNull(currency);
        if (value.signum() < 0) throw new IllegalArgumentException("金額は非負");
    }
}
```

コードを持つ属性 (部署コード、商品コードなど) を VO にすべきか、というよく寄せられる問いには、**識別子として使われるなら Entity、属性なら VO (record)** という素直な判断で答えられます。加えて、値の種類が有限で決まっている場合は `enum` も選択肢ですが、各値ごとに違うデータを持たせたい場合は `sealed interface` のほうが構造化でき、網羅性チェックも効きます。

「不変である」と「交換可能である」の関係に迷う場面もあります。これは「金額 1000 円」を表す VO が 2 つ存在しても、どちらを使っても同じ振る舞いになる、という意味です。`record` は値が同じなら `equals` で真を返すため、この交換可能性が型レベルで担保されます。

→ 詳しくは [8章 ドメインモデルから関心を分離する](part3-ch08-record-domain-model.md)
```

- [ ] **Step 2: Commit**

```bash
git add books/ddd-detachment/appendix-faq.md
git commit -m "追加: FAQ Q12 Value Objectとrecordの関係"
```

---

## Task 14: Q13 Factory パターンとデコーダの関係

**Files:**
- Modify: `books/ddd-detachment/appendix-faq.md` (末尾に追記)

**Issue素材:** #686 Factory IF と Repository / #681 EntityはFactoryでVOはコンストラクタ / #77 再構成コンストラクタ / #383 CSV生成とDB再構成 / #569 ルール変更と過去データ / #451 許容長変更 / #473 機能別バリデーション / #275 Factoryはstaticか

**方向性:** Factory は「正しい状態を生成する責任」、デコーダは「入力をパースして型を確定する責任」。後者は前者の一形態。「新規作成のデコーダ」と「DB再構成のデコーダ」を別定義できる。

- [ ] **Step 1: Q13 を末尾に追記する**

```markdown
### Q13. Factory パターンとデコーダの関係は? (再構成と新規生成でチェックが違う件も)

DDD で語られる Factory パターンは、「正しい状態のドメインオブジェクトを生成する責任」を持ちます。Repository が DB から復元するときの「再構成」と、ユースケースで新規に作る「新規生成」でチェック内容が違う、という悩みが実務で出ます。過去に許容していた値が今は許容されない場合、Factory を呼び分けるのか、バリデーションをスキップするのか、といった判断です。

本書の立場は、**Factory 的責任をデコーダに集約する**、というものです。Factory は「正しい状態の生成」、デコーダは「入力をパースして型を確定させる」——この二つは責任として重なっており、後者は前者の一形態と見なせます。

重要なのは、**デコーダは用途ごとに別定義できる**点です。新規生成用のデコーダと、DB 再構成用のデコーダを別の関数として用意することで、チェック粒度の違いに自然に対応できます。

```java
// 新規生成: 現在のルールで厳しく検査
Decoder<OrderPlan> newOrderPlanDecoder = ...;

// DB再構成: 旧データを通すため必須チェックを緩める
Decoder<OrderPlan> persistedOrderPlanDecoder = ...;
```

「月額 100 万円以上は契約不可」というルールが途中で「50 万円以上不可」に変わったケースでも、新規生成デコーダだけ厳しくすれば、過去の 60 万円のデータは再構成デコーダを通って読み込めます。業務ルールの変遷を、バリデーションスキップではなく、**デコーダの分離**で扱います。

どちらのデコーダも戻り値は同じ `OrderPlan` (sealed interface) です。型が統一されているため、下流の Behavior や Repository は区別を意識しません。

→ 詳しくは [4章 パースとバリデーションを同時に行う](part2-ch04-parse-dont-validate.md) (関連: 11章 デコーダがレイヤーをつなぐ)
```

- [ ] **Step 2: Commit**

```bash
git add books/ddd-detachment/appendix-faq.md
git commit -m "追加: FAQ Q13 Factoryとデコーダの関係・用途別デコーダ"
```

---

## Task 15: Q14 UseCase / Interactor 層はどこに相当するのか

**Files:**
- Modify: `books/ddd-detachment/appendix-faq.md` (末尾に追記)

**Issue素材:** #745 UseCaseとDomainServiceの違い

**方向性:** 本書の ApplicationService が UseCase に相当する。UseCase を廃止しているわけではない。ドメインに振る舞いを持たせすぎないことで UseCase がシンプルになる。

- [ ] **Step 1: Q14 を末尾に追記する**

```markdown
### Q14. UseCase / Interactor 層はどこに相当するのか

クリーンアーキテクチャの文脈で UseCase (Interactor) 層は、アプリケーションの入口として「ドメインオブジェクトを組み合わせて業務処理を実行する」役割を持ちます。本書のコード例では UseCase という名前が前面に出ないため、廃止したのか、と思うかもしれません。

本書では UseCase という名前を使わず、**ApplicationService** と呼んでいますが、責務は同じです。

| 層 | 責務 | 本書での対応 |
| --- | --- | --- |
| Presentation | HTTP を受けて ApplicationService を呼ぶ | Controller |
| UseCase / Interactor | トランザクション制御、Behavior の組み合わせ、横断処理 | ApplicationService |
| Domain | 業務ルールと状態遷移 | `record` + `sealed interface` + Behavior |
| Infrastructure | 永続化、外部 API | Repository 実装、Gateway 実装 |

UseCase を廃止しているわけではなく、**UseCase 層が薄くなる**のが本書の特徴です。ドメインに振る舞いを持たせすぎないのと同様に、UseCase に業務ルールを持たせると「ドメインロジックの置き場」が分散して不明瞭になります。本書の ApplicationService は「どの Behavior をどの順番で呼ぶか」「トランザクション境界を張るか」「失敗をどう集約するか」に専念し、業務ルール検査は Behavior に任せます。

Domain Service との違いは Q7 で述べたとおりです。ApplicationService は「組み立て」、Behavior は「特定 record の振る舞い」、Domain Service は「複数集約にまたがる特殊ケース」という役割分担になります。

→ 詳しくは [9章 データ詰め替え戦略の全体像](part4-ch09-mapping-strategy.md) (関連: 10章 結合強度と距離のバランス、11章 デコーダがレイヤーをつなぐ)
```

- [ ] **Step 2: Commit**

```bash
git add books/ddd-detachment/appendix-faq.md
git commit -m "追加: FAQ Q14 UseCase層はApplicationServiceに対応"
```

---

## Task 16: Q15 Gateway・Presenter の対応 (外部API・メール送信を含む)

**Files:**
- Modify: `books/ddd-detachment/appendix-faq.md` (末尾に追記)

**Issue素材:** #779 SendGrid Adapter配置 / #203 xxxServiceImpl配置 / #45 メール送信の層 / #735 S3アップロード / #534 Repository と Client の呼び分け

**方向性:** Gateway = Repository 実装 / 外部 API Client (インフラ層)、Presenter = レスポンス整形。外部レスポンスもデコーダで record 化。Spring Security・SendGrid の運用詳細には踏み込まない。

- [ ] **Step 1: Q15 を末尾に追記する**

```markdown
### Q15. Gateway・Presenter はどこに対応するのか (外部API・メール送信の置き場も)

クリーンアーキテクチャの語彙では、Gateway は外部との通信を抽象化する層、Presenter はユースケースの出力を表示形式に変換する層として定義されます。本書はこれらの語を前面に出していませんが、責務としては対応するものがあります。

| 概念 | 本書での対応 |
| --- | --- |
| Gateway | Repository 実装 (DB アクセス) / 外部 API Client (インフラ層) |
| Presenter | Controller 内のレスポンス整形 (必要に応じて専用 Presenter クラス) |

外部サービス——メール送信 (SendGrid)、ストレージ (S3)、決済 API など——の置き場は、実務で迷いやすい論点です。本書の整理は以下のとおりです。

- 外部サービスの **インターフェース** はドメイン層に置く (例: `MailSender`)
- **実装** はインフラ層に置く (例: `SendGridMailSender`)
- 外部サービスからの **レスポンスも境界でデコーダを通して record 化する**

```java
public interface MailSender {    // ドメイン層のインターフェース
    Result<MailSent, MailDeliveryFailure> send(Mail mail);
}

public class SendGridMailSender implements MailSender {   // インフラ層の実装
    public Result<MailSent, MailDeliveryFailure> send(Mail mail) {
        SendGridResponse raw = sendGridClient.send(...);
        return responseDecoder.decode(raw);   // 外部レスポンスもデコーダで record 化
    }
}
```

Repository と Gateway は概念としては分離しますが、**どちらもドメイン層に interface、インフラ層に実装**という構造は共通です。本書は Spring Security や SendGrid 固有の運用論には踏み込みませんが、「インターフェースと実装の分離」「外部入力のデコーダ化」という原則は、これらのサービスに対しても同じ形で適用します。

→ 詳しくは [11章 デコーダがレイヤーをつなぐ](part4-ch11-decoder-as-glue.md)
```

- [ ] **Step 2: Commit**

```bash
git add books/ddd-detachment/appendix-faq.md
git commit -m "追加: FAQ Q15 Gateway/Presenterと外部サービスの置き場"
```

---

## Task 17: Q16 Controller でドメインモデルを直接扱うのは層の漏れでは?

**Files:**
- Modify: `books/ddd-detachment/appendix-faq.md` (末尾に追記)

**Issue素材:** #721 TaskStatus Enum を Controller で参照 / #259 ドメイン層オブジェクトが Web 層から見える / #326 JSON 定義の配置 / #103 API 仕様と ドメインモデル依存 / #231 表形式カラム名

**方向性:** Controller が触る `OrderPlan` はデコーダが境界で生成した型で、ドメインロジックそのものではない。型が同じでもレイヤー責務は分かれている。型を分けないことで情報欠落リスクが減る。

- [ ] **Step 1: Q16 を末尾に追記する**

```markdown
### Q16. Controller でドメインモデルを直接扱うのは層の漏れでは?

「Controller が `TaskStatus` のようなドメイン層の `sealed interface` や `record` を直接参照してよいのか」「API 仕様がドメインモデルの変更に引きずられるのではないか」という懸念は、層の分離を重視する立場から自然に出てきます。本書のコード例では Controller がデコーダを呼んでドメイン型を生成し、そのままレスポンスとしても返すため、この懸念がさらに強まるかもしれません。

本書の立場は、**Controller がドメインモデルを直接扱ってよい**、というものです。根拠は二つあります。

**第一に、型が同じでもレイヤーの責務は分かれています。** Controller の責務は「HTTP リクエストを受けて ApplicationService を呼び、結果を HTTP レスポンスにする」ことであり、ドメインロジックを書く場所ではありません。ドメイン型を扱っても、ドメインロジックを書いているわけではない、という区別が保たれていれば層の分離は壊れません。

**第二に、型を分けないことで情報欠落リスクが減ります。** レイヤー間で DTO を詰め替えるたびに、フィールドの追加漏れや型変換ミスが混入します。2章で述べた「コンストラクタの引数順を間違えて null が流れ込み、DB に保存され、配送処理で初めて顕在化」のような問題は、詰め替え段数の多さに起因します。レイヤー間の詰め替えを減らすことは、**層の分離を守りつつ保守性を上げる**選択です。

ただし、API 仕様とドメインモデルの変更タイミングが独立すべきケース——公開 API でドメインリファクタリングの影響を外に出したくない場合など——では、別の record を 1-way Mapping として作ります (9章参照)。すべてを同じ型に揃えるのではなく、**どこで型を分けるかを意図的に設計する**というのが本書の主張です。

→ 詳しくは [9章 データ詰め替え戦略の全体像](part4-ch09-mapping-strategy.md) (関連: 11章 デコーダがレイヤーをつなぐ)
```

- [ ] **Step 2: Commit**

```bash
git add books/ddd-detachment/appendix-faq.md
git commit -m "追加: FAQ Q16 Controllerでドメインモデルを直接扱うことの正当化"
```

---

## Task 18: 締めの結び文を追記

**Files:**
- Modify: `books/ddd-detachment/appendix-faq.md` (末尾に追記)

**方向性:** 16問が終わったあとに、一段落で読み手を本書の立場に戻す締め。新しい論点は加えず、本書全体の主張の短い要約で締める。

- [ ] **Step 1: 結びを末尾に追記する**

```markdown

---

本書の主張は「バリデーション・型変換・永続化の知識を、あるべき場所に置く」という一点に尽きます。従来の DDD 解説が提示してきた語彙——Entity、Value Object、Aggregate、Repository、UseCase——は、本書の構成でも概念として生きています。表現方法が `record` と `sealed interface` とデコーダに移ったとき、これらの語彙がどう対応づくかを理解することが、本書を腹落ちさせる鍵です。
```

- [ ] **Step 2: ファイル全体の問数を確認する**

Run: `grep -c '^### Q' books/ddd-detachment/appendix-faq.md`
Expected: `16`

- [ ] **Step 3: Commit**

```bash
git add books/ddd-detachment/appendix-faq.md
git commit -m "追加: FAQ 末尾の結び文"
```

---

## Task 19: config.yaml の chapters: リストを更新

**Files:**
- Modify: `books/ddd-detachment/config.yaml`

- [ ] **Step 1: chapters: に appendix-faq を追加する**

`books/ddd-detachment/config.yaml` で、`chapters:` の末尾、`appendix-raoh-api` の**直前**に `- appendix-faq` を追加する。

変更前:
```yaml
  - part5-ch13-decision-criteria
  - appendix-raoh-api
  - appendix-references
```

変更後:
```yaml
  - part5-ch13-decision-criteria
  - appendix-faq
  - appendix-raoh-api
  - appendix-references
```

Edit ツールで `old_string` / `new_string` に上記2ブロックを渡して更新する。

- [ ] **Step 2: 順序を確認する**

Run: `grep -A 3 'part5-ch13-decision-criteria' books/ddd-detachment/config.yaml`
Expected:
```
  - part5-ch13-decision-criteria
  - appendix-faq
  - appendix-raoh-api
  - appendix-references
```

- [ ] **Step 3: Commit**

```bash
git add books/ddd-detachment/config.yaml
git commit -m "改善: config.yamlにappendix-faqを追加"
```

---

## Task 20: 全体の文体チェックと仕上げ

**Files:**
- Modify: `books/ddd-detachment/appendix-faq.md` (必要に応じて)

- [ ] **Step 1: 「〜やすいです」表現がないか確認する**

Run: `grep -n 'やすいです' books/ddd-detachment/appendix-faq.md || echo OK`
Expected: `OK`

もし見つかったら、該当箇所を「〜しやすい」「〜する」など断定形に書き換える。

- [ ] **Step 2: Issue番号が本文に露出していないか確認する**

Run: `grep -nE '#[0-9]+' books/ddd-detachment/appendix-faq.md || echo OK`
Expected: `OK` または `#` を含むコード例 (Java コメントの `#` など) のみで、`#数字` で Issue 参照になっているケースがない

もし Issue 番号が露出していたら、その行を典型場面の表現に書き換える。

- [ ] **Step 3: 各問の参照リンク行が存在するか確認する**

Run: `grep -c '^→ 詳しくは' books/ddd-detachment/appendix-faq.md`
Expected: `16`

- [ ] **Step 4: 問の合計が 16 か最終確認する**

Run: `grep -c '^### Q' books/ddd-detachment/appendix-faq.md`
Expected: `16`

- [ ] **Step 5: 相対パスリンクが正しいか検証する**

Run:
```bash
for f in $(grep -oE 'part[0-9]+-ch[0-9]+-[a-z-]+\.md' books/ddd-detachment/appendix-faq.md | sort -u); do
  test -f "books/ddd-detachment/$f" && echo "OK $f" || echo "MISSING $f"
done
```
Expected: すべて `OK` で、存在しないファイル参照がない

- [ ] **Step 6: 修正が発生した場合のみ Commit**

修正があった場合のみ:
```bash
git add books/ddd-detachment/appendix-faq.md
git commit -m "改善: FAQ付録の文体・リンク検証を反映"
```

修正がなければこのステップはスキップする。

---

## Task 21: develop ブランチへの PR を作成する

**Files:** (なし、Git 操作のみ)

- [ ] **Step 1: ブランチをプッシュする**

Run: `git push -u origin feature/faq-appendix`
Expected: feature/faq-appendix ブランチがリモートに作成される

- [ ] **Step 2: PR を作成する**

Run:
```bash
gh pr create --base develop --head feature/faq-appendix --title "追加: 巻末付録FAQ 従来のDDD解説との違い" --body "$(cat <<'EOF'
## Summary
- 巻末付録 `appendix-faq.md` を新規追加 (16問)
- `config.yaml` の `chapters:` に `appendix-faq` を `appendix-raoh-api` の前に挿入
- 現実のDDDコミュニティの質問 (little-hands/ddd-q-and-a の798件) を精査し、本書の立場で明確に答えられる頻出テーマを採録
- 対象読者: 古典的なDDD+クリーンアーキテクチャの典型のみを学んだ読者 (困惑派)

## 設計書
`docs/superpowers/specs/2026-04-20-faq-appendix-design.md`

## 実装計画
`docs/superpowers/plans/2026-04-20-faq-appendix.md`

## Test plan
- [ ] Zenn CLI でプレビューし、章構成に `appendix-faq` が組み込まれていることを確認
- [ ] 各問の相対パスリンクが Zenn のレンダリング上で有効に動作することを確認 (クリックで対応章に遷移)
- [ ] 本文中に Issue 番号や「〜やすいです」の表現が含まれていないことを確認

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```
Expected: PR URL が出力される

- [ ] **Step 3: PR URL をユーザーに共有する**

Run: `gh pr view --web` または URL をメッセージに記載する

---

## Self-Review 結果

**1. Spec coverage:**
設計書 (2026-04-20-faq-appendix-design.md) の 16 問すべてが Task 2〜17 に 1 対 1 で対応している。リード文 (Task 1)、結び (Task 18)、config.yaml 更新 (Task 19)、文体チェック (Task 20)、PR作成 (Task 21) も含む。完成判定セクションの各項目が対応するタスクで確認されている。

**2. Placeholder scan:**
全タスクに実際の本文コード (Markdown) が含まれている。TBD/TODO/実装省略なし。

**3. Type consistency:**
コード例内のクラス名・メソッド名は一貫:
- `Subscription.Draft` / `Subscription.Active` / `Subscription.Suspended` (Q4, Q6, Q10)
- `SubscriptionBehavior` (Q5, Q7, Q8, Q9)
- `OrderPlan` sealed interface (Q1, Q13)
- `SubscriptionRepository` (Q3, Q11)

**4. Sources of truth:**
章参照のファイル名とタイトルは `config.yaml` と各章ファイルのフロントマターから取得したものを使用。リンク検証 Task 20 の Step 5 で全リンクの存在確認を行う。
