package vn.drwp.file.infrastructure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import vn.drwp.file.application.FileService;
import vn.drwp.file.application.FileService.Download;
import vn.drwp.file.application.FileService.FileView;
import vn.drwp.platform.runtime.ConditionalOnRuntimeRole;
import vn.drwp.platform.runtime.RuntimeRole;
import vn.drwp.platform.web.security.CurrentActor;

@RestController
@RequestMapping("/api/files")
@ConditionalOnRuntimeRole(RuntimeRole.API)
class FileController {

    private final FileService files;

    FileController(FileService files) {
        this.files = files;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    FileView upload(@RequestPart("file") MultipartFile file, @RequestParam(required = false) UUID recordId) throws IOException {
        return files.upload(CurrentActor.require(), recordId, file.getOriginalFilename(), file.getContentType(), file.getInputStream());
    }

    @GetMapping("/{id}")
    FileView metadata(@PathVariable UUID id) {
        return files.metadata(CurrentActor.require(), id);
    }

    @GetMapping
    List<FileView> ofRecord(@RequestParam UUID recordId) {
        return files.ofRecord(CurrentActor.require(), recordId);
    }

    @GetMapping("/{id}/content")
    ResponseEntity<InputStreamResource> content(@PathVariable UUID id) {
        Download d = files.download(CurrentActor.require(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(d.meta().name(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.parseMediaType(d.meta().contentType()))
                .contentLength(d.meta().sizeBytes())
                .body(new InputStreamResource(d.content()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        files.delete(CurrentActor.require(), id);
    }
}
