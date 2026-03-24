package io.arachne.samples.conversationbasics;

import org.springframework.stereotype.Component;

import io.arachne.strands.tool.annotation.StrandsTool;

@Component
class SampleReferenceTool {

    @StrandsTool(description = "Use only when the user explicitly asks for the sample reference glossary. This tool is intentionally unrelated to the normal memory prompts and exists so the Bedrock caching demo includes stable tool definitions in the request.")
    @SuppressWarnings("unused")
    String sampleReferenceGlossary() {
        return "sample glossary: azure=blue, verdant=green, crimson=red";
    }
}