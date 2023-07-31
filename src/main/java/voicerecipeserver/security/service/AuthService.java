package voicerecipeserver.security.service;

import lombok.NonNull;
import voicerecipeserver.model.exceptions.AuthException;
import voicerecipeserver.model.exceptions.NotFoundException;
import voicerecipeserver.security.dto.JwtRequest;
import voicerecipeserver.security.dto.JwtResponse;

public interface AuthService {

    JwtResponse login(@NonNull JwtRequest authRequest) throws AuthException, NotFoundException;

    JwtResponse getAccessToken(@NonNull String refreshToken) throws AuthException, NotFoundException;

    JwtResponse refresh(@NonNull String refreshToken) throws AuthException, NotFoundException;


}