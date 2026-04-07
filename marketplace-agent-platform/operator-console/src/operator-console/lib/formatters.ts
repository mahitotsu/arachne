import type { CaseDetailView, CaseListItem } from '../../types';

const labelFormatter = new Intl.DisplayNames(['en'], { type: 'region', fallback: 'none' });

export function formatLabel(value: string | null | undefined): string {
  if (!value) {
    return 'Unknown';
  }

  return value
    .toLowerCase()
    .split('_')
    .filter(Boolean)
    .map((part) => part[0]?.toUpperCase() + part.slice(1))
    .join(' ');
}

export function formatCurrency(amount: number, currency: string): string {
  try {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency,
      maximumFractionDigits: 2,
    }).format(amount);
  } catch {
    return `${amount.toFixed(2)} ${currency}`;
  }
}

export function formatDate(value: string | null | undefined): string {
  if (!value) {
    return 'Pending';
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(parsed);
}

export function formatRelativeTime(value: string): string {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  const diffMs = parsed.getTime() - Date.now();
  const diffMinutes = Math.round(diffMs / 60000);

  if (Math.abs(diffMinutes) < 60) {
    return `${Math.abs(diffMinutes)}m ${diffMinutes <= 0 ? 'ago' : 'from now'}`;
  }

  const diffHours = Math.round(diffMinutes / 60);
  if (Math.abs(diffHours) < 24) {
    return `${Math.abs(diffHours)}h ${diffHours <= 0 ? 'ago' : 'from now'}`;
  }

  const diffDays = Math.round(diffHours / 24);
  return `${Math.abs(diffDays)}d ${diffDays <= 0 ? 'ago' : 'from now'}`;
}

export function buildCaseHeadline(caseItem: Pick<CaseListItem, 'caseType' | 'orderId'> | Pick<CaseDetailView, 'caseType' | 'orderId'>): string {
  const caseType = formatLabel(caseItem.caseType);
  return `${caseType} for ${caseItem.orderId}`;
}

export function countryHint(currency: string): string {
  const normalized = currency?.toUpperCase();
  if (normalized === 'USD') {
    return 'US';
  }
  if (normalized === 'EUR') {
    return 'EU';
  }
  if (normalized === 'JPY') {
    return 'JP';
  }

  return normalized;
}

export function formatCurrencyContext(currency: string): string {
  const region = countryHint(currency);
  const regionName = region.length === 2 ? labelFormatter.of(region) : region;
  return regionName ? `${currency} settlement rail in ${regionName}` : currency;
}
