package com.foodrush.app.model;

import java.util.HashMap;
import java.util.Map;

/** One message in an order's customer-rider chat, stored in orders/{id}/messages. */
public class ChatMessage {
    public final String id;
    public final String fromUid;
    public final String fromName;
    public final String text;
    public final long sentAt;

    public ChatMessage(String id, String fromUid, String fromName, String text, long sentAt) {
        this.id = id;
        this.fromUid = fromUid;
        this.fromName = fromName;
        this.text = text;
        this.sentAt = sentAt;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("fromUid", fromUid);
        m.put("fromName", fromName);
        m.put("text", text);
        m.put("sentAt", sentAt);
        return m;
    }

    public static ChatMessage from(String id, Map<String, Object> m) {
        return new ChatMessage(id, Fields.text(m, "fromUid"), Fields.text(m, "fromName"), Fields.text(m, "text"),
                Fields.number(m, "sentAt"));
    }
}
