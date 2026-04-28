import { NextRequest, NextResponse } from 'next/server';

import { buildProxyResponse, getSessionOrUnauthorized } from '../../../../lib/api-proxy';
import { registryServiceOrigin } from '../../../../lib/service-origins';

type RouteContext = { params: Promise<{ serviceName: string }> };
type ServiceEntry = { serviceName: string; endpoint: string };

export async function GET(request: NextRequest, context: RouteContext) {
  const lookup = getSessionOrUnauthorized(request);
  if ('response' in lookup) return lookup.response;

  const { serviceName } = await context.params;

  const registryRes = await fetch(`${registryServiceOrigin}/registry/services`, {
    headers: { Authorization: `Bearer ${lookup.session.accessToken}` },
    cache: 'no-store',
  });
  if (!registryRes.ok) {
    return NextResponse.json({ error: 'レジストリへのアクセスに失敗しました' }, { status: 502 });
  }

  const services = (await registryRes.json()) as ServiceEntry[];
  const service = services.find(s => s.serviceName === serviceName);
  if (!service) {
    return NextResponse.json({ error: 'サービスが見つかりません' }, { status: 404 });
  }

  let apiDocsRes: Response;
  let text: string;
  try {
    apiDocsRes = await fetch(`${service.endpoint}/v3/api-docs`, { cache: 'no-store' });
    text = await apiDocsRes.text();
  } catch {
    return NextResponse.json({ error: 'サービスに接続できません' }, { status: 503 });
  }

  return buildProxyResponse(apiDocsRes, text);
}
