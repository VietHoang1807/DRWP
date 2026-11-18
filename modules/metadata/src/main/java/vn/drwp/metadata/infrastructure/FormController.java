package vn.drwp.metadata.infrastructure;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import vn.drwp.metadata.application.FormService;
import vn.drwp.metadata.application.FormService.FormView;
import vn.drwp.metadata.application.FormService.VersionView;
import vn.drwp.platform.kernel.error.Violation;
import vn.drwp.platform.runtime.ConditionalOnRuntimeRole;
import vn.drwp.platform.runtime.RuntimeRole;
import vn.drwp.platform.web.security.CurrentActor;

@RestController
@RequestMapping("/api/metadata/forms")
@ConditionalOnRuntimeRole(RuntimeRole.API)
class FormController {

    private final FormService forms;

    FormController(FormService forms) {
        this.forms = forms;
    }

    record CreateFormRequest(@NotBlank String code, @NotBlank String name, String description,
                             @NotNull Map<String, Object> schema) {
    }

    record UpdateFormRequest(String name, String description, String status) {
    }

    record SchemaRequest(Map<String, Object> schema) {
    }

    record ValidateRequest(Map<String, Object> data, boolean full) {
    }

    @GetMapping
    List<FormView> list() {
        return forms.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    VersionView create(@Valid @RequestBody CreateFormRequest r) {
        return forms.create(CurrentActor.require(), r.code(), r.name(), r.description(), r.schema());
    }

    @GetMapping("/{code}")
    FormView get(@PathVariable String code) {
        return forms.get(code);
    }

    @PatchMapping("/{code}")
    FormView update(@PathVariable String code, @RequestBody UpdateFormRequest r) {
        return forms.update(CurrentActor.require(), code, r.name(), r.description(), r.status());
    }

    /** Phiên bản PUBLISHED mới nhất (để render form nhập liệu). */
    @GetMapping("/{code}/published")
    VersionView published(@PathVariable String code) {
        return forms.latestPublished(code);
    }

    @GetMapping("/{code}/versions")
    List<VersionView> versions(@PathVariable String code) {
        return forms.versions(CurrentActor.require(), code);
    }

    @PostMapping("/{code}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    VersionView newDraft(@PathVariable String code, @RequestBody(required = false) SchemaRequest r) {
        return forms.newDraft(CurrentActor.require(), code, r == null ? null : r.schema());
    }

    @GetMapping("/{code}/versions/{versionNo}")
    VersionView version(@PathVariable String code, @PathVariable int versionNo) {
        return forms.version(CurrentActor.require(), code, versionNo);
    }

    @PutMapping("/{code}/versions/{versionNo}")
    VersionView updateDraft(@PathVariable String code, @PathVariable int versionNo, @RequestBody SchemaRequest r) {
        return forms.updateDraft(CurrentActor.require(), code, versionNo, r.schema());
    }

    @DeleteMapping("/{code}/versions/{versionNo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteDraft(@PathVariable String code, @PathVariable int versionNo) {
        forms.deleteDraft(CurrentActor.require(), code, versionNo);
    }

    @PostMapping("/{code}/versions/{versionNo}/publish")
    VersionView publish(@PathVariable String code, @PathVariable int versionNo) {
        return forms.publish(CurrentActor.require(), code, versionNo);
    }

    @PostMapping("/{code}/versions/{versionNo}/deprecate")
    VersionView deprecate(@PathVariable String code, @PathVariable int versionNo) {
        return forms.deprecate(CurrentActor.require(), code, versionNo);
    }

    @PostMapping("/{code}/versions/{versionNo}/validate")
    Map<String, Object> validate(@PathVariable String code, @PathVariable int versionNo, @RequestBody ValidateRequest r) {
        List<Violation> violations = forms.preview(code, versionNo, r.data(), r.full());
        return Map.of("valid", violations.isEmpty(), "violations", violations);
    }
}
