---
title: "Souther - ドメインモデルをスラスラ書けることを追い求めた最果てのJVM言語"
emoji: "🌟"
type: "tech" # tech: 技術記事 / idea: アイデア
topics: ["Souther"]
published: false
---

Javaでドメインモデルを素直に表そうとすると、型が増えます。従業員IDと文字列を分け、金額と整数を分け、申請の状態ごとに別の型を用意する。値の検証や業務上の却下も型と戻り値で表し、現在時刻やデータベースアクセスは外から注入する。業務上の違いをコードの構造へ反映するほど、コンストラクタ、アクセサ、変換処理などの実装も増えていきます。

問題は、それらが規約にとどまることです。検証を通らない構築経路を残すことも、業務上の却下を例外で返すことも、業務ロジックから直接データベースへ触ることもできます。設計を守るには、実装者が毎回同じ判断をしなければなりません。

@[card](https://github.com/kawasima/souther)

[Souther](https://github.com/kawasima/souther) は、業務上の概念を型にするコストを下げながら、こうした規約を言語の制約として扱うために作った小さなJVM言語です。業務データ、値の制約、状態遷移、外界への依存を `.sou` ファイルに記述し、Javaから利用できる型と振る舞いへコンパイルします。

型を追加するたびにJava側の定型的な実装を増やすのではなく、誰が値を構築できるか、どの結果を後段へ渡すか、どこから外界へ出られるかまでを `.sou` ファイルの中で決めます。

この記事では出張申請のモデルを作りながら、`data`、`invariant`、`behavior`、型ルーティング、Javaとの境界を順に見ていきます。

## Southerとは

説明より先に、実物を見てもらうのが早いと思います。冒頭に挙げた出張申請の困りごとを、Southerではこう書きます（リポジトリの [examples/businesstrip](https://github.com/kawasima/souther/tree/main/examples/businesstrip) を基にしています）。

```elm
module example.trip

import String ( length )

data EmployeeId = String
    invariant length(value) > 0

data Amount = Int
    invariant value >= 0

data DraftRequest =
    { applicant: EmployeeId
    , plannedCost: Amount
    }

data Submitted =
    { ...DraftRequest
    , submittedAt: String
    }

data Rejected = { reason: String }

behavior submit : (request: DraftRequest, submittedAt: String) -> Submitted | Rejected
    constructs Submitted, Rejected

let submit (request, submittedAt) = {
    require request.plannedCost.value <= 100000 else Rejected { reason = "high_cost" }
    Submitted { ...request, submittedAt = submittedAt }
}
```

金額は0以上。申請は提出されると「提出済み」か「却下」になり、却下は例外ではなくただの値。冒頭の箇条書きが、ほぼそのままの形で1つのファイルに収まっています。そしてこれは設計書の中の擬似コードではなく、コンパイルするとJavaから使える型と振る舞いになります。以降の章では、このコードを先頭から1つずつ分解していきます。

アプリケーション全体の中での位置づけは次の通りです。

```
外部入力 -> decoder -> Souther の data / behavior -> encoder -> 外部出力
                              ^
                       Java が依存を注入
```

中心にあるのは、業務モデルの内部と外界との境界を非対称にする設計です。JavaはSoutherが生成した型やbehaviorを利用できますが、Southerから任意のJava APIを直接呼ぶことはできません。この制約が、データベースアクセスや現在時刻の取得が純粋な業務計算へ無秩序に入り込むのを防ぎます。

## 必要な環境

- JDK 25
- Maven

コンパイラはJDKのClass-File APIを使っています。生成される `.class` はJava 21以降で動きます。

```bash
git clone https://github.com/kawasima/souther.git
cd souther
mvn install
```

単一の `.sou` を試すだけならCLIが手軽です。コンパイラ・ランタイム・依存を一つにまとめた自己完結の実行ファイルが `souther-cli/target/souther` にできます。

```bash
mvn -pl souther-cli -am -DskipTests install
```

## Hello, world

次の内容を `hello.sou` として保存します。単一ファイルなら `module` 宣言は省略でき、ファイル名がモジュール名になります。

```elm
behavior greet : (name: String) -> String

let greet (name) = "Hello, " ++ name
```

実行します。入力はJSONで渡し、結果もJSONで出力されます。

```bash
./souther-cli/target/souther run hello.sou --behavior greet --input '"world"'
# => "Hello, world"
```

細かい規則がいくつかあります。

- 実行可能なbehaviorがファイルに1つだけなら `--behavior` は省略できます
- 引数のないbehaviorでは `--input` も不要です
- 複数引数はJSON配列で渡します（`--input '[3, 7]'`）
- `run` で動かせるのは `let` を持つ自己完結のbehaviorだけです。外界依存を注入するbehaviorや `>->` のパイプラインは、理由付きで拒否されます

## data で業務データを定義する

業務上の値や状態は `data` で定義します。

```elm
data EmployeeId = String
data Amount = Int
```

これは型エイリアスではありません。`EmployeeId` と `String`、`Amount` と `Int` はそれぞれ別の型として扱われます（いわゆるnewtype）。金額を要求する場所へ、たまたま同じ `Int` を基底に持つ別の値を渡すことはできません。

複数のフィールドはレコードで書きます。

```elm
data DraftRequest =
    { applicant: EmployeeId
    , plannedCost: Amount
    }
```

別のdataのフィールドは `...` で取り込めます。継承ではなく、フィールド構造の合成です。

```elm
data Submitted =
    { ...DraftRequest
    , submittedAt: String
    }
```

複数の可能性は `|` で表します。`Submitted | Rejected` は、Javaでいえば複数のrecordが同じsealed interfaceを実装している構造に近いのですが、後述の通り `Result` のようなコンテナには包まれません。

ちなみに識別子には日本語が使えます。リポジトリの examples/businesstrip は `data 従業員ID = String` のように書かれていて、ユビキタス言語を翻訳せず型名にできます。仕様書とコードの対応表を頭の中に持たなくてよくなるので、業務システムではこちらが本領だと思っています。

## invariant で不正な値を作らせない

```elm
module example.trip

import String ( length )

data EmployeeId = String
    invariant length(value) > 0

data Amount = Int
    invariant value >= 0
```

`value` はnewtypeの内部値です。`invariant` はコメントでも任意実行のバリデーション関数でもなく、そのdataがdecoderまたはbehaviorによって構築されるたびに検査されます。`EmployeeId("")` や `Amount(-100)` を作る処理は、経路を問わず失敗します。

重要なのは、Southerが「不正な値を作ってから検査する」のではなく「検証済みの構築経路だけを許可する」モデルを採っている点です。生成されるJavaクラスのコンストラクタは非公開なので、Java側から `new Amount(-100)` と書くこともできません。`Amount` 型の値を手にした時点で、それが非負であることは決着しています。「この `String`、検証済みでしたっけ?」という会話がそもそも発生しません。

## behavior で業務上の遷移を定義する

冒頭の全体像の後半にあった、出張申請の提出です。予定費用が10万円以内なら `Submitted`、超えたら `Rejected` です。

```elm
data Rejected = { reason: String }

// submit: 予定費用が上限内なら提出済みへ遷移、超過なら却下
behavior submit : (request: DraftRequest, submittedAt: String) -> Submitted | Rejected
    constructs Submitted, Rejected

let submit (request, submittedAt) = {
    require request.plannedCost.value <= 100000 else Rejected { reason = "high_cost" }
    Submitted { ...request, submittedAt = submittedAt }
}
```

`behavior` は、振る舞いの名前、入出力の型、外部依存、dataを構築する権限を宣言します。`constructs` は、このbehaviorが構築してよいdataの指定です。戻り値の型に書くだけでは構築の権限は得られません。

`let` は、そのbehaviorにSoutherで書いた関数本体をバインドします。`let` のないbehaviorはSouther内に実装を持たず、Javaから実装を注入します。

`require ... else ...` は事前条件チェックというより業務上の分岐です。条件を満たさないとき、例外を投げるのではなく `Rejected` という通常の値を返します。

## 業務上の失敗を例外にしない

戻り値 `Submitted | Rejected` には `Result<Success, Failure>` や `Either<Error, Value>` のような構造はありません。`Submitted` と `Rejected` は成功側と失敗側に振り分けられたコンテナの中身ではなく、どちらも対等な名前付きデータです。

単に `Result` という名前を避けたという話ではありません。ある値が正常経路を流れるか途中で脇へ抜けるかは、値自身の属性ではなく、behavior同士をどう合成したかで決まります。次で見ます。

## >-> による型ルーティング

behavior同士は `>->` で合成できます。会員を検索し、見つかった会員だけを表示用データへ変換する例です（examplesの `member` モジュールを単純化しています）。

```elm
behavior findMember : (id: MemberId) -> Member | MemberNotFound | InvalidStoredData

behavior formatMember : (member: Member) -> MemberView

behavior findAndFormatMember = findMember >-> formatMember
// findAndFormatMember : MemberId -> MemberView | MemberNotFound | InvalidStoredData
```

`findMember` の結果のうち、後段の入力型 `Member` に一致するものだけが `formatMember` へ渡ります。`MemberNotFound` と `InvalidStoredData` は後段の入力型に一致しないため、そのまま最終出力へ流れます。「成功値だけをbindする」固定モデルではなく、どのケースを次へ渡すかは後段の入力型で決まる。成功・失敗ではなく型によるルーティングです。

この性質はトランザクション制御とも噛み合います。examplesの `ordering` では、在庫不足が例外ではなく値として返ってくるので、コントローラは出力ケースの `switch` でHTTPステータスを決めながら `setRollbackOnly()` を呼ぶだけです。DBダウンのようなプラットフォーム障害はドメインの帰結ではないためケースにはせず、Java実装が投げた例外をSoutherは素通しし、`TransactionTemplate` の自動ロールバックに乗ります。業務の失敗とインフラの障害が構造として分離されているわけです。

## 外部依存は Java から注入する

データベース、HTTP、ファイル、時計、ID生成器は、Southerから直接呼び出しません。実装を持たないbehaviorとして宣言し、Javaから注入します。

```elm
data DateTime = String

// 実装を書かない behavior は Java から注入される
behavior currentTime : () -> DateTime

// requires で外界依存を明示する
behavior approve : (request: AwaitingApproval) -> Approved
    requires currentTime
    constructs Approved
```

一見不便ですが、目的は明確で、業務計算のどこが外界に触れているかを言語レベルで追跡可能にするためです。

`requires` のないbehaviorは、同じ入力に対して同じ結果を返す計算として閉じています。データベースアクセスや現在時刻の取得が必要になると、その依存がbehaviorの宣言に現れます。Southerは汎用的なエフェクトシステムを持ちませんが、業務モデルで必要になる外界との接点だけを `requires` で追跡します。

Flix のようにIOなどの副作用を型へ注釈するのではなく、Southerでは外界に触れる処理そのものをbehaviorとして宣言し、Javaから注入します。

## decoder と encoder は自動導出される

decoderやencoderを手書きする記法はそもそもありません。データの形状から導出されます。

- 単一プリミティブを包むdataは、JSON上では裸のプリミティブ（`"e-001"`）
- レコードはフィールド名をキーとするJSONオブジェクト
- 直和は `type` フィールドを判別子に、ケース名をタグにする

decoderが不正な外部入力を受け取ると、境界用の `Result` が返ります。これはbehaviorの業務結果とは別物です。decoderの `Result` はJSONの形式不正や制約違反という境界の問題を表し、behaviorの直和は業務上の帰結を表します。両者を同じ「失敗」に混ぜないことが設計の要点です。キー名のカスタマイズや正規化が必要なら、それは境界（Java側）の仕事という割り切りです。

## example はコンパイル時に検査される

個人的に一番気に入っている機能です。behaviorの隣に、入力と期待する帰結の組を置けます。

```elm
example submit
    | "within limit" :
        (DraftRequest { applicant = EmployeeId("e-001"), plannedCost = Amount(50000) }, "2026-07-20T09:00")
            -> Submitted
    | "over limit" :
        (DraftRequest { applicant = EmployeeId("e-001"), plannedCost = Amount(100001) }, "2026-07-20T09:00")
            -> Rejected { reason = "high_cost" }
```

exampleはコンパイル時に評価され、期待した結果と一致しなければビルドが失敗します。したがって、ビルドが通っている限り、ここに書かれた例と実装は一致しています。実装の変更で例だけが古くなる、ということがありません。

従来は、同じ知識が仕様書の記述、テストケース一覧、テストコードの3か所にあり、仕様変更のたびに3つを同期させる必要がありました。exampleでは境界値（100000と100001）とその帰結がbehaviorと同じファイルにあり、同期の作業そのものがありません。上限ちょうどが通るかどうかも、このファイルを見れば分かります。

記述に必要なものも少なく、テストフレームワークもモックもsetupメソッドも使いません。入力は通常の構築経路を通るため、invariantに反するテストデータはそれ自体がビルドエラーになり、不正なテストデータのままテストが通ることはありません。

位置づけとしては、Cucumberなどの「例で仕様を語る」アプローチを、グルーコードなしで言語機能にしたものに近いです。ユニットテストを全面的に置き換えるものではありませんが、仕様上重要な境界値とbehaviorの対応を、実装と乖離しない形で保てます。

## Java プロジェクトへ組み込む

`.sou` はCLIで直接 `.class` へコンパイルできます。Javaソースを生成してjavacへ渡す方式ではなく、Class-File APIで直接 `.class` を出力します。

```bash
./souther-cli/target/souther compile example.sou -d out
```

実アプリケーションでは、javacのアノテーションプロセッサとして組み込むのが本線です。専用のビルドプラグインは要りません。

```xml
<plugin>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <annotationProcessorPaths>
      <path>net.unit8.souther:souther-compiler:0.1.0-SNAPSHOT</path>
    </annotationProcessorPaths>
    <compilerArgs><arg>-Asouther.source=${project.basedir}/src/main/souther</arg></compilerArgs>
  </configuration>
</plugin>
```

`src/main/souther/` に `.sou` を置いて `mvn compile` すると、生成クラスが `target/classes` に出力され、手書きのJavaが同じコンパイルの中でその生成型を参照できます。`souther-compiler` はプロセッサパスに載るだけで、アプリの依存にも成果物のjarにも入りません。なお、Javaソースが1つもないとjavacがアノテーション処理を始めないため、サンプルでは最小の `package-info.java` を置いています。

Java APIからコンパイラを呼ぶこともできます。

```java
Map<String, byte[]> classes = Compiler.compile(source);
Map<String, byte[]> linked = Compiler.compileModules(List.of(employeeSource, tripSource));
```

Spring Boot + jOOQ でHTTPからH2まで実際に通す例が examples にあります。

## 意図的に持たない機能

Southerは、JVM上で動く小さな汎用言語を目指しているわけではありません。業務データの構築、値の制約、状態遷移、外界への依存を一つのモデルに収めるために、必要な機能だけを持たせています。

Southerにあるのは、不変の直積・直和・単位data、`List<T>`、`Map<String, T>`、optional（`T?`）、`invariant`、`match`、`let`、`if`、`require`、レコードリテラルとspread、`behavior` と `requires` / `constructs`、`>->`、decoder/encoderの導出、`exposing` / `import` を持つモジュール。これだけです。

そして次のものがありません。

- 例外
- `null`
- 可変状態
- 非同期実行
- 任意のJVM API呼び出し

実装が追いついていない一覧ではなく、対象範囲を守るための除外です。任意のJava APIを呼べるようにすると `requires` を通さずに時刻やDBへ触れてしまい、外界依存を追跡する設計が崩れます。例外を入れると、behaviorの戻り値に現れない脱出経路が生まれ、型に書かれた業務結果だけを見ても全帰結を把握できなくなります。機能の少なさが目的なのではなく、構築経路・値制約・依存経路を型と構文の範囲で閉じるための制限です。

## Javaアプリケーションのどこで使うか

Southerで書くのは、入力を受け取って業務上の判断を行い、その結果を返す部分です。申請の提出と承認、注文と在庫引当、会員登録、審査、契約状態の遷移、料金計算など、扱うデータと取りうる結果を列挙できる処理を `.sou` に置きます。

HTTPリクエストの受信、データベースアクセス、トランザクション制御、非同期実行、画面表示はJava側に残します。Javaが外部との入出力と実行環境を担当し、Southerがその内側にある業務データと判断を担当する分け方です。

既存のJavaアプリケーションをSoutherへ移行する必要はありません。業務ルールを新しく実装する箇所や、状態遷移が複雑になった箇所から `.sou` へ切り出せます。Javaアプリケーションの中に、業務ルール以外を持ち込めない範囲を作るために使います。

## おわりに

構文だけ見れば、代数的データ型を持つ小さな関数型言語です。ただ、自分が本当に扱いたかったのはそこではなく、誰がその値を構築できるか、どの結果が後段へ流れるか、どこから外界へ出られるか、でした。型安全なデータ定義DSLは珍しくありませんが、そこまでを1つの実行可能モデルに収めようとしているのがSoutherです。

※ 正式リリースできて、始める障壁が下がったら、本記事更新します。
