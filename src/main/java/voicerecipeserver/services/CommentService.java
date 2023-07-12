package voicerecipeserver.services;

import org.springframework.http.ResponseEntity;
import voicerecipeserver.model.dto.CommentDto;
import voicerecipeserver.model.dto.IdDto;
import voicerecipeserver.model.exceptions.BadRequestException;
import voicerecipeserver.model.exceptions.NotFoundException;

public interface CommentService {
    ResponseEntity<IdDto> postComment(CommentDto commentDto) throws NotFoundException, BadRequestException;

    ResponseEntity<IdDto> updateComment(CommentDto commentDto) throws NotFoundException, BadRequestException;

    ResponseEntity<Void> deleteComment(Long commentId);
}