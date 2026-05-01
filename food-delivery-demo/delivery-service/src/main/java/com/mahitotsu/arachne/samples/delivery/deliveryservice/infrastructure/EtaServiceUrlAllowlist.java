package com.mahitotsu.arachne.samples.delivery.deliveryservice.infrastructure;

/**
 * セキュリティ境界: LLM が call_eta_service に渡す URL がレジストリ登録済みのエンドポイントか検証する。
 *
 * <p>本番環境では Envoy / Istio などのサービスメッシュが egress ポリシーとして同等の制御を
 * ネットワーク層で提供する。このインターフェースは Docker Compose ランタイムでサイドカープロキシが
 * 存在しない場合にアプリコード層で同じ保護を提供する。
 */
public interface EtaServiceUrlAllowlist {

    /**
     * 指定した URL がレジストリに登録済みのエンドポイントから派生しているかを返す。
     *
     * @param url LLM が call_eta_service に渡した完全 URL
     * @return 許可リストに含まれる場合 {@code true}
     */
    boolean isAllowed(String url);
}
