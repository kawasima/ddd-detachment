---
title: "結合強度と距離のバランス"
---

## なぜ「詰め替えをなくす」だけでは不十分か

前章で3つのマッピング戦略を整理した。「詰め替えを減らしたい」という直感は正しい。しかし「減らす＝常によい」とは言い切れない。詰め替えを無理に省いた結果、レイヤー間の依存が強くなりすぎることがある。

*Balancing Coupling in Software Design* は、この問題を「結合強度」と「距離」という2つの軸で整理している。

## 結合強度：モデル結合と契約結合

Balancing Coupling では、モジュール間の統合強度として次の2種類を区別する。

### モデル結合（Model Coupling）

モジュール間でドメインモデルを直接共有する。詰め替えが不要な反面、一方のモデル変更が他方に直接波及する。

```text
UseCase ──[Subscription]──▶ Repository
                 ↑
         モデルを共有。Subscription の変更は
         UseCase にも Repository にも影響する
```

### 契約結合（Contract Coupling）

モジュール間のやり取りに専用のモデル（DTO）を使う。変更の波及を抑えられるが、モデルの数と詰め替えの量が増える。

```text
UseCase ──[SubscriptionData]──▶ Repository
              ↑
        専用の転送モデル。Subscription が変わっても
        SubscriptionData が変わらなければ影響しない
```

Full Mapping は契約結合を各レイヤー間で適用した結果だ。モデルの数が最も多く、変更の影響が最も局所化される。

## 距離：近ければ強い結合でも問題ない

Balancing Coupling のもう一方の軸が「距離」だ。距離とは、2つのモジュールを同時にメンテナンスできるかどうかの尺度だ。

- **距離が近い**: 同じ人が同じタイミングで変更できる（同一チーム、同一リポジトリ）
- **距離が遠い**: 異なるチームが独立して変更する（別チーム、別リポジトリ、マイクロサービス間）

| 結合強度 | 距離が近い | 距離が遠い |
| --- | --- | --- |
| モデル結合（強い） | バランスが取れている | 危険 |
| 契約結合（弱い） | 過剰な可能性がある | バランスが取れている |

距離が近ければ、強い結合（モデル結合）でもバランスが取れている。UseCase と Repository が同じチームの同じリポジトリにあるなら、Subscription モデルを共有していても、変更が波及したときに両方を同時に直せる。

距離が遠い場合は、契約結合が必要になる。マイクロサービス間でドメインモデルを直接共有してしまうと、一方のサービスの変更が他方のサービスを壊す。この境界には専用の転送モデルが必要だ。

## DIPで詰め替えの責務を逆転させる

通常のレイヤードアーキテクチャでは、上位層が下位層を呼び出す。詰め替えの責務は呼び出し側（上位層）にある。

```java
// UseCase が Repository を直接呼び出す構成
public class SubscriptionUseCase {
    public void suspend(String id) {
        Subscription.Active active = findActive(id);
        Subscription.Suspended suspended = behavior.suspend(active);

        // UseCase が詰め替えの責務を持つ
        SubscriptionEntity entity = toEntity(suspended);
        entityRepository.save(entity);
    }
}
```

DIP（Dependency Inversion Principle）を使うと、依存の方向が逆転し、詰め替えの責務も逆転する。

```java
// UseCase はインターフェース（抽象）に依存する
public class SubscriptionUseCase {
    private final SubscriptionRepository repository; // interface

    public void suspend(String id) {
        Subscription.Active active = repository.findActive(id);
        Subscription.Suspended suspended = behavior.suspend(active);

        // UseCase はドメインオブジェクトをそのまま渡す
        repository.save(suspended);
    }
}

// インターフェース：ドメインモデルで定義
public interface SubscriptionRepository {
    Subscription.Active findActive(SubscriptionId id);
    void save(Subscription subscription);
}

// 実装クラス：ドメイン層の外に置かれ、詰め替えの責務を持つ
public class SubscriptionRepositoryImpl implements SubscriptionRepository {
    public void save(Subscription subscription) {
        switch (subscription) {
            case Subscription.Active a -> jooq.insertInto(SUBSCRIPTIONS)
                    .set(SUBSCRIPTIONS.STATUS, "ACTIVE")
                    .set(SUBSCRIPTIONS.NEXT_DELIVERY_DATE, a.nextDeliveryDate())
                    // ...
                    .execute();
            case Subscription.Suspended s -> jooq.insertInto(SUBSCRIPTIONS)
                    .set(SUBSCRIPTIONS.STATUS, "SUSPENDED")
                    .set(SUBSCRIPTIONS.NEXT_DELIVERY_DATE, (LocalDate) null)
                    // ...
                    .execute();
        }
    }
}
```

UseCase はドメインモデルのみを知る。詰め替えは `SubscriptionRepositoryImpl` の中に閉じ込められる。UseCase の変更と永続化の変更が互いに影響しない。

## ミールス宅配サービスへの適用

ミールス宅配サービスは単一チームが単一リポジトリで管理する Web アプリケーションだ。レイヤー間の距離は近い。

この条件では、2-way Mapping + DIP の構成がちょうどよい。

```text
[近い距離・強い結合でも許容]

HTTP Layer          Domain Layer        DB Layer
OrderPlanDecoder ── OrderPlan ────────▶ RepositoryImpl
     ↑                  ↑                    ↑
  JsonNode から      ドメインモデルを      DIPで詰め替えを
  直接変換           共有する             実装クラスに閉じ込める
```

- **HTTP Layer → Domain Layer**: Raoh の `OrderPlanDecoder` が `JsonNode` を `OrderPlan` に変換。この1回の詰め替えで済む。
- **Domain Layer → DB Layer**: `SubscriptionRepositoryImpl` が `Subscription` を jOOQ の DSL に変換。DIP により、UseCase は詰め替えを知らない。

`CreateOrderCommand` のような中間 DTO は存在しない。距離が近いので、モデル結合で十分なバランスが取れている。

---

次章では、この設計を注文フロー全体で見渡す。JSON 入力からドメイン record を経て jOOQ で永続化するまでを通しで追い、「変換が一回で済む理由」を確認する。
