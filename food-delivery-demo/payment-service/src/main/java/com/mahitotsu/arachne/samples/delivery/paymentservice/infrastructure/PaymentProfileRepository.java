package com.mahitotsu.arachne.samples.delivery.paymentservice.infrastructure;

import static com.mahitotsu.arachne.samples.delivery.paymentservice.domain.PaymentTypes.*;

import org.springframework.stereotype.Component;

@Component
public class PaymentProfileRepository {

    public PaymentProfile profileFor(PaymentInstructionInput instruction) {
        if (instruction != null && instruction.requestedMethod() != null) {
            return switch (instruction.requestedMethod()) {
                case APPLE_PAY -> new PaymentProfile("apple-pay", "Apple Pay", "メインの iPhone のウォレットに登録済みです。");
                case CASH_ON_DELIVERY -> new PaymentProfile("cash", "代金引換", "現金払いは処理に少し時間がかかりますが、ご利用いただけます。");
                case SAVED_CARD -> new PaymentProfile("card-default", "登録済み Visa（下4桁: 2048）", "デフォルトカードはワンタップ確認済みです。");
            };
        }
        String message = instruction == null ? null : instruction.rawMessage();
        if (message != null && (message.toLowerCase().contains("apple") || message.contains("アップル"))) {
            return new PaymentProfile("apple-pay", "Apple Pay", "メインの iPhone のウォレットに登録済みです。");
        }
        if (message != null && message.contains("現金")) {
            return new PaymentProfile("cash", "代金引換", "現金払いは処理に少し時間がかかりますが、ご利用いただけます。");
        }
        return new PaymentProfile("card-default", "登録済み Visa（下4桁: 2048）", "デフォルトカードはワンタップ確認済みです。");
    }

    public String summarize(PaymentProfile profile, boolean charged) {
        if (charged) {
            return profile.methodLabel() + " で請求を完了しました。" + profile.note();
        }
        return profile.methodLabel() + " を選択しました。" + profile.note();
    }
}