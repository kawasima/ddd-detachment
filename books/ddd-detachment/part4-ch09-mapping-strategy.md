---
title: "データ詰め替え戦略の全体像"
---

## 「詰め替え」とは何か

レイヤードアーキテクチャで実装されたSpring Bootアプリケーションを読んでいると、データを「詰め替え」するコードが頻出する。

```java
// Controller → Service への詰め替え
OrderInput input = new OrderInput();
input.setUserId(form.getUserId());
input.setPlanType(form.getPlanType());
input.setMealSetId(form.getMealSetId());
// ...
service.createOrder(input);
```

```java
// Service → Repository への詰め替え
SubscriptionEntity entity = new SubscriptionEntity();
entity.setId(subscription.getId().value());
entity.setUserId(subscription.getUserId().value());
entity.setStatus(subscription.getStatus().name());
// ...
repository.save(entity);
```

このような詰め替えが何層にも重なると、コードの大半が「あるオブジェクトから別のオブジェクトへ値をコピーする」作業になる。*Get Your Hands Dirty on Clean Architecture*（邦訳：手を動かしてわかるクリーンアーキテクチャ）ではこれを「マッピング戦略」として整理している。

## 3つのマッピング戦略

### No Mapping

レイヤー間でモデルを共有し、詰め替えをしない。

```text
Controller ──[Subscription]──▶ Service ──[Subscription]──▶ Repository
```

Ruby on Railsが代表例だ。ActiveRecordオブジェクトがControllerからViewまで素通りする。変更が速い反面、ドメインモデルがHTTPレスポンスの形やテーブル構造に引きずられやすい。

### 2-way Mapping

各レイヤーが独自のモデルを持ち、隣接するレイヤーとの境界で詰め替える。

```text
Controller ──[Form→Subscription]──▶ Service ──[Subscription→Entity]──▶ Repository
```

Terasolunaのガイドラインが採用している戦略だ。プレゼンテーション層はFormクラス、ドメイン層はドメインオブジェクト、データアクセス層はEntityクラスを持つ。詰め替えは境界で発生するが、各レイヤーの変更が他のレイヤーに波及しにくい。

### Full Mapping

各レイヤーが独自のモデルを持ち、さらにレイヤー間のやり取りに専用のモデル（DTO）を使う。

```text
Controller ──[CreateOrderCommand]──▶ UseCase ──[OrderData]──▶ Repository
              ↑Form→Command                    ↑Subscription→Data
```

クリーンアーキテクチャの実装例でよく見られる。UseCase境界に `Command` オブジェクトや `ResponseModel` を置くパターンだ。モデルの数が最も多く、詰め替えのコードも最も多い。

## 「過剰に見えるクリーンアーキテクチャ」の正体

Qiita や Zenn でよく見かける「Spring Boot + クリーンアーキテクチャ」の記事で、「詰め替えが多すぎる」という批判を目にすることがある。その正体は多くの場合 Full Mapping だ。

```text
HTTP Request
    ↓
OrderForm (プレゼンテーション層)
    ↓ 詰め替え
CreateOrderCommand (ユースケース入力)
    ↓ 詰め替え
Order (ドメインモデル)
    ↓ 詰め替え
OrderEntity (データアクセス層)
    ↓
DB
```

4種類のオブジェクトと3回の詰め替えが発生する。シンプルなCRUDアプリケーションでこれをやると、ほとんどのコードが詰め替えになる。

Full Mapping が意味を持つのは、**レイヤー間の独立性を強く保ちたい大規模システム**だ。UseCase の入出力を安定した契約として固定し、プレゼンテーション層とドメイン層を完全に独立して変更できるようにしたい場合に有効だ。しかし多くのWebアプリケーションでは過剰になる。

## どの戦略を選ぶか

戦略の選択は「レイヤー間の距離」で決まる。この考え方は次章で詳しく説明するが、ここでは判断の目安を示す。

| 状況 | 適した戦略 |
| --- | --- |
| 小〜中規模、チームが同じコードベースを触る | No Mapping または 2-way Mapping |
| プレゼンテーション層とドメイン層を別チームが担当 | 2-way Mapping |
| ドメイン層とデータアクセス層を疎結合に保ちたい | 2-way Mapping |
| マイクロサービス間の境界 | Full Mapping（契約結合） |

ミールス宅配サービスのような一般的なWebアプリケーションでは、2-way Mapping で十分なことが多い。Part 2・3 で構築したアーキテクチャはこれに相当する。

- **Controller → Domain**: `OrderPlanDecoder` が `JsonNode` → `OrderPlan` に変換（1回の詰め替え）
- **Domain → DB**: リポジトリ内で `Subscription` → jOOQのDSLに変換（1回の詰め替え）

`CreateOrderCommand` のような中間オブジェクトは存在しない。

---

次章では、詰め替えの多さを「結合強度」と「距離」という2つの軸で評価する方法を見ていく。
