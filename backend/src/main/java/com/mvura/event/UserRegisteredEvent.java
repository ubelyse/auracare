package com.mvura.event;

import com.mvura.model.User;
import lombok.Getter;

@Getter
public class UserRegisteredEvent {
    private final User user;
    private final String verificationToken;

    public UserRegisteredEvent(User user, String verificationToken) {
        this.user = user;
        this.verificationToken = verificationToken;
    }
}