package ru.yandex.practicum.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.List;

@ConfigurationProperties(prefix = "app.security")
public class GatewaySecurityProperties {

    private final List<User> users;

    @ConstructorBinding
    public GatewaySecurityProperties(List<User> users) {
        this.users = users;
    }

    public List<User> getUsers() {
        return users;
    }

    public static class User {
        private String username;
        private String password;
        private List<String> roles;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }
    }
}
