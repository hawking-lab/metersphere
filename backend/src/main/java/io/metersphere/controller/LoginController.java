package io.metersphere.controller;

import io.metersphere.base.domain.UserRole;
import io.metersphere.controller.request.LoginRequest;
import io.metersphere.dto.UserDTO;
import io.metersphere.i18n.Translator;
import io.metersphere.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.subject.Subject;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

import static io.metersphere.commons.constants.SessionConstants.ATTR_USER;

@RestController
@RequestMapping
public class LoginController {

    @Resource
    private UserService userService;

    @GetMapping(value = "/isLogin")
    public ResultHolder isLogin() {
        if (SecurityUtils.getSubject().isAuthenticated()) {
            return ResultHolder.success(LocaleContextHolder.getLocale());
        }
        return ResultHolder.error("");
    }

    @PostMapping(value = "/signin")
    public ResultHolder login(@RequestBody LoginRequest request) {
        String msg;
        String username = StringUtils.trim(request.getUsername());
        String password = StringUtils.trim(request.getPassword());
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            return ResultHolder.error("user or password can't be null");
        }

        UsernamePasswordToken token = new UsernamePasswordToken(username, password);
        Subject subject = SecurityUtils.getSubject();

        try {
            subject.login(token);
            if (subject.isAuthenticated()) {
                UserDTO user = (UserDTO) subject.getSession().getAttribute(ATTR_USER);
                // 自动选中组织，工作空间
                if (StringUtils.isEmpty(user.getLastOrganizationId())) {
                    List<UserRole> userRoles = user.getUserRoles();
                    List<UserRole> test = userRoles.stream().filter(ur -> ur.getRoleId().startsWith("test")).collect(Collectors.toList());
                    List<UserRole> org = userRoles.stream().filter(ur -> ur.getRoleId().startsWith("org")).collect(Collectors.toList());
                    if (test.size() > 0) {
                        String wsId = test.get(0).getSourceId();
                        userService.switchUserRole("workspace", wsId);
                    } else if (org.size() > 0) {
                        String orgId = org.get(0).getSourceId();
                        userService.switchUserRole("organization", orgId);
                    }
                }
                // 返回 userDTO
                return ResultHolder.success(subject.getSession().getAttribute("user"));
            } else {
                return ResultHolder.error(Translator.get("login_fail"));
            }
        } catch (ExcessiveAttemptsException e) {
            msg = Translator.get("excessive_attempts");
        } catch (LockedAccountException e) {
            msg = Translator.get("user_locked");
        } catch (DisabledAccountException e) {
            msg = Translator.get("user_has_been_disabled");
        } catch (ExpiredCredentialsException e) {
            msg = Translator.get("user_expires");
        } catch (AuthenticationException e) {
            msg = e.getMessage();
        } catch (UnauthorizedException e) {
            msg = Translator.get("not_authorized") + e.getMessage();
        }
        return ResultHolder.error(msg);
    }

    @GetMapping(value = "/signout")
    public ResultHolder logout() {
        SecurityUtils.getSubject().logout();
        return ResultHolder.success("");
    }

    /*Get default language*/
    @GetMapping(value = "/language")
    public String getDefaultLanguage() {
        return userService.getDefaultLanguage();
    }


}
