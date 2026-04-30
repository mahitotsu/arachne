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

## 想定ページ

- `/`: サイトトップと論旨の入口
- `/essay`: 仮説と設計原則の本編
- `/demo`: food-delivery-demo の見せ方
- `/mapping`: 仮説と実装の対応表
- `/appendix`: OpenAPI、prompt contract、execution history、metrics、registry boundary をまとめる証拠面