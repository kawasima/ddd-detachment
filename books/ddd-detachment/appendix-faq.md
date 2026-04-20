---
title: "FAQ: 従来のDDD解説との違い"
---

本書を読み進める過程で、従来広く解説されている「DDD + クリーンアーキテクチャ」の典型——Controller / Service / Repository の3層、`@Entity` をドメインモデルとして使う、Bean Validation でドメインモデルを守る、レイヤーごとに DTO を詰め替える、Aggregate / Repository / UseCase の語彙——と、本書の主張が食い違う場面に何度も出くわすはずです。この付録はその食い違いを 16 個の問いに整理し、本書の立場を簡潔に示します。

各問いは、本書を読む順序で自然に浮かぶ並びにしました。本文の特定の章に対応する内容は、末尾の参照リンクから辿れます。

### Q1. Form / Command / Entity を分けるのが「正しい詰め替え」では?

典型的な DDD+クリーンアーキテクチャの解説では、HTTP リクエストを受ける `Form`、アプリケーション層が受け取る `Command`、ドメイン層の `Entity` をそれぞれ別クラスにして、境界ごとに詰め替える構成がよく示されます。この「レイヤー間DTOによる分離」は、関心の分離を形として示す具体例として定着しています。

本書の立場は、詰め替えの**数**を評価するのではなく、**どのレイヤーがどの型の知識を持つべきか**を評価する、というものです。`Form` と `Command` と `Entity` がほぼ同じ構造をしているなら、それは3クラスが同じ知識を重複して持っている状態です。2章で示したように、1カラムを追加するために複数クラスを修正することになり、修正漏れがコンパイルエラーにならないという実害も伴います。

本書はレイヤー間 DTO としての `Form` / `Command` を不要とします。境界のデコーダが HTTP リクエストを直接ドメイン型 (`OrderPlan` の `sealed interface` ヴァリアント) にパースし、以降は同じ型がアプリケーション層とドメイン層を流れます。

一方で、**すべての DTO がなくなるわけではありません**。外部 API のレスポンス JSON 構造など、ドメインモデルとは目的が異なる DTO は別物として残ります。9章では詰め替えを 2-way Mapping と 1-way Mapping に分け、前者は本書のアプローチで消せるが、後者は必要に応じて残すものと整理しています。

→ 詳しくは [9章 データ詰め替え戦略の全体像](part4-ch09-mapping-strategy.md) (関連: 10章 結合強度と距離のバランス、11章 デコーダがレイヤーをつなぐ)

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

### Q10. Aggregate (集約) はどこに行ったのか

DDD の解説書では、Aggregate は「不変条件を保つ整合性の境界」として語られ、Aggregate Root という可変クラスがその境界の入口となります。本書の構成では Aggregate Root のような可変クラスが前面に出てこないため、集約の概念を放棄したのか、と疑問に思うかもしれません。

本書は**集約の概念自体は保持しつつ、表現方法を変えています**。集約が保証すべき整合性——「Subscription が Active なら nextDeliveryDate は必須」「カスタムプランの食材数は最大 20 品」——は、デコーダと Behavior で担保されます。

- **形式的正当性** (必須属性が揃う、型が正しい、値域に収まる) はデコーダがパース時に保証する
- **業務的正当性** (食材数上限、契約者本人のみ操作可) は Behavior が状態遷移時に検査する
- **不変性** によって、ひとたび生成された集約が不正な状態に陥ることはない

つまり、Aggregate Root というクラスを中心にした「メソッド呼び出しの都度 不変条件を守る」設計から、**型とパース時点で不変条件を確定する**設計に移行しています。境界 (集約の外縁) はデコーダと Repository の変換処理が引き受けます。

集約内の子エンティティや Value Object は、ほとんどの場合 `record` のフィールドとして表現されます。集約間の参照は ID で行い、同じ集約を複数箇所から書き換えることは起こりません。

→ 詳しくは [7章 未検証→検証済みという状態遷移](part3-ch07-state-transition.md) (関連: 8章 ドメインモデルから関心を分離する)

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

### Q16. Controller でドメインモデルを直接扱うのは層の漏れでは?

「Controller が `TaskStatus` のようなドメイン層の `sealed interface` や `record` を直接参照してよいのか」「API 仕様がドメインモデルの変更に引きずられるのではないか」という懸念は、層の分離を重視する立場から自然に出てきます。本書のコード例では Controller がデコーダを呼んでドメイン型を生成し、そのままレスポンスとしても返すため、この懸念がさらに強まるかもしれません。

本書の立場は、**Controller がドメインモデルを直接扱ってよい**、というものです。根拠は二つあります。

**第一に、型が同じでもレイヤーの責務は分かれています。** Controller の責務は「HTTP リクエストを受けて ApplicationService を呼び、結果を HTTP レスポンスにする」ことであり、ドメインロジックを書く場所ではありません。ドメイン型を扱っても、ドメインロジックを書いているわけではない、という区別が保たれていれば層の分離は壊れません。

**第二に、型を分けないことで情報欠落リスクが減ります。** レイヤー間で DTO を詰め替えるたびに、フィールドの追加漏れや型変換ミスが混入します。2章で述べた「コンストラクタの引数順を間違えて null が流れ込み、DB に保存され、配送処理で初めて顕在化」のような問題は、詰め替え段数の多さに起因します。レイヤー間の詰め替えを減らすことは、**層の分離を守りつつ保守性を上げる**選択です。

ただし、API 仕様とドメインモデルの変更タイミングが独立すべきケース——公開 API でドメインリファクタリングの影響を外に出したくない場合など——では、別の record を 1-way Mapping として作ります (9章参照)。すべてを同じ型に揃えるのではなく、**どこで型を分けるかを意図的に設計する**というのが本書の主張です。

→ 詳しくは [9章 データ詰め替え戦略の全体像](part4-ch09-mapping-strategy.md) (関連: 11章 デコーダがレイヤーをつなぐ)

---

本書の主張は「バリデーション・型変換・永続化の知識を、あるべき場所に置く」という一点に尽きます。従来の DDD 解説が提示してきた語彙——Entity、Value Object、Aggregate、Repository、UseCase——は、本書の構成でも概念として生きています。表現方法が `record` と `sealed interface` とデコーダに移ったとき、これらの語彙がどう対応づくかを理解することが、本書を腹落ちさせる鍵です。
