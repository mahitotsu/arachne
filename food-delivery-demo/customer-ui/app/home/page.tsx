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

const WORKFLOW_STEP_LABEL: Record<string, string> = {
  'item-selection': 'アイテム選択中',
  'delivery-selection': '配送選択中',
  'payment': 'お支払い確認中',
};

type OrderHistoryItem = {
  orderId: string;
  itemSummary: string;
  total: number;
  etaLabel: string;
  paymentStatus: string;
  createdAt: string;
};

type CampaignSummary = {
  campaignId: string;
  title: string;
  description: string;
  badge: string;
  validUntil: string;
};

type ServiceHealthSummary = {
  serviceName: string;
  status: string;
};

type SupportStatusResponse = {
  services: ServiceHealthSummary[];
};

type ServiceState = 'loading' | 'ok' | 'error' | 'idle';

const ACCESS_TOKEN_KEY = 'delivery-demo-access-token';
const SESSION_KEY = 'delivery-demo-session-id';

export default function HomePage() {
  const router = useRouter();
  const [profile, setProfile] = useState<CustomerProfile | null>(null);
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [recentDraft, setRecentDraft] = useState<RecentDraft | null>(null);
  const [recentWorkflowStep, setRecentWorkflowStep] = useState<string>('');
  const [orderHistory, setOrderHistory] = useState<OrderHistoryItem[]>([]);
  const [campaigns, setCampaigns] = useState<CampaignSummary[]>([]);
  const [serviceStatuses, setServiceStatuses] = useState<ServiceHealthSummary[]>([]);
  const [bannerDismissed, setBannerDismissed] = useState(false);
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
      } else {
        try {
          const r = await fetch(`/api/backend/session/${sessionId}`, { headers });
          if (r.ok) {
            const data = await r.json();
            if (data.workflowStep && data.workflowStep !== 'completed') {
              setRecentDraft(data.draft as RecentDraft);
              setRecentWorkflowStep(data.workflowStep as string);
            }
            setSvcState(s => ({ ...s, 'order-service': 'ok' }));
          } else {
            setSvcState(s => ({ ...s, 'order-service': r.status === 404 ? 'idle' : 'error' }));
          }
        } catch {
          setSvcState(s => ({ ...s, 'order-service': 'error' }));
        }
      }

      // Fetch order history regardless of active session
      try {
        const hr = await fetch('/api/backend/orders/history', { headers });
        if (hr.ok) {
          const data = await hr.json() as OrderHistoryItem[];
          setOrderHistory(data);
        }
      } catch {
        // history is non-critical — silently skip
      }

      // Fetch support-service data (non-critical)
      await Promise.allSettled([
        fetch('/api/support/campaigns', { headers })
          .then(r => (r.ok ? (r.json() as Promise<CampaignSummary[]>) : []))
          .then(data => setCampaigns(data as CampaignSummary[]))
          .catch(() => {}),
        fetch('/api/support/status', { headers })
          .then(r => (r.ok ? (r.json() as Promise<SupportStatusResponse>) : null))
          .then(data => { if (data) setServiceStatuses(data.services ?? []); })
          .catch(() => {}),
      ]);
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
          <Link href="/agents" className="ag-nav-back" style={{ marginRight: 8 }}>
            🤖 エージェント
          </Link>
          <button type="button" className="h-nav-signout" onClick={signOut}>
            ログアウト
          </button>
        </div>
      </nav>

      {/* Campaign banner */}
      {campaigns.length > 0 && !bannerDismissed && (
        <div className="h-campaign-banner">
          <span className="h-campaign-banner-badge">{campaigns[0].badge}</span>
          <div className="h-campaign-banner-body">
            <span className="h-campaign-banner-title">{campaigns[0].title}</span>
            <span className="h-campaign-banner-desc">{campaigns[0].description}</span>
          </div>
          <span className="h-campaign-banner-until">〜 {campaigns[0].validUntil}</span>
          <button
            type="button"
            className="h-campaign-banner-close"
            aria-label="バナーを閉じる"
            onClick={() => setBannerDismissed(true)}
          >
            ✕
          </button>
        </div>
      )}

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
              <p className="h-recent-label">📦 現在のセッション</p>
              {recentWorkflowStep && (
                <span className="h-recent-step-badge">
                  {WORKFLOW_STEP_LABEL[recentWorkflowStep] ?? recentWorkflowStep}
                </span>
              )}
              {recentDraft.items.length > 0 ? (
                <div className="h-recent-items">
                  {recentDraft.items.slice(0, 4).map(item => (
                    <span key={item.name} className="h-recent-item">
                      {item.quantity}× {item.name}
                    </span>
                  ))}
                </div>
              ) : (
                <p className="h-recent-pending">アイテムを選択中です</p>
              )}
              <div className="h-recent-footer">
                <span className="h-recent-status">{recentDraft.status}</span>
                <Link href="/order" className="h-recent-continue">続きから →</Link>
              </div>
            </div>
          ) : (
            <div className="h-recent-card h-recent-empty">
              <p className="h-recent-label">📦 現在のセッション</p>
              <p className="h-recent-empty-text">
                現在の注文はありません。下のメニューから注文を始めてください。
              </p>
            </div>
          )}

          {/* Service health from support-service */}
          {serviceStatuses.length > 0 && (
            <div className="h-svc-status-card">
              <p className="h-svc-status-title">📡 サービス稼働状況</p>
              <div className="h-svc-status-list">
                {serviceStatuses.map(s => (
                  <div key={s.serviceName} className="h-svc-status-row">
                    <span className={`sp-status-dot sp-status-dot--${s.status.toLowerCase()}`} />
                    <span className="h-svc-status-name">{s.serviceName}</span>
                    <span className={`h-svc-status-label h-svc-status-label--${s.status.toLowerCase()}`}>
                      {s.status}
                    </span>
                  </div>
                ))}
              </div>
              <Link href="/support" className="h-svc-status-link">詳細 → サポートセンター</Link>
            </div>
          )}
        </div>
      </section>

      {/* Order history */}
      <section className="h-history-section">
        <div className="h-history-card">
          <h2 className="h-history-title">📋 注文履歴</h2>
          {orderHistory.length === 0 ? (
            <p className="h-history-empty">まだ注文履歴がありません。</p>
          ) : (
            <div className="h-history-list">
              {orderHistory.map(order => {
                const total = typeof order.total === 'string' ? parseFloat(order.total as unknown as string) : order.total;
                const date = new Date(order.createdAt).toLocaleDateString('ja-JP', { month: 'short', day: 'numeric' });
                return (
                  <div key={order.orderId} className="h-history-row">
                    <div className="h-history-summary">
                      <p className="h-history-items">{order.itemSummary}</p>
                      <p className="h-history-meta">{date} · {order.etaLabel} · {order.paymentStatus}</p>
                    </div>
                    <span className="h-history-total">¥{total.toFixed(0)}</span>
                    <Link
                      href={`/order?reorder=${encodeURIComponent(order.orderId)}`}
                      className="h-history-reorder"
                    >
                      再注文 →
                    </Link>
                  </div>
                );
              })}
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
          <div className="h-bottom-cta-actions">
            <Link href="/order" className="h-cta-btn">
              <span>🗨</span>
              <span>注文チャットを開く</span>
              <span className="h-cta-arrow">→</span>
            </Link>
            <Link href="/support" className="h-support-btn">
              <span>🎧</span>
              <span>サポートセンター</span>
            </Link>
          </div>
        </div>
      </section>

      {/* Floating support entry */}
      <Link href="/support" className="h-float-support" aria-label="サポートセンターを開く">
        <span className="h-float-support-icon">🎧</span>
        <span className="h-float-support-text">サポート</span>
      </Link>
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
