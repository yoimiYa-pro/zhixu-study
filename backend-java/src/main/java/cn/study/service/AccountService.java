package cn.study.service;

import cn.study.repository.Db;
import cn.study.security.SingleUser;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {
    private final Db db;
    private final AuthService auth;
    private final PasswordEncoder passwords;
    public AccountService(Db db,AuthService auth,PasswordEncoder passwords) { this.db=db;this.auth=auth;this.passwords=passwords; }
    public Map<String,Object> rename(String username,String password,String ip) {
        String name=username.strip();
        if (name.isEmpty() || name.codePoints().anyMatch(Character::isISOControl)) throw bad("用户名不能包含控制字符或仅有空格");
        var user=auth.verifyPassword(password,ip);
        if (db.update("update users set username=? where id=? and password_hash=?",name,SingleUser.ID,user.get("passwordHash"))!=1) throw bad("账号信息已更新，请重新登录后重试");
        return auth.me();
    }
    public void changePassword(String current,String next,String ip) {
        if (next.length()<8 || next.getBytes(StandardCharsets.UTF_8).length>72) throw bad("新密码需至少 8 个字符，且不超过 72 字节");
        var user=auth.verifyPassword(current,ip);
        if (passwords.matches(next,user.get("passwordHash").toString())) throw bad("新密码不能与当前密码相同");
        if (db.update("update users set password_hash=?,credential_version=credential_version+1 where id=? and password_hash=?",passwords.encode(next),SingleUser.ID,user.get("passwordHash"))!=1) throw bad("账号信息已更新，请重新登录后重试");
    }
    public Map<String,Object> uploadAvatar(MultipartFile file) {
        if (file.isEmpty() || file.getSize()>512*1024) throw bad("头像文件需小于 512 KB");
        try (var input=ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
            var readers=ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw bad("请上传有效的 PNG 或 JPG 图片");
            var reader=readers.next();
            try {
                String format=reader.getFormatName();
                if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg")) throw bad("请上传 PNG 或 JPG 图片");
                reader.setInput(input,true,true);
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if (width<1 || height<1 || width>4096 || height>4096) throw bad("头像尺寸不能超过 4096 × 4096");
                BufferedImage source=reader.read(0);
                double scale=Math.min(1,256.0/Math.max(width,height));
                var image=new BufferedImage(Math.max(1,(int)(width*scale)),Math.max(1,(int)(height*scale)),BufferedImage.TYPE_INT_ARGB);
                var graphics=image.createGraphics();
                try { graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);graphics.drawImage(source,0,0,image.getWidth(),image.getHeight(),null); }
                finally { graphics.dispose(); }
                var output=new ByteArrayOutputStream();
                ImageIO.write(image,"png",output);
                db.update("update users set avatar=? where id=?",output.toByteArray(),SingleUser.ID);
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException error) { throw bad("图片无法读取，请重新选择 PNG 或 JPG 图片"); }
        return auth.me();
    }
    public Map<String,Object> removeAvatar() { db.update("update users set avatar=null where id=?",SingleUser.ID);return auth.me(); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
