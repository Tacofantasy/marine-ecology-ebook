package com.marine.ecobook.common.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 单 JAR 交付时支持前端路由刷新，API 与静态文件仍由原处理器负责。 */
@Controller
@ConditionalOnResource(resources = "classpath:/static/index.html")
public class SpaController {
    @GetMapping({"/", "/{path:^(?!api$|uploads$|assets$)[^.]+}", "/admin/**", "/ebooks/**"})
    public String index() {
        return "forward:/index.html";
    }
}
