---
title: "付録: 参考文献"
---

本書の執筆にあたり参照した文献・記事を一覧します。

## 書籍

- Tom Hombergs『Get Your Hands Dirty on Clean Architecture』Packt Publishing, 2019（第2版 2023）  
  邦訳: 須田智之 訳『手を動かしてわかるクリーンアーキテクチャ』翔泳社, 2022  
  → 9章「マッピング戦略」（No Mapping / 2-way Mapping / Full Mapping）の分類を参照しています

- Vladik Khononov『Balancing Coupling in Software Design』Addison-Wesley, 2024  
  → 10章「結合強度と距離」（モデル結合 / 契約結合）の概念を参照しています

- Scott Wlaschin『Domain Modeling Made Functional』Pragmatic Bookshelf, 2018  
  邦訳: 猪股健太郎 訳『関数型ドメインモデリング』ドワンゴ, 2024  
  → 「Make Illegal States Unrepresentable」（不正な状態を型で排除する）および sealed interface × record によるドメインモデル表現の考え方を参照しています

## Web記事

- Alexis King「Parse, Don't Validate」(2019)  
  <https://lexi-lambda.github.io/blog/2019/11/05/parse-don-t-validate/>  
  → 4章「パースとバリデーションを同時に行う」の原則の出典です。6章で言及している「Shotgun Parsing」アンチパターンの概念もこの記事で詳しく論じられています

- LangSec Workshop「Shotgun Parsing」  
  <http://langsec.org/papers/langsec-cwes-secdev2016.pdf>  
  → Shotgun Parsing の名称の起源。言語セキュリティ（LangSec）の観点から、入力検証が複数箇所に散らばる危険性を論じています

- Vladimir Khorikov「Always-Valid Domain Model」(2020)  
  <https://enterprisecraftsmanship.com/posts/always-valid-domain-model/>  
  → 6章「Always-Valid Layer」の概念の出典です

## ライブラリ

- Raoh（デコーダ合成ライブラリ）  
  <https://github.com/kawasima/raoh>  
  → 本書のサンプルコードで使用しているデコーダライブラリです。付録「Raoh APIリファレンス」も参照してください

### 代替となるライブラリ・パターン

本書が主張しているのは「デコーダ合成」という設計方針であり、特定のライブラリではありません。次のような代替手段でも同じ発想を実践できます。

- Vavr: `io.vavr.control.Validation`  
  <https://www.vavr.io/vavr-docs/#_validation>  
  → エラーを蓄積できる `Either` の亜種。Java エコシステムの中では最も広く使われている選択肢の一つです

- Functional Java: `fj.data.Validation`  
  <https://www.functionaljava.org/>  
  → Java 向け関数型ライブラリ。`Validation` 型を中心としたエラー蓄積が可能です

- Scala cats: `Validated` / `ValidatedNel`  
  <https://typelevel.org/cats/datatypes/validated.html>  
  → Scala プロジェクトでの定番。本書のデコーダ合成は `Validated` の思想を Java に持ち込んだ形とも言えます

- 手書き `Result<T>` パターン  
  → 薄い `sealed interface Result<T>` + `Ok<T>` / `Err<T>` を自前で定義し、`combine` と `discriminate` に相当するコンビネータを必要な範囲だけ実装する方法もあります。依存を増やしたくない場合の選択肢です
