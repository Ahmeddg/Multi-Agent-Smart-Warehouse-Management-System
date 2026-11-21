package com.warehouse.communication;

import jade.core.AID;
import jade.lang.acl.ACLMessage;

import java.util.Objects;

public final class MessageFactory {
    private MessageFactory() {
    }

    public static ACLMessage request(AID receiver,
                                     String conversationId,
                                     String content) {
        return message(ACLMessage.REQUEST, receiver, conversationId, content);
    }

    public static ACLMessage inform(AID receiver,
                                    String conversationId,
                                    String content) {
        return message(ACLMessage.INFORM, receiver, conversationId, content);
    }

    public static ACLMessage message(int performative,
                                     AID receiver,
                                     String conversationId,
                                     String content) {
        Objects.requireNonNull(receiver);

        ACLMessage message = new ACLMessage(performative);
        message.addReceiver(receiver);
        message.setConversationId(conversationId);
        message.setLanguage("JSON");
        message.setContent(content == null ? "{}" : content);
        return message;
    }
}
