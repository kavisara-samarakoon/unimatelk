package com.unimatelk.api;

import com.unimatelk.service.UploadedMediaService;
import com.unimatelk.service.PrivateImageStorage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.HEAD;

@RestController
@RequestMapping("/uploads")
public class UploadedMediaController {
    private final UploadedMediaService media;

    public UploadedMediaController(UploadedMediaService media) {
        this.media = media;
    }

    @RequestMapping(value = "/chat/{roomId}/{filename}", method = {GET, HEAD})
    public ResponseEntity<byte[]> chat(@AuthenticationPrincipal OAuth2User principal,
            @PathVariable String roomId, @PathVariable String filename, HttpServletRequest request) {
        return image(media.chat(principal, roomId, filename), request);
    }

    @RequestMapping(value = "/profiles/{userId}/{filename}", method = {GET, HEAD})
    public ResponseEntity<byte[]> profile(@AuthenticationPrincipal OAuth2User principal,
            @PathVariable String userId, @PathVariable String filename, HttpServletRequest request) {
        return image(media.profile(principal, userId, filename), request);
    }

    @RequestMapping(value = "/{filename}", method = {GET, HEAD})
    public ResponseEntity<byte[]> legacyProfile(@AuthenticationPrincipal OAuth2User principal,
            @PathVariable String filename, HttpServletRequest request) {
        return image(media.legacyProfile(principal, filename), request);
    }

    private ResponseEntity<byte[]> image(PrivateImageStorage.Image image, HttpServletRequest request) {
        // Returning bytes, rather than a Resource, deliberately avoids automatic range handling.
        // Conditional headers never bypass authorization or produce a cached 304 response.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(image.contentType()).contentLength(image.bytes().length)
                .body("HEAD".equals(request.getMethod()) ? null : image.bytes());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Void> rejected(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff").build();
    }
}
