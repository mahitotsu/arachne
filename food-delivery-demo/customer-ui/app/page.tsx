'use client';

import { FormEvent, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';

import { fetchAuthSession } from '../lib/browser-session';

export default function SignInPage() {
  const router = useRouter();
  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [ready, setReady] = useState(false);

  useEffect(() => {
    void fetchAuthSession().then(session => {
      if (session.authenticated) {
        router.replace('/home');
        return;
      }
      setReady(true);
    });
  }, [router]);

  function fill(id: string, pass: string) {
    setLoginId(id);
    setPassword(pass);
    setError('');
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!loginId.trim() || !password || busy) return;
    setBusy(true);
    setError('');
    try {
      const res = await fetch('/api/customer/auth/sign-in', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ loginId: loginId.trim(), password }),
      });
      if (res.status === 401) {
        setError('ログインIDまたはパスワードが違います。');
        return;
      }
      if (!res.ok) throw new Error(`sign-in failed: ${res.status}`);
      await res.json();
      router.push('/home');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'sign-in failed');
    } finally {
      setBusy(false);
    }
  }

  if (!ready) return null;

  return (
    <div className="si-shell">
      <div className="si-brand">
        <div className="si-brand-inner">
          <div className="si-logo">🍜</div>
          <h1 className="si-title">Arachne<br />Kitchen</h1>
          <p className="si-subtitle">single-kitchen · cloud delivery</p>
          <div className="si-arch">
            <p className="si-arch-lead">
              一つの注文の裏で、<br />複数の AI エージェントが協働します。
            </p>
            <ul className="si-service-list">
              <li><span className="si-dot si-dot--customer" />customer-service</li>
              <li><span className="si-dot si-dot--order" />order-service</li>
              <li><span className="si-dot si-dot--menu" />menu-agent</li>
              <li><span className="si-dot si-dot--kitchen" />kitchen-agent</li>
              <li><span className="si-dot si-dot--delivery" />delivery-agent</li>
              <li><span className="si-dot si-dot--payment" />payment-agent</li>
            </ul>
          </div>
        </div>
      </div>

      <div className="si-form-panel">
        <form className="si-form" onSubmit={handleSubmit} noValidate>
          <div className="si-form-header">
            <h2>サインイン</h2>
            <p>デモアカウントを選択するか、フォームで入力してください。</p>
          </div>

          <div className="si-demos">
            <button
              type="button"
              className={`si-demo-btn${loginId === 'demo' ? ' si-demo-btn--active' : ''}`}
              onClick={() => fill('demo', 'demo-pass')}
              disabled={busy}
            >
              <span className="si-demo-icon">👤</span>
              <div className="si-demo-info">
                <strong>demo</strong>
                <span>個人アカウント</span>
              </div>
            </button>
            <button
              type="button"
              className={`si-demo-btn${loginId === 'family' ? ' si-demo-btn--active' : ''}`}
              onClick={() => fill('family', 'family-pass')}
              disabled={busy}
            >
              <span className="si-demo-icon">👨‍👩‍👧</span>
              <div className="si-demo-info">
                <strong>family</strong>
                <span>ファミリーアカウント</span>
              </div>
            </button>
          </div>

          <div className="si-divider"><span>またはフォームで入力</span></div>

          <label className="si-field">
            <span>Login ID</span>
            <input
              type="text"
              value={loginId}
              onChange={e => setLoginId(e.target.value)}
              placeholder="demo"
              autoComplete="username"
              disabled={busy}
            />
          </label>

          <label className="si-field">
            <span>Password</span>
            <input
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              placeholder="demo-pass"
              autoComplete="current-password"
              disabled={busy}
            />
          </label>

          {error && <p className="si-error">⚠ {error}</p>}

          <button
            type="submit"
            className="si-submit"
            disabled={busy || !loginId.trim() || !password}
          >
            {busy ? '接続中...' : 'サインイン →'}
          </button>
        </form>
      </div>
    </div>
  );
}