---
title: "付録: Raoh APIリファレンス（本書で使用するもの）"
---

本書のサンプルコードで使用する Raoh 0.5.0 の API を一覧します。

> **開示**: Raoh は本書著者が公開している小規模ライブラリです（執筆時点のバージョンは 0.5.0）。本付録は本書のサンプルで使用する API のみを扱います。全 API 一覧・最新情報は [公式リポジトリ](https://github.com/kawasima/raoh) を、代替となるライブラリ・パターンは「参考文献」を参照してください。

## 基本の型

### `Decoder<I, O>`

```java
// net.unit8.raoh.decode.Decoder
@FunctionalInterface
public interface Decoder<I, O> {
    Result<O> decode(I input, Path path);

    default Result<O> decode(I input) { ... }

    default <R> Decoder<I, R> map(Function<O, R> f) { ... }
    default <R> Decoder<I, R> flatMap(Function<O, Result<R>> f) { ... }
}
```

入力 `I` を受け取り、`Result<O>` を返します。`JsonNode` を受け取る場合は `Decoder<JsonNode, T>` となります。

### `Result<T>`

```java
// net.unit8.raoh.Result（sealed interface）
// 実装: net.unit8.raoh.Ok<T> と net.unit8.raoh.Err<T>

Result<OrderPlan> result = decoder.decode(body);
switch (result) {
    case Ok<OrderPlan> ok   -> ok.value();   // T を取り出す
    case Err<OrderPlan> err -> err.issues(); // Issues を取り出す
}
```

静的ファクトリメソッド:

```java
Result.ok(value)                              // Ok を作る
Result.fail(path, errorCode, message)         // Err を作る（単一エラー）
```

### `Issues`

バリデーションエラーのリストです。`Err` から取り出せます。

```java
Issues issues = err.issues();
issues.asList()  // List<Issue>
```

`Issue` は以下のメソッドを持ちます。

| メソッド | 戻り値 | 説明 |
| --- | --- | --- |
| `path()` | `Path` | エラーが発生したフィールドのパス（例: `"startDate"`） |
| `message()` | `String` | ユーザー向けのエラーメッセージ |
| `errorCode()` | `String` | エラーの種別コード（例: `"startDate.tooSoon"`）。クライアント側でのエラー種別判定や国際化に使用します |

---

## JsonDecoders（JSON用の組み込みデコーダ）

`import static net.unit8.raoh.json.JsonDecoders.*` でインポートします。

### `field(name, decoder)`

JSON オブジェクトから指定したフィールドを取り出して、子デコーダに渡します。

```java
Decoder<JsonNode, String> d = field("planType", string());
```

### `string()`

JSON の文字列値を `String` に変換します。メソッドチェーンでバリデーションを追加できます。

```java
string()                  // 文字列（空文字も OK）
string().minLength(1)     // 1文字以上
string().date()           // ISO 8601 日付文字列を LocalDate に変換
```

### `bool()`

JSON の真偽値を `boolean` に変換します。

### `int_()`

JSON の数値を `int` に変換します。（`int` は Java の予約語のため末尾に `_` が付く）

### `enumOf(EnumClass.class)`

文字列を列挙型に変換します。列挙型の `name()` と照合します。

```java
field("frequency", enumOf(DeliveryFrequency.class))
// "WEEKLY" → DeliveryFrequency.WEEKLY
```

### `list(elementDecoder)`

JSON 配列を `List` に変換します。各要素に `elementDecoder` を適用します。

```java
field("meals", list(string().minLength(1)))  // List<String>
list(string()).minSize(1)                    // 1要素以上
```

---

## 合成コンビネータ

### `combine(d1, d2, ...)`

複数のデコーダを組み合わせて、タプル（`Tuple2<A, B>` など）を作ります。`.map()` でレコードに変換します。

```java
Decoder<JsonNode, OrderPlan.StandardPlan> d = combine(
        field("mealSetId", string().minLength(1)).map(MealSetId::new),
        field("frequency", enumOf(DeliveryFrequency.class))
).map(OrderPlan.StandardPlan::new);
```

`combine` は最大8引数（`Tuple2` 〜 `Tuple8`）に対応しています。

### `discriminate(fieldName, variants)`

指定フィールドの値でデコーダを切り替えます。値ごとに異なる型を返せます。

```java
Decoder<JsonNode, OrderPlan> d = discriminate("planType", Map.of(
        "STANDARD", STANDARD_PLAN_DECODER,
        "PREMIUM",  PREMIUM_PLAN_DECODER,
        "CUSTOM",   CUSTOM_PLAN_DECODER
));
```

いずれの variant にも一致しない場合、`no variant matched` エラーになります。

---

## カスタムデコーダの書き方

Decoder は関数型インターフェースなので、ラムダで書けます。`path` を使った詳細なエラーが必要な場合は、完全な形で書きます。

```java
// シンプルなカスタムデコーダ
Decoder<JsonNode, MyType> d = field("value", string()).map(MyType::new);

// path アクセスが必要な場合（ネストしたフィールドのエラーを正確に報告する）
static final Decoder<JsonNode, LocalDate> START_DATE_DECODER = (in, path) -> {
    Result<LocalDate> result = field("startDate", string().date()).decode(in, path);
    return result.flatMap(d ->
            d.isBefore(LocalDate.now().plusDays(3))
                    ? Result.fail(path.append("startDate"),
                                  "startDate.tooSoon",
                                  "開始日は3日以上先の日付を指定してください")
                    : Result.ok(d));
};
```

`flatMap` は `Function<T, Result<U>>` を受け取ります。`path` にアクセスしたい場合は、`Decoder` のラムダ全体を書きます。

---

## 依存関係（Maven）

```xml
<dependency>
    <groupId>net.unit8.raoh</groupId>
    <artifactId>raoh-json</artifactId>
    <version>0.5.0</version>
</dependency>
```

`raoh-json` は `raoh-core`（`Decoder`、`Result`、`Issues` を含む）と `jackson-databind` への依存を含みます。`raoh-core` だけを使う場合は `raoh-core` アーティファクトを指定できます。
