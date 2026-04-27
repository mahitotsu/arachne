package com.mahitotsu.arachne.samples.delivery.supportservice.domain;

import java.util.List;

public record FaqEntry(String id, String question, String answer, List<String> tags) {
}