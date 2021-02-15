package ru.runa.wfe.chat.socket;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.websocket.CloseReason;
import javax.websocket.Session;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.runa.wfe.chat.dto.broadcast.ErrorMessageBroadcast;
import ru.runa.wfe.chat.dto.broadcast.MessageBroadcast;
import ru.runa.wfe.chat.sender.MessageSender;
import ru.runa.wfe.chat.utils.ChatSessionUtils;
import ru.runa.wfe.user.User;

@CommonsLog
@Component
public class ChatSessionHandler {
    private final ConcurrentHashMap<Long, Session> sessions = new ConcurrentHashMap<>(256);
    private final MessageSender messageSender;
    private final ObjectMapper chatObjectMapper;

    @Autowired
    public ChatSessionHandler(@Qualifier("sessionMessageSender") MessageSender messageSender,
                              ObjectMapper chatObjectMapper) {
        this.messageSender = messageSender;
        this.chatObjectMapper = chatObjectMapper;
    }

    public void addSession(Session session) {
        log.warn("addSession method, session ID = " + session.getId());
        User user = ChatSessionUtils.getUser(session);
        Long userId = user.getActor().getId();
        Session replacedSession = sessions.replace(userId, session);

        if (replacedSession != null) {
            log.warn("addSession method, if statement, replace/close session");
            try {
                CloseReason closeReason = new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY,
                        "Replace " + user.getName() + "'s session");
                replacedSession.close(closeReason);
            } catch (IOException e) {
                log.error("An error occurred while closing " + user.getName() + "'s session");
            }
            log.warn("Replace " + user.getName() + "'s session");
        } else {
            log.warn("addSession method, if statement, replacedSession = null");
            sessions.put(userId, session);
        }

        log.warn("addSession method, sessions: " + sessions);
    }

    public void removeSession(Session session) {
        log.warn("removeSession method");
        Long userId = ChatSessionUtils.getUser(session).getActor().getId();
        sessions.remove(userId, session);
    }

    public void sendToSession(Session session, String message) throws IOException {
        log.warn("sendToSession method");
        session.getBasicRemote().sendText(message);
    }

    public void sendMessage(MessageBroadcast dto) throws IOException {
        log.warn("sendMessage(MessageBroadcast dto) method");
        sendMessage(Collections.emptySet(), dto);
    }

    public void sendMessage(Collection<Long> recipientIds, MessageBroadcast dto) throws IOException {
        log.warn("sendMessage(Collection<Long> recipientIds, MessageBroadcast dto) method");
        for (Long id : recipientIds) {
            messageSender.handleMessage(dto, Optional.ofNullable(sessions.get(id)));
        }
    }

    public void messageError(Session session, String message) {
        ErrorMessageBroadcast errorDto = new ErrorMessageBroadcast(message);
        try {
            sendToSession(session, chatObjectMapper.writeValueAsString(errorDto));
        } catch (IOException e) {
            log.error(e);
        }
    }
}
