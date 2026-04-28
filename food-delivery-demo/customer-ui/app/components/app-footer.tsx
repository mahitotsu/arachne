import Link from 'next/link';

export default function AppFooter() {
  return (
    <footer className="pg-footer">
      <div className="pg-footer-brand">
        <span aria-hidden="true">�</span>
        <span className="pg-footer-name">Arachne Kitchen</span>
      </div>
      <nav className="pg-footer-links" aria-label="フッターナビゲーション">
        <Link href="/home" className="pg-footer-link">ホーム</Link>
        <Link href="/support" className="pg-footer-link">サポート</Link>
        <Link href="/agents" className="pg-footer-link">エージェント</Link>
      </nav>
      <p className="pg-footer-copy">© 2026 Arachne Kitchen Delivery</p>
    </footer>
  );
}
