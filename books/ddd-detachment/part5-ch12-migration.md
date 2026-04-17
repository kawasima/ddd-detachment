---
title: "既存コードへの導入"
---

## 全面書き換えは必要ない

3〜11章で示した設計は、新規プロジェクトで最初から採用するのが最もきれいです。しかし現実には、すでに動いているコードベースに対してこのアプローチを導入したいケースの方が多いです。

幸い、Always-Valid Layer とデコーダ合成は局所的に導入できます。全面書き換えなしに、影響を受けやすい箇所から段階的に改善できます。導入は3つのステップで段階的に進めることをお勧めします。最初に Controller から始め、テストが通ることを確認してから次に進みます。

## ステップ 1: Controller の直後にデコーダを挟む

最初に効果が出やすいのは、Controller 層です。今まで `@Valid` と `BindingResult` で処理していたバリデーションを、Raoh デコーダに置き換えます。

**Before**（Bean Validation + Form クラス）:

```java
@PostMapping("/orders")
public ResponseEntity<?> createOrder(
        @Valid @RequestBody OrderPlanForm form,
        BindingResult bindingResult) {
    if (bindingResult.hasErrors()) {
        return ResponseEntity.badRequest().body(bindingResult.getAllErrors());
    }
    // form から OrderPlan への詰め替え
    OrderPlan plan = convertToPlan(form);
    orderService.createOrder(plan);
    return ResponseEntity.status(201).build();
}
```

**After**（Raoh デコーダ）:

```java
@PostMapping("/orders")
public ResponseEntity<?> createOrder(@RequestBody JsonNode body) {
    Result<OrderPlan> result = OrderPlanDecoder.ORDER_PLAN_DECODER.decode(body);

    return switch (result) {
        case Ok<OrderPlan> ok -> {
            orderService.createOrder(ok.value());
            yield ResponseEntity.status(201).build();
        }
        case Err<OrderPlan> err ->
                ResponseEntity.badRequest().body(err.issues());
    };
}
```

`OrderPlanForm` クラス、`@Valid` アノテーション、`convertToPlan()` メソッド——これらがまとめて不要になります。

この時点では、`orderService.createOrder(OrderPlan)` の先（UseCase・Domain・Repository）は変えていません。Controller だけを変更し、テストが通ることを確認してから次に進みます。

## ステップ 2: CreateOrderCommand を消す

デコーダが直接 `OrderPlan` を返すようになると、`CreateOrderCommand` のような入力 DTO の存在が薄れます。

```java
// Before: Service が Command を受け取っていた
public void createOrder(CreateOrderCommand command) {
    OrderPlan plan = toPlan(command); // 詰め替え
    // ...
}

// After: Service が OrderPlan を直接受け取る
public void createOrder(OrderPlan plan) {
    // 詰め替え不要
    // ...
}
```

`CreateOrderCommand` は同一チームが管理しているはずです（距離が近い）。`OrderPlan` を直接受け渡せるなら、中間 DTO は不要です。

ただし、`CreateOrderCommand` が複数のエンドポイントや外部システムから参照されている場合は慎重に判断します。外部 API のスキーマとして公開されているなら、それは「契約」であり簡単には消せません。

## ステップ 3: Entity クラスを整理する

JPA の `@Entity` アノテーションを持つドメインモデルは、永続化の知識がドメインモデルに混入している状態です。これを jOOQ または Spring JdbcTemplate ベースのリポジトリに置き換えます。

**Before**（JPA Entity がドメインモデルを兼ねている）:

```java
@Entity
@Table(name = "subscriptions")
public class Subscription {
    @Id
    private String id;
    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status;
    // ...
}
```

**After**（ドメインモデルと永続化を分離）:

```java
// ドメインモデル（@Entity なし）
public sealed interface Subscription permits Subscription.Active, Subscription.Suspended {
    // ...
}

// リポジトリ実装（永続化の知識はここに集約）
public class SubscriptionRepositoryImpl implements SubscriptionRepository {
    public void save(Subscription subscription) {
        switch (subscription) {
            case Subscription.Active a -> jdbcTemplate.update(
                    "INSERT INTO subscriptions (id, status, next_delivery_date) VALUES (?, ?, ?)",
                    a.id().value(), "ACTIVE", a.nextDeliveryDate());
            case Subscription.Suspended s -> jdbcTemplate.update(
                    "INSERT INTO subscriptions (id, status) VALUES (?, ?)",
                    s.id().value(), "SUSPENDED");
        }
    }
}
```

このステップは影響範囲が大きいです。最初から全モデルに適用しようとせず、変更頻度が高いエンティティや、テストが書きにくくなっているエンティティから始めます。

### ORM 選択の注意

本書のサンプルは jOOQ を採用していますが、これは「ドメインモデルから `@Entity` を剥がす」という原理を最短で示すためです。**「jOOQ が常に正解」という主張ではありません。** 実務では次の観点で判断します。

- **既存チームの習熟度**: JPA に慣れたチームを jOOQ に移行させるコストは無視できません。学習曲線とプロジェクトの期間を比較します。
- **動的クエリの比重**: 複雑な条件分岐や動的 JOIN が多いシステムでは jOOQ の型安全 DSL が効く一方、CRUD が中心のシステムでは Spring Data JPA の自動生成の恩恵が大きいです。
- **トランザクション境界**: JPA の永続化コンテキストは `@Transactional` との親和性が高く、jOOQ はこれを自前で扱う必要があります。
- **スキーマ駆動かコード駆動か**: DB スキーマが外部から与えられるシステムは jOOQ のコードジェネレータが相性よく、アプリケーション主導でスキーマを設計する場合は JPA/Hibernate が選ばれがちです。

重要なのは「**`@Entity` がドメインモデルを兼ねる構成を避ける**」ことであり、そのために JPA を捨てる必要はありません。8章「JPA を使いながら分離する」で示したとおり、JPA 用の永続化エンティティとドメインモデルを別クラスにする構成で同じ効果が得られます。本書の思想を採用するかどうかと、どの ORM を選ぶかは、**独立した判断**です。

## どこから始めるか

導入の効果が出やすい順に並べます。

| 優先度 | 作業 | 効果 |
| --- | --- | --- |
| 高 | Controller に Raoh デコーダを挟む | Form クラスと ConstraintValidator を削除できます。テストが書きやすくなります |
| 中 | UseCase 境界の Command DTO を削除 | 中間クラスの削除。Controller → UseCase の詰め替えが不要になります |
| 低 | JPA Entity をドメインモデルから分離 | ドメインモデルの純粋さが増します。影響範囲が大きいので最後に |

どのステップも「全体を一度に変える」必要はありません。一つの Controller から始め、テストが通ることを確認してから次に進みます。Always-Valid Layer は、一つの境界から少しずつ広げていける設計です。

移行期間中の注意点として、Bean Validation による `OrderPlanForm` とデコーダによる `OrderPlan` が同一アプリケーション内に共存するケースがあります。この場合、UseCase のシグネチャは `OrderPlan` を受け取る形に統一しておくことを推奨します。`OrderPlanForm` を受け取る旧コードは、詰め替え処理（`convertToPlan()`）を Controller 内に閉じ込め、UseCase には触れさせません。これにより、UseCase 以降のコードは移行前後で変更なく保たれます。

### 移行時の落とし穴

Controller を `@Valid @RequestBody OrderPlanForm` から `@RequestBody JsonNode` に切り替えると、Spring MVC の既定の動きがいくつか変わります。移行時に踏みやすい落とし穴を整理します。

- **バリデーションエラーのハンドラが発火しなくなる**: `@Valid` が外れるため、`MethodArgumentNotValidException` を前提にした既存の `@ControllerAdvice` はこのエンドポイントでは発火しません。代わりに、Raoh の `Err` を HTTP エラーに変換する処理を各 Controller に書くか、共通化する必要があります。また「そもそも JSON として解釈できない」リクエストには `HttpMessageNotReadableException` が出ます。これは 3 章「JSON として解釈できないリクエスト」を参照してください。**3種類のエラー形式（既存の Bean Validation エラー、Raoh の Err、JSON 解析失敗）が混在するため、クライアントに返す JSON スキーマを統一する工夫が必要です。** 最もシンプルな対処は、`@ControllerAdvice` で各例外を共通のエラー DTO に変換することです。例えば `MethodArgumentNotValidException`・`Err`・`HttpMessageNotReadableException` をすべて `{ "errors": [{ "field": "...", "message": "..." }] }` の形に正規化するハンドラを1つ用意すれば、クライアントは形式を気にせず処理できます。移行完了後は Bean Validation のハンドラを削除できます。
- **OpenAPI/springdoc のスキーマ自動生成が効かなくなる**: springdoc は Bean Validation のアノテーションとフォームクラスのフィールドを走査してスキーマを生成します。`@RequestBody JsonNode` にするとスキーマが空、あるいは `object` としか出ません。`OrderPlan` の sealed 階層から OpenAPI ドキュメントを作るには、`@Schema` を手書きするか、ドキュメント専用の DTO（Form クラスを残しつつ Controller では使わない）を別途用意する方法があります。
- **フロントエンド向けコード生成への影響**: `openapi-generator` などで TypeScript 型を生成している場合、スキーマの欠落は生成コードの欠落につながります。移行を段階的に進めるときは、**API ドキュメント側が遅れないように** 生成設定を見直してください。

これらは「Raoh デコーダを導入すると自動的にきれいになる」わけではありません。Spring MVC の既定機能が Bean Validation を前提に組まれているため、代替手段を用意する手間が一時的に増えます。導入の効果（フォームクラスと `ConstraintValidator` の削除）とこれらのコストを天秤にかけて、移行範囲を決めてください。

---

次章では、設計判断をどう下すかを整理します。「Raoh と Bean Validation のどちらを選ぶか」「モデル結合と契約結合のどちらを選ぶか」の判断基準をまとめます。
