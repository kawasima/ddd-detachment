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

デコーダは「入力をパースして型を確定させる」仕組みです。失敗は例外ではなく `Issues` という**値**で表現され、複数の失敗を集約できます。これにより、画面上の複数フィールドを一度に赤く表示する UI 要件にも、個別に例外を拾って再構成するような手間をかけずに対応できます。

```java
// Raoh の Result<T> は sealed interface で、Ok<T> と Err<T> のいずれかを返す
Result<OrderPlan> result = decoder.decode(body);
switch (result) {
    case Ok<OrderPlan> ok   -> ok.value();   // 成功値 (ドメインの record) を取り出す
    case Err<OrderPlan> err -> err.issues(); // Issues (複数の Issue) を取り出す
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
                .map(this::toRecord);  // jOOQのレコード → ドメインの record へ直接変換
    }
}
```

中間に「ORM Entity クラス」を置かないため、詰め替えの段数は減ります。2章で示したアウトサイドイン開発——`@Entity` を起点にドメインモデルを後から作る——の弊害もここで解消されます。ドメインモデルが最初にあり、Repository 実装は「外側からドメインへの変換器」として設計されます。

→ 詳しくは [8章 ドメインモデルから関心を分離する](part3-ch08-record-domain-model.md) (関連: 9章 データ詰め替え戦略の全体像)

### Q4. Optional / nullable な属性や「既存データで値がない属性」をどう表現するか

「ドメイン上は必須属性だが、リリース前の既存データには値がないので NULL を許容するしかない」という場面は頻繁に起こります。`Optional<T>` で逃げたり、VO を nullable にしたりすることで解決を図るのが典型ですが、Always-Valid Layer の立場からは、同じ型が「値がある段階」と「ない段階」を両方表現してしまう問題が残ります。

本書の立場は、**段階ごとに型を分ける**——`sealed interface` を使ってライフサイクルの各段階を別の record ヴァリアントで表現する、というものです。たとえば「仮登録のユーザーはメールアドレス確認前なのでプロフィール情報がない、本登録のユーザーは必ずプロフィールを持つ」というケースなら、次のように書きます。

```java
public sealed interface User {
    record Unverified(UserId id, EmailAddress email)
            implements User {}  // プロフィールはまだ無い段階

    record Verified(UserId id, EmailAddress email, Profile profile)
            implements User {}  // 必須属性がすべて揃った段階
}
```

`Verified` の `profile` は `Optional<Profile>` ではなく、直接 `Profile` として持ちます。プロフィール未設定のデータは `Unverified` として読み込み、プロフィールが揃った時点で `Verified` に遷移する処理を振る舞いクラスに書く設計になります。これにより、呼び出し側が「必須と言いながら null が来るかもしれない」と身構える必要がなくなります。

本書の `Subscription` (7章) が `Active` と `Suspended` の 2 段階で同じパターンを採っているのと同じ考え方です。既存データの段階的な埋め戻しも、型の切り替えとして自然に表現できます。

→ 詳しくは [7章 未検証→検証済みという状態遷移](part3-ch07-state-transition.md) (関連: 8章 ドメインモデルから関心を分離する)

### Q5. `record` のドメインモデルは「振る舞いを持つ」原則に反するのでは?

DDD の解説では「ドメインモデルはデータと振る舞いを一体にしたクラスであるべき」「貧血ドメインモデルは避けるべき」と書かれることが多く、`record` のようなデータのみの型に振る舞いを持たせないことは、この原則に反するように見えます。

本書の立場は、**振る舞いを別クラス (振る舞いクラス) に分離することで関心を分離する**、というものです。これは「貧血モデル」とは性質が異なります。貧血モデルの問題は、振る舞いが「どこにあるのかわからない」ことにあります。Controller や Service にばらばらに散らばり、同じ業務ルールが複数箇所に重複する状態が典型的な症状です。本書の構成では、`record` に対応する振る舞いクラスが振る舞いの唯一の置き場として明示されます。

```java
public record Subscription(...) {}  // 状態のみを持つ

public class SubscriptionBehavior {
    // 状態遷移・業務ルール検査はここに集約される
    public Subscription.Suspended suspend(Subscription.Active active) { ... }
    public Subscription.Active    resume(Subscription.Suspended suspended, LocalDate next) { ... }
}
```

データと振る舞いを別クラスにする理由は、**変更理由が別だから**です。データ構造が変わる理由 (カラムを増やす・型を変える) と、業務ルールが変わる理由 (検査条件の追加・遷移の変更) は独立しており、それらを同じクラスに置くと、どちらの変更も相手に影響を及ぼします。関心の分離は「振る舞いをどこかに置くか」ではなく、「何の理由で変更されるかでクラスを分けるか」で評価すべきだ、というのが本書の主張です。

→ 詳しくは [8章 ドメインモデルから関心を分離する](part3-ch08-record-domain-model.md) (関連: 7章 未検証→検証済みという状態遷移)

### Q6. sealed interface での状態遷移は State パターンや enum 分岐と何が違うのか

ステータスを表現する典型手段は enum + if-else、あるいは GoF の State パターンです。「仮登録 / 本登録」「未着手 / 着手中 / 完了」「停止中 / 稼働中」など、実務では多くのモデルが状態を持ちます。これらを `sealed interface` で書くのと、enum や State パターンで書くのは何が違うのか、という問いです。

違いは三つあります。

**第一に、状態ごとに持つ属性が違う**ことを型で表現できます。enum だと Status と属性は別フィールドのため、「Suspended のときは nextDeliveryDate を null にする」といった決まりがコメントと検査コードで維持されます。sealed interface では、`Suspended` record に `nextDeliveryDate` フィールドがそもそも存在しないため、この決まりが型で保証されます。

**第二に、網羅性チェックがコンパイル時に効きます。** `switch` 式で `sealed interface` を分岐すると、すべてのヴァリアントを扱わない場合にコンパイルエラーになります。enum でも網羅性チェックは可能ですが、**すべての状態がフィールド構造を共有する**ため、第一の点(状態ごとの属性差)は型で表現できません。sealed interface はこの制約を持ちません。

**第三に、State パターンとの違いは「状態の入れ替え」ではなく「状態ごとに型が違う」**という点です。State パターンは 1 つのコンテキストオブジェクトが内部で状態オブジェクトを差し替えますが、sealed interface では遷移のたびに新しい record (新しい型) が生まれます。不変性と型安全性の両方を同時に得られます。

```java
Subscription next = switch (current) {
    case Subscription.Active a    -> behavior.suspend(a);
    case Subscription.Suspended s -> behavior.resume(s, tomorrow);
    // 新しい状態を追加したらここが未網羅でコンパイルエラーになる
};
```

→ 詳しくは [7章 未検証→検証済みという状態遷移](part3-ch07-state-transition.md)

### Q7. 振る舞いクラス (SubscriptionBehavior) はドメインサービスと何が違うのか

本書に出てくる `SubscriptionBehavior` のようなクラスを見たとき、これは DDD で言うドメインサービスのことではないか、あるいはアプリケーションサービスのことではないか、と戸惑うことがあります。

この戸惑いそのものが、本書が解きたい問題です。「ドメインサービスか、アプリケーションサービスか、エンティティのメソッドか」——**その三者択一に悩むこと自体が、戦術 DDD の呪縛です**。本書の役割は、そこから読者を解放することにあります。

関心事は 1 つだけです。「そのロジックは **Always-Valid Layer の境界 (デコーダ) で動くのか、内側 (振る舞いクラス) で動くのか**」。6章で論じたとおり、この 2 分割は「層の名前で責務を分ける」従来のやり方を置き換えます。**どのサービスに書くか** ではなく、**データがまだ形式的正当性を得ていない段階か、得たあとの段階か** で決まります。

だから本書には「振る舞いクラスとドメインサービスとアプリケーションサービスをどう使い分けるか」という表はありません。書かないのではなく、**書く意味がない**のです。戦術 DDD の語彙で言い表していた「複数集約にまたがる処理の置き場」のような需要は実在しますが、それは新しいクラスを切れば済む話であり、「ドメインサービスと名付けるべきか」という議論に発展しません。クラス名が `〜Behavior` か `〜Service` かは枝葉の命名であって、本質ではありません。

本書の読者には、層の名前で悩む時間を業務ルールそのものを整理する時間に置き換えてほしい、というのが本書の願いです。

→ 詳しくは [8章 ドメインモデルから関心を分離する](part3-ch08-record-domain-model.md) (関連: 7章 未検証→検証済みという状態遷移)

### Q8. 権限・認可ロジックはドメイン層・UseCase層・Controllerのどこに書くか

「Owner しか削除できない」「自分のブログしか編集できない」のようなロールベースの認可ロジックは、実務で必ず登場します。ドメイン層に書くのか、UseCase 層に書くのか、Controller や AOP で横断的に扱うのか、判断に迷います。update 系のユースケースが増えるたびに同じ認可コードが重複する、という苦しみも典型的です。

本書の立場は、**認可の許可 / 不許可を振る舞いクラスの戻り値として型で表す**、というものです。認可は業務ルールの一部であり、横断的な関心事として外に出すのではなく、振る舞いクラスが sealed 戻り値として表現します。

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

Controller は戻り値を `switch` で受けて HTTP レスポンスに射影します。update ごとに if 文で認可を書くのではなく、振る舞いクラスに認可ロジックが集約されるため、重複が発生しません。AOP で横断処理として書く方法とは異なり、コンパイル時に「このユースケースは認可ロジックを通ったか」が型で追跡できます。

認可がユースケース全体で定型的なケース (「管理者でないと API 自体を呼べない」など) は Controller 側の gate で弾いてかまいません。振る舞いクラスに入れるのは、**業務ドメインの知識と絡む認可** ("この Subscription の契約者本人だけが停止できる") に限ります。

→ 詳しくは [8章 ドメインモデルから関心を分離する](part3-ch08-record-domain-model.md) (関連: 11章 デコーダがレイヤーをつなぐ)

### Q9. 部分更新 (PATCH) のようにフィールド単位で更新したい場合、どう設計するか

REST の PUT / PATCH で「一部フィールドだけ更新したい」という要求はよく出ます。loginId と password で更新画面が別、EC の在庫数だけ更新、ブログのタイトル編集と本文編集が別タイミング、などが典型例です。典型解説に従って「update ユースケース」を 1 つ作ると、引数にあらゆるフィールドが混ざり、「何が指定されたか」の検査が膨らみます。

本書の立場は、**属性セットごとに小さな振る舞いクラスのメソッド (ユースケース) を切る**、というものです。1 つの巨大な `updateSubscription` ではなく、`changeFrequency` / `changeNextDeliveryDate` のように、更新する属性セットごとにメソッドを分けます。

```java
public class SubscriptionBehavior {
    public Subscription.Active changeFrequency(Subscription.Active target, DeliveryFrequency freq) {
        return new Subscription.Active(
                target.id(), target.userId(), target.plan(),
                freq,                           // ここだけ差し替え
                target.nextDeliveryDate());
    }

    public Subscription.Active changeNextDeliveryDate(Subscription.Active target, LocalDate date) {
        return new Subscription.Active(
                target.id(), target.userId(), target.plan(), target.frequency(),
                date);                          // ここだけ差し替え
    }
}
```

入力を受けるデコーダも、部分更新用に別定義します。「配送頻度変更のリクエスト」と「配送日変更のリクエスト」は違う型であり、それぞれが必要な属性だけをパースします。1 つのリクエスト型で「指定されたら変える、なければ変えない」を検査で分けるのは、型の情報量を増やすほうに進むべきです。

Repository の保存単位は集約ルート全体ですが、ユースケースの入力契約は細かいまま保ちます。保存時に record 全体を置き換えるのは、詰め替えコストではなく、不変性と楽観ロックの整合に寄与します。

→ 詳しくは [8章 ドメインモデルから関心を分離する](part3-ch08-record-domain-model.md) (関連: 9章 データ詰め替え戦略の全体像)

### Q10. Aggregate (集約) はどこに行ったのか

DDD の解説書では、Aggregate は「不変条件を保つ整合性の境界」として語られ、Aggregate Root という可変クラスがその境界の入口となります。本書の構成では Aggregate Root のような可変クラスが前面に出てこないため、集約の概念を放棄したのか、と疑問に思うかもしれません。

本書は**集約の概念自体は保持しつつ、表現方法を変えています**。集約が保証すべき整合性——「Subscription が Active なら nextDeliveryDate は必須」「カスタムプランの食材数は最大 20 品」——は、デコーダと振る舞いクラスで担保されます。

- **形式的正当性** (必須属性が揃う、型が正しい、値域に収まる) はデコーダがパース時に保証する
- **業務的正当性** (食材数上限、契約者本人のみ操作可) は振る舞いクラスが状態遷移時に検査する
- **不変性** によって、ひとたび生成された集約が不正な状態に陥ることはない

つまり、Aggregate Root というクラスを中心にした「メソッド呼び出しの都度 不変条件を守る」設計から、**型とパース時点で不変条件を確定する**設計に移行しています。境界 (集約の外縁) はデコーダと Repository の変換処理が引き受けます。

集約内の子エンティティや Value Object は、ほとんどの場合 `record` のフィールドとして表現されます。集約間の参照は ID で行い、同じ集約を複数箇所から書き換えることは起こりません。

→ 詳しくは [7章 未検証→検証済みという状態遷移](part3-ch07-state-transition.md) (関連: 8章 ドメインモデルから関心を分離する)

### Q11. Aggregate Root・Repository・トランザクション境界の対応は?

Aggregate Root を可変クラスにしないなら、Repository やトランザクション境界はどうなるのか、という疑問が続きます。典型解説では Aggregate Root に対して Repository が 1:1 で対応し、Application Service がトランザクションを張る構成が示されます。

本書の対応は以下のとおりです。

| 概念 | 典型解説の表現 | 本書の表現 |
| --- | --- | --- |
| Aggregate Root | 可変クラス + メソッド | 不変 `record` + `sealed interface` |
| Repository | Root を保存/復元 | `record` を保存/復元 (同じ粒度) |
| トランザクション境界 | Application Service | Application Service (変わらず) |
| 集約の不変条件 | Root のメソッドで守る | デコーダとパース時、振る舞いクラスと状態遷移時に守る |

Repository のインターフェースは `findById(id) -> Optional<Subscription>` や `save(subscription)` のように、ドメイン型を引数と戻り値に使います。この点は典型解説と同じです。異なるのは、保存対象が**不変オブジェクト**であることと、復元時に**状態ヴァリアント (Active / Suspended)** を区別して返せることです。

```java
public interface SubscriptionRepository {
    Optional<Subscription> findById(SubscriptionId id);
    Subscription save(Subscription subscription);
    // 型が sealed interface なので、呼び出し側は switch で状態を安全に扱える
}
```

トランザクション境界は Application Service 層に置き、振る舞いクラスを複数呼び出したり他の集約を更新したりする場合にも、一貫して Application Service で制御します。Repository 自体にトランザクション制御を持ち込むことは本書でも避けます (利用側の合成が効かなくなるため)。

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

本書では **Entity も VO も同じく `record`** で書きます。違いは型の書き方ではなく、**等価性の意味**にあります。Entity は識別子で比較する慣習があり、VO はコンポーネント値全体で比較します。`record` の `equals` はコンポーネント値に基づくため、VO の性質には自然に適合し、Entity として使う場合でも「ID フィールドを含む record 全体の比較」に違和感はありません。「コードを持つ属性を Entity にすべきか VO にすべきか」という問いは、**識別子として使うかどうか**だけで判断できます。加えて、値の種類が有限で決まっている場合は `enum` も選択肢ですが、各値ごとに違うデータを持たせたい場合は `sealed interface` のほうが構造化でき、網羅性チェックも効きます。

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

どちらのデコーダも戻り値は同じ `OrderPlan` (sealed interface) です。型が統一されているため、下流の振る舞いクラスや Repository は区別を意識しません。

→ 詳しくは [4章 パースとバリデーションを同時に行う](part2-ch04-parse-dont-validate.md) (関連: 11章 デコーダがレイヤーをつなぐ)

### Q14. UseCase / Interactor 層はどこに相当するのか

クリーンアーキテクチャの文脈で UseCase (Interactor) 層は、アプリケーションの入口として「ドメインオブジェクトを組み合わせて業務処理を実行する」役割を持ちます。本書のコード例で UseCase という名前が前面に出ないため、廃止したのか、と思うかもしれません。

**Controller が受けたリクエストを、デコーダでドメイン型に変換し、振る舞いクラスを呼んで結果を返す**——この組み立て処理を UseCase と呼ぶことはできます。本書のサンプルコードでは Controller がこの役割を兼ねている箇所もあります (11章)。トランザクション境界を張ったり複数の振る舞いクラスを合成したりする場合は独立したクラスに切り出します。名前は本書では Application Service と書いてきました。

ただし Q7 で述べたとおり、本書の関心事は「そのロジックは Always-Valid Layer の境界 (デコーダ) で動くのか、内側 (振る舞いクラス) で動くのか」の 2 択だけです。「UseCase に業務ルールを書くか、ドメインサービスに書くか、エンティティのメソッドに書くか」という典型的な悩みは、本書のアプローチでは**構造的に生じません**。データの状態で決まるからです。

層の名前で悩む議論に引きずり込まれそうになったら、一度離れて自問してください——「このロジックはパース前か、パース後か」。ほとんどの設計上の迷いはそれで解けます。

→ 詳しくは [9章 データ詰め替え戦略の全体像](part4-ch09-mapping-strategy.md) (関連: 11章 デコーダがレイヤーをつなぐ)

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
// 送信結果は sealed interface で成功と失敗を型として表す
public sealed interface MailSendResult {
    record Sent(MailId id) implements MailSendResult {}
    record Failed(String reason) implements MailSendResult {}
}

public interface MailSender {    // ドメイン層のインターフェース
    MailSendResult send(Mail mail);
}

public class SendGridMailSender implements MailSender {   // インフラ層の実装
    public MailSendResult send(Mail mail) {
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

**第一に、型が同じでもレイヤーの責務は分かれています。** Controller の責務は「HTTP リクエストを受けて Application Service を呼び、結果を HTTP レスポンスにする」ことであり、ドメインロジックを書く場所ではありません。ドメイン型を扱っても、ドメインロジックを書いているわけではない、という区別が保たれていれば層の分離は壊れません。

**第二に、型を分けないことで情報欠落リスクが減ります。** レイヤー間で DTO を詰め替えるたびに、フィールドの追加漏れや型変換ミスが混入します。2章で述べた「コンストラクタの引数順を間違えて null が流れ込み、DB に保存され、配送処理で初めて顕在化」のような問題は、詰め替え段数の多さに起因します。レイヤー間の詰め替えを減らすことは、**層の分離を守りつつ保守性を上げる**選択です。

ただし、API 仕様とドメインモデルの変更タイミングが独立すべきケース——公開 API でドメインリファクタリングの影響を外に出したくない場合など——では、別の record を 1-way Mapping として作ります (9章参照)。すべてを同じ型に揃えるのではなく、**どこで型を分けるかを意図的に設計する**というのが本書の主張です。

→ 詳しくは [9章 データ詰め替え戦略の全体像](part4-ch09-mapping-strategy.md) (関連: 11章 デコーダがレイヤーをつなぐ)

---

本書の主張は「バリデーション・型変換・永続化の知識を、あるべき場所に置く」という一点に尽きます。従来の DDD 解説が提示してきた語彙——Entity、Value Object、Aggregate、Repository、UseCase——は、本書の構成でも概念として生きています。表現方法が `record` と `sealed interface` とデコーダに移ったとき、これらの語彙がどう対応づくかを理解することが、本書を腹落ちさせる鍵です。
