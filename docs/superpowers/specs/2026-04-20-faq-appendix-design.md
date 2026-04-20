# 設計書: 巻末付録「FAQ: 従来のDDD解説との違い」

- 日付: 2026-04-20
- 対象書籍: `books/ddd-detachment/`

## 1. 目的

従来ネット記事で広く解説されている「DDD + クリーンアーキテクチャ」の典型パターン (Controller/Service/Repository の3層、@Entity をドメインモデルとして使う、Bean Validation でドメインモデルを守る、DTOで詰め替える、Aggregate/Repository/UseCase の語彙など) しか学んでいない読者が、本書の設計を読んだときに抱く**用語・概念のズレに由来する困惑**を、Q&A形式で解消する。

本書は「古典ドメインモデルパターンの解脱」として以下を主張している:
- ドメインモデルは `record` + `sealed interface` で十分
- バリデーションは Raoh デコーダが境界で一度だけ行う (Always-Valid Layer)
- レイヤー間の詰め替えは最小化し、ドメインモデルをそのまま受け渡す
- 振る舞いは別クラス (SubscriptionBehavior) に分離する

従来の典型と上記の主張はいくつもの点で食い違う。巻末付録FAQはその食い違いを明示し、読者が本書を腹落ちして読み終えられるよう支援する。

## 2. 対象読者

- 従来のQiita/Zenn系「DDD+クリーンアーキテクチャ」解説記事の典型だけを学んでいる読者
- 本書の主張と自分の常識が食い違うことに**困惑している**読者
- (対象外: 懐疑的な読者への反論、特定フレームワーク (Spring/JPA) の運用論)

## 3. 収録形式

- ファイル: `books/ddd-detachment/appendix-faq.md` (新規)
- `config.yaml` の `chapters:` リストで `appendix-raoh-api` の**前**に挿入
- 最終的な順序: 本編 (1〜13章) → `appendix-faq` → `appendix-raoh-api` → `appendix-references`

## 4. 各項目のフォーマット

```markdown
### Q<番号>. <問いの文>

<本文 15〜30 行。典型的な誤解の背景に触れつつ、本書の立場を説明。
必要に応じて簡潔なコード例や1問1図を添える>

→ 詳しくは [<章タイトル>](<章ファイル名>.md) (関連: <他の章名>)
```

- Q の直後に「なぜそう思うのか」のような固定セクションは**置かない** (本文内で自然に触れる)
- 文体は本書既存章に統一 (丁寧な説明、断定形、感情的修辞を抑制、「〜やすいです」を使わない)
- 従来派を貶めない中立トーン。「従来はXだ / 本書はYだ」の対比を明示する

## 5. 参照のリンク化方針

- 相対パスのMarkdownリンクを使う (例: `[9章 マッピング戦略](part4-ch09-mapping-strategy.md)`)
- 複数章にまたがる参照は、**主となる1章**をリンク化し、関連章はテキストで併記する
  - 例: `→ 詳しくは [9章 マッピング戦略](part4-ch09-mapping-strategy.md) (関連: 10章 結合のバランス)`
- 章タイトルは実際のフロントマター `title:` と一致させる

## 6. 収録する17問 (思考順)

思考順 = 本書を読み進めた読者の疑問が浮かぶ順序。以下の順で並べる。

| # | 問い | 軸 | 主要参照章 |
|---|---|---|---|
| 1 | Form / Command / Entity を分けるのが「正しい詰め替え」では? | レイヤー構造 | 9章 (関連: 10〜11章) |
| 2 | デコーダは結局バリデーションと何が違うのか | バリデーション | 4章 (関連: 3, 5, 6章) |
| 3 | Always-Valid Layer は Value Object と同じでは? | DDD戦術 | 6章 (関連: 8章) |
| 4 | `record` のドメインモデルは「振る舞いを持つ」原則に反するのでは? | ドメインモデル | 8章 (関連: 7章) |
| 5 | sealed interface での状態遷移は State パターンと何が違うのか | ドメインモデル | 7章 |
| 6 | 振る舞いクラス (SubscriptionBehavior) は Domain Service と何が違うのか | DDD戦術 | 8章 (関連: 7章) |
| 7 | Aggregate (集約) はどこに行ったのか | DDD戦術 | 7〜8章 |
| 8 | Aggregate Root・Repository・トランザクション境界の対応は? | DDD戦術 / レイヤー構造 | 7〜8章, 9〜11章 |
| 9 | Value Object は `record` に吸収されたのか | DDD戦術 | 8章 |
| 10 | Domain Event は本書のアプローチでどう扱うのか | DDD戦術 | 7章 |
| 11 | Factory パターンとデコーダの関係は? | DDD戦術 | 4章 (関連: 11章) |
| 12 | UseCase / Interactor 層はどこに相当するのか | クリーンアーキ | 9〜11章 |
| 13 | 依存性逆転 (DIP) は本書の構成で成立しているのか | クリーンアーキ | 10章 |
| 14 | Gateway・Presenter はどこに対応するのか | クリーンアーキ | 11章 |
| 15 | ヘキサゴナル / ポート&アダプタとの対応関係 | クリーンアーキ | 10〜11章 |
| 16 | DTO がまったく不要になるという主張なのか | レイヤー構造 | 9章 |
| 17 | Controller でドメインモデルを直接扱うのは層の漏れでは? | レイヤー構造 / クリーンアーキ | 9章 (関連: 11章) |

### 6.1 各問の方向性メモ

執筆時のガイド。本文での触れ方を簡潔に示す。

**Q1. Form / Command / Entity を分けるのが「正しい詰め替え」では?**
典型解説ではレイヤー間DTO分離が「関心の分離」の具体形として示される。本書は、詰め替えの数ではなく**どのレイヤーがどの型の知識を持つべきか**で設計を評価する立場。9章の2-way / 1-way Mappingの区別で整理する。

**Q2. デコーダは結局バリデーションと何が違うのか**
バリデーションは「検査して真偽を返す」、デコーダは「パースして型を確定させる」という役割の違いを示す。**ID存在確認はどちらのレイヤーか**という点も内包し、本書の立場 (存在確認は形式的正当性の一部=デコーダに置ける) を示す。6章の形式的正当性/業務的正当性の表を参照させる。

**Q3. Always-Valid Layer は Value Object と同じでは?**
Value Object は個々の値の概念、Always-Valid Layerは**層**の概念。粒度も責任範囲も異なる。Always-Valid Layer は Value Object を含みうるが、同一ではない。

**Q4. `record` のドメインモデルは「振る舞いを持つ」原則に反するのでは?**
「ドメインモデル=データ+振る舞いを一体にしたクラス」の直感への対応。本書は**振る舞いを別クラス (Behavior)** に分離することで、データの変更理由と振る舞いの変更理由を独立させる。関心の分離の一形態であることを示す。

**Q5. sealed interface での状態遷移は State パターンと何が違うのか**
State パターンは**現在の状態を切り替える仕組み**、sealed interface による状態表現は**状態ごとに型が異なる**もの。コンパイル時網羅性チェックの利点が、通常のState パターンでは得られない点を示す。

**Q6. 振る舞いクラスは Domain Service と何が違うのか**
Domain Service は通常「複数エンティティをまたぐロジック置き場」として定義される。本書の Behavior クラスは**特定の record に対する状態遷移と業務ルール検査**を担当し、対応する record と1:1で紐づく。

**Q7. Aggregate (集約) はどこに行ったのか**
本書は「集約ルートを1つの可変クラスにする」という典型表現を採らない。**不変 record による状態の表現**で、集約が保つべき不変条件はデコーダと Behavior で担保する。集約の概念自体を否定するのではなく、表現方法が変わる。

**Q8. Aggregate Root・Repository・トランザクション境界の対応は?**
Repository は引き続き存在するが、**不変 record を保存/復元する**形に変わる。トランザクション境界は Application Service (Behavior を呼び出す層) に残る。集約の整合性はデコーダ+Behaviorが保証する。

**Q9. Value Object は `record` に吸収されたのか**
実質的にはそう。`record` は不変で等価性が値に基づく。Value Object の本質的な性質を `record` は最初から備える。従来「Value Object をクラスで書いていた」苦労が消える。

**Q10. Domain Event は本書のアプローチでどう扱うのか**
本書は Domain Event を主題としない。ただし**不変 record** は「発生した事実を表現する型」と親和性が高く、sealed interface で Event 型を表現する方向で自然に拡張できる。本書のスコープ外であることを明示する。

**Q11. Factory パターンとデコーダの関係は?**
Factory は「正しい状態のオブジェクトを生成する責任」を持つ。デコーダは**入力をパースしてドメイン型を確定させる**責任を持つ。後者は前者の一形態と見なせる。Factory的責任を境界に集約するのがデコーダの立ち位置。

**Q12. UseCase / Interactor 層はどこに相当するのか**
本書の Application Service (Behavior 呼び出し + トランザクション制御) が UseCase に相当する。UseCase を廃止しているわけではなく、**ドメインに振る舞いを持たせすぎない**ことで UseCase がシンプルになる。

**Q13. 依存性逆転 (DIP) は本書の構成で成立しているのか**
ドメイン層 (record + Behavior) は Raoh や Spring に依存しない。デコーダは境界の型変換器で、ドメイン層を外側のフレームワークから守る方向で機能する。DIPは成立している。

**Q14. Gateway・Presenter はどこに対応するのか**
Gateway = Repository 実装 (インフラ層)、Presenter = Controller 内でのレスポンス整形 or 専用 Presenter クラス。本書は語彙を簡素化しているが、責務としては対応する。

**Q15. ヘキサゴナル / ポート&アダプタとの対応関係**
デコーダは「インバウンドアダプタの一部」、Repository 実装は「アウトバウンドアダプタ」。ドメイン層の record + Behavior は「内側のヘキサゴン」。本書の構成はヘキサゴナルと矛盾せず、**不変 record と Behavior 分離という内側の実装方針を示すもの**と位置づける。

**Q16. DTO がまったく不要になるという主張なのか**
そうではない。**レイヤー間DTOとしての Form / Command は不要**と主張する。一方、外部APIのレスポンス形式など、ドメインモデルとは目的が異なるDTOは引き続き使う (9章 1-way Mapping)。

**Q17. Controller でドメインモデルを直接扱うのは層の漏れでは?**
Controller が触る `OrderPlan` は**デコーダが境界で生成した型**で、ドメインロジックそのものではない。型が同じでもレイヤーの責務は分かれている。むしろ型を分けないことで、詰め替え時の情報欠落リスクが減る。

## 7. 執筆時の文体ガイド

- 本書既存章の文体を踏襲する
- グローバル記憶 `feedback_writing_style.md` に従い「〜やすいです」を使わない
- 断定形で書く (不要な婉曲表現を避ける)
- 従来派を貶めない中立トーン。「従来解説では X と説明されることが多い」→「本書の立場は Y」の対比構造
- 各項目は 15〜30 行を目安 (コード例・表を含む場合はやや長くなってよい)
- `→ 詳しくは` の参照リンク行は各項目の末尾に1行

## 8. `config.yaml` への追記

```yaml
chapters:
  - part0-preface
  - part1-ch01-typical-spring-ddd
  - part1-ch02-problems
  - part2-ch03-bean-validation
  - part2-ch04-parse-dont-validate
  - part2-ch05-comparison
  - part3-ch06-always-valid-layer
  - part3-ch07-state-transition
  - part3-ch08-record-domain-model
  - part4-ch09-mapping-strategy
  - part4-ch10-coupling-balance
  - part4-ch11-decoder-as-glue
  - part5-ch12-migration
  - part5-ch13-decision-criteria
  - appendix-faq          # ← 追加
  - appendix-raoh-api
  - appendix-references
```

## 9. 完成判定

- [ ] `books/ddd-detachment/appendix-faq.md` が作成され、17問すべてが 15〜30行の本文を持つ
- [ ] 各項目の末尾に相対パスの参照リンクがある
- [ ] `config.yaml` の `chapters:` に `appendix-faq` が `appendix-raoh-api` の前に追記されている
- [ ] 本書既存章との文体統一が取れている (「〜やすいです」を含まない、断定形、中立トーン)
- [ ] リード文で対象読者 (従来の典型解説しか知らない困惑派) と付録の目的が明示されている
