---
title: "付録: 参考文献"
---

本書の執筆にあたり参照した文献・記事を一覧します。

## 書籍

- Tom Hombergs『Get Your Hands Dirty on Clean Architecture』Packt Publishing, 2019（第2版 2023）  
  邦訳: 須田智之 訳『手を動かしてわかるクリーンアーキテクチャ』翔泳社, 2022  
  → Part 4「マッピング戦略」（No Mapping / 2-way Mapping / Full Mapping）の分類を参照しています

- Vladik Khononov『Balancing Coupling in Software Design』Addison-Wesley, 2024  
  → Part 4「結合強度と距離」（モデル結合 / 契約結合）の概念を参照しています

- Scott Wlaschin『Domain Modeling Made Functional』Pragmatic Bookshelf, 2018  
  邦訳: 猪股健太郎 訳『関数型ドメインモデリング』ドワンゴ, 2024  
  → 「Make Illegal States Unrepresentable」（不正な状態を型で排除する）および sealed interface × record によるドメインモデル表現の考え方を参照しています

## Web記事

- Alexis King「Parse, Don't Validate」(2019)  
  <https://lexi-lambda.github.io/blog/2019/11/05/parse-don-t-validate/>  
  → Part 2「パースとバリデーションを同時に行う」の原則の出典です

- Vladimir Khorikov「Always-Valid Domain Model」(2020)  
  <https://enterprisecraftsmanship.com/posts/always-valid-domain-model/>  
  → Part 3「Always-Valid Layer」の概念の出典です

## ライブラリ

- Raoh（デコーダ合成ライブラリ）  
  <https://github.com/kawasima/raoh>  
  → 本書のサンプルコードで使用しているデコーダライブラリです。付録「Raoh APIリファレンス」も参照してください
