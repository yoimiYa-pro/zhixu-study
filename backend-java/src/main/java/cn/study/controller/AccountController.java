package cn.study.controller;

import cn.study.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    public record Rename(@NotBlank @Size(max=100) String username,@NotBlank @Size(max=72) String currentPassword) {}
    public record PasswordChange(@NotBlank @Size(max=72) String currentPassword,@NotBlank @Size(min=8,max=72) String newPassword) {}
    private final AccountService accounts;
    public AccountController(AccountService accounts) { this.accounts=accounts; }
    @PatchMapping(value="/profile",consumes=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,Object> rename(@Valid @RequestBody Rename input,HttpServletRequest request) { return accounts.rename(input.username(),input.currentPassword(),request.getRemoteAddr()); }
    @PostMapping(value="/password",consumes=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,Boolean> password(@Valid @RequestBody PasswordChange input,HttpServletRequest request) {
        accounts.changePassword(input.currentPassword(),input.newPassword(),request.getRemoteAddr());
        return Map.of("success",true);
    }
    @PostMapping(value="/avatar",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> avatar(@RequestParam("file") MultipartFile file) { return accounts.uploadAvatar(file); }
    @DeleteMapping("/avatar") public Map<String,Object> removeAvatar() { return accounts.removeAvatar(); }
}
