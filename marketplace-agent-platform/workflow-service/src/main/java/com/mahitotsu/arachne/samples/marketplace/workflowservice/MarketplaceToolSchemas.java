package com.mahitotsu.arachne.samples.marketplace.workflowservice;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class MarketplaceToolSchemas {

    private MarketplaceToolSchemas() {
    }

    static ObjectNode permissiveObjectSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", true);
        return schema;
    }
}