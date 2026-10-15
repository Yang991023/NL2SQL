package com.nl2sql.auth.service;

import com.nl2sql.auth.dto.UserPageRequest;
import com.nl2sql.auth.dto.UserSaveRequest;
import com.nl2sql.auth.dto.UserVO;
import com.nl2sql.auth.entity.User;
import com.nl2sql.auth.repo.UserRepository;
import com.nl2sql.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;

    /** 列表（分页 + 模糊搜索） */
    public Map<String, Object> listPage(UserPageRequest req) {
        Page<User> page = userRepo.findPage(
                req.getKeyword(),
                PageRequest.of(req.getPage() - 1, req.getSize(), Sort.by("id").descending())
        );
        List<UserVO> records = page.getContent().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return Map.of(
                "list", records,
                "total", page.getTotalElements(),
                "page", req.getPage(),
                "size", req.getSize()
        );
    }

    /** 详情 */
    public UserVO detail(Long id) {
        return toVO(userRepo.findById(id)
                .orElseThrow(() -> new BizException(404, "用户不存在")));
    }

    /** 新增 / 更新（密码可留空） */
    @Transactional
    public UserVO save(UserSaveRequest req) {
        User user;
        if (req.getId() != null) {
            // 编辑
            user = userRepo.findById(req.getId())
                    .orElseThrow(() -> new BizException(404, "用户不存在"));
            // 检查账号重复（排除自己）
            if (userRepo.findByUsernameAndIdNot(req.getUsername(), req.getId()).isPresent()) {
                throw new BizException(400, "账号已存在");
            }
            user.setUsername(req.getUsername());
            user.setNickname(req.getNickname());
            user.setEmail(req.getEmail());
            user.setPhone(req.getPhone());
            if (req.getEnabled() != null) user.setEnabled(req.getEnabled());
            if (req.getPassword() != null && !req.getPassword().isBlank()) {
                user.setPassword(encoder.encode(req.getPassword()));
            }
        } else {
            // 新增
            if (userRepo.findByUsername(req.getUsername()).isPresent()) {
                throw new BizException(400, "账号已存在");
            }
            if (req.getPassword() == null || req.getPassword().isBlank()) {
                throw new BizException(400, "新增用户密码不能为空");
            }
            user = new User();
            user.setUsername(req.getUsername());
            user.setPassword(encoder.encode(req.getPassword()));
            user.setNickname(req.getNickname());
            user.setEmail(req.getEmail());
            user.setPhone(req.getPhone());
            user.setEnabled(req.getEnabled() != null ? req.getEnabled() : true);
        }
        return toVO(userRepo.save(user));
    }

    /** 删除 */
    @Transactional
    public void delete(Long id) {
        if (!userRepo.existsById(id)) throw new BizException(404, "用户不存在");
        userRepo.deleteById(id);
    }

    /** 取当前登录用户的简要信息（用于右上角） */
    public UserVO currentUser(Long userId) {
        return toVO(userRepo.findById(userId)
                .orElseThrow(() -> new BizException(404, "用户不存在")));
    }

    private UserVO toVO(User u) {
        UserVO v = new UserVO();
        v.setId(u.getId());
        v.setUsername(u.getUsername());
        v.setNickname(u.getNickname());
        v.setEmail(u.getEmail());
        v.setPhone(u.getPhone());
        v.setEnabled(u.getEnabled());
        v.setCreatedAt(u.getCreatedAt());
        v.setUpdatedAt(u.getUpdatedAt());
        return v;
    }
}
