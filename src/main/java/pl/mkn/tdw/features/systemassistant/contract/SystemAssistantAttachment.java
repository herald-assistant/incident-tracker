package pl.mkn.tdw.features.systemassistant.contract;

/** Durable metadata; binary content stays in the conversation's run directory. */
public record SystemAssistantAttachment(String id, String name, String mimeType, long size, String messageId) { }
