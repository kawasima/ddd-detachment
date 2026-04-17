# Zenn CLI

* [📘 How to use](https://zenn.dev/zenn/articles/zenn-cli-guide)

## GitHub からの Publish 手順

このリポジトリは Zenn の GitHub 連携でコンテンツを管理しています。

### 初回設定

1. [Zenn のダッシュボード](https://zenn.dev/dashboard/deploys) を開く
2. 「GitHub からデプロイ」→「リポジトリを連携する」を選択
3. このリポジトリを選択して連携を完了する

### 記事・本の公開

`main` ブランチに push すると、Zenn が自動的に差分を検出してデプロイします。

```bash
git add books/ articles/
git commit -m "feat: ..."
git push origin main
```

公開状態はファイルの frontmatter で制御します。

```yaml
# 本（config.yaml）
published: true   # true にすると公開

# 記事（articles/*.md）
published: true
```

### プレビュー確認

push 前にローカルでプレビューする場合：

```bash
npx zenn preview
```

ブラウザで `http://localhost:8000` を開くと確認できます。
