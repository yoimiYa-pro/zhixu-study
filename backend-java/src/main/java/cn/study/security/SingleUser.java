package cn.study.security;

import java.util.UUID;
import jakarta.annotation.PostConstruct;
import cn.study.repository.Db;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SingleUser {
    public static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final Db db;
    private final String username, hash;
    public SingleUser(Db db, @Value("${auth.username}") String username, @Value("${auth.password-hash}") String hash) {
        this.db = db; this.username = username; this.hash = hash;
    }
    @PostConstruct void initialize() {
        if (username.isBlank() || !hash.matches("\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}"))
            throw new IllegalStateException("Configure ADMIN_USERNAME and a valid ADMIN_PASSWORD_HASH in .env");
        db.update("insert into users(id,username,password_hash) values (?,?,?) on conflict(id) do nothing", ID, username, hash);
    }
}
