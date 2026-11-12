package com.retail.notification.web;

import com.retail.notification.domain.NotificationStatus;
import com.retail.notification.dto.*;
import com.retail.notification.service.NotificationAdminService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationAdminController {

    private final NotificationAdminService admin;

    public NotificationAdminController(NotificationAdminService admin) {
        this.admin = admin;
    }

    @GetMapping
    public PageResponse<NotificationSummary> list(@RequestParam(required = false) NotificationStatus status,
                                                  @RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size) {
        return admin.list(status, page, size);
    }

    /** "templates" is a fixed word, so it wins over the {id} path below. */
    @GetMapping("/templates")
    public List<TemplateResponse> templates() {
        return admin.templates();
    }

    @PutMapping("/templates/{code}")
    public TemplateResponse updateTemplate(@PathVariable String code, @Valid @RequestBody TemplateUpdateRequest request,
                                           Authentication authentication) {
        return admin.updateTemplate(code, request, authentication.getName());        // the admin's email
    }

    @GetMapping("/{id}")
    public NotificationDetail get(@PathVariable Long id) {
        return admin.get(id);
    }

    @PostMapping("/{id}/retry")
    public NotificationDetail retry(@PathVariable Long id) {
        return admin.retry(id);
    }
}