package com.expensewise.ai.mapper;

import com.expensewise.ai.dto.AiConversationResponse;
import com.expensewise.ai.dto.AiConversationSummaryResponse;
import com.expensewise.ai.dto.AiMessageResponse;
import com.expensewise.ai.entity.AiConversation;
import com.expensewise.ai.entity.AiMessage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AiMapper {

    AiConversationSummaryResponse toSummaryResponse(AiConversation conversation);

    AiMessageResponse toMessageResponse(AiMessage message);

    @Mapping(target = "id", source = "conversation.id")
    @Mapping(target = "title", source = "conversation.title")
    @Mapping(target = "createdAt", source = "conversation.createdAt")
    AiConversationResponse toConversationResponse(AiConversation conversation, List<AiMessageResponse> messages);
}
