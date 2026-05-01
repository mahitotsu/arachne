package com.mahitotsu.arachne.samples.delivery.deliveryservice.infrastructure;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/**
 * SSRF 対策インターセプター。
 *
 * <p>LLM が call_eta_service ツールに渡した URL をレジストリ登録済み許可リストと照合し、
 * 未登録の URL へのアウトバウンドリクエストをブロックする。
 *
 * <p>このインターセプターは {@link HttpExternalEtaGateway} 専用の {@link org.springframework.web.client.RestClient}
 * にのみ適用される。他の RestClient（レジストリ呼び出し等）には影響しない。
 *
 * <p>本番環境では Envoy Egress Gateway や Istio ServiceEntry によるネットワーク層のポリシーが
 * この役割を担う。このクラスは Docker Compose ランタイム向けのアプリ層代替実装である。
 */
@Component
class SsrfGuardInterceptor implements ClientHttpRequestInterceptor {

    private final EtaServiceUrlAllowlist allowlist;

    SsrfGuardInterceptor(EtaServiceUrlAllowlist allowlist) {
        this.allowlist = allowlist;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String url = request.getURI().toString();
        if (!allowlist.isAllowed(url)) {
            throw new HttpClientErrorException(
                    HttpStatus.FORBIDDEN,
                    "SSRF guard: URL はレジストリ許可リストに存在しません: " + url);
        }
        return execution.execute(request, body);
    }
}
