import { ReactNode } from 'react';

interface PageHeaderProps {
  icon: string;
  title: string;
  lead?: string;
  right?: ReactNode;
}

export default function PageHeader({ icon, title, lead, right }: PageHeaderProps) {
  return (
    <header className="pg-header">
      <div className="pg-header-left">
        <div className="pg-header-title-row">
          <span className="pg-header-icon" aria-hidden="true">{icon}</span>
          <h1 className="pg-header-title">{title}</h1>
        </div>
        {lead && <p className="pg-header-lead">{lead}</p>}
      </div>
      {right && <div className="pg-header-right">{right}</div>}
    </header>
  );
}
