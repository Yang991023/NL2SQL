package com.nl2sql.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserSaveRequest {

    /** 新增时必须，编辑时传 id 即可（密码可留空表示不修改） */
    private Long id;

    @NotBlank(message = "账号不能为空")
    private String username;

    /** 编辑时留空则不修改密码 */
    private String password;

    private String nickname;

    private String email;

    private String phone;

    /** 默认为 true */
    private Boolean enabled = true;
}
