package io.github.ih0rd.examples.controller;


import io.github.ih0rd.examples.contracts.LibrariesApi;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/libs")
public class LibrariesController {

    private final LibrariesApi libs;

    public LibrariesController(LibrariesApi libs) {
        this.libs = libs;
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users(@RequestParam int n) {
        return libs.genUsers(n);
    }

    @GetMapping("/users/table")
    public String usersTable(@RequestParam int n) {
        return libs.formatUsers(n);
    }

    @GetMapping("/paragraphs")
    public String paragraphs(@RequestParam int n) {
        return libs.fakeParagraphs(n);
    }
}
