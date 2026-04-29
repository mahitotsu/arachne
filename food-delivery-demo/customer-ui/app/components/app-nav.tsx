'use client';

import Link from 'next/link';
import { ReactNode } from 'react';

interface AppNavProps {
  center?: ReactNode;
  right?: ReactNode;
}

export default function AppNav({ center, right }: AppNavProps) {
  return (
    <nav className="h-nav">
      <Link href="/home" className="h-nav-brand">
        <span>👻</span>
        <span className="h-nav-name">Arachne Kitchen</span>
      </Link>
      {center
        ? <div className="h-nav-center">{center}</div>
        : <div className="h-nav-links">
            <Link href="/home" className="h-nav-link">ホーム</Link>
            <Link href="/support" className="h-nav-link">サポート</Link>
            <Link href="/agents" className="h-nav-link">エージェント</Link>
          </div>
      }
      <div className="h-nav-right">{right ?? null}</div>
    </nav>
  );
}
