'use client';

import { FormEvent, Suspense, useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import ReactMarkdown from 'react-markdown';

import { fetchAuthSession } from '../../lib/browser-session';

// ── Types ────────────────────────────────────────────────────────────────────

type OrderLineItem = {
  name: string;
  quantity: number;
  unitPrice: number;
  note: string;
};

type OrderDraft = {
  status: string;
  items: OrderLineItem[];
  subtotal: number;
  total: number;
  etaLabel: string;
  paymentStatus: string;
  paymentMethod: string;
  orderId: string;
};

type ProposalItem = {
  itemId: string;
  name: string;
  quantity: number;
  unitPrice: number;
  reason: string;
};

type DeliveryOptionChoice = {
  code: string;
  label: string;
  etaMinutes: number;
  fee: number;
  reason: string;
  recommended: boolean;
};

type SuggestResponse = {
  sessionId: string;
  workflowStep: string;
  headline: string;
  summary: string;
  etaMinutes: number;
  proposals: ProposalItem[];
  draft: OrderDraft;
};

type ConfirmItemsResponse = {
  sessionId: string;
  workflowStep: string;
  headline: string;
  items: OrderLineItem[];
  deliveryOptions: DeliveryOptionChoice[];
  draft: OrderDraft;
};

type ConfirmDeliveryResponse = {
  sessionId: string;
  workflowStep: string;
  headline: string;
  draft: OrderDraft;
};

type ConfirmPaymentResponse = {
  sessionId: string;
  workflowStep: string;
  summary: string;
  draft: OrderDraft;
};

type SessionView = {
  sessionId: string;
  workflowStep: string;
  draft: OrderDraft;
  pendingProposal: ProposalItem[];
  pendingDeliveryOptions: DeliveryOptionChoice[];
  snapshot: OrderSnapshot | null;
};

type OrderSnapshot = {
  sessionId: string;
  message: string;
  suggestSummary: string;
  suggestEta: number;
  pendingProposals: ProposalItem[];
  confirmedItems: ProposalItem[];
};

// ── Constants ────────────────────────────────────────────────────────────────

const EMPTY_DRAFT: OrderDraft = {
  status: 'INITIAL',
  items: [],
  subtotal: 0,
  total: 0,
  etaLabel: '',
  paymentStatus: 'PENDING',
  paymentMethod: '',
  orderId: '',
};

const STEPS = ['初期入力', 'アイテム選択', '配送選択', '支払い承認'];

function mapWorkflowStepToStep(workflowStep: string) {
  switch (workflowStep) {
    case 'item-selection':
      return 1;
    case 'delivery-selection':
      return 2;
    case 'payment':
      return 3;
    case 'completed':
      return 4;
    default:
      return 0;
  }
}

function proposalItemsFromDraft(draft: OrderDraft) {
  return draft.items.map((item, index) => ({
    itemId: `confirmed-${index}-${item.name}`,
    name: item.name,
    quantity: item.quantity,
    unitPrice: item.unitPrice,
    reason: item.note,
  }));
}

// ── Main export ───────────────────────────────────────────────────────────────

export default function OrderPage() {
  return (
    <Suspense fallback={null}>
      <OrderPageInner />
    </Suspense>
  );
}

// ── Inner component ───────────────────────────────────────────────────────────

function OrderPageInner() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const [sessionId, setSessionId] = useState('');
  // step: 0=入力, 1=アイテム選択, 2=配送選択, 3=支払い, 4=完了
  const [step, setStep] = useState(0);
  const [committedStep, setCommittedStep] = useState(0);
  const [message, setMessage] = useState('');
  const [refinement, setRefinement] = useState('');
  const [suggestSummary, setSuggestSummary] = useState('');
  const [suggestEta, setSuggestEta] = useState(0);
  const [proposals, setProposals] = useState<ProposalItem[]>([]);
  const [removedItemIds, setRemovedItemIds] = useState<Set<string>>(new Set());
  const [deliveryOptions, setDeliveryOptions] = useState<DeliveryOptionChoice[]>([]);
  const [selectedDeliveryCode, setSelectedDeliveryCode] = useState('');
  const [draft, setDraft] = useState<OrderDraft>(EMPTY_DRAFT);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // ── Auth & session restore ─────────────────────────────────────────────────

  useEffect(() => {
    void (async () => {
      const auth = await fetchAuthSession();
      if (!auth.authenticated) {
        router.replace('/');
        return;
      }

      const itemParam = searchParams.get('item');
      const reorderParam = searchParams.get('reorder');
      if (reorderParam) {
        setMessage(`注文ID ${reorderParam} と同じ内容を再注文したいです。`);
      } else if (itemParam) {
        setMessage(`「${itemParam}」を注文したいです。`);
      }

      try {
        const res = await fetch('/api/backend/order/session', { cache: 'no-store' });
        if (res.status === 404) {
          return;
        }
        if (res.status === 401) {
          router.replace('/');
          return;
        }
        if (!res.ok) {
          throw new Error(`restore failed: ${res.status}`);
        }

        const view = await res.json() as SessionView;
        const restoredDraft = view.draft ?? EMPTY_DRAFT;
        const restoredStep = mapWorkflowStepToStep(view.workflowStep);
        const snapshot = view.snapshot;

        setSessionId(view.sessionId);
        setDraft(restoredDraft);
        setCommittedStep(restoredStep);
        setRemovedItemIds(new Set());
        if (snapshot) {
          setMessage(snapshot.message ?? '');
          setSuggestSummary(snapshot.suggestSummary ?? '');
          setSuggestEta(snapshot.suggestEta ?? 0);
        }

        switch (view.workflowStep) {
          case 'item-selection':
            setProposals(view.pendingProposal?.length ? view.pendingProposal : snapshot?.pendingProposals ?? []);
            setDeliveryOptions([]);
            setSelectedDeliveryCode('');
            setStep(1);
            break;
          case 'delivery-selection':
            setProposals(snapshot?.confirmedItems?.length ? snapshot.confirmedItems : proposalItemsFromDraft(restoredDraft));
            setDeliveryOptions(view.pendingDeliveryOptions ?? []);
            setSelectedDeliveryCode(
              view.pendingDeliveryOptions?.find(o => o.recommended)?.code
              ?? view.pendingDeliveryOptions?.[0]?.code
              ?? ''
            );
            setStep(2);
            break;
          case 'payment':
          case 'completed':
            setProposals(snapshot?.confirmedItems?.length ? snapshot.confirmedItems : proposalItemsFromDraft(restoredDraft));
            setDeliveryOptions([]);
            setSelectedDeliveryCode('');
            setStep(restoredStep);
            break;
          default:
            setProposals([]);
            setDeliveryOptions([]);
            setSelectedDeliveryCode('');
            setCommittedStep(0);
            setStep(0);
        }
      } catch {
        setError('注文セッションの復元に失敗しました');
      }
    })();
  }, [router, searchParams]);

  // ── Helpers ────────────────────────────────────────────────────────────────

  async function startNewOrder() {
    await fetch('/api/backend/order/session', { method: 'DELETE' }).catch(() => {});
    setSessionId('');
    setMessage('');
    setRefinement('');
    setSuggestSummary('');
    setSuggestEta(0);
    setProposals([]);
    setDeliveryOptions([]);
    setSelectedDeliveryCode('');
    setDraft(EMPTY_DRAFT);
    setCommittedStep(0);
    setRemovedItemIds(new Set());
    setStep(0);
    setError('');
  }

  // ── Step 1: initial suggest ────────────────────────────────────────────────

  async function handleSuggest(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!message.trim() || loading) return;
    setLoading(true);
    setError('');
    try {
      const res = await fetch('/api/backend/order/suggest', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          message: message.trim(),
          locale: navigator.languages?.[0] ?? navigator.language ?? '',
        }),
      });
      if (res.status === 401) { router.replace('/'); return; }
      if (!res.ok) throw new Error(`提案取得に失敗しました (${res.status})`);
      const payload: SuggestResponse = await res.json();
      setSessionId(payload.sessionId);
      setProposals(payload.proposals ?? []);
      setRemovedItemIds(new Set());
      setSuggestSummary(payload.summary ?? '');
      setSuggestEta(payload.etaMinutes ?? 0);
      setDraft(payload.draft ?? EMPTY_DRAFT);
      setDeliveryOptions([]);
      setSelectedDeliveryCode('');
      setCommittedStep(1);
      setStep(1);
    } catch (e) {
      setError(e instanceof Error ? e.message : '提案取得に失敗しました');
    } finally {
      setLoading(false);
    }
  }

  // ── Step 1: refinement re-suggest ─────────────────────────────────────────

  async function handleRefinement() {
    if (!refinement.trim() || loading) return;
    setLoading(true);
    setError('');
    try {
      const res = await fetch('/api/backend/order/suggest', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          message: message.trim(),
          locale: navigator.languages?.[0] ?? navigator.language ?? '',
          refinement: refinement.trim(),
        }),
      });
      if (res.status === 401) { router.replace('/'); return; }
      if (!res.ok) throw new Error(`再提案に失敗しました (${res.status})`);
      const payload: SuggestResponse = await res.json();
      setSessionId(payload.sessionId);
      setProposals(payload.proposals ?? []);
      setRemovedItemIds(new Set());
      setSuggestSummary(payload.summary ?? '');
      setSuggestEta(payload.etaMinutes ?? 0);
      setDraft(payload.draft ?? EMPTY_DRAFT);
      setCommittedStep(1);
      setRefinement('');
    } catch (e) {
      setError(e instanceof Error ? e.message : '再提案に失敗しました');
    } finally {
      setLoading(false);
    }
  }

  // ── Step 2: confirm items → delivery options ───────────────────────────────

  async function handleConfirmItems() {
    if (loading) return;
    setLoading(true);
    setError('');
    try {
      // items: [] means "accept all proposals"; pass explicit IDs when user removed some
      const confirmedItems = proposals.filter(p => !removedItemIds.has(p.itemId));
      if (committedStep >= 2) {
        setProposals(confirmedItems);
        setRemovedItemIds(new Set());
        setStep(2);
        return;
      }
      const keptIds = confirmedItems.map(p => ({ itemId: p.itemId }));
      const items = removedItemIds.size > 0 ? keptIds : [];
      const res = await fetch('/api/backend/order/confirm-items', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ items }),
      });
      if (res.status === 401) { router.replace('/'); return; }
      if (!res.ok) throw new Error(`アイテム確定に失敗しました (${res.status})`);
      const payload: ConfirmItemsResponse = await res.json();
      setSessionId(payload.sessionId);
      setProposals(confirmedItems);
      setRemovedItemIds(new Set());
      setDeliveryOptions(payload.deliveryOptions ?? []);
      setDraft(payload.draft ?? EMPTY_DRAFT);
      const recommended = payload.deliveryOptions?.find(o => o.recommended);
      setSelectedDeliveryCode(recommended?.code ?? payload.deliveryOptions?.[0]?.code ?? '');
      setCommittedStep(2);
      setStep(2);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'アイテム確定に失敗しました');
    } finally {
      setLoading(false);
    }
  }

  // ── Step 3: confirm delivery → payment summary ────────────────────────────

  async function handleConfirmDelivery() {
    if (!selectedDeliveryCode || loading) return;
    setLoading(true);
    setError('');
    try {
      const res = await fetch('/api/backend/order/confirm-delivery', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ deliveryCode: selectedDeliveryCode }),
      });
      if (res.status === 401) { router.replace('/'); return; }
      if (!res.ok) throw new Error(`配送確定に失敗しました (${res.status})`);
      const payload: ConfirmDeliveryResponse = await res.json();
      setSessionId(payload.sessionId);
      setDraft(payload.draft ?? EMPTY_DRAFT);
      setCommittedStep(3);
      setStep(3);
    } catch (e) {
      setError(e instanceof Error ? e.message : '配送確定に失敗しました');
    } finally {
      setLoading(false);
    }
  }

  // ── Step 4: confirm payment → completion ──────────────────────────────────

  async function handleConfirmPayment() {
    if (loading) return;
    setLoading(true);
    setError('');
    try {
      const res = await fetch('/api/backend/order/confirm-payment', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({}),
      });
      if (res.status === 401) { router.replace('/'); return; }
      if (!res.ok) throw new Error(`注文確定に失敗しました (${res.status})`);
      const payload: ConfirmPaymentResponse = await res.json();
      setSessionId(payload.sessionId);
      setDraft(payload.draft ?? EMPTY_DRAFT);
      setCommittedStep(4);
      setStep(4);
    } catch (e) {
      setError(e instanceof Error ? e.message : '注文確定に失敗しました');
    } finally {
      setLoading(false);
    }
  }

  // ── Render ─────────────────────────────────────────────────────────────────

  const progressIndex = committedStep < 4 ? committedStep : 3;
  const deliveryFee = Math.max(Number(draft.total) - Number(draft.subtotal), 0);
  const keptProposals = proposals.filter(p => !removedItemIds.has(p.itemId));
  const itemSelectionLocked = step === 1 && committedStep >= 2;

  return (
    <main className="shell">
      {/* Top navigation */}
      <header className="topbar">
        <div className="order-topbar-left">
          <Link href="/home" className="order-back-btn">← ホームへ</Link>
          <div className="brand">
            <span className="brand-icon">🍜</span>
            <span className="brand-name">Arachne Kitchen</span>
            <span className="brand-tagline">single-kitchen cloud delivery</span>
          </div>
        </div>
        <div className="session-pill">
          <span className="session-dot" />
          <span>{sessionId ? `session · ${sessionId.slice(-8)}` : 'new session'}</span>
        </div>
        <button
          type="button"
          className="new-order-btn"
          onClick={startNewOrder}
          disabled={loading}
        >
          ＋ 新しい注文
        </button>
      </header>

      {/* Step progress indicator */}
      <nav className="wf-steps">
        {STEPS.map((label, i) => {
          const canGoBack = i < progressIndex && progressIndex < 3 && !loading;
          return (
          <div
            key={label}
            className={`wf-step${i < progressIndex ? ' wf-step--done' : ''}${i < progressIndex && canGoBack ? ' wf-step--clickable' : ''}${i === step ? ' wf-step--active' : ''}`}
            onClick={canGoBack ? () => setStep(i) : undefined}
            role={canGoBack ? 'button' : undefined}
            tabIndex={canGoBack ? 0 : undefined}
            onKeyDown={canGoBack ? e => { if (e.key === 'Enter' || e.key === ' ') setStep(i); } : undefined}
          >
            <span className="wf-step-num">{i < progressIndex ? '✓' : i + 1}</span>
            <span className="wf-step-label">{label}</span>
          </div>
          );
        })}
      </nav>

      {/* Step content */}
      <section className="wf-content">
        {error && <p className="wf-error">⚠ {error}</p>}

        {/* ── Step 0: Initial Input ── */}
        {step === 0 && (
          <div className="wf-card wf-input-card">
            <h2 className="wf-card-title">ご注文内容を教えてください</h2>
            <p className="wf-card-hint">
              「2人分で辛さ控えめ」「子ども1人います」「予算¥3000以内」など自由に入力できます。
              AI がメニューと配送を提案します。
            </p>
            <form onSubmit={handleSuggest} className="wf-input-form">
              <textarea
                className="wf-textarea"
                value={message}
                onChange={e => setMessage(e.target.value)}
                placeholder="例: 2人分、予算3000円、子どもが1人います"
                rows={3}
                disabled={loading}
              />
              <button
                type="submit"
                className="wf-btn wf-btn--primary"
                disabled={loading || !message.trim()}
              >
                {loading ? '提案中…' : 'AI に提案してもらう →'}
              </button>
            </form>
          </div>
        )}

        {/* ── Step 1: Item Selection ── */}
        {step === 1 && (
          <div className="wf-step-section">
            <div className="wf-step-header">
              <h2>アイテム提案</h2>
              {suggestSummary && (
                <div className="wf-summary wf-md">
                  <ReactMarkdown>{suggestSummary}</ReactMarkdown>
                </div>
              )}
              {suggestEta > 0 && (
                <p className="wf-eta">🕐 調理 ETA: 約 {suggestEta} 分</p>
              )}
              {itemSelectionLocked && (
                <p className="wf-card-hint">確定済みのアイテムを表示しています。内容を変更するには新しい提案からやり直してください。</p>
              )}
            </div>

            <div className="wf-proposal-grid">
              {proposals.map(item => (
                <div
                  key={item.itemId}
                  className={`wf-proposal-card${removedItemIds.has(item.itemId) ? ' wf-proposal-card--removed' : ''}`}
                >
                  <div className="wf-proposal-header">
                    <div className="wf-proposal-name">{item.name}</div>
                    {!itemSelectionLocked && !removedItemIds.has(item.itemId) ? (
                      <button
                        type="button"
                        className="wf-proposal-remove"
                        onClick={() => setRemovedItemIds(prev => new Set([...prev, item.itemId]))}
                        title="このアイテムを削除"
                        aria-label={`${item.name}を削除`}
                      >
                        ×
                      </button>
                    ) : !itemSelectionLocked ? (
                      <button
                        type="button"
                        className="wf-proposal-restore"
                        onClick={() => setRemovedItemIds(prev => { const next = new Set(prev); next.delete(item.itemId); return next; })}
                        title="削除を取り消す"
                        aria-label={`${item.name}を元に戻す`}
                      >
                        ↩
                      </button>
                    ) : null}
                    {itemSelectionLocked && (
                      <span className="wf-proposal-qty">確定済み</span>
                    )}
                  </div>
                  {!removedItemIds.has(item.itemId) && (
                    <>
                      <div className="wf-proposal-meta">
                        <span className="wf-proposal-qty">×{item.quantity}</span>
                        <span className="wf-proposal-price">¥{Number(item.unitPrice).toFixed(0)}</span>
                      </div>
                      {item.reason && (
                        <div className="wf-proposal-reason wf-md">
                          <ReactMarkdown>{item.reason}</ReactMarkdown>
                        </div>
                      )}
                    </>
                  )}
                </div>
              ))}
            </div>

            <div className="wf-refinement">
              <textarea
                className="wf-textarea wf-textarea--sm"
                value={refinement}
                onChange={e => setRefinement(e.target.value)}
                placeholder="気になる点があれば: 「辛さを抑えて」「もう少し野菜を増やして」など"
                rows={2}
                disabled={loading || itemSelectionLocked}
              />
              <button
                type="button"
                className="wf-btn wf-btn--secondary"
                disabled={loading || itemSelectionLocked || !refinement.trim()}
                onClick={handleRefinement}
              >
                {loading ? '再提案中…' : '↺ フィードバックして再提案'}
              </button>
            </div>

            <div className="wf-actions">
              <button
                type="button"
                className="wf-btn wf-btn--secondary"
                disabled={loading}
                onClick={() => setStep(0)}
              >
                ← 前のステップへ
              </button>
              <button
                type="button"
                className="wf-btn wf-btn--primary"
                disabled={loading || keptProposals.length === 0}
                onClick={handleConfirmItems}
              >
                {loading ? '確定中…' : itemSelectionLocked ? '配送選択へ戻る →' : 'この内容で確定 →'}
              </button>
            </div>
          </div>
        )}

        {/* ── Step 2: Delivery Selection ── */}
        {step === 2 && (
          <div className="wf-step-section">
            <div className="wf-step-header">
              <h2>配送方法を選択</h2>
            </div>

            {/* Shared delivery assessment — same summary for all options, show once */}
            {deliveryOptions[0]?.reason && (
              <div className="wf-delivery-assessment">
                <p className="wf-delivery-assessment-label">📋 delivery-agent アセスメント</p>
                <div className="wf-md">
                  <ReactMarkdown>{deliveryOptions[0].reason}</ReactMarkdown>
                </div>
              </div>
            )}

            <div className="wf-delivery-grid">
              {deliveryOptions.map(opt => (
                <button
                  key={opt.code}
                  type="button"
                  className={`wf-delivery-card${selectedDeliveryCode === opt.code ? ' wf-delivery-card--selected' : ''}`}
                  onClick={() => setSelectedDeliveryCode(opt.code)}
                  disabled={loading}
                >
                  {opt.recommended && (
                    <span className="wf-recommended-badge">推奨</span>
                  )}
                  <div className="wf-delivery-label">{opt.label}</div>
                  <div className="wf-delivery-eta">🕐 {opt.etaMinutes} 分</div>
                  <div className="wf-delivery-fee">¥{Number(opt.fee).toFixed(0)}</div>
                </button>
              ))}
            </div>

            <div className="wf-actions">
              <button
                type="button"
                className="wf-btn wf-btn--secondary"
                disabled={loading}
                onClick={() => setStep(1)}
              >
                ← 前のステップへ
              </button>
              <button
                type="button"
                className="wf-btn wf-btn--primary"
                disabled={loading || !selectedDeliveryCode}
                onClick={handleConfirmDelivery}
              >
                {loading ? '確定中…' : '配送方法を確定 →'}
              </button>
            </div>
          </div>
        )}

        {/* ── Step 3: Payment Approval ── */}
        {step === 3 && (
          <div className="wf-step-section">
            <div className="wf-step-header">
              <h2>注文内容の確認</h2>
            </div>

            <div className="wf-summary-table">
              {draft.items.map(item => (
                <div key={`${item.name}-${item.note}`} className="wf-summary-row">
                  <span>{item.quantity}× {item.name}</span>
                  <span>¥{(Number(item.unitPrice) * item.quantity).toFixed(0)}</span>
                </div>
              ))}
              {deliveryFee > 0 && (
                <div className="wf-summary-row">
                  <span>配送料</span>
                  <span>¥{deliveryFee.toFixed(0)}</span>
                </div>
              )}
              <div className="wf-summary-row wf-summary-row--total">
                <span>合計</span>
                <span>¥{Number(draft.total).toFixed(0)}</span>
              </div>
              {draft.paymentMethod && (
                <div className="wf-summary-row">
                  <span>支払い方法</span>
                  <span>{draft.paymentMethod}</span>
                </div>
              )}
              {draft.etaLabel && (
                <div className="wf-summary-row">
                  <span>ETA</span>
                  <span>{draft.etaLabel}</span>
                </div>
              )}
            </div>

            <div className="wf-actions">
              <button
                type="button"
                className="wf-btn wf-btn--primary"
                disabled={loading}
                onClick={handleConfirmPayment}
              >
                {loading ? '注文確定中…' : '注文を確定する ✓'}
              </button>
            </div>
          </div>
        )}

        {/* ── Step 4: Completion ── */}
        {step === 4 && (
          <div className="wf-card wf-complete-card">
            <div className="wf-complete-icon">✅</div>
            <h2 className="wf-card-title">注文が確定しました！</h2>
            {draft.orderId && (
              <p className="wf-complete-id">注文 ID: {draft.orderId}</p>
            )}
            {draft.etaLabel && (
              <p className="wf-complete-eta">📦 配送 ETA: {draft.etaLabel}</p>
            )}
            <p className="wf-complete-total">合計: ¥{Number(draft.total).toFixed(0)}</p>
            <div className="wf-actions wf-actions--center">
              <button
                type="button"
                className="wf-btn wf-btn--primary"
                onClick={startNewOrder}
              >
                ＋ 新しい注文
              </button>
              <Link href="/home" className="wf-btn wf-btn--secondary">
                ホームへ戻る
              </Link>
            </div>
          </div>
        )}
      </section>
    </main>
  );
}
