package com.nl2sql.auth;

import com.nl2sql.auth.dto.LoginRequest;
import com.nl2sql.auth.dto.LoginResponse;
import com.nl2sql.auth.dto.UserPageRequest;
import com.nl2sql.auth.dto.UserSaveRequest;
import com.nl2sql.auth.dto.UserVO;
import com.nl2sql.auth.service.AuthService;
import com.nl2sql.auth.service.UserService;
import com.nl2sql.audit.service.OpLogService;
import com.nl2sql.common.result.R;
import com.nl2sql.security.SecurityContextUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final OpLogService opLog;

    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        try {
            LoginResponse r = authService.login(req);
            opLog.log(r.getUserId(), "LOGIN", "user", r.getUserId(), req.getUsername(), request.getRemoteAddr(), "SUCCESS");
            return R.ok(r);
        } catch (Exception e) {
            opLog.log(null, "LOGIN", null, null, req.getUsername(), request.getRemoteAddr(), "FAIL: " + e.getMessage());
            throw e;
        }
    }

    // ── 用户管理接口 ──────────────────────────────────────────

    /** 当前登录用户信息（右上角用） */
    @GetMapping("/me")
    public R<UserVO> me() {
        Long uid = SecurityContextUtil.currentUserId();
        return R.ok(userService.currentUser(uid));
    }

    /** 用户列表（分页） */
    @GetMapping("/user/list")
    public R<Map<String, Object>> list(UserPageRequest req) {
        return R.ok(userService.listPage(req));
    }

    /** 用户详情 */
    @GetMapping("/user/{id}")
    public R<UserVO> detail(@PathVariable Long id) {
        return R.ok(userService.detail(id));
    }

    /** 新增 / 编辑用户 */
    @PostMapping("/user/save")
    public R<UserVO> save(@Valid @RequestBody UserSaveRequest req) {
        return R.ok(userService.save(req));
    }

    /** 删除用户 */
    @DeleteMapping("/user/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return R.ok();
    }
}
