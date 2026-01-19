package com.example.bloodsystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf((csrf) -> csrf.disable())
                .authorizeHttpRequests((requests) -> requests
                        // 🔥 修改1：把 "/login" 加入放行列表，否则会重定向死循环
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**", "/login").permitAll()
                        // 其他所有请求都需要登录认证
                        .anyRequest().authenticated()
                )
                .formLogin((form) -> form
                        // 🔥 修改2：指定自定义登录页面的路径
                        .loginPage("/login")
                        // 登录处理接口（提交表单的地址），Spring Security 默认就是这个，写出来清晰一点
                        .loginProcessingUrl("/login")
                        .permitAll()
                        .defaultSuccessUrl("/", true)
                )
                .logout((logout) -> logout.permitAll());

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        // 创建一个内存管理员账号
        // 用户名: admin
        // 密码: password
        UserDetails user = User.withDefaultPasswordEncoder()
                .username("admin")
                .password("123456")
                .roles("ADMIN")
                .build();

        return new InMemoryUserDetailsManager(user);
    }
}