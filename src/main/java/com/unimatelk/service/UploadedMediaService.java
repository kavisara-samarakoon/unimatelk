package com.unimatelk.service;

import com.unimatelk.domain.AppUser;
import com.unimatelk.domain.Profile;
import com.unimatelk.repo.AppUserRepository;
import com.unimatelk.repo.ChatMemberRepository;
import com.unimatelk.repo.MessageRepository;
import com.unimatelk.repo.ProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class UploadedMediaService {
    private static final String EXTENSION = "\\.(?i:jpg|jpeg|png|webp)";
    private static final String ID = "[1-9][0-9]{0,18}";
    private static final Pattern CHAT_NAME = Pattern.compile("u" + ID + "-[1-9][0-9]{0,18}" + EXTENSION);
    private static final Pattern PROFILE_NAME = Pattern.compile("(?:profile|cover)-[1-9][0-9]{0,18}" + EXTENSION);
    private static final Pattern LEGACY_PROFILE = Pattern.compile(
            "profile-(" + ID + ")-[1-9][0-9]{0,18}" + EXTENSION);
    private static final Pattern UUID_PROFILE = Pattern.compile(
            "profile_(" + ID + ")_[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}" + EXTENSION);

    private final CurrentUserService current;
    private final ChatMemberRepository members;
    private final MessageRepository messages;
    private final ProfileRepository profiles;
    private final AppUserRepository users;
    private final PrivateImageStorage storage;

    public UploadedMediaService(CurrentUserService current, ChatMemberRepository members,
            MessageRepository messages, ProfileRepository profiles, AppUserRepository users,
            PrivateImageStorage storage) {
        this.current = current;
        this.members = members;
        this.messages = messages;
        this.profiles = profiles;
        this.users = users;
        this.storage = storage;
    }

    public PrivateImageStorage.Image chat(OAuth2User principal, String roomId, String filename) {
        AppUser viewer = current.requireExistingActiveUser(principal);
        long room = positiveId(roomId);
        requireName(CHAT_NAME, filename);
        if (!members.existsByRoomIdAndUserId(room, viewer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        String relative = "chat/" + roomId + "/" + filename;
        String url = "/uploads/" + relative;
        // Compare again in Java: the database may use a case-insensitive collation.
        boolean referenced = messages.findByRoomIdAndTypeAndAttachmentUrl(room, "IMAGE", url).stream()
                .anyMatch(message -> "IMAGE".equals(message.getType()) && url.equals(message.getAttachmentUrl()));
        if (!referenced) throw notFound();
        return storage.read(relative);
    }

    public PrivateImageStorage.Image profile(OAuth2User principal, String userId, String filename) {
        current.requireExistingActiveUser(principal);
        long owner = positiveId(userId);
        requireName(PROFILE_NAME, filename);
        String relative = "profiles/" + userId + "/" + filename;
        requireProfileReference(owner, "/uploads/" + relative);
        return storage.read(relative);
    }

    public PrivateImageStorage.Image legacyProfile(OAuth2User principal, String filename) {
        current.requireExistingActiveUser(principal);
        Matcher matcher = LEGACY_PROFILE.matcher(filename);
        if (!matcher.matches()) {
            matcher = UUID_PROFILE.matcher(filename);
            if (!matcher.matches()) throw notFound();
        }
        long owner = positiveId(matcher.group(1));
        requireProfileReference(owner, "/uploads/" + filename);
        return storage.read(filename);
    }

    private void requireProfileReference(long owner, String url) {
        // PublicProfileController exposes both photos to active authenticated viewers.
        // Its own/admin/matched restrictions apply only to contact details.
        Profile profile = profiles.findByUserId(owner).orElse(null);
        boolean referenced = profile != null && (url.equals(profile.getProfilePhotoPath())
                || url.equals(profile.getCoverPhotoPath()));
        if (!referenced) {
            referenced = users.findById(owner).map(user -> url.equals(user.getPictureUrl())).orElse(false);
        }
        if (!referenced) throw notFound();
    }

    private static long positiveId(String value) {
        if (!value.matches(ID)) throw notFound();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw notFound();
        }
    }

    private static void requireName(Pattern pattern, String filename) {
        if (!pattern.matcher(filename).matches()) throw notFound();
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
