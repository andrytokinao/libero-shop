package com.houssen.liberoshop.web.dto;

import com.houssen.liberoshop.entity.UserApp;

import java.util.List;

/**
 * What the Angular guards need to decide what to show, in one call.
 *
 * <p>{@code authorities} is sent alongside the role so the UI can hide an action without
 * hard-coding the role-to-permission mapping a second time on the client. The server
 * remains the one that enforces it; the client only avoids offering what would be refused.
 *
 * @param homePath landing route of the role, so the front end has no role-to-route table
 * @param settings how the shop works, which hides the screens it does not use; null signed out
 */
public record SessionResponse(boolean authenticated,
                              UserResponse user,
                              String roleLabel,
                              String homePath,
                              List<String> authorities,
                              ShopSettingsResponse settings) {

    public static SessionResponse anonymous() {
        return new SessionResponse(false, null, null, "/login", List.of(), null);
    }

    public static SessionResponse of(UserApp user, String roleLabel, String homePath,
                                     List<String> authorities, ShopSettingsResponse settings) {
        return new SessionResponse(true, UserResponse.of(user), roleLabel, homePath, authorities, settings);
    }
}
