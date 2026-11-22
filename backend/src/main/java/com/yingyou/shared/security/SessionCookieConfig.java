package com.yingyou.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * 访客与账号身份都由 HttpOnly 会话 Cookie 维持，浏览器脚本无法读取。
 */
@Configuration
public class SessionCookieConfig {

    @Bean
    DefaultCookieSerializer sessionCookieSerializer(@Value("${app.session.cookie-secure:false}") boolean secure) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("YINGYOU_SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setUseSecureCookie(secure);
        return serializer;
    }
}
