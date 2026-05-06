# Site

このサブプロジェクトは、Arachne と food-delivery-demo を題材にした長編ブログ兼ショーケースサイトの骨格です。

## 技術スタック

- Astro
- TypeScript
- MDX
- CSS variables ベースのカスタムデザイン

## 使い方

```bash
cd site
npm install
npm run dev
```

ビルド確認:

```bash
npm run build
```

## ページ構成

- `/`: サイトトップと論旨の入口
- `/essay`: 仮説と設計原則の本編（ハブ記事 + 4 本のサブ記事）
- `/demo`: food-delivery-demo のサービス構成・設計原則と実装の対応表
- `/observations`: 実際に動かして確かめた振る舞いの記録（原則ごとに仮説・観察・確認場所）