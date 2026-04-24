'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';

type CustomerProfile = {
  customerId: string;
  loginId: string;
  displayName: string;
  locale: string;
  scopes: string[];
};

type MenuItem = {
  id: string;
  name: string;
  description: string;
  price: number;
  suggestedQuantity: number;
};

type RecentDraft = {
  status: string;
  items: { name: string; quantity: number; unitPrice: number }[];
  total: number;
  orderId: string;
};

type ServiceState = 'loading' | 'ok' | 'error' | 'idle';

const ACCESS_TOKEN_KEY = 'delivery-demo-access-token';
const SESSION_KEY = 'delivery-demo-session-id';

export default function HomePage() {
  const router = useRouter();
  const [profile, setProfile] = useState<CustomerProfile | null>(null);
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [recentDraft, setRecentDraft] = useState<RecentDraft | null>(null);
  const [svcState, setSvcState] = useState<Record<string, ServiceState>>({
    'customer-service': 'loading',
    'menu-service': 'loading',
    'order-service': 'loading',
  });

  useEffect(() => {
    const token = window.localStorage.getItem(ACCESS_TOKEN_KEY);
    if (!token) {
      router.replace('/');
      return;
    }
    const headers = { Authorization: `Bearer ${token}` };

    async function load() {
      const [profileResult, menuResult] = await Promise.allSettled([
        fetch('/api/customer/customers/me', { headers }).then(r => {
          if (r.status === 401) throw Object.assign(new Error('UNAUTH'), { status: 401 });
          if (!r.ok) throw new Error(`${r.status}`);
          return r.json() as Promise<CustomerProfile>;
        }),
        fetch('/api/menu/catalog', { headers }).then(r => {
          if (!r.ok) throw new Error(`${r.status}`);
          return r.json() as Promise<MenuItem[]>;
        }),
      ]);

      if (profileResult.status === 'fulfilled') {
        setProfile(profileResult.value);
        setSvcState(s => ({ ...s, 'customer-service': 'ok' }));
      } else {
        setSvcState(s => ({ ...s, 'customer-service': 'error' }));
        if ((profileResult.reason as { status?: number })?.status === 401) {
          window.localStorage.removeItem(ACCESS_TOKEN_KEY);
          router.replace('/');
          return;
        }
      }

      if (menuResult.status === 'fulfilled') {
        setMenuItems(menuResult.value);
        setSvcState(s => ({ ...s, 'menu-service': 'ok' }));
      } else {
        setSvcState(s => ({ ...s, 'menu-service': 'error' }));
      }

      const sessionId = window.localStorage.getItem(SESSION_KEY);
      if (!sessionId) {
        setSvcState(s => ({ ...s, 'order-service': 'idle' }));
        return;
      }
      try {
        const r = await fetch(`/api/backend/session/${sessionId}`, { headers });
        if (r.ok) {
          const data = await r.json();
          if (data.draft?.items?.length > 0) {
            setRecentDraft(data.draft as RecentDraft);
          }
          setSvcState(s => ({ ...s, 'order-service': 'ok' }));
        } else {
          setSvcState(s => ({ ...s, 'order-service': r.status === 404 ? 'idle' : 'error' }));
        }
      } catch {
        setSvcState(s => ({ ...s, 'order-service': 'error' }));
      }
    }

    void load();
  }, [router]);

  function signOut() {
    window.localStorage.removeItem(ACCESS_TOKEN_KEY);
    window.localStorage.removeItem(SESSION_KEY);
    router.push('/');
  }

  const combos = menuItems.filter(m => m.id.startsWith('combo-'));
  const sides = menuItems.filter(m => m.id.startsWith('side-'));
  const drinks = menuItems.filter(m => m.id.startsWith('drink-'));
  const wraps = menuItems.filter(m => m.id.startsWith('wrap-'));
  const bowls = menuItems.filter(m => m.id.startsWith('bowl-'));
  const desserts = menuItems.filter(m => m.id.startsWith('dessert-'));

  const sources = [
    { key: 'customer-service', label: 'customer-service', icon: '🔐', desc: 'profile & auth' },
    { key: 'menu-service', label: 'menu-service', icon: '🍱', desc: 'menu catalog' },
    { key: 'order-service', label: 'order-service', icon: '📦', desc: 'recent session' },
  ];

  return (
    <div className="h-shell">
      {/* Nav */}
      <nav className="h-nav">
        <Link href="/home" className="h-nav-brand">
          <span>🍜</span>
          <span className="h-nav-name">Arachne Kitchen</span>
        </Link>
        <div className="h-nav-right">
          {profile && (
            <div className="h-nav-profile">
              <span className="h-nav-avatar">👤</span>
              <div>
                <strong>{profile.displayName}</strong>
                <span>{profile.loginId}</span>
              </div>
            </div>
          )}
          <button type="button" className="h-nav-signout" onClick={signOut}>
            ログアウト
          </button>
        </div>
      </nav>

      {/* Hero */}
      <section className="h-hero">
        <div className="h-hero-left">
          <p className="h-hero-greeting">
            {profile ? `おかえりなさい、${profile.displayName} さん 👋` : 'こんにちは 👋'}
          </p>
          <h1 className="h-hero-title">
            今日は何を<br /><span className="gradient-text">食べますか</span>？
          </h1>
          <p className="h-hero-lead">
            メニューをチェックしてから、AI エージェントとの会話で注文しましょう。
            menu-agent · kitchen-agent · delivery-agent · payment-agent が裏で協働します。
          </p>
          <Link href="/order" className="h-cta-btn">
            <span>🗨</span>
            <span>AI エージェントに注文する</span>
            <span className="h-cta-arrow">→</span>
          </Link>
        </div>

        <div className="h-hero-right">
          {/* Service sources */}
          <div className="h-sources-card">
            <p className="h-sources-title">ダッシュボード データソース</p>
            {sources.map(({ key, label, icon, desc }) => (
              <div key={key} className="h-source-row">
                <span className="h-source-icon">{icon}</span>
                <div className="h-source-info">
                  <span className="h-source-name">{label}</span>
                  <span className="h-source-desc">{desc}</span>
                </div>
                <span className={`h-source-badge h-source-badge--${svcState[key]}`}>
                  {svcState[key] === 'loading' ? '接続中' :
                   svcState[key] === 'ok' ? 'OK' :
                   svcState[key] === 'idle' ? 'no session' : 'error'}
                </span>
              </div>
            ))}
          </div>

          {/* Recent session */}
          {recentDraft ? (
            <div className="h-recent-card">
              <p className="h-recent-label">📦 前回のセッション</p>
              <div className="h-recent-items">
                {recentDraft.items.slice(0, 4).map(item => (
                  <span key={item.name} className="h-recent-item">
                    {item.quantity}× {item.name}
                  </span>
                ))}
              </div>
              <div className="h-recent-footer">
                <span className="h-recent-status">{recentDraft.status}</span>
                <Link href="/order" className="h-recent-continue">続きから →</Link>
              </div>
            </div>
          ) : (
            <div className="h-recent-card h-recent-empty">
              <p className="h-recent-label">📦 前回のセッション</p>
              <p className="h-recent-empty-text">
                まだセッションはありません。下のメニューから注文を始めてください。
              </p>
            </div>
          )}
        </div>
      </section>

      {/* Menu catalog */}
      <section className="h-menu-section">
        <div className="h-section-header">
          <div>
            <p className="h-section-source">menu-service · menu-agent</p>
            <h2 className="h-section-title">メニューカタログ</h2>
          </div>
          <Link href="/order" className="h-section-cta">
            チャットで注文する →
          </Link>
        </div>

        {menuItems.length === 0 ? (
          <div className="h-menu-fetching">
            <span>📡</span>
            <p>menu-service からカタログを取得中...</p>
          </div>
        ) : (
          <div className="h-menu-catalog">
            {combos.length > 0 && (
              <div className="h-menu-group">
                <h3 className="h-menu-group-title">🍱 コンボセット</h3>
                <div className="h-menu-grid">
                  {combos.map(item => <MenuCard key={item.id} item={item} />)}
                </div>
              </div>
            )}
            {sides.length > 0 && (
              <div className="h-menu-group">
                <h3 className="h-menu-group-title">🍟 サイド</h3>
                <div className="h-menu-grid">
                  {sides.map(item => <MenuCard key={item.id} item={item} />)}
                </div>
              </div>
            )}
            {bowls.length > 0 && (
              <div className="h-menu-group">
                <h3 className="h-menu-group-title">🍚 ライスボウル</h3>
                <div className="h-menu-grid">
                  {bowls.map(item => <MenuCard key={item.id} item={item} />)}
                </div>
              </div>
            )}
            {(drinks.length > 0 || wraps.length > 0) && (
              <div className="h-menu-group">
                <h3 className="h-menu-group-title">🥤 ドリンク & その他</h3>
                <div className="h-menu-grid">
                  {[...drinks, ...wraps].map(item => <MenuCard key={item.id} item={item} />)}
                </div>
              </div>
            )}
            {desserts.length > 0 && (
              <div className="h-menu-group">
                <h3 className="h-menu-group-title">🍰 デザート</h3>
                <div className="h-menu-grid">
                  {desserts.map(item => <MenuCard key={item.id} item={item} />)}
                </div>
              </div>
            )}
          </div>
        )}
      </section>

      {/* Bottom CTA */}
      <section className="h-bottom-cta">
        <div className="h-bottom-cta-inner">
          <div>
            <h2>メニューが決まったら、AI に話しかけよう。</h2>
            <p>「2人分でスパイシー少なめ、自社エクスプレスで」と話しかけるだけで注文・支払い・配送まで完結します。</p>
          </div>
          <Link href="/order" className="h-cta-btn">
            <span>🗨</span>
            <span>注文チャットを開く</span>
            <span className="h-cta-arrow">→</span>
          </Link>
        </div>
      </section>
    </div>
  );
}

function MenuCard({ item }: { item: MenuItem }) {
  const price = typeof item.price === 'string' ? parseFloat(item.price as unknown as string) : item.price;
  return (
    <div className="h-menu-card">
      <div className="h-menu-card-emoji">{getEmoji(item.id)}</div>
      <div className="h-menu-card-body">
        <h4>{item.name}</h4>
        <p>{item.description}</p>
      </div>
      <div className="h-menu-card-foot">
        <span className="h-menu-card-price">¥{price.toFixed(0)}</span>
        <Link href={`/order?item=${encodeURIComponent(item.name)}`} className="h-menu-card-order">注文する →</Link>
      </div>
    </div>
  );
}

function getEmoji(id: string): string {
  const map: Record<string, string> = {
    'combo-crispy': '🍗',
    'combo-smash': '🍔',
    'combo-kids': '🧒',
    'combo-teriyaki': '🍱',
    'combo-spicy-tuna': '🌶️',
    'side-fries': '🍟',
    'side-nuggets': '🍖',
    'side-onion-rings': '🧅',
    'drink-lemon': '🍋',
    'drink-latte': '☕',
    'drink-matcha-latte': '🍵',
    'wrap-garden': '🌯',
    'bowl-salmon': '🐟',
    'bowl-veggie': '🥗',
    'dessert-choco': '🍫',
    'dessert-matcha': '🍦',
  };
  return map[id] ?? '🍴';
}
