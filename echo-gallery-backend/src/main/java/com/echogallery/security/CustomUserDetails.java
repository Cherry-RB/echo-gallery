package com.echogallery.security;

import com.echogallery.user.User; // 假設這是你原本的 User Entity
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class CustomUserDetails implements UserDetails {
    private final User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    public Long getId() {
        return user.getId(); // 讓 SecurityContext 可以直接抓到 userId 💡
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 暫時給予預設 ROLE_USER 權限，後續可從資料庫動態讀取
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail(); // 通常用 Email 或是 帳號 作為唯一標識
    }
}
