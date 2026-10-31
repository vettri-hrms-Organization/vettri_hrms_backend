package com.haodaone.home;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Home page data for the signed-in person. Each module applies its own permission checks. */
@RestController
@RequestMapping("/api/home")
@PreAuthorize("isAuthenticated()")
public class HomeController {

    private final HomeService service;

    public HomeController(HomeService service) {
        this.service = service;
    }

    @GetMapping("/todos")
    public List<HomeService.Todo> todos() { return service.todos(); }

    @GetMapping("/today")
    public HomeService.Today today() { return service.today(); }

    @GetMapping("/setup")
    public HomeService.Setup setup() { return service.setup(); }
}
