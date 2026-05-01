package com.mahitotsu.arachne.samples.delivery.deliveryservice.infrastructure;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.mahitotsu.arachne.samples.delivery.deliveryservice.config.DeliveryServiceProperties;
import com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.RegistryDiscoverRequestPayload;
import com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.RegistryDiscoverResponsePayload;
import com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.RegistryServiceMatchPayload;

/**
 * registry-service から取得したエンドポイント一覧を TTL キャッシュで保持し、
 * call_eta_service に渡された URL がその一覧に含まれるかを検証する。
 *
 * <p>TTL 期間内はキャッシュを使用するため、新規登録サービスが許可リストに反映されるまで
 * 最大 TTL 秒かかる可能性がある（安全側に倒れる動作 — 正規サービスが一時的にブロックされるが
 * セキュリティホールは生じない）。
 *
 * <p>このキャッシュはルーティング用ではなくセキュリティ検証用であるため TTL による若干の
 * 遅延は許容される。
 */
@Component
class RegistryBackedEtaServiceUrlAllowlist implements EtaServiceUrlAllowlist {

    private static final long TTL_MS = 60_000L;
    private static final String ALLOWLIST_QUERY = "外部ETAを提供するサービスは？";

    private final RestClient restClient;

    private volatile Set<String> allowedPrefixes = Set.of();
    private volatile long refreshedAt = 0L;

    RegistryBackedEtaServiceUrlAllowlist(
            RestClient.Builder restClientBuilder,
            DeliveryServiceProperties properties) {
        String registryBaseUrl = properties.getRegistry().getBaseUrl();
        this.restClient = registryBaseUrl.isBlank()
                ? null
                : restClientBuilder.baseUrl(registryBaseUrl).build();
    }

    @Override
    public boolean isAllowed(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        refreshIfStale();
        Set<String> prefixes = this.allowedPrefixes;
        return prefixes.stream().anyMatch(url::startsWith);
    }

    // synchronized で同時リフレッシュを防ぐ。ロック競合は稀（TTL は 60 秒）。
    private synchronized void refreshIfStale() {
        long now = System.currentTimeMillis();
        if (now - refreshedAt < TTL_MS) {
            return;
        }
        if (restClient == null) {
            allowedPrefixes = Set.of();
            refreshedAt = now;
            return;
        }
        try {
            // availableOnly=false: 一時停止中のサービスも正規登録済みとして許可する
            RegistryDiscoverResponsePayload response = restClient.post()
                    .uri("/registry/discover")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new RegistryDiscoverRequestPayload(ALLOWLIST_QUERY, false))
                    .retrieve()
                    .body(RegistryDiscoverResponsePayload.class);
            if (response != null && response.matches() != null) {
                allowedPrefixes = response.matches().stream()
                        .filter(Objects::nonNull)
                        .map(RegistryServiceMatchPayload::endpoint)
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toUnmodifiableSet());
            }
            refreshedAt = now;
        } catch (Exception ignored) {
            // レジストリ呼び出し失敗時はキャッシュを維持する（既存の許可リストを保持し
            // 全 ETA 呼び出しをブロックしない）
            refreshedAt = now;
        }
    }
}
