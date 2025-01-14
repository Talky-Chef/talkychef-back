package voicerecipeserver.services;

import org.springframework.http.ResponseEntity;
import voicerecipeserver.model.dto.IdDto;
import voicerecipeserver.model.exceptions.InvalidMediaTypeException;
import voicerecipeserver.model.exceptions.NotFoundException;

public interface MediaService {
    ResponseEntity<byte[]> getMediaById(Long id) throws NotFoundException;

    ResponseEntity<IdDto> addMedia(String contentTypeHeader, byte[] data) throws InvalidMediaTypeException;
}
