package com.mahitotsu.arachne.samples.delivery.supportservice.domain;

import java.util.Objects;

public record HandoffInstruction(String target, String message) {

    public static HandoffInstruction fromMessage(String message) {
        String lowered = Objects.requireNonNullElse(message, "").toLowerCase();
        if (lowered.contains("注文")
                && (lowered.contains("変更") || lowered.contains("再注文") || lowered.contains("キャンセル") || lowered.contains("したい"))) {
            return new HandoffInstruction("order", "注文内容の変更や再注文は order-service の注文ワークフローへ引き継ぎます。");
        }
        return new HandoffInstruction("", "");
    }
}